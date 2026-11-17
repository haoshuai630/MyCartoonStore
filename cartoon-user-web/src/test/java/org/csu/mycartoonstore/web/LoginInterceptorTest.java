package org.csu.mycartoonstore.web; // 登录拦截：未登录跳登录页，登录后禁用或换角色也要立刻失效。

import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 每例重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 造真实账号与会话。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc / JdbcTemplate。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造账号、改状态。
import org.springframework.mock.web.MockHttpSession; // 手工会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern; // 断言 302 目标。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M07-01：依据 docs/01 FR-U-02、docs/06 AC-05/AC-07。
// 覆盖：未登录被拦且带回跳地址、真实账号放行、登录后被禁用被拦、管理员会话被拦、账号不存在被拦、公开页放行。
// 不覆盖：购物车页本身的渲染与数据（CartPageTest）。
@AutoConfigureMockMvc // 不占端口。
class LoginInterceptorTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造账号与改状态。

    @Test // 游客访问购物车：跳登录并带上原地址。
    @DisplayName("未登录访问购物车：跳登录并带上原地址")
    void cartRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection()) // 302。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fcart*")); // 回跳地址编码后带上。
    }

    @Test // 游客访问订单：同样被拦。
    @DisplayName("未登录访问订单：同样被拦")
    void ordersRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().is3xxRedirection()) // 302。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Forders*")); // 回跳地址。
    }

    @Test // 真实启用的顾客：放行。
    @DisplayName("已登录的启用顾客访问购物车：放行")
    void loggedInPassesThrough() throws Exception {
        long userId = CartFixture.createUser(jdbc, "li_alice"); // 库里真的有这个人。
        MockHttpSession session = CartFixture.session(userId); // 会话里只放 id。

        mockMvc.perform(get("/cart").session(session)) // 受保护路径。
                .andExpect(status().isOk()); // 不再跳登录。
    }

    @Test // 登录后被禁用：下一个请求就失效。
    @DisplayName("登录后被禁用：受保护请求被拒（AC-07）")
    void disabledAfterLoginIsRejected() throws Exception {
        long userId = CartFixture.createUser(jdbc, "li_carol"); // 先正常启用。
        MockHttpSession session = CartFixture.session(userId); // 手里有旧的登录会话。
        jdbc.update("UPDATE store_user SET status = 'DISABLED' WHERE id = ?", userId); // 管理员把账号停用。

        mockMvc.perform(get("/orders").session(session)) // 再访问受保护页。
                .andExpect(status().is3xxRedirection()) // 被拦。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Forders*")); // 回登录页。
    }

    @Test // 管理员会话不能当顾客用。
    @DisplayName("管理员会话访问用户端受保护页：被拒（AC-05）")
    void adminSessionIsRejected() throws Exception {
        long adminId = CartFixture.createUser(jdbc, "li_admin", "ADMIN", "ENABLED"); // 真实管理员。
        MockHttpSession session = CartFixture.session(adminId); // 拿着管理身份。

        mockMvc.perform(get("/cart").session(session)) // 想用用户端购物车。
                .andExpect(status().is3xxRedirection()) // 被拦。
                .andExpect(redirectedUrlPattern("/login?redirect=*%2Fcart*")); // 回登录页。
    }

    @Test // 账号被删或会话里的 id 根本不存在：按未登录处理。
    @DisplayName("账号不存在：按未登录处理")
    void unknownUserIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession(); // 手工会话。
        session.setAttribute("userId", 999999L); // 库里没有这个 id。

        mockMvc.perform(get("/orders").session(session)) // 受保护页。
                .andExpect(status().is3xxRedirection()); // 被拦。
    }

    @Test // 公开页永远放行。
    @DisplayName("公开页面不受拦截")
    void publicPagesAreOpen() throws Exception {
        mockMvc.perform(get("/main")).andExpect(status().isOk()); // 首页。
        mockMvc.perform(get("/login")).andExpect(status().isOk()); // 登录页。
    }
}
