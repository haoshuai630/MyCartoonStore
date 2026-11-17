package org.csu.mycartoonstore.order.service; // 发货：状态迁移、幂等与错误码。

import org.csu.mycartoonstore.common.exception.BizException; // 业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.order.domain.ShipResult; // 发货结局。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单实体。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 行锁与写回。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.junit.jupiter.api.extension.ExtendWith; // Mockito 扩展。
import org.mockito.ArgumentCaptor; // 捕获写回对象。
import org.mockito.InjectMocks; // 被测对象。
import org.mockito.Mock; // 协作方。
import org.mockito.junit.jupiter.MockitoExtension; // Mockito 生命周期。

import java.time.Clock; // 固定时钟。
import java.time.Instant; // 时间点。
import java.time.LocalDateTime; // 发货时间断言。
import java.time.ZoneOffset; // 东八区。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.Mockito.never; // 不该发生的调用。
import static org.mockito.Mockito.verify; // 行为断言。
import static org.mockito.Mockito.when; // 打桩。

// M07-08：依据 docs/01 FR-A-06、docs/06 AC-29。
// 覆盖：PAID → SHIPPED 并写首次时间、SHIPPED/COMPLETED 幂等、待支付/已取消 409、不存在 404。
// 不覆盖：页面跳转与提示（OrderAdminPageTest）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class ShipServiceTest {

    /** 固定时间：断言 shipped_at 精确到这个时刻 */
    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z"); // UTC 15:00 = 东八区 23:00。

    @Mock // 订单 Mapper。
    private SalesOrderMapper orderMapper;

    /** 被测服务：时钟固定，发货时间可精确断言 */
    private ShipService service() {
        return new ShipService(orderMapper, Clock.fixed(NOW, ZoneOffset.ofHours(8))); // 每次构造一个新实例。
    }

    @Test // PAID：迁移成功并写首次发货时间。
    @DisplayName("已支付订单：发货成功并写 shipped_at")
    void shipPaid() {
        when(orderMapper.lockByOrderNo("CO-1")).thenReturn(order("CO-1", "PAID")); // 待发货订单。
        ArgumentCaptor<SalesOrder> saved = ArgumentCaptor.forClass(SalesOrder.class); // 捕获写回。

        ShipResult result = service().ship("CO-1"); // 发货。

        assertThat(result).isEqualTo(ShipResult.SHIPPED); // 本次真的发货了。
        verify(orderMapper).updateById(saved.capture()); // 写回一次。
        assertThat(saved.getValue().getStatus()).isEqualTo("SHIPPED"); // 状态迁移。
        assertThat(saved.getValue().getShippedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 17, 23, 0)); // 东八区时间。
    }

    @Test // 已发货：幂等，不改时间。
    @DisplayName("已发货订单：幂等，不重复写")
    void shipShippedIsIdempotent() {
        when(orderMapper.lockByOrderNo("CO-2")).thenReturn(order("CO-2", "SHIPPED")); // 已经发过。

        assertThat(service().ship("CO-2")).isEqualTo(ShipResult.ALREADY_SHIPPED); // 提示已处理。
        verify(orderMapper, never()).updateById(org.mockito.ArgumentMatchers.any(SalesOrder.class)); // 不写库。
    }

    @Test // 已完成：同样视为已处理。
    @DisplayName("已完成订单：幂等")
    void shipCompletedIsIdempotent() {
        when(orderMapper.lockByOrderNo("CO-3")).thenReturn(order("CO-3", "COMPLETED")); // 已签收。

        assertThat(service().ship("CO-3")).isEqualTo(ShipResult.ALREADY_SHIPPED); // 不再发货。
    }

    @Test // 待支付：409。
    @DisplayName("待支付订单：409")
    void shipPendingRejected() {
        when(orderMapper.lockByOrderNo("CO-4")).thenReturn(order("CO-4", "PENDING_PAYMENT")); // 未支付。

        assertThatThrownBy(() -> service().ship("CO-4"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test // 已取消：409。
    @DisplayName("已取消订单：409")
    void shipCancelledRejected() {
        when(orderMapper.lockByOrderNo("CO-5")).thenReturn(order("CO-5", "CANCELLED")); // 已取消。

        assertThatThrownBy(() -> service().ship("CO-5"))
                .isInstanceOf(BizException.class)
                .hasMessage("当前状态不可发货");
    }

    @Test // 不存在：404。
    @DisplayName("订单不存在：404")
    void shipMissing() {
        when(orderMapper.lockByOrderNo("NOPE")).thenReturn(null); // 查不到。

        assertThatThrownBy(() -> service().ship("NOPE"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 造订单：只填发货用得到的字段 */
    private SalesOrder order(String orderNo, String status) {
        SalesOrder order = new SalesOrder(); // 空实体。
        order.setId(1L); // 主键。
        order.setOrderNo(orderNo); // 单号。
        order.setStatus(status); // 状态。
        return order; // 交给用例。
    }
}
