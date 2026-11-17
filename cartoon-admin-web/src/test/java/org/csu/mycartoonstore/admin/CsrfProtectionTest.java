package org.csu.mycartoonstore.admin; // 管理端 CSRF 页面级用例：后台写表单同样要令牌。

import jakarta.servlet.RequestDispatcher; // 伪造错误分发里的状态码。
import org.csu.mycartoonstore.admin.support.AdminFixture; // 造管理员与商品。
import org.csu.mycartoonstore.admin.support.AdminIntegrationTest; // 真实 MySQL + 每例重建表。
import org.csu.mycartoonstore.common.security.CsrfToken; // 断言会话令牌。
import org.csu.mycartoonstore.testsupport.CsrfTestSupport; // 给正常路径补令牌。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入依赖。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.http.MediaType; // 明确要 HTML，错误控制器才不会回 JSON。
import org.springframework.jdbc.core.JdbcTemplate; // 核对未写库。
import org.springframework.mock.web.MockHttpSession; // 手工会话。
import org.springframework.test.web.servlet.MockMvc; // 请求入口。
import org.springframework.test.web.servlet.MvcResult; // 取响应与会话。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 响应体断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl; // 跳转断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言。

// M09-03：依据 docs/02 §6.3、docs/04 §1/§3、docs/06 AC-31。
// 覆盖：后台登录页与列表页的隐藏域、无令牌 403、带令牌正常新增、退出也要令牌。
// 不覆盖：分类/商品等业务规则（M07 已验收），这里只验"来源校验是否生效"。
@AutoConfigureMockMvc // 不占端口。
class CsrfProtectionTest extends AdminIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 核对未写库。

    @Test // 后台登录页也要发令牌。
    @DisplayName("后台登录页渲染隐藏域，值与会话一致")
    void loginPageCarriesToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")) // 游客打开后台登录页。
                .andExpect(status().isOk()) // 公开页。
                .andExpect(content().string(containsString("name=\"_csrf\""))) // 表单里有隐藏域。
                .andReturn(); // 取回会话与响应体。

        Object token = result.getRequest().getSession(false).getAttribute(CsrfToken.SESSION_KEY); // 过滤器签发的值。
        assertThat(token).isNotNull(); // 读请求必须签发。
        assertThat(result.getResponse().getContentAsString()).contains(token.toString()); // 页面值与会话值一致。
    }

    @Test // 跨站伪造的"新增分类"不应生效。
    @DisplayName("无令牌新增分类：403，且不写库")
    void createCategoryWithoutTokenIsForbidden() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "csrf_admin"); // 已登录管理员。

        mockMvc.perform(post("/categories") // 没有令牌。
                        .session(AdminFixture.session(adminId))
                        .param("code", "CSRFWORK")
                        .param("name", "伪造分类"))
                .andExpect(status().isForbidden()); // 拒；MockMvc 不做容器错误分发，页面文案见下一个用例。

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE code = 'CSRFWORK'", Integer.class); // 是否写库。
        assertThat(count).isZero(); // 一条都不该有。
    }

    @Test // MockMvc 不做容器错误分发，这里直接让错误控制器渲染 403 视图。
    @DisplayName("后台 403 页面：提示刷新后重试")
    void forbiddenPageRenders() throws Exception {
        mockMvc.perform(get("/error") // Spring Boot 的错误控制器。
                        .accept(MediaType.TEXT_HTML) // 浏览器要的是页面而不是 JSON。
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)) // 伪造错误分发里的状态码。
                .andExpect(content().string(containsString("操作被拒绝"))) // 页面标题。
                .andExpect(content().string(containsString("刷新后重试"))); // 引导语。
    }

    @Test // 正常路径：带令牌的新增照旧成功。
    @DisplayName("带令牌新增分类：照旧 302 回列表并落库")
    void createCategoryWithTokenPasses() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "csrf_admin_ok"); // 已登录管理员。

        mockMvc.perform(post("/categories") // 正常表单。
                        .with(CsrfTestSupport.csrf())
                        .session(AdminFixture.session(adminId))
                        .param("code", "OKWORK")
                        .param("name", "正常分类")
                        .param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection()) // PRG。
                .andExpect(redirectedUrl("/categories")); // 回列表。

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE code = 'OKWORK'", Integer.class); // 是否写库。
        assertThat(count).isEqualTo(1); // 正好一条。
    }

    @Test // 列表页的删除/启停表单也要带令牌。
    @DisplayName("分类列表页的删除表单带隐藏域")
    void listPageCarriesToken() throws Exception {
        long adminId = AdminFixture.createAdmin(jdbc, "csrf_admin_list"); // 已登录管理员。
        AdminFixture.createCategory(jdbc, "LISTWORK", null, "作品", 1); // 一条一级分类，列表才渲染删除表单。

        mockMvc.perform(get("/categories").session(AdminFixture.session(adminId))) // 打开列表。
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\""))); // 删除表单里有隐藏域。
    }

    @Test // 退出同样是一次写操作。
    @DisplayName("无令牌的退出 POST：403，会话仍有效")
    void logoutWithoutTokenIsForbidden() throws Exception {
        MockHttpSession session = AdminFixture.session(1L); // 已登录会话。

        mockMvc.perform(post("/logout").session(session)) // 没有令牌。
                .andExpect(status().isForbidden()); // 拒。

        assertThat(session.getAttribute("userId")).isEqualTo(1L); // 会话没被销毁。
    }
}
