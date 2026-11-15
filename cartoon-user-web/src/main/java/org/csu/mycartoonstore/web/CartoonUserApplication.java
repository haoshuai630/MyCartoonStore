package org.csu.mycartoonstore.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户端应用入口（端口 8080）。
 * <p>扫描整个 {@code org.csu.mycartoonstore} 包，以装配各业务模块的 Service；
 * Mapper 接口分散在四个业务模块里，显式列出，避免通配包扫描失效。</p>
 */
@SpringBootApplication(scanBasePackages = "org.csu.mycartoonstore")
@MapperScan({
        "org.csu.mycartoonstore.account.mapper",
        "org.csu.mycartoonstore.catalog.mapper",
        "org.csu.mycartoonstore.cart.mapper",
        "org.csu.mycartoonstore.order.mapper"
})
public class CartoonUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartoonUserApplication.class, args);
    }
}
