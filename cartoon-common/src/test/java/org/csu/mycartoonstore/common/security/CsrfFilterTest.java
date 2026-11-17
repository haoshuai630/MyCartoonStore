package org.csu.mycartoonstore.common.security; // 过滤器行为：读请求发令牌、写请求校验、静态资源放行。

import jakarta.servlet.ServletException; // 过滤器方法签名声明了它。
import org.junit.jupiter.api.DisplayName; // 用例名用中文说明意图。
import org.junit.jupiter.api.Test; // JUnit 5 用例。
import org.springframework.mock.web.MockFilterChain; // 记录请求有没有走到下游。
import org.springframework.mock.web.MockHttpServletRequest; // 不启 Spring 上下文也能造请求。
import org.springframework.mock.web.MockHttpServletResponse; // 断言 403 与是否提交响应。

import java.io.IOException; // 过滤器方法签名声明了它。

import static org.assertj.core.api.Assertions.assertThat; // 断言库。

/**
 * 不启动 Spring 上下文，直接驱动过滤器：这里只验证"放行 / 拦截"的分支，
 * 令牌怎么渲染到页面由两端接入任务的用例覆盖。
 */
class CsrfFilterTest {

    /** 被测对象：每个用例共用一份，过滤器本身无状态。 */
    private final CsrfFilter filter = new CsrfFilter();

    @Test
    @DisplayName("GET 页面：签发令牌并放行")
    void getIssuesToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login"); // 登录页是第一个会用到令牌的页面。
        MockHttpServletResponse response = new MockHttpServletResponse(); // 记录状态码。
        MockFilterChain chain = new MockFilterChain(); // 记录是否继续走链路。

        filter.doFilter(request, response, chain); // 只跑过滤器。

        assertThat(chain.getRequest()).isNotNull(); // 放行：下游收到了请求。
        assertThat(request.getSession(false)).isNotNull(); // 读页面建了会话。
        assertThat(request.getSession(false).getAttribute(CsrfToken.SESSION_KEY)).isNotNull(); // 会话里有令牌。
        assertThat(response.getStatus()).isEqualTo(200); // 没有错误状态。
    }

    @Test
    @DisplayName("同一会话多次 GET：令牌保持不变")
    void tokenIsStableWithinSession() throws ServletException, IOException {
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/login"); // 第一次访问。
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain()); // 发令牌。
        Object token = first.getSession(false).getAttribute(CsrfToken.SESSION_KEY); // 记住它。

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/cart"); // 同一浏览器的下一次请求。
        second.setSession((jakarta.servlet.http.HttpSession) first.getSession(false)); // 复用同一个会话。
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain()); // 再过一次过滤器。

        assertThat(second.getSession(false).getAttribute(CsrfToken.SESSION_KEY)).isEqualTo(token); // 会话内不换令牌。
    }

    @Test
    @DisplayName("静态资源：放行且不建会话")
    void staticResourceSkipsSession() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/store.css"); // 样式表。
        MockFilterChain chain = new MockFilterChain(); // 记录放行。

        filter.doFilter(request, new MockHttpServletResponse(), chain); // 静态请求不应参与令牌流程。

        assertThat(chain.getRequest()).isNotNull(); // 放行。
        assertThat(request.getSession(false)).isNull(); // 关键：没有为静态资源创建会话。
    }

    @Test
    @DisplayName("带 context-path 的静态资源同样放行")
    void staticResourceWithContextPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/admin.css"); // 站内路径。
        request.setContextPath("/admin"); // 模拟管理端。
        MockFilterChain chain = new MockFilterChain(); // 记录放行。

        filter.doFilter(request, new MockHttpServletResponse(), chain); // 路径要先截掉 /admin 再判断。

        assertThat(chain.getRequest()).isNotNull(); // 放行。
        assertThat(request.getSession(false)).isNull(); // 仍然不建会话。
    }

    @Test
    @DisplayName("POST 没有会话：403 且不进 Controller")
    void postWithoutSessionForbidden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login"); // 伪造的同源外提交。
        MockHttpServletResponse response = new MockHttpServletResponse(); // 记录状态码。
        MockFilterChain chain = new MockFilterChain(); // 用来确认没放行。

        filter.doFilter(request, response, chain); // 没有会话就没有令牌。

        assertThat(response.getStatus()).isEqualTo(403); // 拒。
        assertThat(chain.getRequest()).isNull(); // 没有走到下游。
    }

    @Test
    @DisplayName("POST 有会话但没带令牌：403")
    void postWithoutParamForbidden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/cart/items"); // 加车。
        request.getSession(true).setAttribute(CsrfToken.SESSION_KEY, "token-a"); // 会话里有令牌。
        MockHttpServletResponse response = new MockHttpServletResponse(); // 记录状态码。
        MockFilterChain chain = new MockFilterChain(); // 用来确认没放行。

        filter.doFilter(request, response, chain); // 表单没带隐藏域。

        assertThat(response.getStatus()).isEqualTo(403); // 拒。
        assertThat(chain.getRequest()).isNull(); // 没有走到下游。
    }

    @Test
    @DisplayName("POST 令牌不匹配：403")
    void postWithWrongTokenForbidden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/cart/items"); // 加车。
        request.getSession(true).setAttribute(CsrfToken.SESSION_KEY, "token-a"); // 会话里的令牌。
        request.addParameter(CsrfToken.PARAM_NAME, "token-b"); // 另一个会话的令牌（或猜的）。
        MockHttpServletResponse response = new MockHttpServletResponse(); // 记录状态码。

        filter.doFilter(request, response, new MockFilterChain()); // 比对失败。

        assertThat(response.getStatus()).isEqualTo(403); // 拒。
    }

    @Test
    @DisplayName("POST 令牌一致：放行")
    void postWithCorrectTokenPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/cart/items"); // 加车。
        request.getSession(true).setAttribute(CsrfToken.SESSION_KEY, "token-a"); // 页面拿到的令牌。
        request.addParameter(CsrfToken.PARAM_NAME, "token-a"); // 原样回传。
        MockFilterChain chain = new MockFilterChain(); // 记录放行。

        filter.doFilter(request, new MockHttpServletResponse(), chain); // 校验通过。

        assertThat(chain.getRequest()).isNotNull(); // 放行。
    }

    @Test
    @DisplayName("小写 post 按写请求校验")
    void lowercaseMethodIsChecked() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("post", "/cart/items"); // 畸形方法名。
        MockHttpServletResponse response = new MockHttpServletResponse(); // 记录状态码。

        filter.doFilter(request, response, new MockFilterChain()); // 不能因为大小写绕过。

        assertThat(response.getStatus()).isEqualTo(403); // 仍要校验。
    }

    @Test
    @DisplayName("PUT 与 DELETE 也要校验令牌")
    void otherUnsafeMethodsAreChecked() throws ServletException, IOException {
        MockHttpServletRequest put = new MockHttpServletRequest("PUT", "/profile"); // 将来可能的接口。
        MockHttpServletResponse putResponse = new MockHttpServletResponse(); // 记录状态码。
        filter.doFilter(put, putResponse, new MockFilterChain()); // 无令牌。

        MockHttpServletRequest delete = new MockHttpServletRequest("DELETE", "/cart/items/1"); // 同上。
        MockHttpServletResponse deleteResponse = new MockHttpServletResponse(); // 记录状态码。
        filter.doFilter(delete, deleteResponse, new MockFilterChain()); // 无令牌。

        assertThat(putResponse.getStatus()).isEqualTo(403); // PUT 被拒。
        assertThat(deleteResponse.getStatus()).isEqualTo(403); // DELETE 被拒。
    }
}
