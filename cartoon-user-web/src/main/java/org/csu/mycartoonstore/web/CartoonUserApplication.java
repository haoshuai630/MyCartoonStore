package org.csu.mycartoonstore.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户端应用入口（端口 8080）。
 * <p>扫描整个 {@code org.csu.mycartoonstore} 包，以装配各业务模块的 Service/Mapper。</p>
 */
@SpringBootApplication(scanBasePackages = "org.csu.mycartoonstore")
public class CartoonUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartoonUserApplication.class, args);
    }
}
