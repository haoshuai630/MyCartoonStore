package org.csu.mycartoonstore.web; // 并发验证：同一辆车只成交一次；同一订单只恢复一次库存。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/车条目。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.mock.web.MockHttpSession; // 两个独立会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取响应状态。

import java.math.BigDecimal; // 金额。
import java.util.List; // 两个任务的结果。
import java.util.concurrent.Callable; // 并发任务。
import java.util.concurrent.CountDownLatch; // 同时起跑。
import java.util.concurrent.ExecutorService; // 线程池。
import java.util.concurrent.Executors; // 固定大小线程池。
import java.util.concurrent.Future; // 取结果。
import java.util.concurrent.TimeUnit; // 超时。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。

// M05-06：依据 docs/06 AC-17（同一车并发提交只成交一次）与 AC-21（取消只恢复一次）。
// 用两个独立会话同时发请求，验证行锁 + 快照比对 + 幂等分支在真实事务下的表现。
// 不覆盖：支付并发（M06）。
@AutoConfigureMockMvc // 不占端口。
class ConcurrentOrderTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口，多线程共用一个实例。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    /** 同一辆车两个会话同时提交：只成交一单，另一单 409。 */
    @Test
    @DisplayName("同一车并发提交：只成交一单")
    void concurrentPlaceOrderCreatesOnlyOneOrder() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cc_place"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CC-P01", "28.00", 10, "ON_SALE"); // 库存 10。
        CartFixture.addCartItem(jdbc, userId, productId, 2); // 车里 2 件。
        MockHttpSession sessionA = confirm(userId); // 会话 A 的确认快照。
        MockHttpSession sessionB = confirm(userId); // 会话 B 的确认快照（同一辆车）。

        List<Integer> statuses = runTogether( // 两个线程同时提交。
                () -> place(sessionA), // 提交 A。
                () -> place(sessionB)); // 提交 B。

        assertThat(statuses).containsExactlyInAnyOrder(302, 409); // 一个成功、一个冲突。
        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isEqualTo(1); // 只一单。
        assertThat(count("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(8); // 只扣一次。
        assertThat(CartFixture.countItems(jdbc, userId)).isZero(); // 车已清空。
    }

    /** 同一订单两个会话同时取消：库存只恢复一次。 */
    @Test
    @DisplayName("同一订单并发取消：只恢复一次库存")
    void concurrentCancelRestoresStockOnce() throws Exception {
        long userId = CartFixture.createUser(jdbc, "cc_cancel"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "CC-P02", "28.00", 8, "ON_SALE"); // 模拟已扣到 8。
        seedPendingOrder(userId, "CO202611171000000071", productId, 2); // 待支付订单，库存已扣。

        List<Integer> statuses = runTogether( // 两个线程同时取消。
                () -> cancel("CO202611171000000071", userId), // 取消 A。
                () -> cancel("CO202611171000000071", userId)); // 取消 B。

        assertThat(statuses).containsOnly(302); // 两次都成功重定向（第二次幂等）。
        assertThat(count("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(10); // 只加一次。
        assertThat(count("SELECT version FROM product WHERE id = ?", productId)).isEqualTo(1); // 版本只 +1。
        assertThat(value("SELECT status FROM sales_order WHERE order_no = ?", String.class,
                "CO202611171000000071")).isEqualTo("CANCELLED"); // 状态已取消。
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

    /** 打开确认页，拿到带快照的会话。 */
    private MockHttpSession confirm(long userId) throws Exception {
        MockHttpSession session = CartFixture.session(userId); // 新会话。
        mockMvc.perform(get("/checkout").session(session)).andReturn(); // 生成快照。
        return session; // 返回会话。
    }

    /** 提交订单，返回状态码。 */
    private int place(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/checkout").session(session) // 提交。
                        .param("receiverName", "张三")
                        .param("receiverPhone", "13800000000")
                        .param("receiverAddress", "某市某区某路 1 号"))
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

    /** 造待支付订单 + 明细，并把库存设为"已扣"后的值。 */
    private void seedPendingOrder(long owner, String orderNo, long productId, int quantity) {
        java.time.LocalDateTime created = java.time.LocalDateTime.of(2026, 11, 17, 9, 0); // 固定时间。
        jdbc.update("INSERT INTO sales_order(order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at) VALUES (?,?,?,?,?,?,?,?)",
                orderNo, owner, "PENDING_PAYMENT", new BigDecimal("56.00"), "张三", "13800000000",
                "某市某区某路 1 号", created); // 待支付订单。
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo); // 订单 id。
        jdbc.update("INSERT INTO sales_order_item(order_id, product_id, sku_snapshot, product_name_snapshot, "
                        + "cover_image_snapshot, unit_price, quantity, line_amount) VALUES (?,?,?,?,?,?,?,?)",
                orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/cc.svg",
                new BigDecimal("28.00"), quantity, new BigDecimal("56.00")); // 明细。
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args); // 单值。
        return value == null ? 0 : value; // 空按 0。
    }

    private <T> T value(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, args); // 任意类型单值。
    }
}
