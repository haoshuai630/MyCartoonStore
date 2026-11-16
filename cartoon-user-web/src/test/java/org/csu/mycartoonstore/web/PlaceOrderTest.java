package org.csu.mycartoonstore.web; // 下单事务：写订单与明细、扣库存、删结算条目、失败回滚。

import org.csu.mycartoonstore.cart.service.CartService; // 用 Spy 在"删结算条目"这一步注入失败。
import org.csu.mycartoonstore.web.controller.CheckoutController; // 会话里快照的键。
import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 建用户/商品/车条目。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.mock.web.MockHttpSession; // 带登录身份的会话。
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean; // 包一层 Spy，不替换真实逻辑。
import org.springframework.test.web.servlet.MockMvc; // 发请求。
import org.springframework.test.web.servlet.MvcResult; // 取重定向地址。

import java.math.BigDecimal; // 金额断言。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.hamcrest.Matchers.not; // 反向断言。
import static org.mockito.ArgumentMatchers.anyList; // 匹配条目 id 列表。
import static org.mockito.ArgumentMatchers.anyLong; // 匹配用户 id。
import static org.mockito.Mockito.doThrow; // 让 Spy 在指定方法上抛异常。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 内容断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern; // 断言跳转。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M05-03：依据 docs/01 FR-O-02、docs/02 §7.3、docs/06 AC-14～17。
// 覆盖：正常下单、收货校验失败、车已变化、库存不足、事务回滚、订单详情归属。
// 不覆盖：支付与取消（M06 / M05-05）。
@AutoConfigureMockMvc // 不占端口，直接请求控制器。
class PlaceOrderTest extends AbstractIntegrationTest {

    private static final String NAME = "张三"; // 合法收货人。
    private static final String PHONE = "13800000000"; // 合法电话（含数字）。
    private static final String ADDRESS = "某市某区某路 1 号"; // 合法地址。

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    @MockitoSpyBean // 真实 CartService 外包一层 Spy，只在需要时改行为。
    private CartService cartService; // 用于 AC-16 的异常注入。

    /** 正常下单：1 主表 + 2 明细、库存扣减、版本 +1、车清空、跳到详情。 */
    @Test
    @DisplayName("正常下单：订单、明细、库存、车清空都正确")
    void placeOrderWritesOrderItemsDeductsStockAndClearsCart() throws Exception {
        long userId = CartFixture.createUser(jdbc, "po_ok"); // 建账号。
        long book = CartFixture.createProduct(jdbc, "PO-C01", "28.00", 10, "ON_SALE"); // 单行本 ¥28、库存 10。
        long badge = CartFixture.createProduct(jdbc, "PO-M01", "30.00", 10, "ON_SALE"); // 周边 ¥30、库存 10。
        CartFixture.addCartItem(jdbc, userId, book, 2); // 2 件。
        CartFixture.addCartItem(jdbc, userId, badge, 1); // 1 件。
        MockHttpSession session = confirm(userId); // 打开确认页，生成快照。

        MvcResult result = place(session, NAME, PHONE, ADDRESS) // 提交订单。
                .andExpect(status().is3xxRedirection()) // PRG：重定向。
                .andExpect(redirectedUrlPattern("/orders/CO*")) // 跳订单详情。
                .andReturn(); // 取跳转地址。

        String orderNo = result.getResponse().getRedirectedUrl().substring("/orders/".length()); // 订单号。
        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isEqualTo(1); // 一张订单。
        assertThat(count("SELECT COUNT(*) FROM sales_order_item i JOIN sales_order o ON o.id = i.order_id "
                + "WHERE o.user_id = ?", userId)).isEqualTo(2); // 两条明细。
        assertThat(value("SELECT status FROM sales_order WHERE order_no = ?", String.class, orderNo))
                .isEqualTo("PENDING_PAYMENT"); // 初始待支付。
        assertThat(decimal("SELECT total_amount FROM sales_order WHERE order_no = ?", orderNo))
                .isEqualByComparingTo("86.00"); // 56.00 + 30.00。
        assertThat(count("SELECT stock FROM product WHERE id = ?", book)).isEqualTo(8); // 库存 -2。
        assertThat(count("SELECT stock FROM product WHERE id = ?", badge)).isEqualTo(9); // 库存 -1。
        assertThat(count("SELECT version FROM product WHERE id = ?", book)).isEqualTo(1); // 版本 +1。
        assertThat(CartFixture.countItems(jdbc, userId)).isZero(); // 本次条目已删。
        assertThat(session.getAttribute(CheckoutController.SNAPSHOT_KEY)).isNull(); // 快照作废。

        mockMvc.perform(get("/orders/{no}", orderNo).session(CartFixture.session(userId))) // 打开详情。
                .andExpect(status().isOk()) // 正常渲染。
                .andExpect(content().string(containsString("商品PO-C01"))) // 明细里有第一件。
                .andExpect(content().string(containsString("¥86.00"))) // 总额。
                .andExpect(content().string(containsString(NAME))); // 收货人快照。
    }

