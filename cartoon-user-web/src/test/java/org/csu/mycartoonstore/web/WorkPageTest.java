package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 作品页测试：只出现本作品商品、品类筛选、404。
 */
@AutoConfigureMockMvc
class WorkPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("作品页只显示本作品商品，不串到别家")
    void workPageScopesProducts() throws Exception {
        long workA = seedWork("WA", "作品甲");
        long comicA = seedCategory("WA-COMIC", workA, "单行本");
        insertProduct("WA-C01", comicA, "甲的单行本", "ON_SALE");

        long workB = seedWork("WB", "作品乙");
        long comicB = seedCategory("WB-COMIC", workB, "单行本");
        insertProduct("WB-C01", comicB, "乙的单行本", "ON_SALE");

        mockMvc.perform(get("/works/{id}", workA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("作品甲")))
                .andExpect(content().string(containsString("甲的单行本")))
                .andExpect(content().string(not(containsString("乙的单行本"))));
    }

    @Test
    @DisplayName("品类胶囊能筛到只剩该品类")
    void categoryPillFilters() throws Exception {
        long work = seedWork("WC", "作品丙");
        long comic = seedCategory("WC-COMIC", work, "单行本");
        long badge = seedCategory("WC-BADGE", work, "徽章");
        insertProduct("WC-C01", comic, "丙的单行本", "ON_SALE");
        insertProduct("WC-M01", badge, "丙的徽章", "ON_SALE");

        String badgePage = mockMvc.perform(get("/works/{id}", work).param("categoryId", String.valueOf(badge)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(badgePage).contains("丙的徽章");
        assertThat(badgePage).doesNotContain("丙的单行本");
    }

    @Test
    @DisplayName("不存在的作品 id 返回 404")
    void missingWorkReturns404() throws Exception {
        mockMvc.perform(get("/works/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("二级分类不能当作品页打开")
    void leafAsWorkReturns404() throws Exception {
        long work = seedWork("WD", "作品丁");
        long comic = seedCategory("WD-COMIC", work, "单行本");

        mockMvc.perform(get("/works/{id}", comic))
                .andExpect(status().isNotFound());
    }

    private long seedWork(String code, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 1)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private long seedCategory(String code, long parentId, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,1)", code, parentId, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private void insertProduct(String sku, long categoryId, String name, String status) {
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, cover_image, price, stock, status, sort_order) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                sku, categoryId, name, "简介", "images/products/" + sku + "/cover.svg",
                new BigDecimal("28.00"), 10, status, 1);
    }
}