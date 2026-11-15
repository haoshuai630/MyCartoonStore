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
 * 详情页测试：正常商品、下架商品、不存在的 id、相册与无相册两种情况。
 */
@AutoConfigureMockMvc
class ProductPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("详情页显示名称、价格与两级分类面包屑")
    void detailShowsProductAndBreadcrumb() throws Exception {
        long productId = seedOnSaleProduct();

        mockMvc.perform(get("/product/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试商品详情")))
                .andExpect(content().string(containsString("¥28.00")))
                .andExpect(content().string(containsString("面包屑作品")))
                .andExpect(content().string(containsString("单行本")));
    }

    @Test
    @DisplayName("有相册时渲染缩略图，无相册时只有封面")
    void galleryOnlyWhenImagesExist() throws Exception {
        long withImages = seedOnSaleProduct();
        addImage(withImages, 1, "images/products/DET-C01/1.svg");
        addImage(withImages, 2, "images/products/DET-C01/2.svg");
        long withoutImages = seedOnSaleProduct("DET-C02", "无相册商品");

        String withGallery = mockMvc.perform(get("/product/{id}", withImages))
                .andReturn().getResponse().getContentAsString();
        String noGallery = mockMvc.perform(get("/product/{id}", withoutImages))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(withGallery).contains("thumbs");
        org.assertj.core.api.Assertions.assertThat(noGallery).doesNotContain("thumbs");
    }

    @Test
    @DisplayName("下架商品返回 404，不泄露存在性")
    void draftProductReturns404() throws Exception {
        long categoryId = seedCategory("DRAFTIP");
        long draftId = insertProduct("DRAFT-01", categoryId, "已下架商品", "DRAFT");

        mockMvc.perform(get("/product/{id}", draftId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("不存在的 id 返回 404 而不是 500")
    void missingProductReturns404() throws Exception {
        mockMvc.perform(get("/product/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("商品不存在或已下架")));
    }

    private long seedOnSaleProduct() {
        return seedOnSaleProduct("DET-C01", "测试商品详情");
    }

    private long seedOnSaleProduct(String sku, String name) {
        long categoryId = seedCategory(sku.replace("-", ""));
        return insertProduct(sku, categoryId, name, "ON_SALE");
    }

    private long seedCategory(String code) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 1)", code, "面包屑作品" + code);
        long parentId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,1)",
                code + "-COMIC", parentId, "单行本");
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code + "-COMIC");
    }

    private long insertProduct(String sku, long categoryId, String name, String status) {
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, description, cover_image, price, stock, status, sort_order) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                sku, categoryId, name, "一句话简介", "商品说明正文",
                "images/products/" + sku + "/cover.svg", new BigDecimal("28.00"), 10, status, 1);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }

    private void addImage(long productId, int sortOrder, String path) {
        jdbc.update("INSERT INTO product_image (product_id, image_path, sort_order) VALUES (?,?,?)",
                productId, path, sortOrder);
    }
}