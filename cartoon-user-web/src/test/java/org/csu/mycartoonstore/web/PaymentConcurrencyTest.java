package org.csu.mycartoonstore.web; // 并发验证：支付与取消同一订单只有一种迁移成功；并发重复支付只写一次。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/会话。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取响应状态。

import java.math.BigDecimal; // 金额。
import java.time.LocalDateTime; // 支付/取消时间。
import java.util.List; // 两个任务的结果。
import java.util.concurrent.Callable; // 并发任务。
import java.util.concurrent.CountDownLatch; // 同时起跑。
import java.util.concurrent.ExecutorService; // 线程池。
import java.util.concurrent.Executors; // 固定大小线程池。
import java.util.concurrent.Future; // 取结果。
import java.util.concurrent.TimeUnit; // 超时。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。

// M06-03：依据 docs/06 AC-24 与 docs/02 §8.1「支付与取消只锁订单行」。
// 覆盖：支付与取消并发只有一种迁移成功；并发重复支付只有一次写入，凭证不重复生成。
// 不覆盖：单线程的成功/失败语义（PaymentTest 已覆盖）。
@AutoConfigureMockMvc // 不占端口。
class PaymentConcurrencyTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口，多线程共用一个实例。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    /** 支付与取消同时发生：一个 302、一个 409；最终状态、凭证与库存必须自洽。 */
    @Test
    @DisplayName("支付与取消并发：只有一种迁移成功")
    void concurrentPayAndCancelMigratesOnce() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pc_paycancel"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PC-P01", "28.00", 10, "ON_SALE"); // 商品。

        // 跑 3 轮：谁先拿到订单行锁由调度决定，两种结局都要自洽。
        for (int round = 1; round <= 3; round++) {
            String orderNo = "CO2026111716100000" + round; // 每轮一张独立订单。
            seedPendingOrder(userId, orderNo, productId); // 待支付，库存已扣到 8。

            List<Integer> statuses = runTogether( // 同一刻发起支付与取消。
                    () -> pay(orderNo, userId), // 支付。
                    () -> cancel(orderNo, userId)); // 取消。

            assertThat(statuses).containsExactlyInAnyOrder(302, 409); // 只有一个成功。
            assertThatConsistent(orderNo, productId); // 状态、凭证、库存互相自洽。
        }
    }

    /** 并发重复支付：两个请求都 302，但只写一次、凭证只有一个。 */
    @Test
    @DisplayName("并发重复支付：只写一次凭证")
    void concurrentDoublePayWritesOnce() throws Exception {
        long userId = CartFixture.createUser(jdbc, "pc_double"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PC-P02", "28.00", 10, "ON_SALE"); // 商品。
        seedPendingOrder(userId, "CO202611171620000001", productId); // 待支付订单。

        List<Integer> statuses = runTogether( // 两个会话同时支付同一单。
                () -> pay("CO202611171620000001", userId), // 支付 A。
                () -> pay("CO202611171620000001", userId)); // 支付 B。

        assertThat(statuses).containsOnly(302); // 两次都成功（第二次走幂等）。
        assertThat(value("SELECT status FROM sales_order WHERE order_no = ?", String.class,
                "CO202611171620000001")).isEqualTo("PAID"); // 已支付。
        assertThat(count("SELECT COUNT(DISTINCT payment_no) FROM sales_order WHERE order_no = ?",
                "CO202611171620000001")).isEqualTo(1); // 只生成一个凭证。
        assertThat(count("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(8); // 库存只扣过订单量。
    }

    // ---------------------------------------------------------------- 自洽检查

    /** 最终态只允许两种：PAID（库存保持已扣）或 CANCELLED（库存恢复一次）。 */
    private void assertThatConsistent(String orderNo, long productId) {
        String status = value("SELECT status FROM sales_order WHERE order_no = ?", String.class, orderNo); // 最终状态。
        String paymentNo = value("SELECT payment_no FROM sales_order WHERE order_no = ?", String.class, orderNo); // 凭证。
        LocalDateTime paidAt = value("SELECT paid_at FROM sales_order WHERE order_no = ?",
                LocalDateTime.class, orderNo); // 支付时间。
        LocalDateTime cancelledAt = value("SELECT cancelled_at FROM sales_order WHERE order_no = ?",
                LocalDateTime.class, orderNo); // 取消时间。
        int stock = count("SELECT stock FROM product WHERE id = ?", productId); // 库存。
        int version = count("SELECT version FROM product WHERE id = ?", productId); // 版本。

        if ("PAID".equals(status)) { // 支付先赢。
            assertThat(paymentNo).startsWith("PAY"); // 有凭证。
            assertThat(paidAt).isNotNull(); // 有支付时间。
            assertThat(cancelledAt).isNull(); // 没有取消时间。
            assertThat(stock).isEqualTo(8); // 库存保持已扣。
            assertThat(version).isEqualTo(1); // 版本只在下单时 +1。
        } else { // 取消先赢。
            assertThat(status).isEqualTo("CANCELLED"); // 只可能是已取消。
            assertThat(paymentNo).isNull(); // 没有凭证。
            assertThat(paidAt).isNull(); // 没有支付时间。
            assertThat(cancelledAt).isNotNull(); // 有取消时间。
            assertThat(stock).isEqualTo(10); // 库存恢复一次。
            assertThat(version).isEqualTo(2); // 下单 +1、取消 +1。
        }
    }

    // ---------------------------------------------------------------- 并发夹具

    /** 让两个任务在同一刻起跑，收集各自的 HTTP 状态码。 */
    private List<Integer> runTogether(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); // 两个线程。
        try {
            CountDownLatch ready = new CountDownLatch(2); // 两个任务都准备好。
            CountDownLatch start = new CountDownLatch(1); // 同时放行。
            Future<Integer> a = pool.submit(wrapped(first, ready, start)); // 任务 A。
            Future<Integer> b = pool.submit(wrapped(second, ready, start)); // 任务 B。
            ready.await(5, TimeUnit.SECONDS); // 等两个都就绪。
            start.countDown(); // 发令。
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)); // 收集状态码。
        } finally {
            pool.shutdownNow(); // 无论如何都收线程池。
        }
    }

    /** 把任务包成"先就绪、再等发令、最后执行"，保证两个请求同时起跑。 */
    private Callable<Integer> wrapped(Callable<Integer> task, CountDownLatch ready, CountDownLatch start) {
        return () -> { // 包装后的任务。
            ready.countDown(); // 报告就绪。
            start.await(5, TimeUnit.SECONDS); // 等发令。
            return task.call(); // 真正发请求。
        };
    }

    /** 模拟支付成功，返回状态码。 */
    private int pay(String orderNo, long userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/payment/{no}/success", orderNo)
                        .session(CartFixture.session(userId))) // 每次新会话，但同一用户。
                .andReturn(); // 取响应。
        return result.getResponse().getStatus(); // 状态码。
    }

    /** 取消订单，返回状态码。 */
    private int cancel(String orderNo, long userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/orders/{no}/cancel", orderNo)
                        .session(CartFixture.session(userId))) // 每次新会话，但同一用户。
                .andReturn(); // 取响应。
        return result.getResponse().getStatus(); // 状态码。
    }

    /** 造待支付订单 + 明细，并把库存改成"已扣"后的值。 */
    private void seedPendingOrder(long owner, String orderNo, long productId) {
        LocalDateTime created = LocalDateTime.of(2026, 11, 17, 9, 0); // 固定创建时间。
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at) VALUES (?,?,?,?,?,?,?,?)",
                orderNo, owner, "PENDING_PAYMENT", new BigDecimal("56.00"), "张三", "13800000000",
                "某市某区某路 1 号", created); // 待支付订单。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 订单 id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/pc.svg",
                new BigDecimal("28.00"), 2, new BigDecimal("56.00")); // 两件 ¥28.00。
        jdbc.update("UPDATE product SET stock = 8, version = 1 WHERE id = ?", productId); // 模拟下单已扣 2 件。
    }

    private int count(String sql, Object... args) { // 夹具：整数单值，空按 0。
        Integer value = jdbc.queryForObject(sql, Integer.class, args); // 整数单值。
        return value == null ? 0 : value; // 空按 0。
    }

    private <T> T value(String sql, Class<T> type, Object... args) { // 夹具：任意类型单值。
        return jdbc.queryForObject(sql, type, args); // 任意类型单值。
    }
}
