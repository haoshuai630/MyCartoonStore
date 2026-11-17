package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.csu.mycartoonstore.web.support.CartFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车写操作：改数量与移除的归属校验、版本冲突、数量与种类上限。
 *
 * <p>这些用例走真实 HTTP 接口与真实 MySQL，验证的是"版本条件确实写进了 SQL"，
 * 而不只是 Service 里有个 if。</p>
 */
@AutoConfigureMockMvc
class CartMutationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("改数量：写下新数量并递增版本")
    void changeQuantityWritesAndBumpsVersion() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_ok");
        long productId = CartFixture.createProduct(jdbc, "MUT-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, userId, productId, 2);

        mockMvc.perform(post("/cart/items/{id}/quantity", itemId).with(csrf())
                        .param("quantity", "5")
                        .param("version", "0")
                        .session(CartFixture.session(userId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        assertThat(CartFixture.quantityOf(jdbc, itemId)).isEqualTo(5);
        assertThat(CartFixture.versionOf(jdbc, itemId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("旧版本改数量：409，数量与版本都不变")
    void changeQuantityRejectsStaleVersion() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_stale");
        long productId = CartFixture.createProduct(jdbc, "STALE-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, userId, productId, 2);
        jdbc.update("UPDATE cart_item SET version = 3 WHERE id = ?", itemId);

        mockMvc.perform(post("/cart/items/{id}/quantity", itemId).with(csrf())
                        .param("quantity", "5")
                        .param("version", "2")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("购物车已变化，请刷新后重试")));

        assertThat(CartFixture.quantityOf(jdbc, itemId)).isEqualTo(2);
        assertThat(CartFixture.versionOf(jdbc, itemId)).isEqualTo(3L);
    }

    @Test
    @DisplayName("改他人条目：统一 404，条目不动")
    void changeQuantityRejectsOtherUser() throws Exception {
        long owner = CartFixture.createUser(jdbc, "mut_owner");
        long other = CartFixture.createUser(jdbc, "mut_other");
        long productId = CartFixture.createProduct(jdbc, "OWN-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, owner, productId, 2);

        mockMvc.perform(post("/cart/items/{id}/quantity", itemId).with(csrf())
                        .param("quantity", "9")
                        .param("version", "0")
                        .session(CartFixture.session(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("购物车条目不存在")));

        assertThat(CartFixture.quantityOf(jdbc, itemId)).isEqualTo(2);
    }

    @Test
    @DisplayName("改数量为 0：400，不写库")
    void changeQuantityRejectsZero() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_zero");
        long productId = CartFixture.createProduct(jdbc, "MZ-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, userId, productId, 2);

        mockMvc.perform(post("/cart/items/{id}/quantity", itemId).with(csrf())
                        .param("quantity", "0")
                        .param("version", "0")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isBadRequest());

        assertThat(CartFixture.quantityOf(jdbc, itemId)).isEqualTo(2);
    }

    @Test
    @DisplayName("改数量超过库存：400")
    void changeQuantityRejectsOverStock() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_stock");
        long productId = CartFixture.createProduct(jdbc, "MS-C01", "28.00", 3, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, userId, productId, 1);

        mockMvc.perform(post("/cart/items/{id}/quantity", itemId).with(csrf())
                        .param("quantity", "4")
                        .param("version", "0")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("最多可购买 3 件")));

        assertThat(CartFixture.quantityOf(jdbc, itemId)).isEqualTo(1);
    }

    @Test
    @DisplayName("移除本人条目：删除成功并回购物车")
    void removeOwnItem() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_remove");
        long productId = CartFixture.createProduct(jdbc, "RM-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, userId, productId, 2);

        mockMvc.perform(post("/cart/items/{id}/remove", itemId).with(csrf())
                        .param("version", "0")
                        .session(CartFixture.session(userId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        assertThat(CartFixture.countItems(jdbc, userId)).isZero();
    }

    @Test
    @DisplayName("旧版本移除：409，条目仍在")
    void removeRejectsStaleVersion() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_rm_stale");
        long productId = CartFixture.createProduct(jdbc, "RS-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, userId, productId, 2);
        jdbc.update("UPDATE cart_item SET version = 1 WHERE id = ?", itemId);

        mockMvc.perform(post("/cart/items/{id}/remove", itemId).with(csrf())
                        .param("version", "0")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isConflict());

        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("移除他人条目：统一 404，条目仍在")
    void removeRejectsOtherUser() throws Exception {
        long owner = CartFixture.createUser(jdbc, "mut_rm_owner");
        long other = CartFixture.createUser(jdbc, "mut_rm_other");
        long productId = CartFixture.createProduct(jdbc, "RO-C01", "28.00", 10, "ON_SALE");
        long itemId = CartFixture.addCartItem(jdbc, owner, productId, 2);

        mockMvc.perform(post("/cart/items/{id}/remove", itemId).with(csrf())
                        .param("version", "0")
                        .session(CartFixture.session(other)))
                .andExpect(status().isNotFound());

        assertThat(CartFixture.countItems(jdbc, owner)).isEqualTo(1);
    }

    @Test
    @DisplayName("已有 20 种商品：第 21 种被拒，提示上限")
    void addRejectsTwentyFirstKind() throws Exception {
        long userId = CartFixture.createUser(jdbc, "mut_full");
        for (int i = 0; i < 20; i++) {
            long productId = CartFixture.createProduct(jdbc, "FULL-" + i, "28.00", 10, "ON_SALE");
            CartFixture.addCartItem(jdbc, userId, productId, 1);
        }
        long extra = CartFixture.createProduct(jdbc, "FULL-X", "28.00", 10, "ON_SALE");

        mockMvc.perform(post("/cart/items").with(csrf())
                        .param("productId", String.valueOf(extra))
                        .param("quantity", "1")
                        .session(CartFixture.session(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("最多放 20 种商品")));

        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(20);
    }

    @Test
    @DisplayName("失效行：显示原因、计入徽标、不计入总额、结算被禁用")
    void invalidLinesShowReasonAndBlockCheckout() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cart_invalid");
        long ok = CartFixture.createProduct(jdbc, "INV-C01", "28.00", 10, "ON_SALE");
        long soldOut = CartFixture.createProduct(jdbc, "INV-C02", "30.00", 0, "ON_SALE");
        long draft = CartFixture.createProduct(jdbc, "INV-C03", "40.00", 10, "DRAFT");
        CartFixture.addCartItem(jdbc, userId, ok, 1);
        CartFixture.addCartItem(jdbc, userId, soldOut, 2);
        CartFixture.addCartItem(jdbc, userId, draft, 1);

        mockMvc.perform(get("/cart").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("库存不足，仅剩 0 件")))
                .andExpect(content().string(containsString("已下架")))
                .andExpect(content().string(containsString("共 <b>4</b> 件")))
                .andExpect(content().string(containsString("可结算 <b>1</b> 件")))
                .andExpect(content().string(containsString("¥28.00")))
                .andExpect(content().string(containsString("有 2 个条目已失效")))
                .andExpect(content().string(containsString("失效条目无法结算，请先处理。")));
    }
}
