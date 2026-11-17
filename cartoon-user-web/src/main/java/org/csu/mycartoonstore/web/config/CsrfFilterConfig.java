package org.csu.mycartoonstore.web.config; // 注册 CSRF 过滤器：全站生效并排在过滤器链最前。

import org.csu.mycartoonstore.common.security.CsrfFilter; // 校验逻辑在公共件里，web 层只负责挂载。
import org.springframework.boot.web.servlet.FilterRegistrationBean; // 声明路径与顺序。
import org.springframework.context.annotation.Bean; // 暴露给容器。
import org.springframework.context.annotation.Configuration; // 标记配置类。
import org.springframework.core.Ordered; // 用优先级数值表达"最先执行"。

// M09-02：依据 docs/02 §6.3、docs/04 §1。
// 负责：把 CsrfFilter 挂到全站路径，并排在字符编码过滤器之后、登录拦截器之前。
// 不做：令牌签发与比对（CsrfFilter）、页面隐藏域（fragments/csrf.html）、身份校验（LoginInterceptor）。
@Configuration // 配置类：不写业务逻辑。
public class CsrfFilterConfig {

    /** 过滤器顺序：HIGHEST_PRECEDENCE 留给字符编码，这里紧随其后。 */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10; // 数字越小越先执行。

    /** 注册 CSRF 过滤器：静态资源由过滤器内部放行，这里仍然挂全站。 */
    @Bean // 交给 Spring Boot 装配到内嵌容器。
    public FilterRegistrationBean<CsrfFilter> csrfFilter() {
        FilterRegistrationBean<CsrfFilter> bean = new FilterRegistrationBean<>(new CsrfFilter()); // 过滤器无状态，每次启动 new 一个。
        bean.addUrlPatterns("/*"); // 全站：登录、注册、购物车、订单、支付都要校验。
        bean.setName("csrfFilter"); // 日志里能直接认出是哪个过滤器。
        bean.setOrder(ORDER); // 固定顺序，避免与拦截器、编码过滤器的先后关系被改动。
        return bean; // 注册完成。
    }
}
