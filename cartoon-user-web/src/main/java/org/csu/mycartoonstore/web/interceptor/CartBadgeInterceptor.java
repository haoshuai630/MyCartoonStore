package org.csu.mycartoonstore.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.csu.mycartoonstore.cart.service.CartService;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 页头徽标：每个渲染出来的页面都带上 {@code cartCount}。
 *
 * <p>徽标出现在所有页面的公共片段里，逐页在 Controller 里塞一遍必然会漏；
 * 这里统一在渲染前查一次本人购物车的数量之和（含失效条目），游客固定 0。</p>
 *
 * <p>只对渲染成模板的请求生效：重定向不需要徽标，跳过可以少一次查询。</p>
 */
public class CartBadgeInterceptor implements HandlerInterceptor {

    /** 模板里使用的属性名 */
    public static final String MODEL_KEY = "cartCount";

    private final CartService cartService;

    public CartBadgeInterceptor(CartService cartService) {
        this.cartService = cartService;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null) {
            return;
        }
        String viewName = modelAndView.getViewName();
        if (viewName != null && viewName.startsWith("redirect:")) {
            return;
        }
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(LoginInterceptor.USER_ID);
        int count = userId instanceof Long id ? cartService.badgeQuantity(id) : 0;
        modelAndView.addObject(MODEL_KEY, count);
    }
}
