package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.csu.mycartoonstore.web.support.CartFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 确认收货页测试：仅 SHIPPED 可完成、首次时间、幂等、他人订单 404、按钮只在已发货出现。
 */
@AutoConfigureMockMvc
class CompleteOrderTest extends AbstractIntegrationTest {

    /** 支付时间：造已支付订单时必须有，且不能早于创建时间 */
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 16, 9, 0);

    /** 创建时间：必须早于支付时间，否则 ck_order_time_sequence 不通过 */
    private static final LocalDateTime CREATED_AT = PAID_AT.minusHours(1);

    /** 发货时间：确认收货不能覆盖它 */
    private static final LocalDateTime SHIPPED_AT = LocalDateTime.of(2026, 11, 17, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("已发货订单确认收货：跳回详情、状态完成、写首次 completed_at")
    void shippedOrderCanBeCompleted() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        insertOrder(userId, "CO-COMPLETE-1", "SHIPPED");

        mockMvc.perform(post("/orders/CO-COMPLETE-1/complete").with(csrf()).session(CartFixture.session(userId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/CO-COMPLETE-1"))
                .andExpect(flash().attribute("message", "已确认收货，订单完成"));

        assertThat(statusOf("CO-COMPLETE-1")).isEqualTo("COMPLETED");
        assertThat(completedAtOf("CO-COMPLETE-1")).isNotNull();
        assertThat(shippedAtOf("CO-COMPLETE-1")).isEqualTo(Timestamp.valueOf(SHIPPED_AT));
    }

    @Test
    @DisplayName("重复确认收货：提示已处理，完成时间不变")
    void repeatIsIdempotent() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        insertOrder(userId, "CO-COMPLETE-2", "SHIPPED");

        mockMvc.perform(post("/orders/CO-COMPLETE-2/complete").with(csrf()).session(CartFixture.session(userId)))
                .andExpect(status().is3xxRedirection());
        Timestamp first = completedAtOf("CO-COMPLETE-2");

        mockMvc.perform(post("/orders/CO-COMPLETE-2/complete").with(csrf()).session(CartFixture.session(userId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "订单已完成，无需重复操作"));

        assertThat(completedAtOf("CO-COMPLETE-2")).isEqualTo(first);
    }

    @Test
    @DisplayName("待支付订单不能确认收货：409，状态不变")
    void pendingCannotBeCompleted() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        insertOrder(userId, "CO-COMPLETE-3", "PENDING_PAYMENT");

        mockMvc.perform(post("/orders/CO-COMPLETE-3/complete").with(csrf()).session(CartFixture.session(userId)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("当前状态不可确认收货")));

        assertThat(statusOf("CO-COMPLETE-3")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("他人订单：404，且状态不变")
    void otherUsersOrderCannotBeCompleted() throws Exception {
        long owner = CartFixture.createUser(jdbc, "bob");
        long other = CartFixture.createUser(jdbc, "alice");
        insertOrder(owner, "CO-COMPLETE-4", "SHIPPED");

        mockMvc.perform(post("/orders/CO-COMPLETE-4/complete").with(csrf()).session(CartFixture.session(other)))
                .andExpect(status().isNotFound());

        assertThat(statusOf("CO-COMPLETE-4")).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("详情页：已发货有「确认收货」，待发货没有")
    void buttonOnlyOnShippedOrder() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        insertOrder(userId, "CO-COMPLETE-5", "SHIPPED");
        insertOrder(userId, "CO-COMPLETE-6", "PAID");

        mockMvc.perform(get("/orders/CO-COMPLETE-5").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">确认收货</button>"))); // 按钮本体，不是注释。
        mockMvc.perform(get("/orders/CO-COMPLETE-6").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(">确认收货</button>")))); // 待发货没有按钮。
    }

    /** 造订单：待支付只填必填列；已支付/已发货补齐支付三件套，已发货再补 shipped_at。 */
    private void insertOrder(long userId, String orderNo, String status) {
        if ("PENDING_PAYMENT".equals(status)) {
            jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                            + "receiver_phone, receiver_address) VALUES (?,?,?,?,?,?,?)",
                    orderNo, userId, status, new BigDecimal("28.00"), "收货人", "13900000000", "地址");
            return;
        }
        if ("PAID".equals(status)) {
            jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                            + "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    orderNo, userId, status, new BigDecimal("28.00"), "收货人", "13900000000", "地址",
                    Timestamp.valueOf(CREATED_AT), "MOCK", "PAY" + orderNo, Timestamp.valueOf(PAID_AT));
            return;
        }
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at, shipped_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                orderNo, userId, status, new BigDecimal("28.00"), "收货人", "13900000000", "地址",
                Timestamp.valueOf(CREATED_AT), "MOCK", "PAY" + orderNo,
                Timestamp.valueOf(PAID_AT), Timestamp.valueOf(SHIPPED_AT));
    }

    private String statusOf(String orderNo) {
        return jdbc.queryForObject("SELECT status FROM sales_order WHERE order_no = ?", String.class, orderNo);
    }

    private Timestamp completedAtOf(String orderNo) {
        return jdbc.queryForObject("SELECT completed_at FROM sales_order WHERE order_no = ?",
                Timestamp.class, orderNo);
    }

    private Timestamp shippedAtOf(String orderNo) {
        return jdbc.queryForObject("SELECT shipped_at FROM sales_order WHERE order_no = ?",
                Timestamp.class, orderNo);
    }
}
