package org.csu.mycartoonstore.order.service; // 确认收货：状态迁移、幂等、归属与错误码。

import org.csu.mycartoonstore.common.exception.BizException; // 业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.order.domain.CompleteResult; // 完成结局。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单实体。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 行锁与写回。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.junit.jupiter.api.extension.ExtendWith; // Mockito 扩展。
import org.mockito.ArgumentCaptor; // 捕获写回对象。
import org.mockito.Mock; // 协作方。
import org.mockito.junit.jupiter.MockitoExtension; // Mockito 生命周期。

import java.time.Clock; // 固定时钟。
import java.time.Instant; // 时间点。
import java.time.LocalDateTime; // 完成时间断言。
import java.time.ZoneOffset; // 东八区。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.any; // 任意写回对象。
import static org.mockito.Mockito.never; // 不该发生的调用。
import static org.mockito.Mockito.verify; // 行为断言。
import static org.mockito.Mockito.when; // 打桩。

// M08-03：依据 docs/01 FR-O-05、docs/06 AC-34。
// 覆盖：SHIPPED → COMPLETED 并写首次时间、已完成幂等、其他状态 409、他人订单与不存在 404。
// 不覆盖：页面按钮与提示（CompleteOrderTest）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class OrderCompletionServiceTest {

    /** 固定时间：断言 completed_at 精确到这个时刻 */
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z"); // UTC 02:00 = 东八区 10:00。

    /** 订单归属用户：与服务调用的 userId 一致 */
    private static final long USER_ID = 7L;

    @Mock // 订单 Mapper。
    private SalesOrderMapper orderMapper;

    /** 被测服务：时钟固定，完成时间可精确断言 */
    private OrderCompletionService service() {
        return new OrderCompletionService(orderMapper, Clock.fixed(NOW, ZoneOffset.ofHours(8))); // 每次构造新实例。
    }

    @Test // SHIPPED：迁移成功并写首次完成时间。
    @DisplayName("已发货订单：完成并写 completed_at")
    void completeShipped() {
        SalesOrder order = order("CO-1", "SHIPPED"); // 已发货订单。
        when(orderMapper.lockByOrderNo("CO-1")).thenReturn(order); // 行锁返回它。
        ArgumentCaptor<SalesOrder> saved = ArgumentCaptor.forClass(SalesOrder.class); // 捕获写回。

        CompleteResult result = service().complete(USER_ID, "CO-1"); // 确认收货。

        assertThat(result).isEqualTo(CompleteResult.COMPLETED); // 本次真的完成了。
        verify(orderMapper).updateById(saved.capture()); // 写回一次。
        assertThat(saved.getValue().getStatus()).isEqualTo("COMPLETED"); // 状态迁移。
        assertThat(saved.getValue().getCompletedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 10, 0)); // 东八区时间。
        assertThat(saved.getValue().getShippedAt()).isNotNull(); // 发货时间保留。
    }

    @Test // 已完成：幂等，不改时间。
    @DisplayName("已完成订单：幂等，不重复写")
    void completeAgainIsIdempotent() {
        when(orderMapper.lockByOrderNo("CO-2")).thenReturn(order("CO-2", "COMPLETED")); // 已经完成。

        assertThat(service().complete(USER_ID, "CO-2")).isEqualTo(CompleteResult.ALREADY_COMPLETED); // 提示已处理。
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    @Test // 待发货：409。
    @DisplayName("待发货订单：409")
    void completePaidRejected() {
        when(orderMapper.lockByOrderNo("CO-3")).thenReturn(order("CO-3", "PAID")); // 还没发货。

        assertThatThrownBy(() -> service().complete(USER_ID, "CO-3"))
                .isInstanceOf(BizException.class)
                .hasMessage("当前状态不可确认收货");
        verify(orderMapper, never()).updateById(any(SalesOrder.class)); // 不写库。
    }

    @Test // 他人订单：404，不泄露存在性。
    @DisplayName("他人订单：404，不泄露是否存在")
    void completeOtherUserRejected() {
        when(orderMapper.lockByOrderNo("CO-4")).thenReturn(order("CO-4", "SHIPPED")); // 属于 USER_ID。

        assertThatThrownBy(() -> service().complete(USER_ID + 1, "CO-4"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test // 不存在：404。
    @DisplayName("订单不存在：404")
    void completeMissing() {
        when(orderMapper.lockByOrderNo("NOPE")).thenReturn(null); // 查不到。

        assertThatThrownBy(() -> service().complete(USER_ID, "NOPE"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 造订单：归属固定、发货时间已存在，完成时间留空 */
    private SalesOrder order(String orderNo, String status) {
        SalesOrder order = new SalesOrder(); // 空实体。
        order.setId(1L); // 主键。
        order.setOrderNo(orderNo); // 单号。
        order.setUserId(USER_ID); // 归属。
        order.setStatus(status); // 状态。
        order.setShippedAt(LocalDateTime.of(2026, 11, 17, 9, 0)); // 发货时间：确认收货不覆盖它。
        return order; // 交给用例。
    }
}
