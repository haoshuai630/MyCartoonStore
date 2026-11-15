package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首页渲染测试：验证作品入口、新品 3 条上限、精选 4 条与下架商品不可见。
 */
@AutoConfigureMockMvc
class HomePageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("首页显示作品入口与上架商品，隐藏下架商品")
    void homeShowsOnSaleOnly() throws Exception {
        long categoryId = seedCategory("TESTIP", "测试作品");
        insertProduct("TEST-C01", categoryId, "测试商品A", "28.00", 10, "ON_SALE", 1, 1);
        insertProduct("TEST-C02", categoryId, "测试商品B", "30.00", 10, "DRAFT", 2, 2);

        mockMvc.perform(get("/main"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试作品")))
                .andExpect(content().string(containsString("测试商品A")))
                .andExpect(content().string(containsString("¥28.00")))
                .andExpect(content().string(not(containsString("测试商品B"))));
    }

    @Test
    @DisplayName("新品最多 3 条：第 4 新的商品不出现在「新品上架」")
    void newestIsLimitedToThree() throws Exception {
        long categoryId = seedCategory("LIMITIP", "限量作品");
        insertProduct("L-01", categoryId, "新品一", "28.00", 10, "ON_SALE", 10, 1);
        insertProduct("L-02", categoryId, "新品二", "28.00", 10, "ON_SALE", 10, 2);
        insertProduct("L-03", categoryId, "新品三", "28.00", 10, "ON_SALE", 10, 3);
        insertProduct("L-04", categoryId, "新品四", "28.00", 10, "ON_SALE", 10, 4);

        String body = mockMvc.perform(get("/main"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 前三条在「新品上架」和「精选推荐」各出现一次；第四条只在精选里出现
        assertThat(count(body, "新品一")).isEqualTo(4);
        assertThat(count(body, "新品二")).isEqualTo(4);
        assertThat(count(body, "新品三")).isEqualTo(4);
        assertThat(count(body, "新品四")).isEqualTo(2);
    }

    @Test
    @DisplayName("空库时首页显示空状态，而不是报错")
    void homeShowsEmptyState() throws Exception {
        mockMvc.perform(get("/main"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("暂无上架商品")));
    }

    private int count(String body, String word) {
        int n = 0, i = 0;
        while ((i = body.indexOf(word, i)) >= 0) { n++; i += word.length(); }
        return n;
    }

    private long seedCategory(String code, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 1)", code, name);
        long parentId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,1)",
                code + "-COMIC", parentId, "单行本");
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code + "-COMIC");
    }

    private void insertProduct(String sku, long categoryId, String name, String price, int stock,
                               String status, int sortOrder, int daysAgo) {
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, cover_image, price, stock, status, "
                        + "sort_order, created_at) VALUES (?,?,?,?,?,?,?,?,?, DATE_SUB(NOW(), INTERVAL ? DAY))",
                sku, categoryId, name, "测试简介", "images/products/" + sku + "/cover.svg",
                new BigDecimal(price), stock, status, sortOrder, daysAgo);
    }
}