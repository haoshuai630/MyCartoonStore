package org.csu.mycartoonstore.web.controller; // 修改密码：验证旧密码、校验新密码，成功后要求重新登录。

import jakarta.servlet.http.HttpSession; // 当前用户与成功后销毁会话。
import org.csu.mycartoonstore.account.dto.PasswordForm; // 新密码规则与两次一致校验。
import org.csu.mycartoonstore.account.service.AccountService; // 改密的业务入口。
import org.csu.mycartoonstore.common.exception.BizException; // 校验失败提示。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 未登录兜底 401。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // 注册为控制器。
import org.springframework.ui.Model; // 页面数据。
import org.springframework.web.bind.annotation.GetMapping; // 打开改密页。
import org.springframework.web.bind.annotation.PostMapping; // 提交改密。
import org.springframework.web.bind.annotation.RequestParam; // 三个密码字段。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 成功后的一次性提示。

// M08-02：依据 docs/01 FR-U-04、docs/04 §2.2、docs/05 §2.1。
// 负责：打开改密页、比对两次输入、成功后销毁当前会话并跳登录页；
// 不做：旧密码比对、强度规则与散列（都在 AccountService / PasswordForm），也不吊销其他浏览器的会话。
@Controller // 用户端控制器：只处理页面。
public class PasswordController {

    private final AccountService accountService; // 改密的业务入口。

    public PasswordController(AccountService accountService) { // 构造器注入。
        this.accountService = accountService; // 保存账号服务。
    }

    /** 打开改密页：表单为空，密码从不回显。 */
    @GetMapping("/password") // 受保护路径：WebConfig 已注册。
    public String page() {
        return "password"; // 渲染改密页。
    }

    /**
     * 提交改密：两次输入一致后交给服务层；成功销毁会话并回登录页，失败回表单。
     */
    @PostMapping("/password") // 写操作用 POST：刷新不会重复提交。
    public String change(@RequestParam(required = false) String oldPassword, // 旧密码。
                         @RequestParam(required = false) String newPassword, // 新密码。
                         @RequestParam(required = false) String confirmPassword, // 再次输入。
                         HttpSession session, Model model, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 身份只来自服务端会话。
        try {
            PasswordForm.requireSame(newPassword, confirmPassword); // 表单层面：两次输入一致。
            accountService.changePassword(userId, oldPassword, newPassword); // 旧密码、强度与写库。
        } catch (BizException e) { // 校验失败：回到表单提示，密码不回填。
            model.addAttribute("error", e.getMessage()); // 具体哪条规则不满足。
            return "password"; // 200 回表单，沿用登录/注册的惯例。
        }
        session.invalidate(); // 改密成功：结束当前浏览器会话，要求用新密码重登。
        redirect.addFlashAttribute("message", "密码已修改，请重新登录"); // 跳登录页后提示一次。
        return "redirect:/login"; // PRG：不在本页留写操作。
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
