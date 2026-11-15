package org.csu.mycartoonstore.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器：受保护路径在未登录时跳登录页，并记住原地址。
 *
 * <p>只拦明确列出的路径，不做"全站拦截 + 白名单"：
 * 后者每新增一个页面都要记得放行，漏一次就把公开页面锁住了。</p>
 */
public class LoginInterceptor implements HandlerInterceptor {

    /** 会话里表示登录态的键，与 AuthController 写入的一致 */
    public static final String USER_ID = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(USER_ID) != null) {
            return true;
        }
        // 把原地址（含查询串）编码后带过去，登录成功再跳回来
        String target = request.getRequestURI();
        if (request.getQueryString() != null) {
            target = target + "?" + request.getQueryString();
        }
        String encoded = URLEncoder.encode(target, StandardCharsets.UTF_8);
        response.sendRedirect(request.getContextPath() + "/login?redirect=" + encoded);
        return false;
    }
}