package org.csu.mycartoonstore.payment.service; // 验证支付状态迁移与幂等分支，不碰数据库。

import org.csu.mycartoonstore.common.exception.BizException; // 断言越权与状态冲突。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 断言 404 / 409。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 被测服务操作的订单实体。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 锁订单行与写回，这里用 Mock。
import org.csu.mycartoonstore.payment.domain.PaymentResult; // 三种结局。
import org.junit.jupiter.api.BeforeEach; // 每个用例前重建 Mock。
import org.junit.jupiter.api.DisplayName; // 用例名进入测试报告。
import org.junit.jupiter.api.Test; // JUnit 用例入口。

import java.math.BigDecimal; // 订单总额。
import java.time.Clock; // 固定时钟让 paid_at 可断言。
import java.time.Instant; // 固定时刻。
import java.time.LocalDateTime; // 构造 created_at / 已支付时间。
import java.time.ZoneOffset; // 东八区：与页面、数据库一致。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.any; // updateById 入参匹配。
import static org.mockito.Mockito.mock; // 造 Mock。
import static org.mockito.Mockito.never; // 断言"没有写库"。
import static org.mockito.Mockito.verify; // 断言调用。
import static org.mockito.Mockito.verifyNoInteractions; // 断言没碰凭证生成器。
import static org.mockito.Mockito.verifyNoMoreInteractions; // 断言只锁行与写回。
import static org.mockito.Mockito.when; // 设定返回值。

// M06-01：依据 docs/01 FR-P-01、docs/02 §7.4、docs/06 AC-20。
// 覆盖：待支付成功写三件套、已支付幂等、已取消/已发货 409、他人/不存在 404、失败不写库。
// 不覆盖页面、路由与库存断言：前者 M06-02，后者由 M06-03 的集成用例从真实入口验证。
class PaymentServiceTest {

