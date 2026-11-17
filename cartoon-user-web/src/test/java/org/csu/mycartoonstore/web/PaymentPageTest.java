package org.csu.mycartoonstore.web; // 模拟支付页：渲染、状态分流、两个动作与入口。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/会话。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造订单。
import org.springframework.mock.web.MockHttpSession; // 同一个会话：一次性提示存在会话里。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import java.math.BigDecimal; // 订单总额。
import java.time.LocalDateTime; // create_at / paid_at。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 内容断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 断言重定向。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern; // 断言登录回跳。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M06-02：依据 docs/04 §2.4、docs/05 §4.7/§4.8、docs/01 FR-P-01 第 1/2/7 条。
// 覆盖：支付页金额与声明、已支付跳详情、已取消 409、他人/不存在 404、游客拦截、失败回支付页、入口只对待支付出现。
// 不覆盖状态迁移与凭证内容（M06-03 的 AC-20 集成用例）。
@AutoConfigureMockMvc // 不占端口。
class PaymentPageTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造订单与核对。

    /** 支付页：大字金额、教学声明、两个按钮，且不出现第三方渠道。 */
    @Test
    @DisplayName("支付页展示金额、声明与两个按钮")
    void paymentPageShowsAmountAndDeclaration() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pay_page"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PAY-P01", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171500000001", "PENDING_PAYMENT", productId); // 待支付订单。

        mockMvc.perform(get("/payment/{no}", "CO202611171500000001").session(CartFixture.session(userId))) // 打开支付页。
                .andExpect(status().isOk()) // 正常渲染。
                .andExpect(content().string(containsString("¥56.00"))) // 大字金额取订单总额。
                .andExpect(content().string(containsString("教学模拟支付，不涉及真实资金"))) // 教学声明。
                .andExpect(content().string(containsString("模拟支付成功"))) // 成功按钮。
                .andExpect(content().string(containsString("模拟支付失败"))) // 失败按钮。
                .andExpect(content().string(not(containsString("支付宝")))) // 不出现真实渠道。
                .andExpect(content().string(not(containsString("微信支付")))); // 同上。
    }

    /** 已支付订单：不再展示支付页，直接回详情。 */
    @Test
    @DisplayName("已支付订单：302 回详情")
    void paidOrderRedirectsToDetail() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pay_paid"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PAY-P02", "28.00", 8, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171500000002", "PAID", productId); // 已支付订单。

        mockMvc.perform(get("/payment/{no}", "CO202611171500000002").session(CartFixture.session(userId))) // 打开支付页。
                .andExpect(status().is3xxRedirection()) // 重定向。
                .andExpect(redirectedUrl("/orders/CO202611171500000002")); // 回详情。
    }

    /** 已取消订单：409，不渲染支付页。 */
    @Test
    @DisplayName("已取消订单：409")
    void cancelledOrderIsConflict() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pay_cancel"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PAY-P03", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171500000003", "CANCELLED", productId); // 已取消订单。

        mockMvc.perform(get("/payment/{no}", "CO202611171500000003").session(CartFixture.session(userId))) // 打开支付页。
                .andExpect(status().isConflict()); // 409。
    }

    /** 他人订单：404，与不存在同码同文案。 */
    @Test
    @DisplayName("他人订单支付页：404")
    void otherUsersOrderIsNotFound() throws Exception {
        long owner = CartFixture.createUser(jdbc, "pay_owner"); // 订单归属人。
        long other = CartFixture.createUser(jdbc, "pay_other"); // 另一个人。
        long productId = CartFixture.createProduct(jdbc, "PAY-P04", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(owner, "CO202611171500000004", "PENDING_PAYMENT", productId); // 他人待支付订单。

        mockMvc.perform(get("/payment/{no}", "CO202611171500000004").session(CartFixture.session(other))) // 别人来访问。
                .andExpect(status().isNotFound()); // 统一 404。
    }

    /** 不存在的订单：404。 */
    @Test
    @DisplayName("不存在的订单支付页：404")
    void missingOrderIsNotFound() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pay_missing"); // 建账号。

        mockMvc.perform(get("/payment/{no}", "CO00000000000000").session(CartFixture.session(userId))) // 不存在的单。
                .andExpect(status().isNotFound()); // 404。
    }

    /** 游客打开支付页：拦到登录并带回跳。 */
    @Test
    @DisplayName("游客打开支付页：拦到登录页")
    void guestIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/payment/{no}", "CO202611171500000001")) // 不带会话。
                .andExpect(status().is3xxRedirection()) // 302。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fpayment*")); // 登录后回支付页。
    }

    /** 模拟失败：回支付页并提示可重试，订单仍是待支付。 */
    @Test
    @DisplayName("模拟失败：回支付页提示可重试")
    void failureReturnsToPaymentPage() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pay_fail"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PAY-P05", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171500000005", "PENDING_PAYMENT", productId); // 待支付订单。
        MockHttpSession session = CartFixture.session(userId); // 同一个会话：一次性提示存在会话里。

        mockMvc.perform(post("/payment/{no}/failure", "CO202611171500000005").with(csrf())
                        .session(session)) // 点"模拟支付失败"。
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/payment/CO202611171500000005")); // 回支付页。

        mockMvc.perform(get("/payment/{no}", "CO202611171500000005").session(session)) // 再看支付页。
                .andExpect(status().isOk()) // 仍可重试。
                .andExpect(content().string(containsString("支付失败，可重试"))); // 一次性提示。

        assertThat(statusOf("CO202611171500000005")).isEqualTo("PENDING_PAYMENT"); // 状态没变。
    }

    /** 入口：待支付详情/卡片给去支付与取消，已支付都不给。 */
    @Test
    @DisplayName("入口：只有待支付显示去支付")
    void paymentEntryOnlyOnPending() throws Exception {
        long pendingUser = CartFixture.createUser(jdbc, "pay_entry_pending"); // 有待支付单的人。
        long paidUser = CartFixture.createUser(jdbc, "pay_entry_paid"); // 只有已支付单的人。
        long productId = CartFixture.createProduct(jdbc, "PAY-P06", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(pendingUser, "CO202611171500000006", "PENDING_PAYMENT", productId); // 待支付。
        seedOrder(paidUser, "CO202611171500000007", "PAID", productId); // 已支付。

        mockMvc.perform(get("/orders/{no}", "CO202611171500000006").session(CartFixture.session(pendingUser))) // 待支付详情。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("去支付"))) // 有支付入口。
                .andExpect(content().string(containsString("取消订单"))); // 也有取消。

        mockMvc.perform(get("/orders/{no}", "CO202611171500000007").session(CartFixture.session(paidUser))) // 已支付详情。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(not(containsString("去支付")))) // 没有支付入口。
                .andExpect(content().string(not(containsString("取消订单")))); // 也不能取消。

        mockMvc.perform(get("/orders").session(CartFixture.session(pendingUser))) // 待支付订单的列表。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("去支付"))) // 卡片上有支付入口。
                .andExpect(content().string(containsString(">取消</button>"))); // 卡片上也能取消。

        mockMvc.perform(get("/orders").session(CartFixture.session(paidUser))) // 已支付订单的列表。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(not(containsString("去支付")))) // 卡片上没有支付入口。
                .andExpect(content().string(not(containsString(">取消</button>")))); // 也不能取消。
    }

    /** 取消前二次确认：详情、支付页、列表卡片三处都有 confirm。 */
    @Test
    @DisplayName("取消订单前有二次确认")
    void cancelAsksForConfirmation() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pay_confirm"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PAY-P07", "28.00", 10, "ON_SALE"); // 商品。
        seedOrder(userId, "CO202611171500000008", "PENDING_PAYMENT", productId); // 待支付订单。
        MockHttpSession session = CartFixture.session(userId); // 同一个会话。
        String confirm = "return confirm('取消后将关闭订单并释放库存，确定取消吗？')"; // 与服务端无关的前端确认文案。

        mockMvc.perform(get("/orders/{no}", "CO202611171500000008").session(session)) // 详情页。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString(confirm))); // 有二次确认。

        mockMvc.perform(get("/payment/{no}", "CO202611171500000008").session(session)) // 支付页。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString(confirm))); // 有二次确认。

        mockMvc.perform(get("/orders").session(session)) // 列表卡片。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString(confirm))); // 有二次确认。
    }

    // ---------------------------------------------------------------- 夹具

    /** 造一单 + 一行明细；已支付补三件套、已取消补取消时间，满足数据库 CHECK。 */
    private void seedOrder(long owner, String orderNo, String status, long productId) {
        LocalDateTime created = LocalDateTime.of(2026, 11, 17, 9, 0); // 固定创建时间。
        boolean paid = "PAID".equals(status); // 只有已支付需要凭证。
        boolean cancelled = "CANCELLED".equals(status); // 已取消必须有取消时间。
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at, cancelled_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, owner, status, new BigDecimal("56.00"), "张三", "13800000000",
                "某市某区某路 1 号", created,
                paid ? "MOCK" : null, paid ? "PAY" + orderNo : null, paid ? created.plusMinutes(5) : null,
                cancelled ? created.plusMinutes(1) : null); // 支付/取消的时间字段都与状态成组。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 订单 id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/pay.svg",
                new BigDecimal("28.00"), 2, new BigDecimal("56.00")); // 两件 ¥28.00。
    }

    private String statusOf(String orderNo) { // 夹具：只取状态，断言"失败不写库"。
        return jdbc.queryForObject("SELECT status FROM sales_order WHERE order_no = ?", String.class, orderNo); // 状态。
    }
}
