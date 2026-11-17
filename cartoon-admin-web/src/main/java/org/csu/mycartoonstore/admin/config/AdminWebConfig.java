package org.csu.mycartoonstore.admin.config; // 管理端 Web 配置：注册身份拦截器。

import org.csu.mycartoonstore.account.service.AccountService; // 拦截器要按 id 重查账号。
import org.csu.mycartoonstore.admin.interceptor.AdminAuthInterceptor; // 只放行启用 ADMIN。
import org.springframework.context.annotation.Configuration; // 标记配置类。
import org.springframework.web.servlet.config.annotation.InterceptorRegistry; // 拦截器注册表。
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // MVC 扩展点。

// M07-02：管理端第一次引入拦截器。
// 与用户端不同，这里采用"全站拦截 + 白名单"：后台页面只会增加、不会减少，
// 新加一个管理页忘了登记就等于默认受保护，比逐个列出更安全。
@Configuration // 配置类：不写业务逻辑。
public class AdminWebConfig implements WebMvcConfigurer {

    private final AccountService accountService; // 拦截器需要它按 id 读账号。

    public AdminWebConfig(AccountService accountService) { // 构造器注入。
        this.accountService = accountService; // 保存账号服务。
    }

    @Override // 覆盖 MVC 扩展点：注册拦截器。
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(accountService)) // 每个后台请求都重查身份。
                .addPathPatterns("/**") // 默认全部受保护。
                .excludePathPatterns("/login", "/logout", "/health", // 登录与探活不需要登录态。
                        "/css/**", "/images/**", "/js/**", "/favicon.ico", "/error"); // 静态资源与错误页放行。
    }
}
