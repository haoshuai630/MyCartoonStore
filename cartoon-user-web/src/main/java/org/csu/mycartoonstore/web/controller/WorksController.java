package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.catalog.dto.ProductSearchCommand;
import org.csu.mycartoonstore.catalog.service.CatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 作品墙：所有作品（一级分类）的入口页。
 *
 * <p>作品多了以后，导航只需要指向这一页，不必把每部作品都塞进侧栏。</p>
 */
@Controller
public class WorksController {

    private final CatalogService catalogService;

    /** 构造器：注入查询服务，作品墙与作品页共用。 */
    public WorksController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * 作品页：作品横幅 + 品类筛选 + 商品列表。
     *
     * <p>路径里的 id 是唯一可信的作品来源，查询参数里的 workId 一律覆盖，
     * 避免有人用 ?workId= 越权看别家作品。</p>
     */
    @GetMapping("/works/{id}")
    public String detail(@PathVariable long id, ProductSearchCommand command, Model model) {
        model.addAttribute("work", catalogService.work(id));  // 不存在或层级不对由 Service 抛 404，控制器不重复判断。
        command.setWorkId(String.valueOf(id));  // 用路径 id 覆盖参数里的 workId，防止 ?workId=2 越权看别家作品。
        model.addAttribute("page", catalogService.search(command));
        model.addAttribute("query", command);
        return "work";  // 视图名对应 templates/work.html。
    }

    /** 作品墙：每部作品一张卡，带品类数与在售数 */
    @GetMapping("/works")
    public String list(Model model) {
        model.addAttribute("works", catalogService.works());
        return "works";
    }
}
