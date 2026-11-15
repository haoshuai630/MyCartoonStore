package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.cart.dto.CartItemView;
import org.csu.mycartoonstore.cart.mapper.CartItemMapper;
import org.csu.mycartoonstore.catalog.entity.Product;
import org.csu.mycartoonstore.catalog.mapper.ProductMapper;
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper;
import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M01 集成测试：用真实 MySQL 验证数据库约束与自定义 SQL。
 *
 * <p>每个用例前由基类重建七张表，起点固定为空库。所有断言都落在数据库或 Mapper 的真实行为上，
 * 不用 Mock 证明 SQL 能跑。</p>
 *
 * <p>注意：MySQL 的 CHECK 违规（错误码 3819）在 Spring 里是 {@code UncategorizedSQLException}，
 * 不是 {@code DataIntegrityViolationException}，所以统一断言 {@code DataAccessException} + 错误文案。</p>
 */
class DataAccessTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Test
    @DisplayName("商品 SKU 唯一：重复插入被拒")
    void skuMustBeUnique() {
        long categoryId = newSecondaryCategory("DUPTEST");
        insertProduct("DUP-001", categoryId, "28.00", 10, "ON_SALE", 100);

        assertThatThrownBy(() -> insertProduct("DUP-001", categoryId, "30.00", 5, "ON_SALE", 100))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("价格必须大于 0：CHECK 拒绝 0 元")
    void priceMustBePositive() {
        long categoryId = newSecondaryCategory("PRICETEST");

        assertThatThrownBy(() -> insertProduct("BAD-PRICE", categoryId, "0.00", 1, "ON_SALE", 100))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Check constraint");
    }

    @Test
    @DisplayName("同一用户同一商品在购物车里只能有一条")
    void cartUniquePerUserProduct() {
        long userId = insertUser("u-cart");
        long categoryId = newSecondaryCategory("CARTTEST");
        long productId = insertProduct("CART-001", categoryId, "28.00", 10, "ON_SALE", 100);

        jdbc.update("INSERT INTO cart_item (user_id, product_id, quantity) VALUES (?,?,?)", userId, productId, 1);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO cart_item (user_id, product_id, quantity) VALUES (?,?,?)", userId, productId, 2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("有子分类的一级分类不能删除：外键 RESTRICT 拒绝")
    void cannotDeleteCategoryWithChildren() {
        long primaryId = insertPrimaryCategory("CAT-DEL", "作品-CATDEL");
        insertSecondaryCategory("CAT-DEL-COMIC", primaryId, "单行本");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM category WHERE id = ?", primaryId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    @DisplayName("明细行金额必须等于单价×数量：CHECK 拒绝不一致")
    void lineAmountMustMatchUnitPriceTimesQuantity() {
        long userId = insertUser("u-order");
        long categoryId = newSecondaryCategory("ORDTEST");
        long productId = insertProduct("ORD-001", categoryId, "50.00", 10, "ON_SALE", 100);
        long orderId = insertOrder("TEST-ORDER-1", userId, "PENDING_PAYMENT", "100.00");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sales_order_item (order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "ORD-001", "测试商品", "images/x.svg",
                new BigDecimal("50.00"), 2, new BigDecimal("999.00")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Check constraint");
    }

    @Test
    @DisplayName("已支付订单必须有支付时间：CHECK 拒绝半套支付信息")
    void paidOrderMustHavePaidAt() {
        long userId = insertUser("u-paid");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, receiver_phone, "
                        + "receiver_address, payment_method, payment_no, paid_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                "TEST-ORDER-2", userId, "PAID", new BigDecimal("10.00"),
                "收件人", "13800000000", "地址", "MOCK", "PAY-TEST-2", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Check constraint");
    }

    @Test
    @DisplayName("下架商品不会被首页查询返回")
    void draftProductIsInvisible() {
        long categoryId = newSecondaryCategory("DRAFTTEST");
        insertProduct("HIDDEN-01", categoryId, "28.00", 10, "DRAFT", 1);
        insertProduct("VISIBLE-01", categoryId, "28.00", 10, "ON_SALE", 1);

        assertThat(productMapper.selectFeatured(10))
                .extracting(Product::getSku)
                .containsExactly("VISIBLE-01");
    }

    @Test
    @DisplayName("精选推荐按 sort_order 升序返回")
    void featuredOrderedBySortOrder() {
        long categoryId = newSecondaryCategory("FEATURED");
        insertProduct("S-3", categoryId, "28.00", 10, "ON_SALE", 3);
        insertProduct("S-1", categoryId, "28.00", 10, "ON_SALE", 1);
        insertProduct("S-4", categoryId, "28.00", 10, "ON_SALE", 4);
        insertProduct("S-2", categoryId, "28.00", 10, "ON_SALE", 2);

        assertThat(productMapper.selectFeatured(3))
                .extracting(Product::getSku)
                .containsExactly("S-1", "S-2", "S-3");
    }

    @Test
    @DisplayName("扣库存：足够时扣减并升版，不足时影响 0 行")
    void deductStockRespectsInventory() {
        long categoryId = newSecondaryCategory("STOCKTEST");
        long productId = insertProduct("STOCK-01", categoryId, "28.00", 1, "ON_SALE", 100);

        assertThat(productMapper.deductStock(productId, 2)).isZero();
        assertThat(productMapper.deductStock(productId, 1)).isEqualTo(1);

        Integer stock = jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        Long version = jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId);
        assertThat(stock).isZero();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    @DisplayName("购物车视图联表取到商品当前价格与库存")
    void cartViewJoinsCurrentPriceAndStock() {
        long userId = insertUser("u-view");
        long categoryId = newSecondaryCategory("VIEWTEST");
        long productId = insertProduct("VIEW-01", categoryId, "28.00", 7, "ON_SALE", 100);
        jdbc.update("INSERT INTO cart_item (user_id, product_id, quantity) VALUES (?,?,?)", userId, productId, 2);

        List<CartItemView> views = cartItemMapper.selectViewByUser(userId);
        assertThat(views).hasSize(1);
        CartItemView view = views.get(0);
        assertThat(view.getProductName()).isEqualTo("测试商品");
        assertThat(view.getPrice()).isEqualByComparingTo("28.00");
        assertThat(view.getStock()).isEqualTo(7);
        assertThat(view.getQuantity()).isEqualTo(2);
        assertThat(view.getCategoryName()).isEqualTo("单行本");
    }

    @Test
    @DisplayName("订单号查重：不存在返回 0，存在返回 1")
    void countByOrderNoWorks() {
        long userId = insertUser("u-count");
        assertThat(salesOrderMapper.countByOrderNo("NOPE-1")).isZero();

        insertOrder("EXISTS-1", userId, "PENDING_PAYMENT", "10.00");
        assertThat(salesOrderMapper.countByOrderNo("EXISTS-1")).isEqualTo(1);
    }

    /** 建一个一级 + 一个二级，返回二级 id；名称带 code 后缀，避免同级重名冲突。 */
    private long newSecondaryCategory(String codeSuffix) {
        long primaryId = insertPrimaryCategory("IP-" + codeSuffix, "作品-" + codeSuffix);
        return insertSecondaryCategory("COMIC-" + codeSuffix, primaryId, "单行本");
    }

    /** 插入一个启用状态的普通用户，返回 id。 */
    private long insertUser(String username) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                        + "VALUES (?,?,?,?,?)",
                username, "$2a$10$0123456789012345678901234567890123456789012345678901",
                "测试用户", "CUSTOMER", "ENABLED");
        return jdbc.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username);
    }

    /** 插入一级分类（parent_id 为空）。 */
    private long insertPrimaryCategory(String code, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?, NULL, ?, 0)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    /** 插入二级分类，父必须是一级。 */
    private long insertSecondaryCategory(String code, long parentId, String name) {
        jdbc.update("INSERT INTO category (code, parent_id, name, sort_order) VALUES (?,?,?,0)",
                code, parentId, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    /** 插入一个商品，返回 id；库存与状态由用例指定。 */
    private long insertProduct(String sku, long categoryId, String price, int stock, String status, int sortOrder) {
        jdbc.update("INSERT INTO product (sku, category_id, name, cover_image, price, stock, status, sort_order) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                sku, categoryId, "测试商品", "images/products/" + sku + "/cover.svg",
                new BigDecimal(price), stock, status, sortOrder);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }

    /** 插入一张订单（不含明细），返回 id。 */
    private long insertOrder(String orderNo, long userId, String status, String total) {
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address) VALUES (?,?,?,?,?,?,?)",
                orderNo, userId, status, new BigDecimal(total), "收件人", "13800000000", "测试地址");
        return jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
    }
}