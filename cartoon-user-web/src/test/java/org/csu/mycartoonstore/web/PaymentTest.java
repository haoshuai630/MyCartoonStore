package org.csu.mycartoonstore.web; // 模拟支付：AC-20 的状态、凭证、库存与归属。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/车条目。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造订单与核对。
import org.springframework.mock.web.MockHttpSession; // 带登录身份的会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import java.math.BigDecimal; // 金额。
import java.time.LocalDateTime; // paid_at 类型。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 断言重定向。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M06-03：依据 docs/06 AC-20、docs/01 FR-P-01 第 3～6 条。
// 覆盖：成功写三件套且不扣库存、失败无凭证、失败后重试成功、再次成功幂等、
//       他人 404、已取消/已发货 409、下单后改价下架仍可支付、购物车不被回填。
// 不覆盖：页面呈现（M06-02 的 PaymentPageTest）与并发（PaymentConcurrencyTest）。
@AutoConfigureMockMvc // 不占端口。
class PaymentTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造订单与核对。

    /** 成功：PAID + MOCK + 凭证 + 时间；库存、版本、购物车都不变。 */
    @Test
    @DisplayName("支付成功：写三件套，库存不再扣")
    void successWritesCredentialsAndKeepsStock() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_ok"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P01", "28.00", 10, "ON_SALE"); // 商品。
        CartFixture.addCartItem(jdbc, userId, productId, 2); // 车里还有 2 件（与订单无关）。
        seedOrder(userId, "CO202611171600000001", "PENDING_PAYMENT", productId, 2, 8, 1); // 待支付，库存已扣到 8。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000001")
                        .session(CartFixture.session(userId))) // 模拟支付成功。
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/orders/CO202611171600000001")); // 回详情。

        assertThat(statusOf("CO202611171600000001")).isEqualTo("PAID"); // 状态迁移。
        assertThat(stringValue("SELECT payment_method FROM sales_order WHERE order_no = ?",
                "CO202611171600000001")).isEqualTo("MOCK"); // 教学模拟。
        assertThat(stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000001")).startsWith("PAY"); // 服务端生成的凭证。
        assertThat(value("SELECT paid_at FROM sales_order WHERE order_no = ?",
                LocalDateTime.class, "CO202611171600000001")).isNotNull(); // 支付时间。
        assertThat(intValue("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(8); // 库存不再扣。
        assertThat(intValue("SELECT version FROM product WHERE id = ?", productId)).isEqualTo(1); // 版本不变。
        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1); // 购物车不回填、不清空。
    }

    /** 失败：仍是待支付，三件套为空，库存不变。 */
    @Test
    @DisplayName("支付失败：无凭证，仍是待支付")
    void failureLeavesPendingWithoutCredentials() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_fail"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P02", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171600000002", "PENDING_PAYMENT", productId, 2, 8, 1); // 待支付。

        mockMvc.perform(post("/payment/{no}/failure", "CO202611171600000002")
                        .session(CartFixture.session(userId))) // 模拟支付失败。
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/payment/CO202611171600000002")); // 回支付页。

        assertThat(statusOf("CO202611171600000002")).isEqualTo("PENDING_PAYMENT"); // 状态不变。
        assertThat(stringValue("SELECT payment_method FROM sales_order WHERE order_no = ?",
                "CO202611171600000002")).isNull(); // 方式为空。
        assertThat(stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000002")).isNull(); // 凭证为空。
        assertThat(value("SELECT paid_at FROM sales_order WHERE order_no = ?",
                LocalDateTime.class, "CO202611171600000002")).isNull(); // 支付时间为空。
        assertThat(intValue("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(8); // 库存不变。
    }

    /** 失败后重试成功：只有一次状态迁移，库存只按订单量扣过一次。 */
    @Test
    @DisplayName("失败后重试成功：状态 PAID，库存不变")
    void failureThenSuccessPaysOnce() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_retry"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P03", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171600000003", "PENDING_PAYMENT", productId, 2, 8, 1); // 待支付。
        MockHttpSession session = CartFixture.session(userId); // 同一会话。

        mockMvc.perform(post("/payment/{no}/failure", "CO202611171600000003").session(session)) // 先失败。
                .andExpect(status().is3xxRedirection()); // 可重试。
        assertThat(statusOf("CO202611171600000003")).isEqualTo("PENDING_PAYMENT"); // 仍待支付。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000003").session(session)) // 再成功。
                .andExpect(status().is3xxRedirection()); // PRG。

        assertThat(statusOf("CO202611171600000003")).isEqualTo("PAID"); // 迁移成功。
        assertThat(stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000003")).startsWith("PAY"); // 这时才有凭证。
        assertThat(intValue("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(8); // 库存不变。
    }

    /** 再次成功：幂等，凭证与支付时间保持首次的值。 */
    @Test
    @DisplayName("再次支付：凭证与时间不变")
    void successTwiceKeepsFirstCredentials() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_twice"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P04", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171600000004", "PENDING_PAYMENT", productId, 1, 9, 1); // 待支付。
        MockHttpSession session = CartFixture.session(userId); // 同一会话。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000004").session(session)) // 第一次支付。
                .andExpect(status().is3xxRedirection()); // 成功。
        String firstNo = stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000004"); // 首次凭证。
        LocalDateTime firstPaidAt = value("SELECT paid_at FROM sales_order WHERE order_no = ?",
                LocalDateTime.class, "CO202611171600000004"); // 首次支付时间。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000004").session(session)) // 再点一次。
                .andExpect(status().is3xxRedirection()) // 幂等成功。
                .andExpect(redirectedUrl("/orders/CO202611171600000004")); // 回详情。

        assertThat(statusOf("CO202611171600000004")).isEqualTo("PAID"); // 仍是已支付。
        assertThat(stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000004")).isEqualTo(firstNo); // 凭证没被换掉。
        assertThat(value("SELECT paid_at FROM sales_order WHERE order_no = ?",
                LocalDateTime.class, "CO202611171600000004")).isEqualTo(firstPaidAt); // 时间没被改写。
    }

    /** 他人订单：404，状态与凭证不变。 */
    @Test
    @DisplayName("他人订单支付：404")
    void otherUsersOrderCannotBePaid() throws Exception {
        long owner = CartFixture.createUser(jdbc, "pt_owner"); // 订单归属人。
        long other = CartFixture.createUser(jdbc, "pt_other"); // 另一个人。
        long productId = CartFixture.createProduct(jdbc, "PT-P05", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(owner, "CO202611171600000005", "PENDING_PAYMENT", productId, 1, 9, 1); // 他人待支付单。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000005")
                        .session(CartFixture.session(other))) // 别人来付。
                .andExpect(status().isNotFound()); // 统一 404。

        assertThat(statusOf("CO202611171600000005")).isEqualTo("PENDING_PAYMENT"); // 状态不变。
        assertThat(stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000005")).isNull(); // 没有凭证。
    }

    /** 已取消订单：409，库存不会被再恢复一次。 */
    @Test
    @DisplayName("已取消订单支付：409")
    void cancelledOrderCannotBePaid() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_cancel"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P06", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171600000006", "CANCELLED", productId, 2, 10, 2); // 已取消，库存已恢复。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000006")
                        .session(CartFixture.session(userId))) // 试图支付已取消订单。
                .andExpect(status().isConflict()); // 409。

        assertThat(statusOf("CO202611171600000006")).isEqualTo("CANCELLED"); // 状态不变。
        assertThat(intValue("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(10); // 库存不再加。
        assertThat(stringValue("SELECT payment_no FROM sales_order WHERE order_no = ?",
                "CO202611171600000006")).isNull(); // 仍无凭证。
    }

    /** 已发货订单：正常流程不会出现，防御性 409。 */
    @Test
    @DisplayName("已发货订单支付：409")
    void shippedOrderCannotBePaid() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_shipped"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P07", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171600000007", "SHIPPED", productId, 1, 9, 1); // 已发货。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000007")
                        .session(CartFixture.session(userId))) // 试图再支付。
                .andExpect(status().isConflict()); // 409。

        assertThat(statusOf("CO202611171600000007")).isEqualTo("SHIPPED"); // 状态不变。
    }

    /** 下单后商品改价、下架：有效待支付订单仍可支付，金额仍取订单总额。 */
    @Test
    @DisplayName("下单后改价下架：仍可支付")
    void priceChangeAfterOrderStillPayable() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pt_price"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PT-P08", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171600000008", "PENDING_PAYMENT", productId, 2, 8, 1); // 待支付。
        jdbc.update("UPDATE product SET price = 99.00, status = 'DRAFT' WHERE id = ?", productId); // 改价 + 下架。

        mockMvc.perform(post("/payment/{no}/success", "CO202611171600000008")
                        .session(CartFixture.session(userId))) // 支付。
                .andExpect(status().is3xxRedirection()); // 成功。

        assertThat(statusOf("CO202611171600000008")).isEqualTo("PAID"); // 不受商品变化影响。
        assertThat(decimalValue("SELECT total_amount FROM sales_order WHERE order_no = ?",
                "CO202611171600000008")).isEqualByComparingTo("56.00"); // 金额仍是下单时总额。
    }

    // ---------------------------------------------------------------- 夹具

    /** 造订单 + 一行明细，并把商品改成"下单后"的库存与版本；按状态补齐支付/取消/发货时间。 */
    private void seedOrder(long owner, String orderNo, String status, long productId,
                           int quantity, int stockAfter, int versionAfter) {
        LocalDateTime created = LocalDateTime.of(2026, 11, 17, 9, 0); // 固定创建时间。
        BigDecimal lineAmount = new BigDecimal("28.00").multiply(BigDecimal.valueOf(quantity)); // 行金额 = 单价 × 数量。
        boolean paid = "PAID".equals(status) || "SHIPPED".equals(status); // 已支付/已发货要有支付三件套。
        boolean cancelled = "CANCELLED".equals(status); // 已取消要有取消时间。
        boolean shipped = "SHIPPED".equals(status); // 已发货要有发货时间。
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at, "
                        + "cancelled_at, shipped_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, owner, status, lineAmount, "张三", "13800000000",
                "某市某区某路 1 号", created,
                paid ? "MOCK" : null, paid ? "PAY" + orderNo : null, paid ? created.plusMinutes(5) : null,
                cancelled ? created.plusMinutes(1) : null, shipped ? created.plusMinutes(30) : null); // 时间字段与状态成组。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 订单 id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/pt.svg",
                new BigDecimal("28.00"), quantity, lineAmount); // 明细行金额由数量决定，满足 CHECK。
        jdbc.update("UPDATE product SET stock = ?, version = ? WHERE id = ?",
                stockAfter, versionAfter, productId); // 模拟下单后的库存与版本。
    }

    private String statusOf(String orderNo) { // 夹具：只取状态。
        return stringValue("SELECT status FROM sales_order WHERE order_no = ?", orderNo); // 订单状态。
    }

    private String stringValue(String sql, Object... args) { // 夹具：字符串单值。
        return jdbc.queryForObject(sql, String.class, args); // 字符串单值。
    }

    private int intValue(String sql, Object... args) { // 夹具：整数单值，空按 0。
        Integer value = jdbc.queryForObject(sql, Integer.class, args); // 整数单值。
        return value == null ? 0 : value; // 空按 0。
    }

    private BigDecimal decimalValue(String sql, Object... args) { // 夹具：金额单值。
        return jdbc.queryForObject(sql, BigDecimal.class, args); // 金额单值。
    }

    private <T> T value(String sql, Class<T> type, Object... args) { // 夹具：任意类型单值。
        return jdbc.queryForObject(sql, type, args); // 任意类型单值。
    }
}
