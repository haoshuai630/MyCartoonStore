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
