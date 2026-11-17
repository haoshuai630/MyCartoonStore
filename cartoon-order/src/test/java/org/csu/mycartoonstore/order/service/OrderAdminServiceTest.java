package org.csu.mycartoonstore.order.service; // 后台订单查询：参数归一与不存在处理。

import org.csu.mycartoonstore.common.exception.BizException; // 业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.order.dto.AdminOrderPage; // 一页结果。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单实体。
import org.csu.mycartoonstore.order.mapper.SalesOrderItemMapper; // 明细。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 订单。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.junit.jupiter.api.extension.ExtendWith; // Mockito 扩展。
import org.mockito.InjectMocks; // 被测对象。
import org.mockito.Mock; // 协作方。
import org.mockito.junit.jupiter.MockitoExtension; // Mockito 生命周期。

import java.util.List; // 列表。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.any; // 任意参数。
import static org.mockito.ArgumentMatchers.anyLong; // 任意 id。
import static org.mockito.Mockito.never; // 不该发生的调用。
import static org.mockito.Mockito.verify; // 行为断言。
import static org.mockito.Mockito.when; // 打桩。

// M07-08：依据 docs/04 §3.4、docs/06 AC-29。
// 覆盖：空参数默认分页、状态白名单、超长单号、详情不存在。
// 不覆盖：真实 SQL 与分页数据（OrderAdminPageTest）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class OrderAdminServiceTest {

    @Mock // 订单 Mapper。
    private SalesOrderMapper orderMapper;

    @Mock // 明细 Mapper。
    private SalesOrderItemMapper itemMapper;

    @InjectMocks // 被测服务。
    private OrderAdminService service;

    @Test // 空参数：第 1 页、每页 10 条、状态 ALL。
    @DisplayName("列表：空参数用默认分页")
    void listDefaults() {
        when(orderMapper.selectCount(any())).thenReturn(0L); // 没有订单。
        when(orderMapper.selectList(any())).thenReturn(List.of()); // 空页。

        AdminOrderPage page = service.list(null, null, null, null); // 什么都不传。

        assertThat(page.getPage()).isEqualTo(1); // 第 1 页。
        assertThat(page.getPageSize()).isEqualTo(10); // 默认 10 条。
        assertThat(page.getStatus()).isEqualTo("ALL"); // 状态回显 ALL。
        assertThat(page.isEmpty()).isTrue(); // 空结果。
    }

    @Test // 非法状态：400。
    @DisplayName("列表：非法状态 → 400")
    void listBadStatus() {
        assertThatThrownBy(() -> service.list("SOLD", null, null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test // 超长订单号：400，不静默截断。
    @DisplayName("列表：超长订单号 → 400")
    void listLongOrderNo() {
        assertThatThrownBy(() -> service.list(null, "N".repeat(41), null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过长");
    }

    @Test // 删除商品前查引用（M10-01）。
    @DisplayName("商品引用计数：按 product_id 数明细")
    void countItemsByProduct() {
        when(itemMapper.selectCount(any())).thenReturn(2L); // 两条明细引用该商品。

        assertThat(service.countItemsByProduct(7L)).isEqualTo(2L); // 原样返回条数。
    }

    // ---------------------------------------------------------------- 修改与删除（M10-03）

    @Test // 待支付订单可改收货三列。
    @DisplayName("改收货信息：待支付可改，只写三列")
    void updateReceiverWritesThreeColumns() {
        when(orderMapper.findByOrderNo("CO1")).thenReturn(order("CO1", 5L, "PENDING_PAYMENT")); // 待支付。
        when(orderMapper.updateReceiver(5L, "李四", "13900000000", "新地址 2 号")).thenReturn(1); // 写成功。

        service.updateReceiver("CO1", " 李四 ", "13900000000", " 新地址 2 号 "); // 去空白后写入。

        verify(orderMapper).updateReceiver(5L, "李四", "13900000000", "新地址 2 号"); // 三列定向更新。
    }

    @Test // 已发货：409，不写库。
    @DisplayName("改收货信息：已发货 → 409")
    void updateReceiverShippedRejected() {
        when(orderMapper.findByOrderNo("CO2")).thenReturn(order("CO2", 6L, "SHIPPED")); // 已发货。

        assertThatThrownBy(() -> service.updateReceiver("CO2", "李四", "139", "地址"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(orderMapper, never()).updateReceiver(anyLong(), any(), any(), any()); // 不写库。
    }

    @Test // 收货人为空：400。
    @DisplayName("改收货信息：收货人为空 → 400")
    void updateReceiverBlankName() {
        when(orderMapper.findByOrderNo("CO3")).thenReturn(order("CO3", 7L, "PAID")); // 待发货。

        assertThatThrownBy(() -> service.updateReceiver("CO3", "   ", "139", "地址"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test // 删除：已取消订单，先删明细再删主表。
    @DisplayName("删除订单：已取消 → 明细与主表都删")
    void deleteCancelledOrder() {
        when(orderMapper.findByOrderNo("CO4")).thenReturn(order("CO4", 8L, "CANCELLED")); // 已取消。
        when(itemMapper.delete(any())).thenReturn(2); // 两条明细。
        when(orderMapper.deleteById(8L)).thenReturn(1); // 主表一条。

        service.delete("CO4"); // 执行删除。

        verify(itemMapper).delete(any()); // 先删明细。
        verify(orderMapper).deleteById(8L); // 再删主表。
    }

    @Test // 非已取消：409，不删任何东西。
    @DisplayName("删除订单：待支付 → 409")
    void deletePendingRejected() {
        when(orderMapper.findByOrderNo("CO5")).thenReturn(order("CO5", 9L, "PENDING_PAYMENT")); // 待支付。

        assertThatThrownBy(() -> service.delete("CO5"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(orderMapper, never()).deleteById(any(java.io.Serializable.class)); // 不误删。
    }

    /** 造一张订单：只填状态判断要用的字段。 */
    private SalesOrder order(String orderNo, long id, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus(status);
        return order;
    }

    @Test // 详情不存在：404。
    @DisplayName("详情：不存在 → 404")
    void detailMissing() {
        when(orderMapper.findByOrderNo("NOPE")).thenReturn(null); // 查不到。

        assertThatThrownBy(() -> service.detail("NOPE"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test // 详情：返回订单与明细。
    @DisplayName("详情：返回订单")
    void detailOk() {
        SalesOrder order = new SalesOrder(); // 空实体。
        order.setId(9L); // 主键。
        order.setOrderNo("CO-9"); // 单号。
        when(orderMapper.findByOrderNo("CO-9")).thenReturn(order); // 查得到。

        assertThat(service.detail("CO-9").getId()).isEqualTo(9L); // 返回同一单。
    }
}
