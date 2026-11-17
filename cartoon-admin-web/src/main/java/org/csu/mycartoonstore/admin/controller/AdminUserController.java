package org.csu.mycartoonstore.admin.controller; // 顾客管理：列表、筛选、启停。

import org.csu.mycartoonstore.account.dto.AdminUserRow; // 列表行。
import org.csu.mycartoonstore.account.dto.Profile; // 校验后的三项资料。
import org.csu.mycartoonstore.account.service.AccountAdminService; // 业务规则。
import org.csu.mycartoonstore.common.exception.BizException; // 400 提示。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 区分 400 与 404。
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

    /**
     * 修改顾客资料：显示名、邮箱、电话三列。
     *
     * <p>校验失败（超长、显示名空）由服务层抛 400，回列表并提示；成功后 PRG。</p>
     */
    @PostMapping("/{id}/profile")
    public String updateProfile(@PathVariable long id,
                                @RequestParam String displayName, // 必填，≤50。
                                @RequestParam(required = false) String email, // 可空，≤100。
                                @RequestParam(required = false) String phone, // 可空，≤30。
                                RedirectAttributes redirect) {
        try {
            Profile profile = accountAdminService.updateProfile(id, displayName, email, phone); // 只写三列。
            redirect.addFlashAttribute("message", "已保存顾客资料：「" + profile.getDisplayName() + "」");
        } catch (BizException e) { // 400 回列表提示；404 继续往上抛，走统一错误页。
            if (e.code() != ErrorCode.BAD_REQUEST) {
                throw e; // 不存在或不是顾客：保持 404，不伪装成"保存失败"。
            }
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users"; // PRG。
    }

    /** 重置密码：生成一次性临时密码，明文只在这一次提示里显示。 */
    @PostMapping("/{id}/password-reset")
    public String resetPassword(@PathVariable long id, RedirectAttributes redirect) {
        String temp = accountAdminService.resetPassword(id); // 写 BCrypt，返回明文一次。
        redirect.addFlashAttribute("tempPassword", temp); // 单独放一个属性，页面醒目展示。
        redirect.addFlashAttribute("message", "已重置密码，请把临时密码转达给顾客，并提醒其尽快修改");
        return "redirect:/users"; // PRG。
    }
}
