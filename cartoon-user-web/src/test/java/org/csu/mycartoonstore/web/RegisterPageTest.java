package org.csu.mycartoonstore.web;

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册页测试：成功入库、密码是散列、重复与不一致的提示。
 */
@AutoConfigureMockMvc
class RegisterPageTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("打开注册页返回表单")
    void registerPageRenders() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("注册")))
                .andExpect(content().string(containsString("confirmPassword")));
    }

    @Test
    @DisplayName("注册成功：跳登录页，库里是 BCrypt 散列")
    void registerSucceeds() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "newbie")
                        .param("password", "Password123")
                        .param("confirmPassword", "Password123")
                        .param("displayName", "新用户"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE username = 'newbie'", String.class);
        assertThat(hash).startsWith("$2");
        assertThat(hash).isNotEqualTo("Password123");
    }

    @Test
    @DisplayName("用户名重复：留在注册页并提示")
    void registerDuplicate() throws Exception {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES ('alice', '$2a$10$0123456789012345678901234567890123456789012345678901', '爱丽丝', 'CUSTOMER', 'ENABLED')");

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "alice")
                        .param("password", "Password123")
                        .param("confirmPassword", "Password123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("用户名已被占用")));
    }

    @Test
    @DisplayName("两次密码不一致：不写入数据库")
    void registerPasswordMismatch() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "mismatch")
                        .param("password", "Password123")
                        .param("confirmPassword", "Password456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("两次输入的密码不一致")));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE username = 'mismatch'", Integer.class);
        assertThat(count).isZero();
    }
}