package org.csu.mycartoonstore.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探活接口：仅用于验证工程骨架能否启动与路由，M02 起会被真实页面替代。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "mycartoonstore-user ok";
    }
}
