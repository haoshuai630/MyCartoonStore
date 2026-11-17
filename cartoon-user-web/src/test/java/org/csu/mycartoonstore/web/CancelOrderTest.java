package org.csu.mycartoonstore.web; // 取消订单：库存只恢复一次、状态限制、归属 404、车不回填。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/车条目。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造订单与核对。
import org.springframework.mock.web.MockHttpSession; // 带登录身份的会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取重定向地址。

import java.math.BigDecimal; // 金额。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 内容断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 取消后回详情。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码。

// M05-05：依据 docs/01 FR-O-04、docs/02 §7.5、docs/06 AC-21～23。
// 覆盖：取消恢复库存一次、重复取消幂等、已支付不可取消、归属 404、车不回填、按钮只在待支付出现。
// 不覆盖：支付（M06）。
@AutoConfigureMockMvc // 不占端口。
class CancelOrderTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造订单与核对。

    /** 取消待支付订单：库存 +2、版本 +1、状态 CANCELLED、车不回填。 */
    @Test
    @DisplayName("取消待支付订单：库存恢复一次、车不回填")
    void cancelPendingOrderRestoresStockOnce() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cx_ok"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CX-P01", "28.00", 10, "ON_SALE"); // 库存 10。
        CartFixture.addCartItem(jdbc, userId, productId, 2); // 买 2 件。
        MockHttpSession session = CartFixture.session(userId); // 同一个会话。
        mockMvc.perform(get("/checkout").session(session)).andExpect(status().isOk()); // 生成快照。
        MvcResult placed = mockMvc.perform(post("/checkout").session(session) // 下单。
                        .param("receiverName", "张三")
                        .param("receiverPhone", "13800000000")
                        .param("receiverAddress", "某市某区某路 1 号"))
                .andExpect(status().is3xxRedirection()) // 跳详情。
                .andReturn(); // 取跳转地址。
        String orderNo = placed.getResponse().getRedirectedUrl().substring("/orders/".length()); // 订单号。
        assertThat(stock(productId)).isEqualTo(8); // 下单后库存 8。

        mockMvc.perform(post("/orders/{no}/cancel", orderNo).session(session)) // 取消。
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/orders/" + orderNo)); // 回详情。

        assertThat(statusOf(orderNo)).isEqualTo("CANCELLED"); // 状态迁移。
        assertThat(cancelledAtOf(orderNo)).isNotNull(); // 取消时间已写。
        assertThat(stock(productId)).isEqualTo(10); // 库存 +2。
        assertThat(version(productId)).isEqualTo(2); // 下单 +1、取消 +1。
        assertThat(CartFixture.countItems(jdbc, userId)).isZero(); // 车不自动回填。
    }

    /** 重复取消：第二次不再恢复库存，提示"无需重复操作"。 */
    @Test
    @DisplayName("重复取消：只恢复一次库存")
    void cancelTwiceRestoresOnlyOnce() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cx_twice"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CX-P02", "28.00", 10, "ON_SALE"); // 库存 10。
        seedOrder(userId, "CO202611161000000061", "PENDING_PAYMENT", productId, 2, 8); // 模拟已扣 2 件。
        MockHttpSession session = CartFixture.session(userId); // 会话。

        mockMvc.perform(post("/orders/{no}/cancel", "CO202611161000000061").session(session)) // 第一次取消。
                .andExpect(status().is3xxRedirection()); // 成功。
        mockMvc.perform(get("/orders/{no}", "CO202611161000000061").session(session)) // 看第一次提示。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("库存已恢复"))); // 第一次是真取消。
        mockMvc.perform(post("/orders/{no}/cancel", "CO202611161000000061").session(session)) // 第二次取消。
                .andExpect(status().is3xxRedirection()); // 幂等，不报错。
        mockMvc.perform(get("/orders/{no}", "CO202611161000000061").session(session)) // 看第二次提示。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("无需重复操作"))); // 第二次是幂等提示。

        assertThat(stock(productId)).isEqualTo(10); // 只加了一次。
        assertThat(version(productId)).isEqualTo(1); // 版本也只加一次。
    }

    /** 已支付订单不可取消：409，状态与库存都不变。 */
    @Test
    @DisplayName("已支付订单不可取消：409")
    void cancelPaidOrderRejected() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cx_paid"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CX-P03", "28.00", 8, "ON_SALE"); // 已扣库存 8。
        seedOrder(userId, "CO202611161000000062", "PAID", productId, 2, 8); // 已支付订单。

        mockMvc.perform(post("/orders/{no}/cancel", "CO202611161000000062")
                        .session(CartFixture.session(userId))) // 取消。
                .andExpect(status().isConflict()); // 409。

        assertThat(statusOf("CO202611161000000062")).isEqualTo("PAID"); // 状态不变。
        assertThat(stock(productId)).isEqualTo(8); // 库存不变。
    }

    /** 他人订单：404，状态与库存都不变。 */
    @Test
    @DisplayName("他人订单不可取消：404")
    void cancelOtherUsersOrderIs404() throws Exception {
        long owner = CartFixture.createUser(jdbc, "cx_owner"); // 订单归属人。
        long other = CartFixture.createUser(jdbc, "cx_other"); // 另一个人。
        long productId = CartFixture.createProduct(jdbc, "CX-P04", "28.00", 8, "ON_SALE"); // 已扣库存。
        seedOrder(owner, "CO202611161000000063", "PENDING_PAYMENT", productId, 2, 8); // 他人待支付单。

        mockMvc.perform(post("/orders/{no}/cancel", "CO202611161000000063")
                        .session(CartFixture.session(other))) // 别人来取消。
                .andExpect(status().isNotFound()); // 统一 404。

        assertThat(statusOf("CO202611161000000063")).isEqualTo("PENDING_PAYMENT"); // 状态不变。
        assertThat(stock(productId)).isEqualTo(8); // 库存不变。
    }

    /** 不存在的订单：404。 */
    @Test
    @DisplayName("不存在的订单：404")
    void cancelMissingOrderIs404() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cx_missing"); // 建账号。

        mockMvc.perform(post("/orders/{no}/cancel", "CO00000000000000")
                        .session(CartFixture.session(userId))) // 取消不存在的单。
                .andExpect(status().isNotFound()); // 404。
    }

    /** 详情页：待支付显示取消按钮，已支付不显示。 */
    @Test
    @DisplayName("详情页：只有待支付显示取消按钮")
    void detailShowsCancelOnlyWhenPending() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cx_button"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CX-P05", "28.00", 8, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611161000000064", "PENDING_PAYMENT", productId, 1, 8); // 待支付。
        seedOrder(userId, "CO202611161000000065", "PAID", productId, 1, 8); // 已支付。

        mockMvc.perform(get("/orders/{no}", "CO202611161000000064").session(CartFixture.session(userId))) // 待支付详情。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("取消订单"))); // 有按钮。

        mockMvc.perform(get("/orders/{no}", "CO202611161000000065").session(CartFixture.session(userId))) // 已支付详情。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(not(containsString("取消订单")))); // 无按钮。
    }

    /** 取消后：待支付页签不再有它，全部页签显示已取消。 */
    @Test
    @DisplayName("取消后：从待支付页签消失、全部里显示已取消")
    void cancelledOrderMovesOutOfPendingTab() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cx_tab"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CX-P06", "28.00", 8, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611161000000066", "PENDING_PAYMENT", productId, 1, 8); // 待支付。
        MockHttpSession session = CartFixture.session(userId); // 会话。

        mockMvc.perform(post("/orders/{no}/cancel", "CO202611161000000066").session(session)) // 取消。
                .andExpect(status().is3xxRedirection()); // 成功。

        mockMvc.perform(get("/orders").param("status", "PENDING_PAYMENT").session(session)) // 待支付页签。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(not(containsString("CO202611161000000066")))); // 不在待支付。
        mockMvc.perform(get("/orders").session(session)) // 全部页签。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("CO202611161000000066"))) // 还在全部。
                .andExpect(content().string(containsString("已取消"))); // 状态已变。
    }

    // ---------------------------------------------------------------- 夹具

    /** 造一单 + 一行明细，并把商品库存改成"已扣"后的值。 */
    private void seedOrder(long owner, String orderNo, String status, long productId, int quantity, int stockAfter) {
        String paymentNo = "PAID".equals(status) ? "PAY" + orderNo : null; // 已支付要有凭证。
        java.time.LocalDateTime created = java.time.LocalDateTime.of(2026, 11, 17, 9, 0); // 固定创建时间。
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, receiver_phone, "
                        + "receiver_address, created_at, payment_method, payment_no, paid_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, owner, status, new BigDecimal("56.00"), "张三", "13800000000",
                "某市某区某路 1 号", created,
                paymentNo == null ? null : "MOCK", paymentNo,
                paymentNo == null ? null : created.plusMinutes(5)); // 支付时间。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 订单 id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/cx.svg",
                new BigDecimal("28.00"), quantity, new BigDecimal("28.00").multiply(BigDecimal.valueOf(quantity))); // 明细。
        jdbc.update("UPDATE product SET stock = ? WHERE id = ?", stockAfter, productId); // 模拟下单已扣库存。
    }

    private String statusOf(String orderNo) {
        return jdbc.queryForObject("SELECT status FROM sales_order WHERE order_no = ?", String.class, orderNo); // 状态。
    }

    private java.time.LocalDateTime cancelledAtOf(String orderNo) {
        return jdbc.queryForObject("SELECT cancelled_at FROM sales_order WHERE order_no = ?",
                java.time.LocalDateTime.class, orderNo); // 取消时间。
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId); // 库存。
    }

    private int version(long productId) {
        return jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Integer.class, productId); // 版本。
    }
}
