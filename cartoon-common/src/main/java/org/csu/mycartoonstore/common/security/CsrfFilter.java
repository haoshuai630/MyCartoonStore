package org.csu.mycartoonstore.common.security; // 每请求一次：读请求签发令牌，写请求校验令牌。

import jakarta.servlet.FilterChain; // 校验通过后要继续走后面的过滤器与 DispatcherServlet。
import jakarta.servlet.ServletException; // 下游抛出的检查异常原样透传。
import jakarta.servlet.http.HttpServletRequest; // 读方法、路径、参数与会话。
import jakarta.servlet.http.HttpServletResponse; // 校验失败时直接回 403，不进 Controller。
import jakarta.servlet.http.HttpSession; // 令牌存在会话里，不写 Cookie。
import org.springframework.web.filter.OncePerRequestFilter; // 保证一次请求只跑一遍，错误分发不重复校验。

import java.io.IOException; // 过滤器方法签名要求。
import java.util.List; // 静态资源前缀清单。
import java.util.Locale; // 方法名统一按大写比较，避免大小写差异绕过判断。
import java.util.Set; // 安全方法与固定静态文件集合。

// M09-01：依据 docs/02 §6.3、docs/04 §1、docs/06 AC-31。
// 负责：读请求确保会话里有令牌并放行；写请求比对 `_csrf`，不一致直接 403 并中断链路。
// 不做：身份与归属判断（拦截器 / Service）、错误页渲染（容器 + templates/error/403.html）、
//       静态资源鉴权（CSS 与图片直接放行，不为它们建会话）。
public class CsrfFilter extends OncePerRequestFilter {

    /** 不改变状态的方法：浏览器预取与普通链接都可能不带令牌，不能因此 403。 */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    /** 静态资源目录：直接放行，避免每个 CSS / 图片请求都建一个会话。 */
    private static final List<String> STATIC_PREFIXES = List.of("/css/", "/images/", "/js/", "/webjars/");

    /** 根目录下的固定静态文件：只列确实存在的，不做通配。 */
    private static final Set<String> STATIC_FILES = Set.of("/favicon.ico");

    @Override // 覆盖一次性过滤器的扩展点。
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathWithinApplication(request); // 去掉 context-path，两端共用同一套判断。
        if (isStatic(path)) { // 静态资源不参与令牌流程。
            chain.doFilter(request, response); // 原样放行。
            return; // 不建会话、不校验。
        }
        if (SAFE_METHODS.contains(methodOf(request))) { // 读请求只负责发令牌。
            ensureToken(request); // 会话里没有就签发，页面渲染才有值可填。
            chain.doFilter(request, response); // 继续走 MVC。
            return; // 读请求到此结束。
        }
        HttpSession session = request.getSession(false); // 写请求不新建会话：没有会话就不可能持有令牌。
        String expected = session == null ? null : (String) session.getAttribute(CsrfToken.SESSION_KEY); // 会话里的令牌。
        String actual = request.getParameter(CsrfToken.PARAM_NAME); // 表单隐藏域带来的令牌。
        if (!CsrfToken.matches(expected, actual)) { // 缺失、空值、不相等都拒绝。
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "请求来源校验失败，请刷新后重试"); // 由 error/403.html 呈现。
            return; // 不把请求交给 Controller，业务与数据库都不会被触碰。
        }
        chain.doFilter(request, response); // 令牌一致，正常执行。
    }

    /** 取请求方法并统一大写：Servlet 规范给的是大写，这里再兜一层。 */
    private String methodOf(HttpServletRequest request) {
        String method = request.getMethod(); // 可能是 null（畸形请求）。
        return method == null ? "" : method.toUpperCase(Locale.ROOT); // 空串不在安全方法集合里，按写请求处理。
    }

    /** 应用内路径：MockMvc 下 context-path 为空，Tomcat 下管理端为 /admin。 */
    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI(); // 含 context-path 的原始地址。
        String contextPath = request.getContextPath(); // 管理端是 /admin，用户端是空串。
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) { // 只有带前缀时才截。
            return uri.substring(contextPath.length()); // 得到 /css/... 这样的站内路径。
        }
        return uri; // 用户端原样返回。
    }

    /** 静态资源判断：只按前缀与固定文件名匹配，避免误放行 /api 之类的业务路径。 */
    private boolean isStatic(String path) {
        if (STATIC_FILES.contains(path)) { // /favicon.ico 这类根文件。
            return true; // 直接放行。
        }
        for (String prefix : STATIC_PREFIXES) { // 目录前缀清单很短，线性判断足够。
            if (path.startsWith(prefix)) { // /css/store.css、/images/... 都命中。
                return true; // 命中即放行。
            }
        }
        return false; // 其余路径都要走令牌流程。
    }

    /** 确保会话里有令牌：已存在就不覆盖，否则同一会话里开着两个页面会互相失效。 */
    private void ensureToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true); // 读页面可以建会话：登录页本身就要发令牌。
        if (session.getAttribute(CsrfToken.SESSION_KEY) == null) { // 首次访问或会话被重建之后。
            session.setAttribute(CsrfToken.SESSION_KEY, CsrfToken.generate()); // 一个会话只签发一次。
        }
    }
}
