package org.csu.mycartoonstore.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 登录拦截器：受保护路径在未登录时跳登录页，并记住回来的地址。
 *
 * <p>只拦明确列出的路径，不做"全站拦截 + 白名单"：
 * 后者每新增一个页面都要记得放行，漏一次就把公开页面锁住了。</p>
 *
 * <p>回跳地址分两种情况：GET 用被拦的原地址；POST 不能直接 GET（会 405），
 * 改用来源页（Referer）的路径——用户在详情页点「加入购物车」被拦，登录后回详情页再点一次。</p>
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
        String encoded = URLEncoder.encode(returnTarget(request), StandardCharsets.UTF_8);
        response.sendRedirect(request.getContextPath() + "/login?redirect=" + encoded);
        return false;
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
