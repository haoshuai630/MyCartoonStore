package org.csu.mycartoonstore.web; // 结算确认页：路由、快照入会话、空车/失效回购物车、入口开关。

import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 断言会话里存的是快照。
import org.csu.mycartoonstore.web.controller.CheckoutController; // 会话键常量。
import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/车条目。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据。
import org.springframework.mock.web.MockHttpSession; // 带登录身份的会话。
import org.springframework.test.web.servlet.MockMvc; // 发起请求。
import org.springframework.test.web.servlet.MvcResult; // 取会话里的快照。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 内容断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 断言重定向。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern; // 断言登录回跳。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M05-02：依据 docs/04 §2.3、docs/05 §4.5 与 AC-12/AC-13 的页面部分。
// 覆盖：结算页渲染、快照进会话、空车/失效回购物车、购物车入口开关、游客拦截。
// 不覆盖：提交订单（M05-03）。
@AutoConfigureMockMvc // 不占端口，直接请求控制器。
class CheckoutPageTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据用。

    /** 结算页展示商品、合计与 10 分钟提示。 */
    @Test
    @DisplayName("结算页展示商品、合计与 10 分钟提示")
    void checkoutPageShowsItemsAndTotal() throws Exception {
        long userId = CartFixture.createUser(jdbc, "co_show"); // 建账号。
        long book = CartFixture.createProduct(jdbc, "CO-C01", "28.00", 10, "ON_SALE"); // 单行本。
        long badge = CartFixture.createProduct(jdbc, "CO-M01", "30.00", 10, "ON_SALE"); // 周边。
        CartFixture.addCartItem(jdbc, userId, book, 2); // 放 2 件。
        CartFixture.addCartItem(jdbc, userId, badge, 1); // 放 1 件。

        mockMvc.perform(get("/checkout").session(CartFixture.session(userId))) // 打开确认页。
                .andExpect(status().isOk()) // 页面正常。
                .andExpect(content().string(containsString("商品CO-C01"))) // 清单里有第一件。
                .andExpect(content().string(containsString("商品CO-M01"))) // 清单里有第二件。
                .andExpect(content().string(containsString("¥86.00"))) // 56.00 + 30.00。
                .andExpect(content().string(containsString("请在 10 分钟内提交订单"))); // 时限提示。
    }

    /** 打开确认页会把快照写进会话，供提交时比对。 */
    @Test
    @DisplayName("确认页把快照写进会话")
    void checkoutStoresSnapshotInSession() throws Exception {
        long userId = CartFixture.createUser(jdbc, "co_snap"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CO-C02", "28.00", 10, "ON_SALE"); // 建商品。
        CartFixture.addCartItem(jdbc, userId, productId, 2); // 放 2 件。
        MockHttpSession session = CartFixture.session(userId); // 同一个会话。

        MvcResult result = mockMvc.perform(get("/checkout").session(session)) // 打开确认页。
                .andExpect(status().isOk()) // 正常渲染。
                .andReturn(); // 拿结果取会话。

        Object stored = result.getRequest().getSession().getAttribute(CheckoutController.SNAPSHOT_KEY); // 取快照。
        assertThat(stored).isInstanceOf(CheckoutSnapshot.class); // 类型正确。
        CheckoutSnapshot snapshot = (CheckoutSnapshot) stored; // 转成快照。
        assertThat(snapshot.getLines()).hasSize(1); // 一行。
        assertThat(snapshot.getLines().get(0).getQuantity()).isEqualTo(2); // 数量 2。
        assertThat(snapshot.getTotalAmount()).isEqualByComparingTo("56.00"); // 总额 56.00。
    }

    /** 空车打开确认页：回购物车，不甩错误页。 */
    @Test
    @DisplayName("空车打开确认页：重定向回购物车")
    void emptyCartRedirectsToCart() throws Exception {
        long userId = CartFixture.createUser(jdbc, "co_empty"); // 建账号但不加车。

        mockMvc.perform(get("/checkout").session(CartFixture.session(userId))) // 打开确认页。
                .andExpect(status().is3xxRedirection()) // 重定向而不是错误页。
                .andExpect(redirectedUrl("/cart")); // 回到购物车。
    }

    /** 有失效条目：同样回购物车，先处理再结算。 */
    @Test
    @DisplayName("有失效条目：重定向回购物车")
    void invalidCartRedirectsToCart() throws Exception {
        long userId = CartFixture.createUser(jdbc, "co_invalid"); // 建账号。
        long soldOut = CartFixture.createProduct(jdbc, "CO-C03", "28.00", 0, "ON_SALE"); // 库存 0。
        CartFixture.addCartItem(jdbc, userId, soldOut, 1); // 放 1 件，已经是失效行。

        mockMvc.perform(get("/checkout").session(CartFixture.session(userId))) // 打开确认页。
                .andExpect(status().is3xxRedirection()) // 重定向。
                .andExpect(redirectedUrl("/cart")); // 回购物车处理。
    }

    /** 全部可用时，购物车页给“去结算”入口。 */
    @Test
    @DisplayName("购物车页：可结算时给去结算入口")
    void cartPageLinksToCheckoutWhenSettleable() throws Exception {
        long userId = CartFixture.createUser(jdbc, "co_link"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CO-C04", "28.00", 10, "ON_SALE"); // 建商品。
        CartFixture.addCartItem(jdbc, userId, productId, 1); // 放 1 件。

        mockMvc.perform(get("/cart").session(CartFixture.session(userId))) // 打开购物车。
                .andExpect(status().isOk()) // 正常渲染。
                .andExpect(content().string(containsString("href=\"/checkout\""))); // 有结算入口。
    }

    /** 有失效条目时，购物车页不给入口，只给禁用按钮。 */
    @Test
    @DisplayName("购物车页：失效条目不给出结算入口")
    void cartPageHidesCheckoutWhenInvalid() throws Exception {
        long userId = CartFixture.createUser(jdbc, "co_nolink"); // 建账号。
        long soldOut = CartFixture.createProduct(jdbc, "CO-C05", "28.00", 0, "ON_SALE"); // 库存 0。
        CartFixture.addCartItem(jdbc, userId, soldOut, 1); // 失效行。

        mockMvc.perform(get("/cart").session(CartFixture.session(userId))) // 打开购物车。
                .andExpect(status().isOk()) // 正常渲染。
                .andExpect(content().string(not(containsString("href=\"/checkout\"")))) // 没有可点入口。
                .andExpect(content().string(containsString("失效条目无法结算"))); // 说明原因。
    }

    /** 游客打开确认页：拦到登录并带回跳。 */
    @Test
    @DisplayName("游客打开确认页：拦到登录页")
    void guestCheckoutRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/checkout")) // 不带会话。
                .andExpect(status().is3xxRedirection()) // 302 到登录。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fcheckout*")); // 登录后回确认页。
    }
}
