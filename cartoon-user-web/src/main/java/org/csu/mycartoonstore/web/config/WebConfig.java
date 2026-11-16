package org.csu.mycartoonstore.web.config;

import org.csu.mycartoonstore.cart.service.CartService;
import org.csu.mycartoonstore.web.interceptor.CartBadgeInterceptor;
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册登录拦截器与购物车徽标拦截器。
 *
 * <p>受保护路径集中写在这里，新增模块时只改这一处；
 * 公开页面（首页、作品、列表、详情、登录、注册）不受影响。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CartService cartService;

    public WebConfig(CartService cartService) {
        this.cartService = cartService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/cart", "/cart/**", "/orders", "/orders/**", "/profile", "/profile/**");
        // 徽标：所有页面都要显示，所以不限定路径
        registry.addInterceptor(new CartBadgeInterceptor(cartService));
    }
}
