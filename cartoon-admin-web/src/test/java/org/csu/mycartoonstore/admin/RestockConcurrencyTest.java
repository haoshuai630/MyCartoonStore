package org.csu.mycartoonstore.admin; // AC-28：用户下单与后台补货并发，库存最终一致。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号、分类与商品。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.csu.mycartoonstore.catalog.service.ProductAdminService; // 后台补货入口。
import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 下单需要的确认快照。
import org.csu.mycartoonstore.order.dto.Receiver; // 已校验的收货信息。
import org.csu.mycartoonstore.order.service.OrderService; // 用户下单入口。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入服务与 JdbcTemplate。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。

import java.math.BigDecimal; // 金额。
import java.time.Instant; // 快照时刻。
import java.util.List; // 快照行。
import java.util.concurrent.CountDownLatch; // 同步起点。
import java.util.concurrent.ExecutorService; // 两个线程。
import java.util.concurrent.Executors; // 创建线程池。
import java.util.concurrent.Future; // 结果。
import java.util.concurrent.TimeUnit; // 超时。
import java.util.concurrent.atomic.AtomicReference; // 收集异常。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。

// M07-07：依据 docs/06 AC-28、docs/02 §8.1、docs/09 §5「并发测试用两个独立事务」。
// 场景：初始库存 10，用户买 2 件、后台补 3 件，两个事务同时发起。
// 期望：两项都成功后库存 = 10 - 2 + 3 = 11，版本 +2，恰好一单。
// 说明：这里是两个独立连接、两个独立事务（同一进程）；"两个应用实例"的手工证据在 M07-09。
class RestockConcurrencyTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    @Autowired // 由 Spring 注入。
    private OrderService orderService; // 用户下单入口。

    @Autowired // 由 Spring 注入。
    private ProductAdminService productAdminService; // 后台补货入口。

    @Test // 两个事务并发：库存最终一致。
    @DisplayName("AC-28：用户买 2 与后台补 3 并发，库存 10 → 11")
    void restockAndOrderConcurrently() throws Exception {
        long userId = AdminFixture.createUser(jdbc, "conc_user", "CUSTOMER", "ENABLED"); // 买家。
        long top = AdminFixture.createCategory(jdbc, "CONC", null, "并发作品", 1); // 一级。
        long leaf = AdminFixture.createCategory(jdbc, "CONC-COMIC", top, "单行本", 1); // 二级。
        long productId = AdminFixture.createProductIn(jdbc, "CONC-1", leaf, "28.00", 10, "ON_SALE"); // 初始库存 10。
        jdbc.update("INSERT INTO cart_item (user_id, product_id, quantity, version) VALUES (?,?,?,0)",
                userId, productId, 2); // 车里 2 件。
        long cartItemId = jdbc.queryForObject(
                "SELECT id FROM cart_item WHERE user_id = ? AND product_id = ?", Long.class, userId, productId); // 快照行要用。

        CheckoutSnapshot snapshot = new CheckoutSnapshot(userId, // 确认快照：与车行完全一致。
                List.of(new CheckoutSnapshot.Line(cartItemId, productId, 2, 0L, new BigDecimal("28.00"))),
                new BigDecimal("56.00"), Instant.now()); // 总额 = 28 × 2。
        Receiver receiver = new Receiver("张三", "13800000000", "某市某区某路 1 号"); // 收货信息已校验。

        CountDownLatch start = new CountDownLatch(1); // 两个线程同一起点。
        AtomicReference<Throwable> orderError = new AtomicReference<>(); // 下单异常。
        AtomicReference<Throwable> restockError = new AtomicReference<>(); // 补货异常。
        ExecutorService pool = Executors.newFixedThreadPool(2); // 两个独立线程。
        try {
            Future<?> order = pool.submit(() -> { // 线程 A：用户下单。
                try {
                    start.await(); // 等发令。
                    orderService.placeOrder(userId, snapshot, receiver); // 独立事务。
                } catch (Throwable t) {
                    orderError.set(t); // 记下异常，主线程断言。
                }
            });
            Future<?> restock = pool.submit(() -> { // 线程 B：后台补货。
                try {
                    start.await(); // 等发令。
                    productAdminService.restock(productId, "3"); // 独立事务。
                } catch (Throwable t) {
                    restockError.set(t); // 记下异常。
                }
            });
            start.countDown(); // 同时放行。
            order.get(15, TimeUnit.SECONDS); // 等下单结束。
            restock.get(15, TimeUnit.SECONDS); // 等补货结束。
        } finally {
            pool.shutdownNow(); // 无论成败都收线程池。
        }

        assertThat(orderError.get()).isNull(); // 下单不应失败。
        assertThat(restockError.get()).isNull(); // 补货不应失败。
        assertThat(AdminFixture.stockOf(jdbc, productId)).isEqualTo(11); // 10 - 2 + 3。
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId))
                .isEqualTo(2L); // 扣减与补货各 +1。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_order", Integer.class))
                .isEqualTo(1); // 恰好一单。
    }
}
