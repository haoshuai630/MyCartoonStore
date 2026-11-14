package org.csu.mycartoonstore.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探活接口：验证管理端骨架与 context-path 是否正确（实际地址 /admin/health）。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "mycartoonstore-admin ok";
    }
}
