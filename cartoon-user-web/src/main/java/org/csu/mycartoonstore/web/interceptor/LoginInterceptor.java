package org.csu.mycartoonstore.web.interceptor; // 登录拦截：未登录跳登录页；已登录也每请求重查账号。

import jakarta.servlet.http.HttpServletRequest; // 取会话、方法与来源页。
import jakarta.servlet.http.HttpServletResponse; // 发 302。
import jakarta.servlet.http.HttpSession; // 登录态存在会话里。
import org.csu.mycartoonstore.account.entity.StoreUser; // 重查账号角色与状态。
import org.csu.mycartoonstore.account.service.AccountService; // 按 id 读库，禁用即时生效。
import org.springframework.web.servlet.HandlerInterceptor; // 拦截器接口。

import java.net.URI; // 解析 Referer。
import java.net.URLEncoder; // 回跳地址要编码。
import java.nio.charset.StandardCharsets; // 编码用 UTF-8。
import java.util.Objects; // 比较主机名。

// 只拦明确列出的路径，不做"全站拦截 + 白名单"：
// 后者每新增一个页面都要记得放行，漏一次就把公开页面锁住了。
// 除会话里有没有 userId 之外，还要按 id 重读 store_user：
// 账号被禁用或角色被改后，手里的旧会话必须立刻失效（FR-U-02、AC-07）。
public class LoginInterceptor implements HandlerInterceptor {

    /** 会话里表示登录态的键，与 AuthController 写入的一致 */
    public static final String USER_ID = "userId";

    /** 用户端只承认普通顾客；管理员会话不能在这里通过 */
    private static final String ROLE_CUSTOMER = "CUSTOMER"; // 与 AccountService 的角色口径一致。

    /** 停用账号即使会话还在也不再放行 */
    private static final String STATUS_ENABLED = "ENABLED"; // 与数据库 status 列取值一致。

    private final AccountService accountService; // 每个受保护请求重读一次账号。

    public LoginInterceptor(AccountService accountService) { // 由 WebConfig 注入。
        this.accountService = accountService; // 保存账号服务：只用到 findById。
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false); // 不新建会话：游客访问受保护页不该产生 Session。
        Object userId = session == null ? null : session.getAttribute(USER_ID); // 会话里的身份。
        if (userId instanceof Long id && stillAllowed(id)) { // 身份存在且账号仍启用、仍是顾客。
            return true; // 放行。
        }
        if (session != null) {
            session.invalidate(); // 账号已停用/被删/换成管理角色：作废这份旧会话。
        }
        String encoded = URLEncoder.encode(returnTarget(request), StandardCharsets.UTF_8); // 记住要回来的站内地址。
        response.sendRedirect(request.getContextPath() + "/login?redirect=" + encoded); // 302 到登录页。
        return false; // 中断本次请求。
    }

    /** 账号是否仍可访问用户端受保护资源：存在、是 CUSTOMER、状态 ENABLED。 */
    private boolean stillAllowed(long userId) {
        StoreUser user = accountService.findById(userId); // 每次请求都读库，禁用即时生效。
        return user != null // 账号被删也按未登录处理。
                && ROLE_CUSTOMER.equals(user.getRole()) // 管理员不能借用户端会话购物。
                && STATUS_ENABLED.equals(user.getStatus()); // 禁用账号立刻失去登录态。
    }

    /** 登录后要回去的站内地址：GET 用原地址，POST 用来源页 */
    private String returnTarget(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String fromReferer = sameSitePath(request);
            if (fromReferer != null) {
                return fromReferer;
            }
        }
        String target = request.getRequestURI();
        if (request.getQueryString() != null) {
            target = target + "?" + request.getQueryString();
        }
        return target;
    }

    /**
     * 从 Referer 取站内路径；只接受与当前请求同源（协议、主机、端口一致）的来源。
     * <p>不校验同源就会被外部页面构造成"登录后跳任意站"，所以宁可回首页也不接受陌生来源。</p>
     */
    private String sameSitePath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(referer);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }
        if (!uri.getScheme().equalsIgnoreCase(request.getScheme())
                || !Objects.equals(uri.getHost(), request.getServerName())) {
            return null;
        }
        int port = uri.getPort() == -1
                ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
                : uri.getPort();
        if (port != request.getServerPort()) {
            return null;
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            return "/main";
        }
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }
}
