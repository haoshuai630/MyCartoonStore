package org.csu.mycartoonstore.web; // 订单列表：状态页签、完整单号、分页、空状态与快照不变。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品。
import org.junit.jupiter.api.BeforeEach; // 每个用例造基础数据。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 直接造订单。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import java.time.LocalDateTime; // 控制创建时间与时间线。

import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 内容断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern; // 游客拦截。

// M05-04：依据 docs/05 §4.6/4.7 与 docs/06 AC-19/AC-30。
// 覆盖：空状态、只显示本人、按状态过滤、完整单号搜索、分页、非法参数 400、快照不随商品改、时间线。
// 不覆盖：支付/取消按钮（M06 / M05-05）。
@AutoConfigureMockMvc // 不占端口。
class OrderListTest extends AbstractIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 16, 10, 0); // 固定基准时间。

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造订单与明细。

    private long userId; // 主测试用户。
    private long productId; // 明细引用的商品。

    @BeforeEach // 每个用例前重建基础数据（基类已重置库）。
    void setUp() {
        userId = CartFixture.createUser(jdbc, "ol_user"); // 主用户。
        productId = CartFixture.createProduct(jdbc, "OL-P01", "28.00", 100, "ON_SALE"); // 明细引用。
    }

    /** 没有订单：空状态文案，且给出继续逛的入口。 */
    @Test
    @DisplayName("没有订单：显示空状态")
    void emptyOrderListShowsEmptyState() throws Exception {
        mockMvc.perform(get("/orders").session(CartFixture.session(userId))) // 打开列表。
                .andExpect(status().isOk()) // 正常渲染。
                .andExpect(content().string(containsString("还没有订单"))) // 空状态文案。
                .andExpect(content().string(containsString("去逛逛"))); // 有继续逛的入口。
    }

    /** 只显示本人订单，且新的在前。 */
    @Test
    @DisplayName("只显示本人订单，新的在前")
    void listShowsOnlyOwnOrdersNewestFirst() throws Exception {
        seedOrder("CO202611161000000001", "PENDING_PAYMENT", "28.00", T0); // 本人 10:00。
        seedOrder("CO202611161100000002", "PENDING_PAYMENT", "56.00", T0.plusHours(1)); // 本人 11:00。
        long other = CartFixture.createUser(jdbc, "ol_other"); // 另一个用户。
        seedOrderFor(other, "CO202611161200000003", "PENDING_PAYMENT", "30.00", T0.plusHours(2)); // 他人订单。

        String html = mockMvc.perform(get("/orders").session(CartFixture.session(userId))) // 打开列表。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(not(containsString("CO202611161200000003")))) // 不显示他人订单。
                .andReturn().getResponse().getContentAsString(); // 取页面。

        org.assertj.core.api.Assertions.assertThat(html.indexOf("CO202611161100000002"))
                .isLessThan(html.indexOf("CO202611161000000001")); // 11:00 排在 10:00 前。
    }

    /** 状态页签：只显示该状态，角标是真实数量。 */
    @Test
    @DisplayName("状态页签过滤：只显示该状态")
    void statusTabFiltersOrders() throws Exception {
        seedOrder("CO202611161000000011", "PENDING_PAYMENT", "28.00", T0); // 待支付。
        seedOrder("CO202611161000000012", "PAID", "56.00", T0.plusMinutes(1)); // 待发货。
        seedOrder("CO202611161000000013", "CANCELLED", "30.00", T0.plusMinutes(2)); // 已取消。

        String html = mockMvc.perform(get("/orders").param("status", "PAID").session(CartFixture.session(userId))) // 待发货页签。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("CO202611161000000012"))) // 只显示待发货。
                .andExpect(content().string(not(containsString("CO202611161000000011")))) // 不显示待支付。
                .andExpect(content().string(not(containsString("CO202611161000000013")))) // 不显示已取消。
                .andReturn().getResponse().getContentAsString(); // 取页面。

        // 页签文案里的空格受模板换行影响，先压缩空白再断言"待发货 1"。
        org.assertj.core.api.Assertions.assertThat(html.replaceAll("\\s+", ""))
                .contains("待发货1"); // 角标数量来自真实数据。
    }

    /** 完整单号搜索：精确匹配；部分单号搜不到。 */
    @Test
    @DisplayName("完整单号搜索：精确匹配")
    void searchByExactOrderNo() throws Exception {
        seedOrder("CO202611161000000021", "PENDING_PAYMENT", "28.00", T0); // 目标。
        seedOrder("CO202611161000000022", "PENDING_PAYMENT", "56.00", T0.plusMinutes(1)); // 干扰项。

        mockMvc.perform(get("/orders").param("orderNo", "CO202611161000000021")
                        .session(CartFixture.session(userId))) // 精确搜索。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("CO202611161000000021"))) // 命中。
                .andExpect(content().string(not(containsString("CO202611161000000022")))); // 不命中干扰项。

        mockMvc.perform(get("/orders").param("orderNo", "CO20261116100000002")
                        .session(CartFixture.session(userId))) // 只输入前缀。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("没有订单号是"))); // 精确匹配，搜不到。
    }

    /** 11 单分两页：第 1 页 10 单，第 2 页 1 单，翻页保留条件。 */
    @Test
    @DisplayName("分页：11 单分两页")
    void paginationWorks() throws Exception {
        for (int i = 1; i <= 11; i++) { // 造 11 单。
            seedOrder(String.format("CO2026111610%08d", i), "PENDING_PAYMENT", "28.00",
                    T0.plusMinutes(i)); // 时间递增，保证顺序稳定。
        }

        String first = mockMvc.perform(get("/orders").session(CartFixture.session(userId))) // 第 1 页。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("第 1 / 2 页"))) // 两页。
                .andExpect(content().string(containsString("page=2"))) // 有下一页链接。
                .andReturn().getResponse().getContentAsString(); // 取页面。

        mockMvc.perform(get("/orders").param("page", "2").session(CartFixture.session(userId))) // 第 2 页。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("第 2 / 2 页"))) // 第 2 页。
                .andExpect(content().string(containsString("CO202611161000000001"))); // 最早那单在末页。

        org.assertj.core.api.Assertions.assertThat(countCards(first)).isEqualTo(10); // 第一页正好 10 张卡。
    }

    /** 非法参数：未知状态、非数字页码、0 页码都 400。 */
    @Test
    @DisplayName("非法参数：状态/页码错误都 400")
    void invalidParamsReturn400() throws Exception {
        mockMvc.perform(get("/orders").param("status", "FOO").session(CartFixture.session(userId))) // 未知状态。
                .andExpect(status().isBadRequest()); // 400。
        mockMvc.perform(get("/orders").param("page", "abc").session(CartFixture.session(userId))) // 非数字页码。
                .andExpect(status().isBadRequest()); // 400。
        mockMvc.perform(get("/orders").param("page", "0").session(CartFixture.session(userId))) // 0 页码。
                .andExpect(status().isBadRequest()); // 400。
    }

    /** AC-19：下单后改商品名与价格，列表与详情仍显示成交快照。 */
    @Test
    @DisplayName("AC-19：商品改名改价，历史订单仍显示快照")
    void snapshotSurvivesProductEdit() throws Exception {
        seedOrder("CO202611161000000031", "PENDING_PAYMENT", "28.00", T0); // 一单。
        seedItem("CO202611161000000031", "旧商品名", "28.00", 1); // 明细快照：旧名 ¥28。
        jdbc.update("UPDATE product SET name = ?, price = ?, status = 'DRAFT', cover_image = ? WHERE id = ?",
                "新商品名", "99.00", "/images/covers/new.svg", productId); // 商品改名、改价、下架。

        mockMvc.perform(get("/orders/{no}", "CO202611161000000031").session(CartFixture.session(userId))) // 详情。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("旧商品名"))) // 名称快照。
                .andExpect(content().string(not(containsString("新商品名")))) // 不显示新名。
                .andExpect(content().string(containsString("¥28.00"))); // 价格快照。
    }

    /** 时间线：待支付只有创建；已支付多一个“模拟支付”节点。 */
    @Test
    @DisplayName("时间线：只显示已发生节点")
    void timelineShowsOnlyOccurredNodes() throws Exception {
        seedOrder("CO202611161000000041", "PENDING_PAYMENT", "28.00", T0); // 待支付。
        seedOrder("CO202611161000000042", "PAID", "28.00", T0.plusMinutes(1)); // 已支付。

        mockMvc.perform(get("/orders/{no}", "CO202611161000000041").session(CartFixture.session(userId))) // 待支付。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(not(containsString("<b>模拟支付</b>")))); // 未支付不显示支付节点。

        mockMvc.perform(get("/orders/{no}", "CO202611161000000042").session(CartFixture.session(userId))) // 已支付。
                .andExpect(status().isOk()) // 正常。
                .andExpect(content().string(containsString("<b>模拟支付</b>"))); // 显示支付节点。
    }

    /** 游客打开列表：拦到登录页。 */
    @Test
    @DisplayName("游客打开列表：拦到登录页")
    void guestOrderListRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/orders")) // 不带会话。
                .andExpect(status().is3xxRedirection()) // 302。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Forders*")); // 登录后回列表。
    }

    // ---------------------------------------------------------------- 夹具

    /** 给主用户造一单，并按状态补齐约束字段。 */
    private void seedOrder(String orderNo, String status, String total, LocalDateTime createdAt) {
        seedOrderFor(userId, orderNo, status, total, createdAt); // 复用通用夹具。
    }

    /** 给指定用户造一单：状态字段满足数据库 CHECK。 */
    private void seedOrderFor(long owner, String orderNo, String status, String total, LocalDateTime createdAt) {
        String paymentNo = null; // 未支付为空。
        LocalDateTime paidAt = null; // 未支付为空。
        LocalDateTime cancelledAt = null; // 未取消为空。
        if ("PAID".equals(status)) { // 已支付：三件套齐全。
            paymentNo = "PAY" + orderNo; // 唯一凭证。
            paidAt = createdAt.plusMinutes(5); // 支付时间在创建之后。
        }
        if ("CANCELLED".equals(status)) { // 已取消：取消时间必填。
            cancelledAt = createdAt.plusMinutes(10); // 取消时间。
        }
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at, cancelled_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, owner, status, new java.math.BigDecimal(total), "张三", "13800000000",
                "某市某区某路 1 号", createdAt,
                paymentNo == null ? null : "MOCK", paymentNo, paidAt, cancelledAt); // 一次插入。
    }

    /** 给订单造一行明细快照。 */
    private void seedItem(String orderNo, String name, String price, int quantity) {
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 订单 id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, name, "/images/covers/ol.svg",
                new java.math.BigDecimal(price), quantity,
                new java.math.BigDecimal(price).multiply(java.math.BigDecimal.valueOf(quantity))); // 行金额 = 单价 × 数量。
    }

    /** 数页面里订单卡片数量。 */
    private int countCards(String html) {
        int count = 0; // 计数器。
        int index = 0; // 查找位置。
        while ((index = html.indexOf("class=\"order-card\"", index)) >= 0) { // 每次命中一张卡。
            count++; // 计数。
            index += 18; // 越过当前匹配。
        }
        return count; // 返回卡片数。
    }
}
