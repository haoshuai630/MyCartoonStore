package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.csu.mycartoonstore.web.support.CartFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 改密页测试：旧密码校验、强度规则、两次一致、成功后销毁当前会话。
 *
 * <p>密码用真实的 BCrypt 编码写库，避免用假散列测出"永远不匹配"的假绿。</p>
 */
@AutoConfigureMockMvc
class PasswordPageTest extends AbstractIntegrationTest {

    private static final String OLD_PASSWORD = "Cartoon123!"; // 与种子账号一致，便于人工复现。

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    @DisplayName("未登录打开改密页：302 到登录页并记住回跳地址")
    void passwordRequiresLogin() throws Exception {
        mockMvc.perform(get("/password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fpassword*"));
    }

    @Test
    @DisplayName("改密页渲染三个密码框与规则说明")
    void passwordRenders() throws Exception {
        long userId = user("alice");

        mockMvc.perform(get("/password").session(CartFixture.session(userId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("修改密码")))
                .andExpect(content().string(containsString("oldPassword")))
                .andExpect(content().string(containsString("confirmPassword")));
    }

    @Test
    @DisplayName("旧密码不正确：回表单提示，散列不变")
    void wrongOldPasswordRejected() throws Exception {
        long userId = user("alice");
        String before = hashOf(userId);

        mockMvc.perform(post("/password").session(CartFixture.session(userId))
                        .param("oldPassword", "wrong-old")
                        .param("newPassword", "NewPass123")
                        .param("confirmPassword", "NewPass123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("原密码不正确")));

        assertThat(hashOf(userId)).isEqualTo(before);
    }

    @Test
    @DisplayName("新密码太弱（纯字母）：400 提示两类字符")
    void weakPasswordRejected() throws Exception {
        long userId = user("alice");

        mockMvc.perform(post("/password").session(CartFixture.session(userId))
                        .param("oldPassword", OLD_PASSWORD)
                        .param("newPassword", "abcdefgh")
                        .param("confirmPassword", "abcdefgh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("两类")));
    }

    @Test
    @DisplayName("两次输入不一致：回表单提示，散列不变")
    void mismatchRejected() throws Exception {
        long userId = user("alice");
        String before = hashOf(userId);

        mockMvc.perform(post("/password").session(CartFixture.session(userId))
                        .param("oldPassword", OLD_PASSWORD)
                        .param("newPassword", "NewPass123")
                        .param("confirmPassword", "NewPass456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("两次输入的新密码不一致")));

        assertThat(hashOf(userId)).isEqualTo(before);
    }

    @Test
    @DisplayName("改密成功：跳登录页、当前会话失效、库里是新散列")
    void changeSucceeds() throws Exception {
        long userId = user("alice");
        MockHttpSession session = CartFixture.session(userId);

        MvcResult result = mockMvc.perform(post("/password").session(session)
                        .param("oldPassword", OLD_PASSWORD)
                        .param("newPassword", "NewPass123")
                        .param("confirmPassword", "NewPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("message", "密码已修改，请重新登录"))
                .andReturn();

        assertThat(session.isInvalid()).isTrue(); // 当前会话已销毁。
        HttpSession fresh = result.getRequest().getSession(false); // 只为 flash 新建的会话。
        assertThat(fresh).isNotNull();
        assertThat(fresh.getAttribute("userId")).isNull(); // 新会话里没有身份，等于已退出。
        assertThat(encoder.matches("NewPass123", hashOf(userId))).isTrue();
        assertThat(encoder.matches(OLD_PASSWORD, hashOf(userId))).isFalse();
    }

    @Test
    @DisplayName("其他浏览器的会话继续有效：只销毁当前这一份")
    void otherSessionStaysLoggedIn() throws Exception {
        long userId = user("alice");
        MockHttpSession current = CartFixture.session(userId);
        MockHttpSession other = CartFixture.session(userId);

        mockMvc.perform(post("/password").session(current)
                        .param("oldPassword", OLD_PASSWORD)
                        .param("newPassword", "NewPass123")
                        .param("confirmPassword", "NewPass123"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/cart").session(other))
                .andExpect(status().isOk());
    }

    /** 建一个密码可用的顾客：散列用真实 BCrypt，旧密码才能匹配。 */
    private long user(String username) {
        long userId = CartFixture.createUser(jdbc, username);
        jdbc.update("UPDATE store_user SET password_hash = ? WHERE id = ?",
                encoder.encode(OLD_PASSWORD), userId);
        return userId;
    }

    /** 现查散列，断言"没被改"或"改成了新密码"。 */
    private String hashOf(long userId) {
        return jdbc.queryForObject("SELECT password_hash FROM store_user WHERE id = ?", String.class, userId);
    }
}
