package org.csu.mycartoonstore.web.config;

import org.csu.mycartoonstore.web.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册登录拦截器并声明受保护路径。
 *
 * <p>受保护路径集中写在这里，新增模块时只改这一处；
 * 公开页面（首页、作品、列表、详情、登录、注册）不受影响。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/cart", "/cart/**", "/orders", "/orders/**", "/profile", "/profile/**");
    }
}