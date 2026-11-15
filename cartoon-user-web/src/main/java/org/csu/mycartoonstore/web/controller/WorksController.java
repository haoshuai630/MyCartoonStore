package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.catalog.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 作品墙：所有作品（一级分类）的入口页。
 *
 * <p>作品多了以后，导航只需要指向这一页，不必把每部作品都塞进侧栏。</p>
 */
@Controller
public class WorksController {

    private final CatalogService catalogService;

    public WorksController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 作品墙：每部作品一张卡，带品类数与在售数 */
    @GetMapping("/works")
    public String list(Model model) {
        model.addAttribute("works", catalogService.works());
        return "works";
    }
}