    /** 收货人缺失：400，不产生订单，车保留。 */
    @Test
    @DisplayName("收货人缺失：400，不产生订单")
    void placeOrderRejectsMissingReceiver() throws Exception {
        long userId = CartFixture.createUser(jdbc, "po_noreceiver"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PO-C02", "28.00", 10, "ON_SALE"); // 建商品。
        CartFixture.addCartItem(jdbc, userId, productId, 1); // 1 件。
        MockHttpSession session = confirm(userId); // 生成快照。

        mockMvc.perform(post("/checkout").session(session) // 不传收货人。
                        .param("receiverName", " ") // 全空白也算缺失。
                        .param("receiverPhone", PHONE)
                        .param("receiverAddress", ADDRESS))
                .andExpect(status().isBadRequest()); // 400。

        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isZero(); // 没有订单。
        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1); // 车还在。
    }

    /** 确认后车被改：409，不产生订单、不扣库存。 */
    @Test
    @DisplayName("确认后车被改：409，不产生订单")
    void placeOrderRejectsChangedCart() throws Exception {
        long userId = CartFixture.createUser(jdbc, "po_changed"); // 建账号。
        long first = CartFixture.createProduct(jdbc, "PO-C03", "28.00", 10, "ON_SALE"); // 第一件。
        long second = CartFixture.createProduct(jdbc, "PO-C04", "30.00", 10, "ON_SALE"); // 第二件。
        CartFixture.addCartItem(jdbc, userId, first, 1); // 确认前 1 件。
        MockHttpSession session = confirm(userId); // 生成快照。
        CartFixture.addCartItem(jdbc, userId, second, 1); // 确认后又加一件。

        place(session, NAME, PHONE, ADDRESS) // 用旧快照提交。
                .andExpect(status().isConflict()); // 409：车已变化。

        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isZero(); // 没有订单。
        assertThat(count("SELECT stock FROM product WHERE id = ?", first)).isEqualTo(10); // 库存未动。
        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(2); // 车保留。
    }

