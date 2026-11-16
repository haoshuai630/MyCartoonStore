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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-09 用例：购物车只认 user_id，Session 里不存副本。
 *
 * <p>退出会销毁会话对象，但 cart_item 一行不动；换一个会话（等价于重新登录或换浏览器）
 * 仍按同一个 user_id 查到同一辆车；另一个账号既看不到、也改不了。</p>
 *
 * <p>这里不 mock 会话内容，而是真的走"加车 → 退出 → 新会话查车"，
 * 用来证明"车在库里、不在会话里"这个结构。</p>
 */
@AutoConfigureMockMvc
class CartPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("退出后换新会话（重登/换浏览器）：同一账号仍看到原车")
    void cartSurvivesLogoutAndNewSession() throws Exception {
        long userId = CartFixture.createUser(jdbc, "ac09_alice");
        long productId = CartFixture.createProduct(jdbc, "AC09-C01", "28.00", 10, "ON_SALE");
        MockHttpSession first = CartFixture.session(userId);

        // 浏览器 A：加 2 件
        mockMvc.perform(post("/cart/items")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "2")
                        .session(first))
                .andExpect(redirectedUrl("/cart"));

        // 退出：只销毁会话，不执行任何购物车 SQL
        mockMvc.perform(post("/logout").session(first))
                .andExpect(redirectedUrl("/main"));

        // 新会话 = 重新登录 / 换浏览器：会话里仍是同一个 user_id
        MockHttpSession second = CartFixture.session(userId);
        mockMvc.perform(get("/cart").session(second))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("商品AC09-C01")))
                .andExpect(content().string(containsString("共 <b>2</b> 件")));

        // 页头徽标同样来自库，不来自会话
        mockMvc.perform(get("/main").session(second))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("购物车 <b>2</b>")));

        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1);
        assertThat(quantityOf(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("另一个账号：看不到别人的车，也改不了别人的条目")
    void otherAccountCannotReadOrModify() throws Exception {
        long owner = CartFixture.createUser(jdbc, "ac09_owner");
        long intruder = CartFixture.createUser(jdbc, "ac09_intruder");
        long productId = CartFixture.createProduct(jdbc, "AC09-C02", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, owner, productId, 2);

        MockHttpSession intruderSession = CartFixture.session(intruder);

        // 读：空车，不出现别人的商品
        mockMvc.perform(get("/cart").session(intruderSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("购物车还是空的")))
                .andExpect(content().string(not(containsString("商品AC09-C02"))));

        // 写：改数量与移除都被归属校验拒绝
        mockMvc.perform(post("/cart/items/{id}/quantity", itemId)
                        .param("quantity", "5")
                        .param("version", "0")
                        .session(intruderSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/cart/items/{id}/remove", itemId)
                        .param("version", "0")
                        .session(intruderSession))
                .andExpect(status().isForbidden());

        // 原条目一行未动
        assertThat(CartFixture.countItems(jdbc, owner)).isEqualTo(1);
        assertThat(CartFixture.quantityOf(jdbc, itemId)).isEqualTo(2);
        assertThat(CartFixture.countItems(jdbc, intruder)).isZero();
    }

    private Integer quantityOf(long userId) {
        return jdbc.queryForObject("SELECT quantity FROM cart_item WHERE user_id = ?",
                Integer.class, userId);
    }
}
