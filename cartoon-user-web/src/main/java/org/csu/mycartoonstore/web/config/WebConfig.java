package org.csu.mycartoonstore.web.config; // Web 层配置：注册登录拦截器与购物车徽标拦截器。

import org.csu.mycartoonstore.account.service.AccountService; // 登录拦截器要按 id 重查账号角色与状态。
import org.csu.mycartoonstore.cart.service.CartService; // 徽标拦截器要用它查件数。
import org.csu.mycartoonstore.web.interceptor.CartBadgeInterceptor; // 给所有页面注入 cartCount。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 未登录跳登录并记住回跳地址。
import org.springframework.context.annotation.Configuration; // 标记配置类，被组件扫描发现。
import org.springframework.web.servlet.config.annotation.InterceptorRegistry; // 拦截器注册表。
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // MVC 扩展点。

// M02-01 建立，M03-04 接入登录拦截，M04-02 接入徽标，M05-02 新增 /checkout，M06-02 新增 /payment。
// M07-01：登录拦截器改为每请求重读账号，需要注入 AccountService。
// 只做"注册拦截器"这一件事：路径集中写在这里，新增模块时只改这一处。
@Configuration // 配置类：不写业务逻辑。
public class WebConfig implements WebMvcConfigurer {

    private final CartService cartService; // 徽标拦截器需要按用户查件数。

    private final AccountService accountService; // 登录拦截器需要按 id 读账号。

    public WebConfig(CartService cartService, AccountService accountService) { // 构造器注入。
        this.cartService = cartService; // 保存购物车服务，交给徽标拦截器。
        this.accountService = accountService; // 保存账号服务，交给登录拦截器。
    }

    @Override // 覆盖 MVC 扩展点：注册拦截器。
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(accountService)) // 每次请求重查账号，禁用即时生效。
                .addPathPatterns("/cart", "/cart/**", "/checkout", "/checkout/**",
                        "/orders", "/orders/**", "/payment", "/payment/**",
                        "/profile", "/profile/**"); // 受保护路径集中列在这里；M06-02 新增 /payment。
        // 徽标：所有页面都要显示，所以不限定路径。
        registry.addInterceptor(new CartBadgeInterceptor(cartService));
    }
}