    /** 确认后库存被买走：409，不产生订单、不部分扣库存。 */
    @Test
    @DisplayName("确认后库存不足：409，无订单、无部分扣库存")
    void placeOrderRejectsStockShortageAfterConfirm() throws Exception {
        long userId = CartFixture.createUser(jdbc, "po_short"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PO-C05", "28.00", 10, "ON_SALE"); // 库存 10。
        CartFixture.addCartItem(jdbc, userId, productId, 2); // 确认时够。
        MockHttpSession session = confirm(userId); // 生成快照。
        jdbc.update("UPDATE product SET stock = 1 WHERE id = ?", productId); // 确认后库存掉到 1。

        place(session, NAME, PHONE, ADDRESS) // 提交。
                .andExpect(status().isConflict()); // 409：库存不足。

        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isZero(); // 没有订单。
        assertThat(count("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(1); // 库存没再扣。
        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1); // 车保留。
    }

    /** 删结算条目时抛异常：订单、明细、库存、车全部回滚。 */
    @Test
    @DisplayName("删车时异常：订单、明细、库存、车全部回滚")
    void placeOrderRollsBackWhenCartRemovalFails() throws Exception {
        long userId = CartFixture.createUser(jdbc, "po_rollback"); // 建账号。
        long productId = CartFixture.createProduct(jdbc, "PO-C06", "28.00", 10, "ON_SALE"); // 库存 10。
        CartFixture.addCartItem(jdbc, userId, productId, 2); // 2 件。
        MockHttpSession session = confirm(userId); // 生成快照。
        doThrow(new RuntimeException("boom")).when(cartService) // 在事务最后一步注入失败。
                .removeCheckedItems(anyLong(), anyList());

        // MockMvc 会把未处理的技术异常原样抛出（不会变成 500 响应），这里断言异常确实冒出来。
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> place(session, NAME, PHONE, ADDRESS))
                .hasMessageContaining("boom"); // 异常来自刚注入的那一步。

        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isZero(); // 主表回滚。
        assertThat(count("SELECT COUNT(*) FROM sales_order_item i JOIN sales_order o ON o.id = i.order_id "
                + "WHERE o.user_id = ?", userId)).isZero(); // 明细回滚。
        assertThat(count("SELECT stock FROM product WHERE id = ?", productId)).isEqualTo(10); // 库存回滚。
        assertThat(count("SELECT version FROM product WHERE id = ?", productId)).isZero(); // 版本回滚。
        assertThat(CartFixture.countItems(jdbc, userId)).isEqualTo(1); // 车保留。
    }

    /** 订单详情：他人订单与不存在都 404。 */
    @Test
    @DisplayName("订单详情：他人订单与不存在都 404")
    void orderDetailIsOwnershipChecked() throws Exception {
        long owner = CartFixture.createUser(jdbc, "po_owner"); // 下单人。
        long other = CartFixture.createUser(jdbc, "po_other"); // 另一个账号。
        long productId = CartFixture.createProduct(jdbc, "PO-C07", "28.00", 10, "ON_SALE"); // 建商品。
        CartFixture.addCartItem(jdbc, owner, productId, 1); // 1 件。
        MockHttpSession session = confirm(owner); // 生成快照。
        MvcResult result = place(session, NAME, PHONE, ADDRESS).andReturn(); // 下单。
        String orderNo = result.getResponse().getRedirectedUrl().substring("/orders/".length()); // 订单号。

        mockMvc.perform(get("/orders/{no}", orderNo).session(CartFixture.session(other))) // 他人访问。
                .andExpect(status().isNotFound()); // 统一 404。
        mockMvc.perform(get("/orders/{no}", "CO00000000000000").session(CartFixture.session(owner))) // 不存在。
                .andExpect(status().isNotFound()); // 同样 404。
    }

    // ---------------------------------------------------------------- 夹具

    /** 打开确认页，返回带快照的会话。 */
    private MockHttpSession confirm(long userId) throws Exception {
        MockHttpSession session = CartFixture.session(userId); // 带登录身份的会话。
        mockMvc.perform(get("/checkout").session(session)) // 生成快照。
                .andExpect(status().isOk()); // 正常渲染。
        return session; // 会话里已有 checkoutSnapshot。
    }

    /** 用给定会话与收货信息提交订单。 */
    private org.springframework.test.web.servlet.ResultActions place(MockHttpSession session,
                                                                     String name, String phone, String address)
            throws Exception {
        return mockMvc.perform(post("/checkout").session(session) // POST 提交。
                .param("receiverName", name) // 收货人。
                .param("receiverPhone", phone) // 电话。
                .param("receiverAddress", address)); // 地址。
    }

    /** 整数/计数查询。 */
    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args); // 单值查询。
        return value == null ? 0 : value; // 空结果按 0。
    }

    /** 任意类型单值查询。 */
    private <T> T value(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, args); // 交给 JdbcTemplate。
    }

    /** 金额查询。 */
    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args); // 金额用 BigDecimal。
    }
}
