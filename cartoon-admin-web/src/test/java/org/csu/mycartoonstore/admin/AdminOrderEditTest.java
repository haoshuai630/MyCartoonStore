package org.csu.mycartoonstore.admin; // 后台订单修改与删除（M10-03）。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号与会话。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入依赖。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造订单与核对。
import org.springframework.mock.web.MockHttpSession; // 管理端会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import java.math.BigDecimal; // 金额。
import java.time.LocalDateTime; // 时间。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造 POST。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 跳转断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M10-03：依据指导书实验内容二「修改订单、删除订单」。
// 覆盖：待支付可改收货信息、已发货 409、已取消可删（明细一并删）、待支付不可删。
// 不覆盖：发货（M07-08 已验收）。
@AutoConfigureMockMvc // 不占端口。
class AdminOrderEditTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造订单与核对。

    @Test // 待支付：改收货信息落库，回详情。
    @DisplayName("改收货信息：待支付订单三列更新")
    void updateReceiverOnPendingOrder() throws Exception {
        String orderNo = seedOrder("PENDING_PAYMENT", null); // 一张待支付订单。

        mockMvc.perform(post("/orders/{no}/receiver", orderNo).with(csrf())
                        .param("receiverName", "李四")
                        .param("receiverPhone", "13900000000")
                        .param("receiverAddress", "新地址 2 号")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/orders/" + orderNo)); // 回详情。

        assertThat(jdbc.queryForObject("SELECT receiver_name FROM sales_order WHERE order_no = ?",
                String.class, orderNo)).isEqualTo("李四"); // 收货人。
        assertThat(jdbc.queryForObject("SELECT receiver_address FROM sales_order WHERE order_no = ?",
                String.class, orderNo)).isEqualTo("新地址 2 号"); // 地址。
        assertThat(jdbc.queryForObject("SELECT total_amount FROM sales_order WHERE order_no = ?",
                BigDecimal.class, orderNo)).isEqualByComparingTo("28.00"); // 金额不变。
    }

    @Test // 已发货：409，收货信息不变。
    @DisplayName("改收货信息：已发货 → 409")
    void updateReceiverOnShippedOrderIsRejected() throws Exception {
        String orderNo = seedOrder("SHIPPED", LocalDateTime.of(2026, 11, 18, 10, 0)); // 已发货。

        mockMvc.perform(post("/orders/{no}/receiver", orderNo).with(csrf())
                        .param("receiverName", "李四")
                        .param("receiverPhone", "13900000000")
                        .param("receiverAddress", "新地址")
                        .session(adminSession()))
                .andExpect(status().isConflict()); // 409。

        assertThat(jdbc.queryForObject("SELECT receiver_name FROM sales_order WHERE order_no = ?",
                String.class, orderNo)).isEqualTo("张三"); // 未改写。
    }

    @Test // 已取消：可以删，明细一并删。
    @DisplayName("删除订单：已取消 → 主表与明细都删")
    void deleteCancelledOrder() throws Exception {
        String orderNo = seedOrder("CANCELLED", null); // 一张已取消订单。

        mockMvc.perform(post("/orders/{no}/delete", orderNo).with(csrf())
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/orders")); // 回列表。

        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE order_no = '" + orderNo + "'")).isZero(); // 主表没了。
        assertThat(count("SELECT COUNT(*) FROM sales_order_item i WHERE i.order_id NOT IN "
                + "(SELECT id FROM sales_order)")).isZero(); // 没有悬空明细。
    }

    @Test // 待支付：不能删，订单还在。
    @DisplayName("删除订单：待支付 → 409")
    void deletePendingOrderIsRejected() throws Exception {
        String orderNo = seedOrder("PENDING_PAYMENT", null); // 待支付。

        mockMvc.perform(post("/orders/{no}/delete", orderNo).with(csrf())
                        .session(adminSession()))
                .andExpect(status().isConflict()); // 409。

        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE order_no = '" + orderNo + "'")).isEqualTo(1); // 仍在。
    }

    /** 造一张订单 + 一条明细；shippedAt 非空时视为已发货，CANCELLED 时补取消时间。 */
    private String seedOrder(String status, LocalDateTime shippedAt) {
        String orderNo = "CO20261118000000" + (1000 + (int) (System.nanoTime() % 1000)); // 每次不同，避免撞唯一键。
        long customerId = AdminFixture.createCustomer(jdbc, "ord_" + orderNo.substring(14)); // 归属顾客。
        LocalDateTime created = LocalDateTime.of(2026, 11, 18, 9, 0); // 固定创建时间。
        LocalDateTime cancelledAt = "CANCELLED".equals(status) ? created.plusMinutes(5) : null; // 已取消要写时间。
        boolean paid = "PAID".equals(status) || "SHIPPED".equals(status) || "COMPLETED".equals(status); // 已支付三件套。
        String paymentNo = paid ? "PAY" + orderNo : null; // 支付凭证。
        LocalDateTime paidAt = paid ? created.plusMinutes(3) : null; // 支付时间。
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at, "
                        + "shipped_at, cancelled_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, customerId, status, new BigDecimal("28.00"), "张三", "13800000000",
                "某市某区某路 1 号", created, paid ? "MOCK" : null, paymentNo, paidAt,
                shippedAt, cancelledAt); // 主表：满足 CHECK 的分组约束。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
        long productId = AdminFixture.createProduct(jdbc, "OK-" + orderNo.substring(14), "28.00", 5, "ON_SALE"); // 明细要有商品。
        jdbc.update("INSERT INTO sales_order_item (order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "OK-1", "商品", "images/products/OK-1/cover.svg",
                new BigDecimal("28.00"), 1, new BigDecimal("28.00")); // 明细。
        return orderNo;
    }

    /** 管理员会话。 */
    private MockHttpSession adminSession() {
        long adminId = AdminFixture.createAdmin(jdbc, "ord_admin"); // 每例新建。
        return AdminFixture.session(adminId); // 已登录态。
    }

    /** 单值计数。 */
    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class); // 单值查询。
        return value == null ? 0 : value; // 空值按 0。
    }
}
