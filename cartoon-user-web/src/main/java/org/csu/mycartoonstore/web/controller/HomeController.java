package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.catalog.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器。
 *
 * <p>只负责取数与挂模型，页面逻辑留在模板；数量上限（3 / 4）在这里写死，
 * 与 01 篇 FR-G-01 的口径一致，避免模板里散落魔法数字。</p>
 */
@Controller
public class HomeController {

    private static final int NEWEST_LIMIT = 3;
    private static final int FEATURED_LIMIT = 4;

    private final CatalogService catalogService;

    public HomeController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 根路径与 /main 共用首页视图，装配作品入口与两个商品栏目 */
    @GetMapping({"/", "/main"})
    public String main(Model model) {
        model.addAttribute("categories", catalogService.topCategories());
        model.addAttribute("newest", catalogService.newest(NEWEST_LIMIT));
        model.addAttribute("featured", catalogService.featured(FEATURED_LIMIT));
        return "main";
    }
}