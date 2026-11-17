package org.csu.mycartoonstore.admin; // 后台顾客资料修改与密码重置（M10-02）。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号与会话。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入依赖。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据与核对。
import org.springframework.security.crypto.password.PasswordEncoder; // 校验重置后的散列。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取一次性提示。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造 POST。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash; // 一次性属性断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 跳转断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M10-02：依据指导书实验内容二「用户信息查看、修改、密码重置」。
// 覆盖：改三列落库、显示名空 400 提示、管理员 404、重置后旧密码失效且临时密码可登录。
// 不覆盖：列表与启停（M08-04 已验收）。
@AutoConfigureMockMvc // 不占端口。
class AdminUserProfileTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对。

    @Autowired // 由 Spring 注入。
    private PasswordEncoder passwordEncoder; // 校验散列。

    @Test // 改三项资料：落库并回列表。
    @DisplayName("改资料：显示名、邮箱、电话三列落库")
    void updateProfileWritesColumns() throws Exception {
        long customerId = AdminFixture.createCustomer(jdbc, "pf_ok"); // 一位顾客。

        mockMvc.perform(post("/users/{id}/profile", customerId).with(csrf())
                        .param("displayName", "新名字")
                        .param("email", "new@example.com")
                        .param("phone", "13900000000")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attributeExists("message")); // 成功提示。

        assertThat(jdbc.queryForObject("SELECT display_name FROM store_user WHERE id = ?", String.class, customerId))
                .isEqualTo("新名字"); // 显示名。
        assertThat(jdbc.queryForObject("SELECT email FROM store_user WHERE id = ?", String.class, customerId))
                .isEqualTo("new@example.com"); // 邮箱。
        assertThat(jdbc.queryForObject("SELECT phone FROM store_user WHERE id = ?", String.class, customerId))
                .isEqualTo("13900000000"); // 电话。
    }

    @Test // 显示名为空：回列表带红字，不写库。
    @DisplayName("改资料：显示名为空 → 提示且不落库")
    void updateProfileBlankName() throws Exception {
        long customerId = AdminFixture.createCustomer(jdbc, "pf_blank"); // 一位顾客。

        mockMvc.perform(post("/users/{id}/profile", customerId).with(csrf())
                        .param("displayName", "   ")
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // 回列表。
                .andExpect(flash().attributeExists("error")); // 红字提示。

        assertThat(jdbc.queryForObject("SELECT display_name FROM store_user WHERE id = ?", String.class, customerId))
                .isEqualTo("测试账号"); // 未被改写。
    }

    @Test // 目标是管理员：404，不能借后台改管理员资料。
    @DisplayName("改资料：目标是管理员 → 404")
    void updateProfileAdminRejected() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "pf_admin_target"); // 另一个管理员。

        mockMvc.perform(post("/users/{id}/profile", adminId).with(csrf())
                        .param("displayName", "被篡改")
                        .session(adminSession()))
                .andExpect(status().isNotFound()); // 404。
    }

    @Test // 重置：旧密码失效、临时密码可校验通过，明文只在 flash 里出现一次。
    @DisplayName("重置密码：旧密码失效，临时密码可用且只显示一次")
    void resetPasswordReplacesHash() throws Exception {
        long customerId = AdminFixture.createCustomer(jdbc, "pf_reset"); // 一位顾客。

        MvcResult result = mockMvc.perform(post("/users/{id}/password-reset", customerId).with(csrf())
                        .session(adminSession()))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attributeExists("tempPassword")) // 临时密码只在这一次。
                .andReturn();

        String temp = (String) result.getFlashMap().get("tempPassword"); // 取一次性明文。
        String hash = jdbc.queryForObject("SELECT password_hash FROM store_user WHERE id = ?",
                String.class, customerId); // 库里的散列。
        assertThat(temp).hasSize(12); // 12 位。
        assertThat(hash).isNotEqualTo("$2a$10$test"); // 旧散列被替换。
        assertThat(passwordEncoder.matches(temp, hash)).isTrue(); // 临时密码可校验通过。
    }

    /** 管理员会话。 */
    private org.springframework.mock.web.MockHttpSession adminSession() {
        long adminId = AdminFixture.createAdmin(jdbc, "pf_admin"); // 每例新建。
        return AdminFixture.session(adminId); // 已登录态。
    }
}
