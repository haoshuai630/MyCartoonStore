package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录 / 登出测试：会话写入、失败提示、禁用账号、回跳地址。
 */
@AutoConfigureMockMvc
class LoginPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("打开登录页返回表单")
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("登录")));
    }

    @Test
    @DisplayName("登录成功：会话里有 userId，跳首页")
    void loginSucceeds() throws Exception {
        seedUser("alice", "Cartoon123!", "ENABLED");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "Cartoon123!")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/main"));

        assertThat(session.getAttribute("userId")).isNotNull();
        assertThat(session.getAttribute("displayName")).isEqualTo("爱丽丝");
    }

    @Test
    @DisplayName("密码错误：留在登录页并提示")
    void loginWrongPassword() throws Exception {
        seedUser("alice", "Cartoon123!", "ENABLED");

        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "bad-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用")));
    }

    @Test
    @DisplayName("禁用账号：与密码错误同文案，不透露账号状态")
    void loginDisabled() throws Exception {
        seedUser("carol", "Cartoon123!", "DISABLED");

        mockMvc.perform(post("/login")
                        .param("username", "carol")
                        .param("password", "Cartoon123!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用")));
    }

    @Test
    @DisplayName("管理员不能登录用户端：同文案，且不写会话（AC-05）")
    void adminCannotLogIn() throws Exception {
        seedUser("admin", "Cartoon123!", "ENABLED", "ADMIN");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "Cartoon123!")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用")));

        assertThat(session.getAttribute("userId")).isNull();
    }

    @Test
    @DisplayName("登录后回到 redirect 指定的站内页面")
    void loginHonoursRedirect() throws Exception {
        seedUser("alice", "Cartoon123!", "ENABLED");

        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "Cartoon123!")
                        .param("redirect", "/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
    }

    @Test
    @DisplayName("站外地址不被接受，回首页")
    void loginRejectsExternalRedirect() throws Exception {
        seedUser("alice", "Cartoon123!", "ENABLED");

        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "Cartoon123!")
                        .param("redirect", "//evil.example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/main"));
    }

    @Test
    @DisplayName("登出后会话被清空")
    void logoutClearsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/main"));

        assertThat(session.isInvalid()).isTrue();
    }

    /** 造一个用户，密码用真实 BCrypt 编码；角色缺省为普通顾客 */
    private void seedUser(String username, String rawPassword, String status) {
        seedUser(username, rawPassword, status, "CUSTOMER");
    }

    /** 造一个指定角色的用户：管理端/用户端互斥的用例要用 */
    private void seedUser(String username, String rawPassword, String status, String role) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) VALUES (?,?,?,?,?)",
                username, passwordEncoder.encode(rawPassword), "爱丽丝", role, status);
    }
}