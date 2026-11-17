package org.csu.mycartoonstore.admin.support; // 管理端测试夹具：造账号、造商品、造订单、造会话。

import org.springframework.jdbc.core.JdbcTemplate; // 直接写库准备数据。
import org.springframework.mock.web.MockHttpSession; // 手工会话。

import java.math.BigDecimal; // 价格与金额。

// M07-02：管理端用例的准备数据都从这里来。
// 账号：管理端只认 ADMIN，所以单独提供 createAdmin；顾客账号用于"顾客不能进后台"。
// 商品与库存：M07-06/07 的版本冲突、补货并发要造"下单后"的状态。
public final class AdminFixture {

    private AdminFixture() { // 工具类不实例化。
    }

    /** 建一个启用的管理员，返回 id */
    public static long createAdmin(JdbcTemplate jdbc, String username) {
        return createUser(jdbc, username, "ADMIN", "ENABLED"); // 管理端登录只认这个角色。
    }

    /** 建一个启用的普通顾客，返回 id；用于"顾客不能进后台" */
    public static long createCustomer(JdbcTemplate jdbc, String username) {
        return createUser(jdbc, username, "CUSTOMER", "ENABLED"); // 顾客：启用状态。
    }

    /** 建指定角色与状态的账号，返回 id */
    public static long createUser(JdbcTemplate jdbc, String username, String role, String status) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES (?,?,?,?,?)", username, "$2a$10$test", "测试账号", role, status); // 散列不参与登录用例。
        return jdbc.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username); // 会话要用 id。
    }

    /** 已登录会话：拦截器只认 userId，显示名用于页头 */
    public static MockHttpSession session(long userId) {
        MockHttpSession session = new MockHttpSession(); // 手工会话，不依赖登录接口。
        session.setAttribute("userId", userId); // 与 AdminAuthInterceptor.USER_ID 一致。
        session.setAttribute("displayName", "测试管理员"); // 页头显示。
        return session; // 交给请求构造器。
    }

    /** 把商品改成指定库存与版本：模拟"用户下单后"的状态 */
    public static void setStock(JdbcTemplate jdbc, long productId, int stock, long version) {
        jdbc.update("UPDATE product SET stock = ?, version = ? WHERE id = ?", stock, version, productId); // 两条都要写，版本冲突靠它。
    }

    /** 读商品库存，核对并发结果 */
    public static int stockOf(JdbcTemplate jdbc, long productId) {
        Integer stock = jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId); // 单值查询。
        return stock == null ? 0 : stock; // 空值按 0，断言时不必再判空。
    }

    /** 建一个分类：parentId 为空即一级；返回 id */
    public static long createCategory(JdbcTemplate jdbc, String code, Long parentId, String name, int sortOrder) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,?)",
                code, parentId, name, sortOrder); // parent_key 是生成列，不用写。
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code); // 用例要用 id。
    }

    /** 在指定二级分类下建商品，返回 id；状态由用例给 */
    public static long createProductIn(JdbcTemplate jdbc, String sku, long categoryId,
                                       String price, int stock, String status) {
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, description, cover_image, "
                        + "price, stock, status, sort_order) VALUES (?,?,?,?,?,?,?,?,?,1)",
                sku, categoryId, "商品" + sku, "一句话简介", "商品说明", // 名称由 SKU 派生，便于断言。
                "images/products/" + sku + "/cover.svg", new BigDecimal(price), stock, status); // 封面按 SKU 拼。
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku); // 用例要用 id。
    }

    /** 建"作品 + 单行本 + 商品"，返回商品 id；sku 同时用作分类 code 前缀保证唯一 */
    public static long createProduct(JdbcTemplate jdbc, String sku, String price, int stock, String status) {
        String workCode = sku.replaceAll("[^A-Za-z0-9]", "").toUpperCase(); // 分类编码只允许大写。
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 1)",
                workCode, "作品" + workCode); // 一级分类：parent_id 为空。
        long workId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, workCode); // 取一级 id。
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,1)",
                workCode + "-COMIC", workId, "单行本"); // 二级分类必须以 -COMIC 结尾。
        long categoryId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class,
                workCode + "-COMIC"); // 商品只能挂二级。
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, description, cover_image, "
                        + "price, stock, status, sort_order) VALUES (?,?,?,?,?,?,?,?,?,1)", // 列数与占位符一一对应。
                sku, categoryId, "商品" + sku, "一句话简介", "商品说明", // 名称与文案由 sku 派生。
                "images/products/" + sku + "/cover.svg", new BigDecimal(price), stock, status); // 封面按 SKU 拼路径。
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku); // 用例要用商品 id。
    }
}
