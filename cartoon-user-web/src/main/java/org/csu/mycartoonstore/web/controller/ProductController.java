package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.catalog.dto.ProductDetail;
import org.csu.mycartoonstore.catalog.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品详情页。
 *
 * <p>商品不存在、已下架都在 Service 里转 404，由 GlobalExceptionHandler 输出错误页；
 * 控制器只负责把详情挂到模型上。</p>
 */
@Controller
public class ProductController {

    private final CatalogService catalogService;

    public ProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 按 id 查看商品详情 */
    @GetMapping("/product/{id}")
    public String detail(@PathVariable long id, Model model) {
        ProductDetail detail = catalogService.detail(id);
        model.addAttribute("detail", detail);
        return "product";
    }
}