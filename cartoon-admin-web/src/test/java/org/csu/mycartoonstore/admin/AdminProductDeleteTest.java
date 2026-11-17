package org.csu.mycartoonstore.admin; // 后台删除商品：有订单引用拒绝，无引用时清购物车与相册。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号、分类、商品。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc / JdbcTemplate。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.mock.web.MockHttpSession; // 管理端会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import java.math.BigDecimal; // 订单金额。
import java.time.LocalDateTime; // 订单时间。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造 POST。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 跳转断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M10-01：依据指导书实验内容二「删除商品」、docs/06 AC-25/AC-26 的引用校验口径。
// 覆盖：无引用 → 商品、相册、购物车条目都没了；有订单引用 → 409 且商品仍在。
// 不覆盖：下架（M07-07 已验收）。
@AutoConfigureMockMvc // 不占端口。
class AdminProductDeleteTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    @Test // 无订单引用：删掉商品、相册，并清掉顾客车里的条目。
    @DisplayName("删除商品：相册与购物车条目一并清理")
    void deleteProductClearsImagesAndCart() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "DEL-1", leaf, "28.00", 5, "ON_SALE"); // 上架商品。
        jdbc.update("INSERT INTO product_image (product_id, image_path, alt_text, sort_order) VALUES (?,?,?,1)",
                productId, "images/products/DEL-1/1.svg", "图一"); // 一条相册。
        long customerId = AdminFixture.createCustomer(jdbc, "del_buyer"); // 一位顾客。
        jdbc.update("INSERT INTO cart_item (user_id, product_id, quantity, version) VALUES (?,?,?,0)",
                customerId, productId, 2); // 车里放了这件商品。

        mockMvc.perform(post("/products/{id}/delete", productId).with(csrf())
                        .session(adminSession())) // 管理员点删除。
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/products"));

        assertThat(count("SELECT COUNT(*) FROM product WHERE id = " + productId)).isZero(); // 商品没了。
        assertThat(count("SELECT COUNT(*) FROM product_image WHERE product_id = " + productId)).isZero(); // 相册没了。
        assertThat(count("SELECT COUNT(*) FROM cart_item WHERE product_id = " + productId)).isZero(); // 车里也清了。
    }

    @Test // 有订单引用：拒绝删除，商品与订单都还在。
    @DisplayName("删除商品：有订单引用 → 409 且商品保留")
    void deleteProductWithOrderReferenceIsRejected() throws Exception {
        long leaf = seedLeaf(); // 二级分类。
        long productId = AdminFixture.createProductIn(jdbc, "DEL-2", leaf, "28.00", 5, "ON_SALE"); // 上架商品。
        seedOrderWithItem(productId); // 历史订单引用了它。

        mockMvc.perform(post("/products/{id}/delete", productId).with(csrf())
                        .session(adminSession()))
                .andExpect(status().isConflict()); // 409：只能下架。

        assertThat(count("SELECT COUNT(*) FROM product WHERE id = " + productId)).isEqualTo(1); // 商品仍在。
        assertThat(count("SELECT COUNT(*) FROM sales_order_item WHERE product_id = " + productId)).isEqualTo(1); // 明细未动。
    }

    @Test // 商品不存在：404。
    @DisplayName("删除商品：不存在 → 404")
    void deleteMissingProduct() throws Exception {
        mockMvc.perform(post("/products/{id}/delete", 999_999L).with(csrf())
                        .session(adminSession()))
                .andExpect(status().isNotFound()); // 404。
    }

    /** 造一条引用该商品的已取消订单：CANCELLED 也必须保留明细。 */
    private void seedOrderWithItem(long productId) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 9, 0); // 固定时间。
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES ('del_owner', '$2a$10$test', '下单人', 'CUSTOMER', 'ENABLED')"); // 订单必须有归属。
        Long userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 'del_owner'", Long.class);
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at, cancelled_at) VALUES (?,?,?,?,?,?,?,?,?)",
                "CO202611180000000001", userId, "CANCELLED", new BigDecimal("28.00"), "张三",
                "13800000000", "某市某区某路 1 号", now, now.plusMinutes(5)); // 已取消也要留时间。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = 'CO202611180000000001'",
                Long.class);
        jdbc.update("INSERT INTO sales_order_item (order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "DEL-2", "商品DEL-2", "images/products/DEL-2/cover.svg",
                new BigDecimal("28.00"), 1, new BigDecimal("28.00")); // 明细指向该商品。
    }

    /** 造"作品 + 单行本"，返回二级分类 id。 */
    private long seedLeaf() {
        long top = AdminFixture.createCategory(jdbc, "DELWORK", null, "删除测试作品", 1); // 一级。
        return AdminFixture.createCategory(jdbc, "DELWORK-COMIC", top, "单行本", 1); // 二级。
    }

    /** 管理员会话。 */
    private MockHttpSession adminSession() {
        long adminId = AdminFixture.createAdmin(jdbc, "del_admin"); // 每例新建，避免互相影响。
        return AdminFixture.session(adminId); // 已登录态。
    }

    /** 单值计数。 */
    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class); // 单值查询。
        return value == null ? 0 : value; // 空值按 0。
    }
}
