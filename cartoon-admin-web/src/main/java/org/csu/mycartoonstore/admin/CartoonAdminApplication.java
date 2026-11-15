package org.csu.mycartoonstore.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理端应用入口（端口 8081，context-path=/admin）。
 * <p>与用户端共享业务模块，但会话与 Cookie 完全独立。</p>
 */
@SpringBootApplication(scanBasePackages = "org.csu.mycartoonstore")
@MapperScan({
        "org.csu.mycartoonstore.account.mapper",
        "org.csu.mycartoonstore.catalog.mapper",
        "org.csu.mycartoonstore.cart.mapper",
        "org.csu.mycartoonstore.order.mapper"
})
public class CartoonAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartoonAdminApplication.class, args);
    }
}
