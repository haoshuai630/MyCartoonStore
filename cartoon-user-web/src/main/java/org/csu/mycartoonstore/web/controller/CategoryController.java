package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.catalog.dto.ProductSearchCommand;
import org.csu.mycartoonstore.catalog.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 商品列表页。
 *
 * <p>筛选条件全部通过查询参数绑定到 ProductSearchCommand，由 CatalogService 归一与校验；
 * 控制器不解析参数，也不做分页计算。</p>
 */
@Controller
public class CategoryController {

    private final CatalogService catalogService;

    public CategoryController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 列表页：分类、类型、排序、关键字、页码都走同一个入口 */
    @GetMapping("/category")
    public String list(ProductSearchCommand command, Model model) {
        model.addAttribute("page", catalogService.search(command));
        model.addAttribute("categories", catalogService.allCategories());
        model.addAttribute("query", command);
        return "category";
    }
}