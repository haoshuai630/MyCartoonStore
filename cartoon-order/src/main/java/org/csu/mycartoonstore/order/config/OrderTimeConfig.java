package org.csu.mycartoonstore.order.config; // 订单模块的时间源：统一提供一个时钟，便于测试固定时间。

import org.springframework.context.annotation.Bean; // 声明 Spring Bean。
import org.springframework.context.annotation.Configuration; // 标记配置类，被组件扫描发现。

import java.time.Clock; // 注入给业务服务，避免每处直接调 Instant.now()。

// M05-01：结算确认需要"确认时刻"与"10 分钟时限"，集中到一个可替换的 Clock。
// 生产用系统时钟；测试直接 new CheckoutService(cartService, Clock.fixed(...))，不依赖这里。
@Configuration // 配置类：只声明 Bean，不写业务。
public class OrderTimeConfig {

    /** 系统默认时区的时钟：订单时间统一取自这里。 */
    @Bean // 交给 Spring 管理，CheckoutService 构造器自动注入。
    public Clock clock() {
        return Clock.systemDefaultZone(); // 与数据库 DATETIME、页面时区保持一致。
    }
}
