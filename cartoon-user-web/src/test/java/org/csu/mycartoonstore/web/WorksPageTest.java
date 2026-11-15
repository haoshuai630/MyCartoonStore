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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 作品墙测试：作品名、品类数、在售数。
 */
@AutoConfigureMockMvc
class WorksPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("作品墙显示作品名、品类数与在售数")
    /** 用例：作品墙显示名称、品类数与在售数。 */
    void worksShowNameAndCounts() throws Exception {
        long workId = seedWork("W1", "测试作品甲");
        long comic = seedCategory("W1-COMIC", workId, "单行本");
        seedCategory("W1-BADGE", workId, "徽章");
        insertProduct("W1-C01", comic, "在售卷", "ON_SALE");
        insertProduct("W1-C02", comic, "另一卷", "ON_SALE");

        mockMvc.perform(get("/works"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试作品甲")))
                .andExpect(content().string(containsString("2 个品类")))
                .andExpect(content().string(containsString("2 件在售")))
                .andExpect(content().string(containsString("共 1 部")));
    }

    @Test
    @DisplayName("空库时作品墙显示空状态")
    /** 用例：空库显示空状态文案。 */
    void worksEmptyState() throws Exception {
        mockMvc.perform(get("/works"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("还没有任何作品")));
    }

    /** 插入一部作品（一级分类），返回 id。 */
    private long seedWork(String code, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 1)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    /** 在某部作品下插入一个品类，返回 id。 */
    private long seedCategory(String code, long parentId, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,1)", code, parentId, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    /** 插入一件商品，状态由用例指定。 */
    private void insertProduct(String sku, long categoryId, String name, String status) {
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, cover_image, price, stock, status, sort_order) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                sku, categoryId, name, "简介", "images/products/" + sku + "/cover.svg",
                new BigDecimal("28.00"), 10, status, 1);
    }
}