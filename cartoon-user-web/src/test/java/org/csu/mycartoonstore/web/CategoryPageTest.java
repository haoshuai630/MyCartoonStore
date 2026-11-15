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
 * 列表页测试：类型筛选、分页、非法排序参数。
 *
 * <p>固定造 8 件商品（7 件单行本 + 1 件周边），默认每页 6 条，因此第 2 页应有 2 条。</p>
 */
@AutoConfigureMockMvc
class CategoryPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("列表页显示两类商品与作品侧栏")
    void listShowsAllTypes() throws Exception {
        seed();

        mockMvc.perform(get("/category"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("作品一")))
                .andExpect(content().string(containsString("单行本")))
                .andExpect(content().string(containsString("周边")))
                .andExpect(content().string(containsString("C01")))
                .andExpect(content().string(containsString("单行本1")));
    }

    @Test
    @DisplayName("类型筛选：只要周边时不出现单行本")
    void filterByMerch() throws Exception {
        seed();

        mockMvc.perform(get("/category").param("productType", "MERCH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("M01")))
                .andExpect(content().string(not(containsString("C01"))));
    }

    @Test
    @DisplayName("分页：8 件商品，第 2 页只剩 2 件")
    void paginationSplitsPages() throws Exception {
        seed();

        String first = mockMvc.perform(get("/category"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(count(first, "C0")).isGreaterThan(0);
        assertThat(first).contains("第 1 / 2 页");

        String second = mockMvc.perform(get("/category").param("page", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(second).contains("第 2 / 2 页");
        assertThat(second).doesNotContain("C01");
    }

    @Test
    @DisplayName("非法排序值返回 400，而不是静默降级")
    void invalidSortIsRejected() throws Exception {
        seed();

        mockMvc.perform(get("/category").param("sort", "PRICE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("作品下拉能筛到只剩该作品商品")
    void filterByWork() throws Exception {
        seed();
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES ('T2', NULL, '作品二', 2)");
        long otherWork = jdbc.queryForObject("SELECT id FROM category WHERE code = 'T2'", Long.class);
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES ('T2-COMIC', ?, '单行本', 1)", otherWork);
        long otherComic = jdbc.queryForObject("SELECT id FROM category WHERE code = 'T2-COMIC'", Long.class);
        insertProduct("X01", otherComic, "别家商品", "28.00", 10, "ON_SALE", 1);

        long workId = jdbc.queryForObject("SELECT id FROM category WHERE code = 'T1'", Long.class);

        mockMvc.perform(get("/category").param("workId", String.valueOf(workId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("单行本1")))
                .andExpect(content().string(not(containsString("别家商品"))));
    }

    private int count(String body, String word) {
        int n = 0, i = 0;
        while ((i = body.indexOf(word, i)) >= 0) { n++; i += word.length(); }
        return n;
    }

    private void seed() {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES ('T1', NULL, '作品一', 1)");
        long topId = jdbc.queryForObject("SELECT id FROM category WHERE code = 'T1'", Long.class);
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES ('T1-COMIC', ?, '单行本', 1)", topId);
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES ('T1-MERCH', ?, '周边', 2)", topId);
        long comicId = jdbc.queryForObject("SELECT id FROM category WHERE code = 'T1-COMIC'", Long.class);
        long merchId = jdbc.queryForObject("SELECT id FROM category WHERE code = 'T1-MERCH'", Long.class);

        for (int i = 1; i <= 7; i++) {
            insertProduct(String.format("C%02d", i), comicId, "单行本" + i, "28.00", 10, "ON_SALE", i);
        }
        insertProduct("M01", merchId, "周边M01", "58.00", 5, "ON_SALE", 8);
    }

    private void insertProduct(String sku, long categoryId, String name, String price, int stock,
                               String status, int sortOrder) {
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, cover_image, price, stock, status, sort_order) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                sku, categoryId, name, "测试简介", "images/products/" + sku + "/cover.svg",
                new BigDecimal(price), stock, status, sortOrder);
    }
}