    private static final long USER = 7L; // 订单归属人。
    private static final long OTHER_USER = 8L; // 另一个人，用来验证越权。
    private static final String ORDER_NO = "CO202611171200000001"; // 固定订单号。
    private static final Instant NOW = Instant.parse("2026-11-17T04:00:00Z"); // UTC 04:00 = 东八区 12:00。
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 17, 12, 0); // 断言用的支付时间。

    private SalesOrderMapper orderMapper; // 订单 Mapper Mock。
    private PaymentNoGenerator paymentNoGenerator; // 凭证生成器 Mock。
    private PaymentService service; // 被测对象。

    @BeforeEach // 每个用例前重建，避免桩与调用记录互相污染。
    void setUp() {
        orderMapper = mock(SalesOrderMapper.class); // 假 Mapper：只回放锁行结果。
        paymentNoGenerator = mock(PaymentNoGenerator.class); // 假生成器：凭证号由用例指定。
        service = new PaymentService(orderMapper, paymentNoGenerator,
                Clock.fixed(NOW, ZoneOffset.ofHours(8))); // 时钟固定在 12:00。
    }

    /** 待支付 → PAID：写 MOCK、凭证与时间，且只更新订单这一行。 */
    @Test
    @DisplayName("待支付支付成功：PAID、MOCK、凭证与时间都有值")
    void paySuccessMovesPendingToPaid() {
        SalesOrder order = orderOf(USER, "PENDING_PAYMENT"); // 待支付的本人订单。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。
        when(paymentNoGenerator.next()).thenReturn("PAY202611171200000001"); // 凭证号固定，便于断言。

        PaymentResult result = service.paySuccess(USER, ORDER_NO); // 被测动作。

        // 断言：状态、方式、凭证、时间四件套全部落位。
        assertThat(result).isEqualTo(PaymentResult.PAID); // 本次真的支付成功。
        assertThat(order.getStatus()).isEqualTo("PAID"); // 进入待发货。
        assertThat(order.getPaymentMethod()).isEqualTo("MOCK"); // 教学模拟。
        assertThat(order.getPaymentNo()).isEqualTo("PAY202611171200000001"); // 服务端生成的凭证。
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT); // 取注入时钟。
        verify(orderMapper).lockByOrderNo(ORDER_NO); // 先锁行。
        verify(orderMapper).updateById(order); // 只写订单这一次。
        verifyNoMoreInteractions(orderMapper); // 没有多余的库存/明细访问，支付不碰别处。
    }

    /** 已支付再支付：幂等，不重新生成凭证，也不改写首次时间。 */
    @Test
    @DisplayName("已支付再支付：凭证与时间保持首次的值")
    void paySuccessOnPaidKeepsFirstPaymentInfo() {
        SalesOrder order = orderOf(USER, "PAID"); // 已支付：凭证与时间都是首次的。
        order.setPaymentNo("PAY202611170900000123"); // 首次凭证号。
        order.setPaidAt(LocalDateTime.of(2026, 11, 17, 9, 0)); // 首次支付时间。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        PaymentResult result = service.paySuccess(USER, ORDER_NO); // 被测动作：重复提交。

        // 断言：返回"之前已支付"，首次凭证与时间一字未改，且完全没有写库。
        assertThat(result).isEqualTo(PaymentResult.ALREADY_PAID); // 幂等分支。
        assertThat(order.getPaymentNo()).isEqualTo("PAY202611170900000123"); // 凭证没被换掉。
        assertThat(order.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 9, 0)); // 时间没被改写。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 幂等分支不产生 UPDATE。
        verifyNoInteractions(paymentNoGenerator); // 也不会生成新凭证。
    }

    /** 已取消订单不可支付：409，状态不变。 */
    @Test
    @DisplayName("已取消订单支付：409")
    void paySuccessOnCancelledIsConflict() {
        SalesOrder order = orderOf(USER, "CANCELLED"); // 已取消。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        // 断言：409 与"不可支付"文案；没有写库、没有生成凭证。
        assertThatThrownBy(() -> service.paySuccess(USER, ORDER_NO)) // 已取消：拒绝支付。
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT); // 状态冲突。
                    assertThat(e.getMessage()).contains("不可支付"); // 文案说明原因。
                });
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
        verifyNoInteractions(paymentNoGenerator); // 不生成凭证。
    }

    /** 已发货订单不可支付：正常流程不会出现，防御性拒绝。 */
    @Test
    @DisplayName("已发货订单支付：409")
    void paySuccessOnShippedIsConflict() {
        SalesOrder order = orderOf(USER, "SHIPPED"); // 已发货：正常流程不会出现。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        // 断言：409，不写库。
        assertThatThrownBy(() -> service.paySuccess(USER, ORDER_NO)) // 已发货：同样拒绝。
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT)); // 状态冲突。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    /** 他人订单：404，与不存在同码同文案。 */
    @Test
    @DisplayName("他人订单支付：404")
    void paySuccessOnOtherUsersOrderIsNotFound() {
        SalesOrder order = orderOf(OTHER_USER, "PENDING_PAYMENT"); // 属于别人的待支付订单。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        // 断言：404，不写库、不生成凭证。
        assertThatThrownBy(() -> service.paySuccess(USER, ORDER_NO)) // 越权：服务层必须拒绝。
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND)); // 与不存在同码。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
        verifyNoInteractions(paymentNoGenerator); // 不生成凭证。
    }

    /** 不存在的订单：404。 */
    @Test
    @DisplayName("不存在的订单支付：404")
    void paySuccessOnMissingOrderIsNotFound() {
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(null); // 查不到任何订单。

        // 断言：404，不写库。
        assertThatThrownBy(() -> service.paySuccess(USER, ORDER_NO)) // 不存在：统一 404。
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND)); // 不泄露存在性。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    /** 支付失败：订单仍是待支付，三件套保持为空。 */
    @Test
    @DisplayName("支付失败：不写库，凭证仍为空")
    void payFailureKeepsPendingUnchanged() {
        SalesOrder order = orderOf(USER, "PENDING_PAYMENT"); // 待支付、三件套为空。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        PaymentResult result = service.payFailure(USER, ORDER_NO); // 被测动作：显式模拟失败。

        // 断言：结果 FAILED，状态与三件套都没有变化，也没有生成凭证。
        assertThat(result).isEqualTo(PaymentResult.FAILED); // 本次失败。
        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT"); // 仍是待支付。
        assertThat(order.getPaymentNo()).isNull(); // 没有凭证。
        assertThat(order.getPaidAt()).isNull(); // 没有支付时间。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 失败分支绝不能写库。
        verifyNoInteractions(paymentNoGenerator); // 失败不生成凭证。
    }

    /** 已支付订单再点失败：不改任何字段，返回"已支付"让页面改跳详情。 */
    @Test
    @DisplayName("已支付订单点失败：无变化")
    void payFailureOnPaidIsNoOp() {
        SalesOrder order = orderOf(USER, "PAID"); // 已支付：只关心凭证不被改写。
        order.setPaymentNo("PAY202611170900000123"); // 首次凭证号。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        PaymentResult result = service.payFailure(USER, ORDER_NO); // 被测动作。

        // 断言：返回 ALREADY_PAID，凭证不变，不写库。
        assertThat(result).isEqualTo(PaymentResult.ALREADY_PAID); // 已经付过。
        assertThat(order.getPaymentNo()).isEqualTo("PAY202611170900000123"); // 凭证保持原样。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    /** 已取消订单点失败：409，状态不变。 */
    @Test
    @DisplayName("已取消订单点失败：409")
    void payFailureOnCancelledIsConflict() {
        SalesOrder order = orderOf(USER, "CANCELLED"); // 已取消。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        // 断言：409，不写库。
        assertThatThrownBy(() -> service.payFailure(USER, ORDER_NO)) // 已取消：失败动作也拒绝。
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT)); // 状态冲突。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    /** 他人订单点失败：404。 */
    @Test
    @DisplayName("他人订单点失败：404")
    void payFailureOnOtherUsersOrderIsNotFound() {
        SalesOrder order = orderOf(OTHER_USER, "PENDING_PAYMENT"); // 属于别人：越权必须 404。
        when(orderMapper.lockByOrderNo(ORDER_NO)).thenReturn(order); // 锁行拿到这笔单。

        // 断言：404，不写库。
        assertThatThrownBy(() -> service.payFailure(USER, ORDER_NO)) // 越权：失败动作也拒绝。
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND)); // 与不存在同码。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    // ---------------------------------------------------------------- 夹具

    /** 造一笔订单：只填状态与归属，支付三件套默认全空。 */
    private SalesOrder orderOf(long userId, String status) {
        SalesOrder order = new SalesOrder(); // 未落库的订单对象。
        order.setId(1L); // 固定主键。
        order.setOrderNo(ORDER_NO); // 固定订单号。
        order.setUserId(userId); // 归属人。
        order.setStatus(status); // 当前状态。
        order.setTotalAmount(new BigDecimal("56.00")); // 两件 ¥28.00。
        order.setCreatedAt(LocalDateTime.of(2026, 11, 17, 11, 0)); // 支付时间必须晚于创建时间。
        return order; // 交给用例。
    }
}
