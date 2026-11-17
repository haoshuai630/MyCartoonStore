package org.csu.mycartoonstore.admin.controller; // 顾客管理：列表、筛选、启停。

import org.csu.mycartoonstore.account.dto.AdminUserRow; // 列表行。
import org.csu.mycartoonstore.account.service.AccountAdminService; // 业务规则。
import org.csu.mycartoonstore.common.page.PageResult; // 分页结果。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 传模板。
import org.springframework.web.bind.annotation.GetMapping; // 列表。
import org.springframework.web.bind.annotation.PathVariable; // 目标账号 id。
import org.springframework.web.bind.annotation.PostMapping; // 启停。
import org.springframework.web.bind.annotation.RequestMapping; // 路由前缀。
import org.springframework.web.bind.annotation.RequestParam; // 筛选与状态参数。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 一次性提示。

// M08-04：依据 docs/01 FR-A-07、docs/04 §3.5、docs/05 §4.9。
// 只做参数绑定、页面选择与跳转；筛选、分页与"只处理 CUSTOMER"全在 AccountAdminService。
// 启停是写操作，成功后 PRG，刷新不会重复提交。
@Controller // 返回模板名。
@RequestMapping("/users") // 管理端顾客路由。
public class AdminUserController {

    private final AccountAdminService accountAdminService; // 业务服务。

    public AdminUserController(AccountAdminService accountAdminService) { // 构造器注入。
        this.accountAdminService = accountAdminService; // 保存服务。
    }

    /** 列表：用户名包含 + 状态筛选，按 id 升序分页。 */
    @GetMapping
    public String list(@RequestParam(required = false) String keyword, // 用户名包含。
                       @RequestParam(required = false) String status, // ALL / ENABLED / DISABLED。
                       @RequestParam(required = false) String page, // 页码。
                       Model model) {
        PageResult<AdminUserRow> result = accountAdminService.list(keyword, status, page); // 归一 + 查询。
        model.addAttribute("page", result); // 当前页数据。
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim()); // 回显关键字。
        model.addAttribute("status", status == null || status.isBlank() ? "ALL" : status); // 回显状态。
        model.addAttribute("active", "users"); // 导航高亮。
        return "admin/user-list"; // 列表模板。
    }

    /** 启用 / 禁用：重复设置同一状态时提示当前状态，不算失败。 */
    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable long id, // 目标顾客 id。
                               @RequestParam String status, // ENABLED / DISABLED。
                               RedirectAttributes redirect) {
        boolean changed = accountAdminService.changeStatus(id, status); // 角色与状态都在服务层校验。
        String action = "ENABLED".equals(status) ? "启用" : "禁用"; // 中文动作。
        redirect.addFlashAttribute("message",
                changed ? "已" + action + "该账号" : "该账号当前已是" + action + "状态"); // 一次性提示。
        return "redirect:/users"; // PRG：回列表看真实状态。
    }
}
