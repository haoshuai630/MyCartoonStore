package org.csu.mycartoonstore.admin.controller; // 分类维护页面：列表、新增、编辑、删除。

import jakarta.servlet.http.HttpServletResponse; // 表单校验失败时也要给出正确状态码。
import org.csu.mycartoonstore.catalog.dto.CategoryForm; // 表单对象。
import org.csu.mycartoonstore.catalog.entity.Category; // 父分类展示。
import org.csu.mycartoonstore.catalog.service.CategoryAdminService; // 业务规则都在这里。
import org.csu.mycartoonstore.common.exception.BizException; // 校验失败回表单。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 传模板。
import org.springframework.web.bind.annotation.GetMapping; // 列表、表单页。
import org.springframework.web.bind.annotation.ModelAttribute; // 表单绑定。
import org.springframework.web.bind.annotation.PathVariable; // 分类 id。
import org.springframework.web.bind.annotation.PostMapping; // 新增、编辑、删除。
import org.springframework.web.bind.annotation.RequestMapping; // 路由前缀。
import org.springframework.web.bind.annotation.RequestParam; // 可选的父级参数。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 一次性提示。

// M07-04：依据 docs/04 §3.2、docs/05 §4.9、docs/06 AC-25/AC-26。
// 只做参数绑定、页面选择与跳转；层级、编码、唯一性、引用校验全部在 CategoryAdminService。
// 表单校验失败时回到表单并保留已填内容（docs/05 §5），同时把 HTTP 状态设成 400/409，
// 让"参数错误"在协议层也成立。
@Controller // 返回模板名。
@RequestMapping("/categories") // 六条分类路由的前缀。
public class AdminCategoryController {

    private final CategoryAdminService categoryAdminService; // 业务服务。

    public AdminCategoryController(CategoryAdminService categoryAdminService) { // 构造器注入。
        this.categoryAdminService = categoryAdminService; // 保存服务。
    }

    /** 列表：两级缩进树 + 每行的计数与删除提示。 */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("rows", categoryAdminService.tree()); // 已排好序的扁平行。
        model.addAttribute("active", "categories"); // 导航高亮。
        return "admin/category-list"; // 列表模板。
    }

    /** 新增表单：带 parentId 时是"在某作品下新增品类"。 */
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long parentId, Model model) {
        Category parent = categoryAdminService.topCategory(parentId); // 父级必须是一级。
        if (parentId != null && parent == null) { // 传了不合法的父级。
            throw BizException.notFound("父分类不存在"); // 统一 404 错误页，不进入半截表单。
        }
        model.addAttribute("form", categoryAdminService.blankForm(parentId)); // 空表单。
        model.addAttribute("parentName", parent == null ? null : parent.getName()); // 只读展示父名。
        model.addAttribute("editing", false); // 新增模式。
        model.addAttribute("active", "categories");
        return "admin/category-form"; // 表单模板。
    }

    /** 新增提交：校验失败回表单并保留输入，同时给出 400/409。 */
    @PostMapping
    public String create(@ModelAttribute CategoryForm form, Model model,
                         RedirectAttributes redirect, HttpServletResponse response) {
        try {
            Category created = categoryAdminService.create(form); // 规则全在服务里。
            redirect.addFlashAttribute("message", "已新增分类：「" + created.getName() + "」"); // 一次性提示。
            return "redirect:/categories"; // PRG：刷新不会重复提交。
        } catch (BizException e) { // 编码格式、层级、同名等失败。
            return backToForm(form, false, e, response, model); // 新增失败回表单。
        }
    }

    /** 编辑表单：编码与父级只读展示。 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        CategoryForm form = categoryAdminService.form(id); // 不存在时抛 404，交给全局处理器。
        Category parent = categoryAdminService.topCategory(form.getParentId()); // 父级名称。
        model.addAttribute("form", form); // 当前值。
        model.addAttribute("parentName", parent == null ? null : parent.getName()); // 只读展示。
        model.addAttribute("editing", true); // 编辑模式。
        model.addAttribute("active", "categories");
        return "admin/category-form"; // 复用同一个模板。
    }

    /** 编辑提交：只改名称与排序。 */
    @PostMapping("/{id}")
    public String update(@PathVariable long id, @ModelAttribute CategoryForm form, Model model,
                         RedirectAttributes redirect, HttpServletResponse response) {
        try {
            categoryAdminService.update(id, form); // 编码与父级即便带了也不采纳。
            redirect.addFlashAttribute("message", "已保存分类：「" + form.getName().trim() + "」"); // 一次性提示。
            return "redirect:/categories"; // PRG。
        } catch (BizException e) { // 同名、参数非法等。
            return backToForm(form, true, e, response, model); // 编辑失败回表单。
        }
    }

    /** 删除：有子分类或商品引用时由全局异常处理器转 409 错误页。 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        categoryAdminService.delete(id); // 校验不过直接抛 BizException。
        redirect.addFlashAttribute("message", "已删除分类"); // 成功提示。
        return "redirect:/categories"; // 回列表。
    }

    /**
     * 校验失败：回到表单页。
     *
     * <p>保留用户已填的值（docs/05 §5），并把 HTTP 状态设成业务异常对应的 400/409；
     * 这样页面有红字、协议层也不是"成功"。</p>
     */
    private String backToForm(CategoryForm form, boolean editing, BizException e,
                              HttpServletResponse response, Model model) {
        response.setStatus(e.code().status()); // 400 或 409。
        Category parent = categoryAdminService.topCategory(form.getParentId()); // 父级展示。
        model.addAttribute("form", form); // 保留输入。
        model.addAttribute("error", e.getMessage()); // 红字提示。
        model.addAttribute("parentName", parent == null ? null : parent.getName());
        model.addAttribute("editing", editing); // 由调用方说明是新增还是编辑。
        model.addAttribute("active", "categories");
        return "admin/category-form"; // 同一个模板。
    }
}
