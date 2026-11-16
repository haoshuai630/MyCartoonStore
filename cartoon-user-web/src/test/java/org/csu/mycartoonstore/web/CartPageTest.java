package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.csu.mycartoonstore.web.support.CartFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车页面与加入购物车：空车、正常行、失效行、徽标、游客拦截。
 */
@AutoConfigureMockMvc
class CartPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("空车：显示空状态与去逛逛入口，不渲染表格")
    void emptyCartShowsEmptyState() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_empty");

        mockMvc.perform(get("/cart").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("购物车还是空的")))
                .andExpect(content().string(not(containsString("cart-row"))));
    }

    @Test
    @DisplayName("购物车页：显示名称、单价、数量、小计与合计")
    void cartPageShowsLinesAndTotals() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_show");
        long book = CartFixture.createProduct(jdbc, "SHOW-C01", "28.00", 10, "ON_SALE");
        long badge = CartFixture.createProduct(jdbc, "SHOW-M01", "30.00", 10, "ON_SALE");
        CartFixture.addCartItem(jdbc, userId, book, 2);
        CartFixture.addCartItem(jdbc, userId, badge, 1);

        mockMvc.perform(get("/cart").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("商品SHOW-C01")))
                .andExpect(content().string(containsString("¥28.00")))
                .andExpect(content().string(containsString("¥56.00")))
                .andExpect(content().string(containsString("共 <b>3</b> 件")))
                .andExpect(content().string(containsString("¥86.00")));
    }

    @Test
    @DisplayName("页头徽标：等于全部条目数量之和，跨页面可见")
    void badgeShowsTotalQuantity() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_badge");
        long a = CartFixture.createProduct(jdbc, "BADGE-C01", "28.00", 10, "ON_SALE");
        long b = CartFixture.createProduct(jdbc, "BADGE-C02", "28.00", 10, "ON_SALE");
        CartFixture.addCartItem(jdbc, userId, a, 2);
        CartFixture.addCartItem(jdbc, userId, b, 3);

        mockMvc.perform(get("/main").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("购物车 <b>5</b>")));
    }

    @Test
    @DisplayName("游客的徽标固定为 0")
    void guestBadgeIsZero() throws Exception {
        mockMvc.perform(get("/main"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("购物车 <b>0</b>")));
    }

    @Test
    @DisplayName("加入购物车：写库并回到购物车页")
    void addItemCreatesLineAndRedirects() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_add");
        long productId = CartFixture.createProduct(jdbc, "ADD-C01", "28.00", 10, "ON_SALE");

        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "2")
                        .session(CartFixture.session(userId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1);
        Integer quantity = jdbc.queryForObject("SELECT quantity FROM cart_item WHERE user_id = ?",
                Integer.class, userId);
        assertThat(quantity).isEqualTo(2);
    }

    @Test
    @DisplayName("同商品重复加入：数量累加，条目数不变")
    void addSameProductAccumulates() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_merge");
        long productId = CartFixture.createProduct(jdbc, "MERGE-C01", "28.00", 10, "ON_SALE");
        MockHttpSession session = CartFixture.session(userId);

        mockMvc.perform(post("/cart/items")
                .param("productId", String.valueOf(productId)).param("quantity", "1").session(session));
        mockMvc.perform(post("/cart/items")
                .param("productId", String.valueOf(productId)).param("quantity", "2").session(session));

        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1);
        Integer quantity = jdbc.queryForObject("SELECT quantity FROM cart_item WHERE user_id = ?",
                Integer.class, userId);
        assertThat(quantity).isEqualTo(3);
    }

    @Test
    @DisplayName("游客加车：302 到登录页，回跳目标是详情页而不是 POST 地址")
    void guestAddRedirectsToDetailPage() throws Exception {
        long productId = CartFixture.createProduct(jdbc, "GUEST-C01", "28.00", 10, "ON_SALE");

        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1")
                        .header("Referer", "http://localhost/product/" + productId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fproduct%2F*"));
    }

    @Test
    @DisplayName("数量 0：400，不写库")
    void addRejectsZeroQuantity() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_zero");
        long productId = CartFixture.createProduct(jdbc, "ZERO-C01", "28.00", 10, "ON_SALE");

        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "0")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("数量必须是 1–99 的整数")));

        assertThat(CartFixture.countItems(jdbc, userId)).isZero();
    }

    @Test
    @DisplayName("加车数量超过库存：400，提示可购买数量")
    void addRejectsOverStock() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_stock");
        long productId = CartFixture.createProduct(jdbc, "STOCK-C01", "28.00", 2, "ON_SALE");

        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "3")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("最多可购买 2 件")));

        assertThat(CartFixture.countItems(jdbc, userId)).isZero();
    }

    @Test
    @DisplayName("加车下架商品：404")
    void addRejectsDraftProduct() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_draft");
        long productId = CartFixture.createProduct(jdbc, "DRAFT-C01", "28.00", 10, "DRAFT");

        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isNotFound());
    }
}
