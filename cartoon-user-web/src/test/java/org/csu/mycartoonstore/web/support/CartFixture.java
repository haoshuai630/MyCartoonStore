package org.csu.mycartoonstore.web.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;

/**
 * 购物车测试夹具：建账号、建商品、直接写购物车条目。
 *
 * <p>购物车写操作走接口，但"要有一辆车"的准备工作不值得再走一遍加车接口——
 * 直接插 {@code cart_item} 能把用例聚焦在被测的那一条规则上，
 * 也方便造出"库存不足 / 已下架"这类正常加车加不出来的失效行。</p>
 */
public final class CartFixture {

    private CartFixture() {
    }

    /** 建一个登录用户，返回 id；密码哈希只要非空即可，测试不走登录接口 */
    public static long createUser(JdbcTemplate jdbc, String username) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES (?,?,?,?,?)", username, "$2a$10$test", "测试用户", "CUSTOMER", "ENABLED");
        return jdbc.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username);
    }

    /** 建"作品 + 单行本 + 商品"，返回商品 id；sku 同时用作分类 code 前缀保证唯一 */
    public static long createProduct(JdbcTemplate jdbc, String sku, String price, int stock, String status) {
        String workCode = sku.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 1)",
                workCode, "作品" + workCode);
        long workId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, workCode);
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,1)",
                workCode + "-COMIC", workId, "单行本");
        long categoryId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class,
                workCode + "-COMIC");
        jdbc.update("INSERT INTO product (sku, category_id, name, summary, description, cover_image, "
                        + "price, stock, status, sort_order) VALUES (?,?,?,?,?,?,?,?,?,1)",
                sku, categoryId, "商品" + sku, "一句话简介", "商品说明",
                "images/products/" + sku + "/cover.svg", new BigDecimal(price), stock, status);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }

    /** 给指定用户放一条购物车条目，返回条目 id */
    public static long addCartItem(JdbcTemplate jdbc, long userId, long productId, int quantity) {
        jdbc.update("INSERT INTO cart_item (user_id, product_id, quantity, version) VALUES (?,?,?,0)",
                userId, productId, quantity);
        return jdbc.queryForObject("SELECT id FROM cart_item WHERE user_id = ? AND product_id = ?",
                Long.class, userId, productId);
    }

    /** 当前条目数量；条目已删除时返回 null */
    public static Integer quantityOf(JdbcTemplate jdbc, long itemId) {
        java.util.List<Integer> rows = jdbc.queryForList(
                "SELECT quantity FROM cart_item WHERE id = ?", Integer.class, itemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 当前条目版本；条目已删除时返回 null */
    public static Long versionOf(JdbcTemplate jdbc, long itemId) {
        java.util.List<Long> rows = jdbc.queryForList(
                "SELECT version FROM cart_item WHERE id = ?", Long.class, itemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 车里的条目数 */
    public static int countItems(JdbcTemplate jdbc, long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cart_item WHERE user_id = ?",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    /** 已登录会话：拦截器只认 userId */
    public static MockHttpSession session(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        session.setAttribute("displayName", "测试用户");
        return session;
    }
}
