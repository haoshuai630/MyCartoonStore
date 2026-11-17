package org.csu.mycartoonstore.web; // CSRF 页面级用例：页面带令牌、写请求无令牌被拒。

import jakarta.servlet.RequestDispatcher; // 伪造错误分发里的状态码。
import org.csu.mycartoonstore.common.security.CsrfToken; // 断言会话令牌与参数名。
import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 每例重建表。
import org.csu.mycartoonstore.web.support.CartFixture; // 造商品与购物车条目。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入依赖。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.http.MediaType; // 明确要 HTML，错误控制器才不会回 JSON。
import org.springframework.jdbc.core.JdbcTemplate; // 造数据、核对未写库。
import org.springframework.mock.web.MockHttpSession; // 手工会话。
import org.springframework.security.crypto.password.PasswordEncoder; // 造可登录的密码。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取响应与会话。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 响应体断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 跳转断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M09-03：依据 docs/02 §6.3、docs/04 §1、docs/06 AC-31。
// 覆盖：页面隐藏域、同一会话令牌稳定、无令牌/错令牌 403、带令牌原行为、退出也要令牌、游客写请求 403 而非 302。
// 不覆盖：过滤器内部的分支（M09-01 单测）、静态资源的会话行为（同上）。
@AutoConfigureMockMvc // 不占端口，走真实 Spring MVC + 过滤器链。
class CsrfProtectionTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造数据与核对未写库。

    @Autowired // 由 Spring 注入。
    private PasswordEncoder passwordEncoder; // 造真实 BCrypt 密码。

    @Test // 登录页是第一个会用到令牌的页面。
    @DisplayName("登录页渲染 CSRF 隐藏域，值与会话一致")
    void loginPageCarriesToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")) // 游客打开登录页。
                .andExpect(status().isOk()) // 公开页。
                .andExpect(content().string(containsString("name=\"_csrf\""))) // 表单里有隐藏域。
                .andReturn(); // 取回会话与响应体。

        Object token = result.getRequest().getSession(false).getAttribute(CsrfToken.SESSION_KEY); // 过滤器签发的值。
        assertThat(token).isNotNull(); // 读请求必须签发令牌。
        assertThat(result.getResponse().getContentAsString()).contains(token.toString()); // 页面上的值就是会话里的值。
    }

    @Test // 同一浏览器开两个页面：令牌不能变，否则前一个页面失效。
    @DisplayName("同一会话多次访问：令牌保持不变")
    void tokenStableAcrossPages() throws Exception {
        MockHttpSession session = new MockHttpSession(); // 同一个浏览器的会话。

        MvcResult first = mockMvc.perform(get("/login").session(session)).andReturn(); // 第一个页面。
        Object token = session.getAttribute(CsrfToken.SESSION_KEY); // 第一次签发的值。
        MvcResult second = mockMvc.perform(get("/register").session(session)).andReturn(); // 第二个页面。

        assertThat(token).isNotNull(); // 第一次就有值。
        assertThat(second.getRequest().getSession(false).getAttribute(CsrfToken.SESSION_KEY)).isEqualTo(token); // 没被覆盖。
        assertThat(first.getResponse().getContentAsString()).contains(token.toString()); // 第一个页面也带的是它。
    }

    @Test // 外部站点伪造的表单不会带令牌。
    @DisplayName("无令牌的登录 POST：403，且不写库")
    void postWithoutTokenIsForbidden() throws Exception {
        seedUser("csrf_none", "Cartoon123!"); // 账号存在，确认拒绝发生在业务之前。

        mockMvc.perform(post("/login") // 没有会话、没有令牌。
                        .param("username", "csrf_none")
                        .param("password", "Cartoon123!"))
                .andExpect(status().isForbidden()); // 403，而不是 302 或 200。
    }

    @Test // 猜一个值、或者拿别的会话的值来试。
    @DisplayName("错令牌的登录 POST：403")
    void postWithWrongTokenIsForbidden() throws Exception {
        seedUser("csrf_wrong", "Cartoon123!"); // 账号存在。
        MockHttpSession session = new MockHttpSession(); // 先拿到合法令牌的会话。
        mockMvc.perform(get("/login").session(session)).andReturn(); // 滤镜签发令牌。

        mockMvc.perform(post("/login") // 带着会话，但参数是伪造的。
                        .with(request -> {
                            request.addParameter(CsrfToken.PARAM_NAME, "forged-token"); // 故意写错。
                            return request; // 不加 .with(csrf())，避免被覆盖。
                        })
                        .session(session)
                        .param("username", "csrf_wrong")
                        .param("password", "Cartoon123!"))
                .andExpect(status().isForbidden()); // 拒。
    }

    @Test // 正常路径：令牌正确时行为与 M03 验收时一致。
    @DisplayName("带令牌的登录 POST：照旧 302 /main")
    void postWithTokenPasses() throws Exception {
        seedUser("csrf_ok", "Cartoon123!"); // 启用的顾客。

        mockMvc.perform(post("/login") // 正常表单。
                        .with(org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf())
                        .with(org.csu.mycartoonstore.web.support.CaptchaFixture.captcha()) // M10-04：登录要验证码。
                        .param("username", "csrf_ok")
                        .param("password", "Cartoon123!"))
                .andExpect(status().is3xxRedirection()) // 登录成功。
                .andExpect(redirectedUrl("/main")); // 默认回首页。
    }

    @Test // 退出也是一次写操作，不能让跨站链接把用户登出。
    @DisplayName("无令牌的退出 POST：403，会话仍有效")
    void logoutWithoutTokenIsForbidden() throws Exception {
        MockHttpSession session = CartFixture.session(1L); // 已登录会话。

        mockMvc.perform(post("/logout").session(session)) // 没有令牌。
                .andExpect(status().isForbidden()); // 拒。

        assertThat(session.getAttribute("userId")).isEqualTo(1L); // 会话没被销毁。
    }

    @Test // 未登录 + 无令牌：先 403，不是 302 登录页（docs/04 §1 口径）。
    @DisplayName("游客无令牌写受保护路径：403 而非 302")
    void guestPostIsForbiddenNotRedirect() throws Exception {
        mockMvc.perform(post("/cart/items") // 游客直接提交加车。
                        .param("productId", "1")
                        .param("quantity", "1"))
                .andExpect(status().isForbidden()); // 来源不可信，先拒；302 才算失败。
    }

    @Test // MockMvc 不做容器错误分发，这里直接让错误控制器渲染 403 视图。
    @DisplayName("403 页面：提示刷新后重试")
    void forbiddenPageRenders() throws Exception {
        mockMvc.perform(get("/error") // Spring Boot 的错误控制器。
                        .accept(MediaType.TEXT_HTML) // 浏览器要的是页面而不是 JSON。
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)) // 伪造错误分发里的状态码。
                .andExpect(content().string(containsString("请求未被接受"))) // 页面标题。
                .andExpect(content().string(containsString("刷新后重试"))); // 引导语。
    }

    @Test // 每个带写表单的页面都要有隐藏域：这里抽两个高频页。
    @DisplayName("商品页与购物车页的写表单都带隐藏域")
    void formPagesCarryToken() throws Exception {
        long userId = CartFixture.createUser(jdbc, "csrf_forms"); // 需要一个已登录用户看购物车。
        long productId = CartFixture.createProduct(jdbc, "CSRF-C01", "28.00", 10, "ON_SALE"); // 上架商品。
        CartFixture.addCartItem(jdbc, userId, productId, 1); // 车里放一件，购物车页才渲染改量表单。

        mockMvc.perform(get("/product/" + productId)) // 详情页的「加入购物车」表单。
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/cart").session(CartFixture.session(userId))) // 购物车页的改量与移除表单。
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    /** 造一个可用密码的启用顾客：登录用例要用真实散列。 */
    private void seedUser(String username, String rawPassword) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) VALUES (?,?,?,?,?)",
                username, passwordEncoder.encode(rawPassword), "测试用户", "CUSTOMER", "ENABLED"); // 与生产一致的角色与状态。
    }
}
