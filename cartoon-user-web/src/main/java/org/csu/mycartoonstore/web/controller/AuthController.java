package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.account.entity.StoreUser;
import org.csu.mycartoonstore.account.service.AccountService;
import org.csu.mycartoonstore.common.exception.BizException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 注册页。
 *
 * <p>格式与唯一性校验都在 AccountService；控制器只多做一件 Service 不该管的事：
 * 确认两次密码一致。成功后不自动登录，跳登录页让用户自己登一次。</p>
 */
@Controller
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** 打开注册页：带一个空表单 */
    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }

    /**
     * 提交注册：成功跳登录页并提示，失败回到表单并显示原因。
     *
     * <p>用 RedirectAttributes 传一次性提示，刷新不会重复提交。</p>
     */
    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam(required = false) String displayName,
                           Model model,
                           RedirectAttributes redirect) {
        if (!password.equals(confirmPassword)) {
            return backToForm(model, username, displayName, "两次输入的密码不一致");
        }
        try {
            StoreUser created = accountService.register(username, password, displayName);
            redirect.addFlashAttribute("message", "注册成功，" + created.getUsername() + "，请登录");
            return "redirect:/login";
        } catch (BizException e) {
            return backToForm(model, username, displayName, e.getMessage());
        }
    }

    /** 打开登录页：带上注册成功等一次性提示 */
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    /**
     * 提交登录：校验通过后把 userId 与显示名写进 Session。
     *
     * <p>登录后回到 redirect 指定的站内路径；只接受以 / 开头的地址，
     * 避免被构造成跳转到站外的开放重定向。</p>
     */
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String redirect,
                        HttpSession session,
                        Model model) {
        try {
            StoreUser user = accountService.authenticate(username, password);
            session.setAttribute("userId", user.getId());
            session.setAttribute("displayName", user.getDisplayName());
            return "redirect:" + safeRedirect(redirect);
        } catch (BizException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            return "auth/login";
        }
    }

    /** 登出：只清会话，不删其他状态 */
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/main";
    }

    /** 只允许站内相对路径，其他一律回首页 */
    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/main";
        }
        return redirect;
    }

    /** 回到表单并回填已填内容，避免用户重填 */
    private String backToForm(Model model, String username, String displayName, String error) {
        model.addAttribute("error", error);
        model.addAttribute("username", username);
        model.addAttribute("displayName", displayName);
        return "auth/register";
    }
}