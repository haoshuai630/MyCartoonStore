package org.csu.mycartoonstore.admin.interceptor; // 管理端拦截：只放行启用 ADMIN，其余一律回登录页。

import jakarta.servlet.http.HttpServletRequest; // 取会话与原始地址。
import jakarta.servlet.http.HttpServletResponse; // 发 302。
import jakarta.servlet.http.HttpSession; // 管理端登录态存在会话里。
import org.csu.mycartoonstore.account.entity.StoreUser; // 重查账号角色与状态。
import org.csu.mycartoonstore.account.service.AccountService; // 按 id 读库，停用即时生效。
import org.springframework.web.servlet.HandlerInterceptor; // 拦截器接口。

import java.net.URLEncoder; // 回跳地址要编码。
import java.nio.charset.StandardCharsets; // 编码用 UTF-8。

// M07-02：依据 docs/01 FR-A-01、docs/02 §6.1、docs/04 §3.1。
// 管理端每个受保护请求都按 id 重读 store_user：只认 role=ADMIN 且 status=ENABLED。
// 只做"能不能进后台"这一层判断；分类/商品/订单的业务规则在各 Service。
// 不做 CSRF（M09）、不记录操作审计（本期不做）。
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** 会话里表示管理端登录态的键，与 AdminAuthController 写入的一致 */
    public static final String USER_ID = "userId";

    /** 会话里显示用的管理员名字 */
    public static final String DISPLAY_NAME = "displayName";

    /** 管理端只承认管理员角色 */
    private static final String ROLE_ADMIN = "ADMIN"; // 与 AccountService 的角色口径一致。

    /** 停用账号即使会话还在也不再放行 */
    private static final String STATUS_ENABLED = "ENABLED"; // 与数据库 status 列取值一致。

    private final AccountService accountService; // 每个受保护请求重读一次账号。

    public AdminAuthInterceptor(AccountService accountService) { // 由 AdminWebConfig 注入。
        this.accountService = accountService; // 只用到 findById。
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false); // 不新建会话：游客不该拿到管理端 Session。
        Object userId = session == null ? null : session.getAttribute(USER_ID); // 会话里的身份。
        if (userId instanceof Long id && allowed(id)) { // 身份存在且账号仍是启用管理员。
            return true; // 放行。
        }
        if (session != null) {
            session.invalidate(); // 账号停用/被删/换成顾客：作废这份旧会话。
        }
        String target = URLEncoder.encode(insidePath(request), StandardCharsets.UTF_8); // 记住要回来的后台地址。
        response.sendRedirect(request.getContextPath() + "/login?redirect=" + target); // 302 到管理端登录页。
        return false; // 中断本次请求。
    }

    /** 账号是否仍可进后台：存在、是 ADMIN、状态 ENABLED。 */
    private boolean allowed(long userId) {
        StoreUser user = accountService.findById(userId); // 每次请求都读库，停用即时生效。
        return user != null // 账号被删也按未登录处理。
                && ROLE_ADMIN.equals(user.getRole()) // 顾客不能进后台。
                && STATUS_ENABLED.equals(user.getStatus()); // 停用账号立刻失去登录态。
    }

    /**
     * 取出 context-path 之后的站内路径。
     * <p>重定向目标要交给 Spring 再拼一次 context-path，所以这里必须去掉 /admin 前缀，
     * 否则登录后会变成 /admin/admin/xxx。</p>
     */
    private String insidePath(HttpServletRequest request) {
        String uri = request.getRequestURI(); // 含 context-path，例如 /admin/categories。
        String contextPath = request.getContextPath(); // 本应用固定 /admin。
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri; // 去掉前缀。
        if (path.isBlank()) {
            path = "/"; // 访问 /admin 本身时回控制台。
        }
        return request.getQueryString() == null ? path : path + "?" + request.getQueryString(); // 查询串一并带上。
    }
}
