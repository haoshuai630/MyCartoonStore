package org.csu.mycartoonstore.web.controller; // 个人资料：本人查看并修改显示名、邮箱、电话。

import jakarta.servlet.http.HttpSession; // 取当前用户与刷新页头显示名。
import org.csu.mycartoonstore.account.entity.StoreUser; // 资料页回显与保存后的最新值。
import org.csu.mycartoonstore.account.service.AccountService; // 资料读取与保存。
import org.csu.mycartoonstore.common.exception.BizException; // 校验失败提示。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 未登录兜底 401。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // 注册为控制器。
import org.springframework.ui.Model; // 页面数据。
import org.springframework.web.bind.annotation.GetMapping; // 打开资料页。
import org.springframework.web.bind.annotation.PostMapping; // 保存资料。
import org.springframework.web.bind.annotation.RequestParam; // 表单字段。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 保存成功的一次性提示。

// M08-01：依据 docs/01 FR-U-03、docs/04 §2.2、docs/05 §2.1。
// 负责：资料页回显、提交绑定、成功后刷新页头显示名并重定向；
// 不做：字段校验（ProfileForm）、写库（AccountService）、改用户名/角色/状态（产品上不允许）。
@Controller // 用户端控制器：只处理页面。
public class ProfileController {

    private final AccountService accountService; // 读取与保存资料。

    public ProfileController(AccountService accountService) { // 构造器注入。
        this.accountService = accountService; // 保存账号服务。
    }

    /**
     * 打开资料页：回显当前值，用户名只读展示。
     */
    @GetMapping("/profile") // 受保护路径：WebConfig 已注册。
    public String page(HttpSession session, Model model) {
        StoreUser user = accountService.profile(currentUserId(session)); // 只按会话里的 id 取。
        fill(model, user); // 表单初值来自库。
        return "profile"; // 渲染资料页。
    }

    /**
     * 保存资料：成功回跳本页并刷新页头；校验失败回表单并保留已填内容。
     */
    @PostMapping("/profile") // 写操作用 POST：刷新不会重复提交。
    public String save(@RequestParam String displayName, // 必填。
                       @RequestParam(required = false) String email, // 可空。
                       @RequestParam(required = false) String phone, // 可空。
                       HttpSession session, Model model, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 身份只来自服务端会话。
        try {
            StoreUser updated = accountService.updateProfile(userId, displayName, email, phone); // 校验与写库在服务层。
            session.setAttribute("displayName", updated.getDisplayName()); // 页头立即显示新名字。
            redirect.addFlashAttribute("message", "资料已保存"); // 一次性提示，刷新不重复。
            return "redirect:/profile"; // PRG：避免刷新重复提交。
        } catch (BizException e) { // 校验失败：回到表单逐条提示。
            model.addAttribute("error", e.getMessage()); // 具体哪一项不合法。
            model.addAttribute("user", accountService.profile(userId)); // 用户名仍要显示。
            model.addAttribute("displayName", displayName); // 回填刚填的显示名。
            model.addAttribute("email", email); // 回填邮箱。
            model.addAttribute("phone", phone); // 回填电话。
            return "profile"; // 200 回表单，沿用登录/注册的惯例。
        }
    }

    /** 表单初值：显示名、邮箱、电话；用户名单独给模板做只读展示。 */
    private void fill(Model model, StoreUser user) {
        model.addAttribute("user", user); // 用户名与角色。
        model.addAttribute("displayName", user.getDisplayName()); // 显示名。
        model.addAttribute("email", user.getEmail()); // 可能为 null。
        model.addAttribute("phone", user.getPhone()); // 可能为 null。
    }

    /** 当前登录用户 id：拦截器已保证有会话，这里兜底 401。 */
    private long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(LoginInterceptor.USER_ID); // 拦截器写入的键。
        if (userId instanceof Long id) {
            return id; // 正常路径。
        }
        throw new BizException(ErrorCode.UNAUTHORIZED); // 异常路径：401。
    }
}
