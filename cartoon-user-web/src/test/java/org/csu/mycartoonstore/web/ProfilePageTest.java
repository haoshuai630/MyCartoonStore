package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.csu.mycartoonstore.web.support.CartFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人资料页测试：未登录拦截、回显、保存、字段校验与历史订单快照不变。
 *
 * <p>只改本人的三列：用户名、角色、状态即使出现在表单里也不会被写库（FR-U-03）。</p>
 */
@AutoConfigureMockMvc
class ProfilePageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("未登录打开资料页：302 到登录页并记住回跳地址")
    void profileRequiresLogin() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fprofile*"));
    }

    @Test
    @DisplayName("资料页回显用户名、显示名与只读提示")
    void profileRenders() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        jdbc.update("UPDATE store_user SET display_name = '爱丽丝', email = 'alice@example.com' WHERE id = ?",
                userId);

        mockMvc.perform(get("/profile").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("个人资料")))
                .andExpect(content().string(containsString("alice")))
                .andExpect(content().string(containsString("爱丽丝")))
                .andExpect(content().string(containsString("alice@example.com")));
    }

    @Test
    @DisplayName("保存成功：跳回本页、库与页头显示名同时更新")
    void profileSaves() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        MockHttpSession session = CartFixture.session(userId);

        mockMvc.perform(post("/profile").session(session)
                        .param("displayName", "  新名字  ")
                        .param("email", "new@example.com")
                        .param("phone", "13800000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        assertThat(jdbc.queryForObject("SELECT display_name FROM store_user WHERE id = ?", String.class, userId))
                .isEqualTo("新名字");
        assertThat(jdbc.queryForObject("SELECT email FROM store_user WHERE id = ?", String.class, userId))
                .isEqualTo("new@example.com");
        assertThat(session.getAttribute("displayName")).isEqualTo("新名字");
    }

    @Test
    @DisplayName("邮箱留空：存 null，页面不回显占位文本")
    void profileClearsOptionalFields() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        jdbc.update("UPDATE store_user SET email = 'old@example.com' WHERE id = ?", userId);

        mockMvc.perform(post("/profile").session(CartFixture.session(userId))
                        .param("displayName", "爱丽丝")
                        .param("email", "   ")
                        .param("phone", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject("SELECT email FROM store_user WHERE id = ?", String.class, userId)).isNull();
        assertThat(jdbc.queryForObject("SELECT phone FROM store_user WHERE id = ?", String.class, userId)).isNull();
    }

    @Test
    @DisplayName("显示名空白：回表单提示，不写库")
    void profileRejectsBlankDisplayName() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");

        mockMvc.perform(post("/profile").session(CartFixture.session(userId))
                        .param("displayName", "   ")
                        .param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("显示名不能为空")));

        assertThat(jdbc.queryForObject("SELECT display_name FROM store_user WHERE id = ?", String.class, userId))
                .isEqualTo("测试用户");
    }

    @Test
    @DisplayName("邮箱超长：回表单提示上限")
    void profileRejectsTooLongEmail() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");

        mockMvc.perform(post("/profile").session(CartFixture.session(userId))
                        .param("displayName", "爱丽丝")
                        .param("email", "a".repeat(95) + "@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("邮箱不能超过 100")));
    }

    @Test
    @DisplayName("提交 username/role/status 也不改：只接受三项资料")
    void profileIgnoresProtectedFields() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");

        mockMvc.perform(post("/profile").session(CartFixture.session(userId))
                        .param("displayName", "爱丽丝")
                        .param("username", "hacker")
                        .param("role", "ADMIN")
                        .param("status", "DISABLED"))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject("SELECT username FROM store_user WHERE id = ?", String.class, userId))
                .isEqualTo("alice");
        assertThat(jdbc.queryForObject("SELECT role FROM store_user WHERE id = ?", String.class, userId))
                .isEqualTo("CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT status FROM store_user WHERE id = ?", String.class, userId))
                .isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("改资料不影响历史订单的收货快照")
    void profileKeepsOrderSnapshot() throws Exception {
        long userId = CartFixture.createUser(jdbc, "alice");
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address) VALUES (?,?,?,?,?,?,?)",
                "CO20261118000001", userId, "PENDING_PAYMENT", new BigDecimal("28.00"),
                "旧收货人", "13900000000", "旧地址");

        mockMvc.perform(post("/profile").session(CartFixture.session(userId))
                        .param("displayName", "新名字")
                        .param("phone", "13700000000"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/orders/CO20261118000001").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("旧收货人")))
                .andExpect(content().string(containsString("旧地址")));
        assertThat(jdbc.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE order_no = 'CO20261118000001'", String.class))
                .isEqualTo("旧收货人");
    }
}
