package org.csu.mycartoonstore.admin; // 订单处理页面：全站列表、详情与发货（AC-29）。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号、分类、商品。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc / JdbcTemplate。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.mock.web.MockHttpSession; // 管理端会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import java.math.BigDecimal; // 金额。
import java.time.LocalDateTime; // 建单时间。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 断言页面内容。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 断言 302 目标。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M07-08：依据 docs/04 §3.4、docs/06 AC-29。
// 覆盖：全站列表（跨用户）、状态与单号筛选、详情快照与支付信息、PAID 发货、非 PAID 409、重复发货保持首次时间。
// 不覆盖：用户端订单页（M05 已验收）。
@AutoConfigureMockMvc // 不占端口。
class OrderAdminPageTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造单与核对。

    @Test // 列表：跨用户、状态标签与金额。
    @DisplayName("列表：显示全站订单与所属用户")
    void listShowsAllOrders() throws Exception {
        long productId = seedProduct(); // 一件商品，明细要用。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer1"); // 买家一。
        long other = AdminFixture.createCustomer(jdbc, "buyer2"); // 买家二。
        seedOrder("ORD-1", buyer, "PAID", productId, "被测商品", "28.00"); // 待发货。
        seedOrder("ORD-2", other, "PENDING_PAYMENT", productId, "被测商品", "28.00"); // 待支付。

        mockMvc.perform(get("/orders").session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("ORD-1"))) // 第一单。
                .andExpect(content().string(containsString("ORD-2"))) // 第二单。
                .andExpect(content().string(containsString("待发货"))) // 状态标签。
                .andExpect(content().string(containsString("待支付"))) // 状态标签。
                .andExpect(content().string(containsString("测试账号"))); // 所属用户。
    }

    @Test // 状态筛选。
    @DisplayName("列表：按状态筛选")
    void listFiltersByStatus() throws Exception {
        long productId = seedProduct(); // 商品。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer3"); // 买家。
        seedOrder("ORD-P", buyer, "PAID", productId, "商品", "28.00"); // 待发货。
        seedOrder("ORD-W", buyer, "PENDING_PAYMENT", productId, "商品", "28.00"); // 待支付。

        mockMvc.perform(get("/orders").param("status", "PAID").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ORD-P"))) // 命中。
                .andExpect(content().string(not(containsString("ORD-W")))); // 被过滤。
    }

    @Test // 完整单号精确匹配。
    @DisplayName("列表：订单号精确匹配")
    void listFiltersByOrderNo() throws Exception {
        long productId = seedProduct(); // 商品。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer4"); // 买家。
        seedOrder("ORD-EXACT", buyer, "PAID", productId, "商品", "28.00"); // 目标单。
        seedOrder("ORD-OTHER", buyer, "PAID", productId, "商品", "28.00"); // 干扰单。

        mockMvc.perform(get("/orders").param("orderNo", "ORD-EXACT").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ORD-EXACT"))) // 命中。
                .andExpect(content().string(not(containsString("ORD-OTHER")))); // 精确匹配不是前缀。
    }

    @Test // 详情：明细快照、收货信息、支付凭证。
    @DisplayName("详情：快照与支付信息")
    void detailShowsOrder() throws Exception {
        long productId = seedProduct(); // 商品。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer5"); // 买家。
        seedOrder("ORD-D", buyer, "PAID", productId, "详情商品", "28.00"); // 待发货。

        mockMvc.perform(get("/orders/{no}", "ORD-D").session(adminSession()))
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("详情商品"))) // 明细快照名。
                .andExpect(content().string(containsString("张三"))) // 收货人。
                .andExpect(content().string(containsString("MOCK"))) // 支付方式。
                .andExpect(content().string(containsString("PAYORD-D"))) // 支付凭证。
                .andExpect(content().string(containsString("发货"))); // 发货按钮。
    }

    @Test // 发货：PAID → SHIPPED 并写时间。
    @DisplayName("发货：PAID → SHIPPED 并写 shipped_at（AC-29）")
    void shipPaidOrder() throws Exception {
        long productId = seedProduct(); // 商品。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer6"); // 买家。
        seedOrder("ORD-S", buyer, "PAID", productId, "商品", "28.00"); // 待发货。

        mockMvc.perform(post("/orders/{no}/ship", "ORD-S").session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/orders/ORD-S")); // 回详情。

        assertThat(jdbc.queryForObject("SELECT status FROM sales_order WHERE order_no = 'ORD-S'", String.class))
                .isEqualTo("SHIPPED"); // 已发货。
        assertThat(jdbc.queryForObject("SELECT shipped_at FROM sales_order WHERE order_no = 'ORD-S'",
                LocalDateTime.class)).isNotNull(); // 有发货时间。
    }

    @Test // 待支付发货：409。
    @DisplayName("发货：待支付订单 → 409（AC-29）")
    void shipPendingRejected() throws Exception {
        long productId = seedProduct(); // 商品。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer7"); // 买家。
        seedOrder("ORD-N", buyer, "PENDING_PAYMENT", productId, "商品", "28.00"); // 待支付。

        mockMvc.perform(post("/orders/{no}/ship", "ORD-N").session(adminSession()))
                .andExpect(status().isConflict()); // 409 错误页。

        assertThat(jdbc.queryForObject("SELECT status FROM sales_order WHERE order_no = 'ORD-N'", String.class))
                .isEqualTo("PENDING_PAYMENT"); // 状态没变。
    }

    @Test // 重复发货：提示已处理，首次时间不变。
    @DisplayName("发货：重复提交保持首次时间（AC-29）")
    void shipTwiceKeepsFirstTime() throws Exception {
        long productId = seedProduct(); // 商品。
        long buyer = AdminFixture.createCustomer(jdbc, "buyer8"); // 买家。
        seedOrder("ORD-T", buyer, "PAID", productId, "商品", "28.00"); // 待发货。
        mockMvc.perform(post("/orders/{no}/ship", "ORD-T").session(adminSession())); // 第一次发货。
        LocalDateTime first = jdbc.queryForObject("SELECT shipped_at FROM sales_order WHERE order_no = 'ORD-T'",
                LocalDateTime.class); // 首次时间。

        mockMvc.perform(post("/orders/{no}/ship", "ORD-T").session(adminSession())) // 再点一次。
                .andExpect(status().is3xxRedirection()) // 不报错。
                .andExpect(redirectedUrl("/orders/ORD-T"));

        assertThat(jdbc.queryForObject("SELECT shipped_at FROM sales_order WHERE order_no = 'ORD-T'",
                LocalDateTime.class)).isEqualTo(first); // 时间没被改写。
    }

    @Test // 订单不存在：404。
    @DisplayName("详情：不存在的订单 → 404")
    void detailMissing() throws Exception {
        mockMvc.perform(get("/orders/{no}", "NOPE").session(adminSession()))
                .andExpect(status().isNotFound()); // 404 错误页。
    }

    /** 造"作品 + 单行本 + 商品"，返回商品 id */
    private long seedProduct() {
        long top = AdminFixture.createCategory(jdbc, "ORD", null, "订单作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "ORD-COMIC", top, "单行本", 1); // 二级。
        return AdminFixture.createProductIn(jdbc, "ORD-P1", leaf, "28.00", 10, "ON_SALE"); // 商品。
    }

    /**
     * 建一张带一条明细的订单。
     *
     * <p>支付/发货/完成时间按状态成组写入，满足数据库 CHECK：PAID 及以上必须有支付三件套，
     * SHIPPED/COMPLETED 必须有 shipped_at，COMPLETED 还必须有 completed_at。</p>
     */
    private void seedOrder(String orderNo, long userId, String status, long productId,
                           String productName, String total) {
        LocalDateTime created = LocalDateTime.of(2026, 11, 17, 9, 0); // 固定创建时间。
        boolean paid = !"PENDING_PAYMENT".equals(status) && !"CANCELLED".equals(status); // 是否需要支付三件套。
        boolean shipped = "SHIPPED".equals(status) || "COMPLETED".equals(status); // 是否需要发货时间。
        boolean completed = "COMPLETED".equals(status); // 是否需要完成时间。
        boolean cancelled = "CANCELLED".equals(status); // 是否需要取消时间。
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, receiver_phone, "
                        + "receiver_address, created_at, payment_method, payment_no, paid_at, cancelled_at, shipped_at, "
                        + "completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, userId, status, new BigDecimal(total), "张三", "13800000000", "某市某区某路 1 号", created,
                paid ? "MOCK" : null, paid ? "PAY" + orderNo : null, paid ? created.plusMinutes(5) : null,
                cancelled ? created.plusMinutes(1) : null, shipped ? created.plusMinutes(30) : null,
                completed ? created.plusMinutes(60) : null); // 时间字段与状态成组。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 明细要 order_id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, productName, "images/x.svg",
                new BigDecimal(total), 1, new BigDecimal(total)); // 一行为整单金额。
    }

    /** 管理员会话：一个用例里可能取两次，账号只建一次 */
    private MockHttpSession adminSession() {
        Long adminId = jdbc.query("SELECT id FROM store_user WHERE username = 'order_admin'",
                rs -> rs.next() ? rs.getLong(1) : null); // 先查。
        if (adminId == null) {
            adminId = AdminFixture.createAdmin(jdbc, "order_admin"); // 没有才建。
        }
        return AdminFixture.session(adminId); // 带登录态。
    }
}
