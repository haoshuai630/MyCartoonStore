package org.csu.mycartoonstore.web.controller;

import org.csu.mycartoonstore.account.entity.StoreUser;
import org.csu.mycartoonstore.account.service.AccountService;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.web.captcha.CaptchaService; // 验证码会话键与参数名。
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;
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
    private final CaptchaService captchaService; // 验证码会话键与参数名的来源。

    public AuthController(AccountService accountService, CaptchaService captchaService) {
        this.accountService = accountService;
        this.captchaService = captchaService;
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
                           @RequestParam(required = false) String captcha,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirect) {
        if (!verifyCaptcha(session, captcha)) { // M10-04：验证码一次性，先于其他校验。
            return backToForm(model, username, displayName, "验证码不正确，请重新输入");
        }
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
     * 提交登录：校验通过后换会话 ID 再写身份。
     *
     * <p>登录成功先 {@code changeSessionId()}，让登录前的会话 ID 立即作废，防止会话固定攻击
     * （docs/02 §6.2 的"重建会话"在 Servlet 3.1+ 的等价做法，与管理端 M07-02 同构）。
     * 只换 ID 不换会话，所以 CSRF 令牌与回跳参数都不用重新搬运。
     * 失败统一回表单，文案由 AccountService 给出，不区分账号不存在、密码错与已停用。</p>
     */
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String redirect,
                        @RequestParam(required = false) String captcha,
                        HttpServletRequest request,
                        Model model) {
        HttpSession loginSession = request.getSession(true); // 验证码要读会话。
        if (!verifyCaptcha(loginSession, captcha)) { // 验证码错误：回表单，不区分账号是否存在。
            model.addAttribute("error", "验证码不正确，请重新输入");
            model.addAttribute("username", username);
            return "auth/login";
        }
        try {
            StoreUser user = accountService.authenticateCustomer(username, password); // 只接受启用的 CUSTOMER。
            HttpSession session = request.getSession(true); // CSRF 令牌已经建过会话；直接 POST 时在这里补建。
            request.changeSessionId(); // 换掉登录前的会话 ID：旧 ID 作废，防会话固定。
            session.setAttribute("userId", user.getId()); // 换 ID 不影响属性，令牌仍在会话里。
            session.setAttribute("displayName", user.getDisplayName());
            return "redirect:" + safeRedirect(redirect);
        } catch (BizException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            return "auth/login";
        }
    }

    /**
     * 校验并作废验证码：会话里的值与输入忽略大小写比较，读一次就删。
     *
     * <p>一次性可以防止同一张图被反复利用；页面刷新会重新请求 {@code /captcha} 拿新码。</p>
     */
    private boolean verifyCaptcha(HttpSession session, String input) {
        Object expected = session.getAttribute(CaptchaService.SESSION_KEY); // 会话里的值。
        session.removeAttribute(CaptchaService.SESSION_KEY); // 无论对错都作废。
        return expected != null && input != null // 缺一即失败。
                && expected.toString().equalsIgnoreCase(input.strip()); // 忽略大小写与首尾空白。
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