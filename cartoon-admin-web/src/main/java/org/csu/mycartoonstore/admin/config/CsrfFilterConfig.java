package org.csu.mycartoonstore.admin.config; // 注册 CSRF 过滤器：后台全站生效。

import org.csu.mycartoonstore.common.security.CsrfFilter; // 校验逻辑与用户端共用同一份。
import org.springframework.boot.web.servlet.FilterRegistrationBean; // 声明路径与顺序。
import org.springframework.context.annotation.Bean; // 暴露给容器。
import org.springframework.context.annotation.Configuration; // 标记配置类。
import org.springframework.core.Ordered; // 用优先级数值表达"最先执行"。

// M09-02：依据 docs/02 §6.3、docs/04 §1。
// 负责：把 CsrfFilter 挂到 /admin 下的全部路径，排在身份拦截器之前。
// 不做：令牌签发与比对（CsrfFilter）、页面隐藏域（admin/csrf.html）、身份校验（AdminAuthInterceptor）。
@Configuration // 配置类：不写业务逻辑。
public class CsrfFilterConfig {

    /** 过滤器顺序：HIGHEST_PRECEDENCE 留给字符编码，这里紧随其后。 */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10; // 数字越小越先执行。

    /** 注册 CSRF 过滤器：路径以应用内路径书写，容器会自动拼上 /admin。 */
    @Bean // 交给 Spring Boot 装配到内嵌容器。
    public FilterRegistrationBean<CsrfFilter> csrfFilter() {
        FilterRegistrationBean<CsrfFilter> bean = new FilterRegistrationBean<>(new CsrfFilter()); // 过滤器无状态。
        bean.addUrlPatterns("/*"); // 后台所有入口：登录、分类、商品、订单、顾客、相册。
        bean.setName("csrfFilter"); // 日志里能直接认出是哪个过滤器。
        bean.setOrder(ORDER); // 先校验来源，再判断身份。
        return bean; // 注册完成。
    }
}
