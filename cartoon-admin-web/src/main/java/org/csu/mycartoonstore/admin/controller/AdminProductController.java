package org.csu.mycartoonstore.admin.controller; // 商品维护页面：列表、新增、编辑。

import jakarta.servlet.http.HttpServletResponse; // 表单校验失败时给出正确状态码。
import org.csu.mycartoonstore.catalog.dto.AdminProductRow; // 列表行。
import org.csu.mycartoonstore.catalog.dto.ProductForm; // 表单对象。
import org.csu.mycartoonstore.catalog.entity.Product; // 编辑页要显示库存与状态。
import org.csu.mycartoonstore.catalog.service.ProductAdminService; // 业务规则都在这里。
import org.csu.mycartoonstore.common.exception.BizException; // 校验失败回表单。
import org.csu.mycartoonstore.common.page.PageResult; // 分页结果。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 传模板。
import org.springframework.web.bind.annotation.GetMapping; // 列表与表单页。
import org.springframework.web.bind.annotation.ModelAttribute; // 表单绑定。
import org.springframework.web.bind.annotation.PathVariable; // 商品 id。
import org.springframework.web.bind.annotation.PostMapping; // 新增与编辑。
import org.springframework.web.bind.annotation.RequestMapping; // 路由前缀。
import org.springframework.web.bind.annotation.RequestParam; // 列表筛选参数。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 一次性提示。

// M07-06：依据 docs/04 §3.3、docs/05 §4.9、docs/06 AC-25/AC-27。
// 只做参数绑定、页面选择与跳转；字段校验、价格解析、版本冲突全在 ProductAdminService。
// 与分类页同一口径：校验失败回表单并保留输入，同时把 HTTP 状态设为 400/409。
@Controller // 返回模板名。
@RequestMapping("/products") // 商品路由前缀。
public class AdminProductController {

    private final ProductAdminService productAdminService; // 业务服务。

    public AdminProductController(ProductAdminService productAdminService) { // 构造器注入。
        this.productAdminService = productAdminService; // 保存服务。
    }

    /** 列表：含下架商品，按分类 / 名称 / 状态筛选并分页。 */
    @GetMapping
    public String list(@RequestParam(required = false) String categoryId, // 一级或二级分类。
                       @RequestParam(required = false) String keyword, // 商品名包含。
                       @RequestParam(required = false) String status, // ALL / DRAFT / ON_SALE。
                       @RequestParam(required = false) String page, // 页码。
                       @RequestParam(required = false) String pageSize, // 页大小。
                       Model model) {
        PageResult<AdminProductRow> result = productAdminService.search(categoryId, keyword, status, page, pageSize); // 归一 + 查询。
        model.addAttribute("page", result); // 当前页数据。
        model.addAttribute("filterOptions", productAdminService.filterOptions()); // 分类下拉。
        model.addAttribute("categoryId", categoryId); // 回显筛选。
        model.addAttribute("keyword", keyword); // 回显关键字。
        model.addAttribute("status", status == null || status.isBlank() ? "ALL" : status); // 回显状态。
        model.addAttribute("active", "products"); // 导航高亮。
        return "admin/product-list"; // 列表模板。
    }

    /** 新增表单：状态固定 DRAFT，可填初始库存。 */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", productAdminService.blankForm()); // 空表单。
        model.addAttribute("options", productAdminService.categoryOptions()); // 二级分类下拉。
        model.addAttribute("editing", false); // 新增模式。
        model.addAttribute("active", "products");
        return "admin/product-form"; // 表单模板。
    }

    /** 新增提交：校验失败回表单并保留输入。 */
    @PostMapping
    public String create(@ModelAttribute ProductForm form, Model model,
                         RedirectAttributes redirect, HttpServletResponse response) {
        try {
            Product created = productAdminService.create(form); // 规则在服务里。
            redirect.addFlashAttribute("message", "已新增商品：「" + created.getName() + "」，当前为下架状态"); // 一次性提示。
            return "redirect:/products"; // PRG。
        } catch (BizException e) { // SKU、价格、分类等失败。
            return backToForm(form, false, e, response, model); // 回表单。
        }
    }

    /** 编辑表单：SKU 与库存只读，版本放隐藏域。 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        ProductForm form = productAdminService.form(id); // 不存在时抛 404。
        Product product = productAdminService.detail(id); // 库存与状态展示用。
        model.addAttribute("form", form); // 当前值。
        model.addAttribute("product", product); // 只读信息。
        model.addAttribute("options", productAdminService.categoryOptions()); // 分类下拉。
        model.addAttribute("editing", true); // 编辑模式。
        model.addAttribute("active", "products");
        return "admin/product-form"; // 复用同一个模板。
    }

    /** 编辑提交：只改资料与价格，版本冲突 → 409。 */
    @PostMapping("/{id}")
    public String update(@PathVariable long id, @ModelAttribute ProductForm form, Model model,
                         RedirectAttributes redirect, HttpServletResponse response) {
        form.setId(id); // 失败回表单时要用它取只读的库存与状态。
        try {
            productAdminService.update(id, form); // 版本条件更新。
            redirect.addFlashAttribute("message", "已保存商品：「" + form.getName().trim() + "」"); // 一次性提示。
            return "redirect:/products"; // PRG。
        } catch (BizException e) { // 版本冲突、字段非法等。
            return backToForm(form, true, e, response, model); // 回表单。
        }
    }

    /**
     * 校验失败：回到表单页。
     *
     * <p>保留输入（docs/05 §5），HTTP 状态取业务异常的 400/409；
     * 编辑模式还要把库存与状态带回页面（表单本身不提交这两项）。</p>
     */
    private String backToForm(ProductForm form, boolean editing, BizException e,
                              HttpServletResponse response, Model model) {
        response.setStatus(e.code().status()); // 400 或 409。
        model.addAttribute("form", form); // 保留输入。
        model.addAttribute("error", e.getMessage()); // 红字提示。
        model.addAttribute("options", productAdminService.categoryOptions()); // 下拉不能丢。
        model.addAttribute("editing", editing); // 新增/编辑模式。
        model.addAttribute("active", "products");
        if (editing && form.getId() != null) { // 编辑页要显示只读的库存与状态。
            model.addAttribute("product", productAdminService.detail(form.getId())); // 商品被删会抛 404，属正常兜底。
        }
        return "admin/product-form"; // 同一个模板。
    }
}
