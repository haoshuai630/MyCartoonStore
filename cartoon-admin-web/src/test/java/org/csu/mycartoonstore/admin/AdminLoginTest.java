package org.csu.mycartoonstore.admin; // 管理端登录、控制台与身份拦截。

import org.csu.mycartoonstore.admin.support.AdminFixture; // 造账号与会话。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc / JdbcTemplate。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造账号、改状态。
import org.springframework.mock.web.MockHttpSession; // 手工会话。
import org.springframework.security.crypto.password.PasswordEncoder; // 真实 BCrypt 散列。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取登录后的会话。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 断言页面内容。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 断言 302 目标。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M07-02：依据 docs/01 FR-A-01、docs/06 AC-05/AC-07、docs/04 §3.1。
// 覆盖：登录页渲染、管理员登录成功、顾客/停用/错密码被拒、控制台需要登录、顾客会话进不来、
//       登录后被停用立即失效、退出清会话、静态资源放行。
// 不覆盖：分类/商品/订单页面（M07-04/06/08）。
@AutoConfigureMockMvc // 不占端口。
class AdminLoginTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造账号、改状态。

    @Autowired // 由 Spring 注入。
    private PasswordEncoder passwordEncoder; // 造真实可登录的密码。

    @Test // 登录页是公开页：不带会话也要能打开。
    @DisplayName("登录页可匿名访问，页面上写明这是管理员入口")
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login")) // 未登录访问后台登录页。
                .andExpect(status().isOk()) // 200：公开页不跳转。
                .andExpect(content().string(containsString("管理员登录"))) // 标题与用户端区分。
                .andExpect(content().string(containsString("只接受管理员账号"))); // 文案说明身份边界。
    }

    @Test // 已登录后再打开登录页：不该重复登录。
    @DisplayName("已登录管理员访问登录页：跳控制台")
    void loggedInAdminGoesToConsole() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "ad_login"); // 库里真实的启用管理员。

        mockMvc.perform(get("/login").session(AdminFixture.session(adminId))) // 带着登录态访问登录页。
                .andExpect(status().is3xxRedirection()) // 302 而不是渲染表单。
                .andExpect(redirectedUrl("/")); // 直接回控制台。
    }

    @Test // 正确的管理员账号：登录成功并写入会话。
    @DisplayName("管理员登录成功：302 控制台，会话写入 id 与显示名")
    void loginSucceeds() throws Exception {
        long adminId = seed("ad_ok", "Cartoon123!", "ADMIN", "ENABLED"); // 密码可用的启用管理员。
        MvcResult result = mockMvc.perform(post("/login").with(csrf()) // 提交登录表单。
                        .param("username", "ad_ok") // 用户名。
                        .param("password", "Cartoon123!")) // 正确密码。
                .andExpect(status().is3xxRedirection()) // POST/Redirect/GET。
                .andExpect(redirectedUrl("/")) // 默认回控制台。
                .andReturn(); // 取回结果，方便检查会话。

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false); // 登录后请求上的会话。
        assertThat(session).isNotNull(); // 一定建了会话。
        assertThat(session.getAttribute("userId")).isEqualTo(adminId); // 会话只存 id。
        assertThat(session.getAttribute("displayName")).isNotNull(); // 页头要显示名。
    }

    @Test // 顾客不能进后台（AC-05）。
    @DisplayName("顾客登录管理端：统一文案，不写登录态（AC-05）")
    void customerCannotLogIn() throws Exception {
        seed("ad_cust", "Cartoon123!", "CUSTOMER", "ENABLED"); // 启用的普通顾客。

        MvcResult result = mockMvc.perform(post("/login").with(csrf()) // 顾客尝试登录后台。
                        .param("username", "ad_cust") // 用顾客账号登录后台。
                        .param("password", "Cartoon123!")) // 密码是对的，只差角色。
                .andExpect(status().isOk()) // 停在登录页，不是 302。
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用"))) // 不透露"角色不符"。
                .andReturn();

        // M09-02 起 CSRF 令牌需要一个会话，所以这里只断言"没有写入登录态"。
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false); // 可能为 null，也可能只有令牌。
        assertThat(session == null || session.getAttribute("userId") == null).isTrue(); // 不留下半登录态。
    }

    @Test // 停用管理员不能登录。
    @DisplayName("停用管理员登录：同文案")
    void disabledAdminCannotLogIn() throws Exception {
        seed("ad_off", "Cartoon123!", "ADMIN", "DISABLED"); // 账号被停用。

        mockMvc.perform(post("/login").with(csrf()) // 停用账号尝试登录。
                        .param("username", "ad_off") // 停用的管理员账号。
                        .param("password", "Cartoon123!")) // 密码是对的，只差状态。
                .andExpect(status().isOk()) // 停在登录页。
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用"))); // 与密码错同文案，不暴露状态。
    }

    @Test // 密码错：不透露账号是否存在。
    @DisplayName("密码错误：同文案")
    void wrongPassword() throws Exception {
        seed("ad_pwd", "Cartoon123!", "ADMIN", "ENABLED"); // 启用的管理员，密码故意输错。

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "ad_pwd") // 存在的管理员。
                        .param("password", "bad-pass")) // 故意输错密码。
                .andExpect(status().isOk()) // 停在登录页。
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用"))); // 同一句文案。
    }

    @Test // 未登录访问后台：跳登录页并记住地址。
    @DisplayName("未登录访问控制台：302 登录页并带回跳地址")
    void consoleRequiresLogin() throws Exception {
        mockMvc.perform(get("/")) // 后台首页，没有会话。
                .andExpect(status().is3xxRedirection()) // 被拦截器拦下。
                .andExpect(redirectedUrl("/login?redirect=%2F")); // 回跳地址编码后带上。
    }

    @Test // 登录后的控制台：显示当前管理员与三个待接入入口。
    @DisplayName("管理员访问控制台：显示身份，无统计数字")
    void consoleRendersForAdmin() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "ad_home"); // 启用的管理员。

        mockMvc.perform(get("/").session(AdminFixture.session(adminId))) // 带登录态。
                .andExpect(status().isOk()) // 200。
                .andExpect(content().string(containsString("控制台"))) // 页面标题。
                .andExpect(content().string(containsString("测试管理员"))) // 当前管理员显示名。
                .andExpect(content().string(containsString("分类维护"))) // 分类入口。
                .andExpect(content().string(containsString("商品维护"))) // 商品入口。
                .andExpect(content().string(containsString("订单处理"))); // 订单入口。
    }

    @Test // 顾客拿到管理端会话也不能用（AC-05）。
    @DisplayName("顾客会话访问控制台：被拒（AC-05）")
    void customerSessionRejected() throws Exception {
        long customerId = AdminFixture.createCustomer(jdbc, "ad_cust_s"); // 顾客账号。
        MockHttpSession session = AdminFixture.session(customerId); // 冒充后台会话。

        mockMvc.perform(get("/").session(session)) // 想进后台。
                .andExpect(status().is3xxRedirection()) // 被拦。
                .andExpect(redirectedUrl("/login?redirect=%2F")); // 回登录页。

        assertThat(session.isInvalid()).isTrue(); // 旧会话被作废，不能继续尝试。
    }

    @Test // 登录后被停用：下一个请求就失效（AC-07）。
    @DisplayName("登录后被停用：受保护请求被拒（AC-07）")
    void disabledAfterLoginRejected() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "ad_later"); // 先启用。
        MockHttpSession session = AdminFixture.session(adminId); // 拿到登录态。
        jdbc.update("UPDATE store_user SET status = 'DISABLED' WHERE id = ?", adminId); // 管理员把账号停用。

        mockMvc.perform(get("/").session(session)) // 再访问后台。
                .andExpect(status().is3xxRedirection()) // 被拦。
                .andExpect(redirectedUrl("/login?redirect=%2F")); // 回登录页。
    }

    @Test // 退出：销毁会话。
    @DisplayName("退出：会话被销毁并回登录页")
    void logoutClearsSession() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "ad_out"); // 启用的管理员。
        MockHttpSession session = AdminFixture.session(adminId); // 登录态。

        mockMvc.perform(post("/logout").with(csrf()).session(session)) // 提交退出。
                .andExpect(status().is3xxRedirection()) // 302。
                .andExpect(redirectedUrl("/login")); // 回登录页。

        assertThat(session.isInvalid()).isTrue(); // 会话作废，旧 ID 不能用。
    }

    @Test // 静态资源不需要登录。
    @DisplayName("样式表可匿名访问")
    void cssIsOpen() throws Exception {
        mockMvc.perform(get("/css/admin.css")) // 后台样式表。
                .andExpect(status().isOk()); // 不被拦截器拦。
    }

    /** 造一个密码可用的账号：密码用真实 BCrypt，角色与状态由用例指定 */
    private long seed(String username, String rawPassword, String role, String status) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) VALUES (?,?,?,?,?)", // 直接写库准备账号。
                username, passwordEncoder.encode(rawPassword), "测试管理员", role, status); // 真实散列 + 用例指定的角色与状态。
        return jdbc.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username); // 返回 id 给会话用。
    }
}
