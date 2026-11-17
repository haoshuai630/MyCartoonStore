package org.csu.mycartoonstore.admin;

import org.csu.mycartoonstore.admin.support.AdminFixture;
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 相册维护页测试：列表、新增、编辑、删除、校验与"不改封面/不动版本"。
 */
@AutoConfigureMockMvc
class ImageAdminPageTest extends AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("未登录访问相册页：302 到管理端登录页")
    void imagesRequireLogin() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long productId = AdminFixture.createProduct(jdbc, "IMGPROD", "28.00", 10, "ON_SALE");

        mockMvc.perform(get("/products/" + productId + "/images"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fproducts*"));
    }

    @Test
    @DisplayName("相册页渲染商品信息与已有图片")
    void listRenders() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long productId = AdminFixture.createProduct(jdbc, "IMGPROD", "28.00", 10, "ON_SALE");
        insertImage(productId, "images/products/IMGPROD/1.svg", "图一", 1);

        mockMvc.perform(get("/products/" + productId + "/images").session(AdminFixture.session(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("相册维护")))
                .andExpect(content().string(containsString("IMGPROD")))
                .andExpect(content().string(containsString("images/products/IMGPROD/1.svg")));
    }

    @Test
    @DisplayName("新增图片：回列表并写库")
    void addViaPage() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long productId = AdminFixture.createProduct(jdbc, "IMGPROD", "28.00", 10, "ON_SALE");

        mockMvc.perform(post("/products/" + productId + "/images").session(AdminFixture.session(adminId))
                        .param("imagePath", "images/products/IMGPROD/2.svg")
                        .param("altText", "新图")
                        .param("sortOrder", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/" + productId + "/images"))
                .andExpect(flash().attribute("message", "已新增一张相册图"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_image WHERE product_id = ?",
                Integer.class, productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sort_order FROM product_image WHERE product_id = ?",
                Integer.class, productId)).isEqualTo(4);
    }

    @Test
    @DisplayName("编辑图片：只改路径、说明与排序")
    void editViaPage() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long productId = AdminFixture.createProduct(jdbc, "IMGPROD", "28.00", 10, "ON_SALE");
        long imageId = insertImage(productId, "images/products/IMGPROD/1.svg", "旧说明", 1);

        mockMvc.perform(post("/products/" + productId + "/images/" + imageId)
                        .session(AdminFixture.session(adminId))
                        .param("imagePath", "images/products/IMGPROD/9.svg")
                        .param("altText", "新说明")
                        .param("sortOrder", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "已保存相册图"));

        assertThat(jdbc.queryForObject("SELECT image_path FROM product_image WHERE id = ?",
                String.class, imageId)).isEqualTo("images/products/IMGPROD/9.svg");
        assertThat(jdbc.queryForObject("SELECT alt_text FROM product_image WHERE id = ?",
                String.class, imageId)).isEqualTo("新说明");
        assertThat(jdbc.queryForObject("SELECT sort_order FROM product_image WHERE id = ?",
                Integer.class, imageId)).isEqualTo(9);
    }

    @Test
    @DisplayName("删除图片：只删记录，封面与商品版本不变")
    void deleteViaPage() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long productId = AdminFixture.createProduct(jdbc, "IMGPROD", "28.00", 10, "ON_SALE");
        long imageId = insertImage(productId, "images/products/IMGPROD/1.svg", "图一", 1);
        String coverBefore = coverOf(productId);
        long versionBefore = versionOf(productId);

        mockMvc.perform(post("/products/" + productId + "/images/" + imageId + "/delete")
                        .session(AdminFixture.session(adminId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "已删除相册图（文件保留）"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_image WHERE id = ?",
                Integer.class, imageId)).isZero();
        assertThat(coverOf(productId)).isEqualTo(coverBefore); // 封面不变。
        assertThat(versionOf(productId)).isEqualTo(versionBefore); // 相册不写商品版本。
    }

    @Test
    @DisplayName("外链路径：400、回列表并提示，不写库")
    void externalPathRejected() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");
        long productId = AdminFixture.createProduct(jdbc, "IMGPROD", "28.00", 10, "ON_SALE");

        mockMvc.perform(post("/products/" + productId + "/images").session(AdminFixture.session(adminId))
                        .param("imagePath", "https://example.com/a.jpg"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("图片路径只接受站内路径")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_image WHERE product_id = ?",
                Integer.class, productId)).isZero();
    }

    @Test
    @DisplayName("商品不存在：404")
    void unknownProduct() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "rootadmin");

        mockMvc.perform(get("/products/9999/images").session(AdminFixture.session(adminId)))
                .andExpect(status().isNotFound());
    }

    /** 插一张相册图，返回 id */
    private long insertImage(long productId, String path, String alt, int sort) {
        jdbc.update("INSERT INTO product_image (product_id, image_path, alt_text, sort_order) VALUES (?,?,?,?)",
                productId, path, alt, sort);
        return jdbc.queryForObject("SELECT id FROM product_image WHERE product_id = ? AND image_path = ?",
                Long.class, productId, path);
    }

    private String coverOf(long productId) {
        return jdbc.queryForObject("SELECT cover_image FROM product WHERE id = ?", String.class, productId);
    }

    private long versionOf(long productId) {
        Long version = jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId);
        return version == null ? 0 : version;
    }
}
