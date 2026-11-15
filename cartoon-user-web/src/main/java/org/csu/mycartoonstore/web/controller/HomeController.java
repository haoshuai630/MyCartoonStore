package org.csu.mycartoonstore.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器。
 *
 * <p>M02-02 只负责把布局跑通；M02-03 在这里注入作品入口与两个商品栏目。</p>
 */
@Controller
public class HomeController {

    /** 根路径与 /main 共用首页视图；M02-03 在这里装配作品入口与商品栏目。 */
    @GetMapping({"/", "/main"})
    public String main() {
        return "main";
    }
}
