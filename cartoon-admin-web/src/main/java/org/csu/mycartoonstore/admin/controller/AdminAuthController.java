package org.csu.mycartoonstore.admin.controller; // 管理端登录与退出。

import jakarta.servlet.http.HttpServletRequest; // 重建会话要用请求而不是 session 参数。
import jakarta.servlet.http.HttpSession; // 旧会话要作废。
import org.csu.mycartoonstore.account.entity.StoreUser; // 登录成功后只取 id 与显示名。
import org.csu.mycartoonstore.account.service.AccountService; // 管理端登录校验。
import org.csu.mycartoonstore.admin.interceptor.AdminAuthInterceptor; // 会话键常量与拦截器保持一致。
import org.csu.mycartoonstore.common.exception.BizException; // 登录失败转表单提示。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 失败时回填用户名与提示。
import org.springframework.web.bind.annotation.GetMapping; // 打开登录页。
import org.springframework.web.bind.annotation.PostMapping; // 提交登录、退出。
import org.springframework.web.bind.annotation.RequestParam; // 接收登录表单的三个字段。

// M07-02：依据 docs/01 FR-A-01、docs/02 §6.2、docs/04 §3.1。
// 只做"管理员登录/退出"：角色与状态由 AccountService.authenticateAdmin 判断。
// 不做验证码、记住登录、找回密码（docs/01 §1.3 明确不做）。
@Controller // 返回模板名，不走 REST。
public class AdminAuthController {

    private final AccountService accountService; // 只调用两个登出入口里的管理端那个。

    public AdminAuthController(AccountService accountService) { // 构造器注入。
        this.accountService = accountService; // 保存账号服务。
    }

    /** 打开登录页：已登录的管理员直接进控制台。 */
    @GetMapping("/login")
    public String page(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // 不新建会话。
        if (session != null && session.getAttribute(AdminAuthInterceptor.USER_ID) != null) { // 已经有登录态。
            return "redirect:/"; // 拦截器会再核一次身份，不合格自然回登录页。
        }
        return "admin/login"; // 首次访问：渲染登录表单。
    }

    /**
     * 提交登录：校验通过后换会话 ID 再写身份。
     *
     * <p>登录成功先 {@code changeSessionId()}，让登录前的会话 ID 立即作废，防止会话固定攻击
     * （docs/02 §6.2 的"重建会话"在 Servlet 3.1+ 的等价做法）。失败统一回表单，
     * 文案由 AccountService 给出，不区分账号不存在、密码错、角色不符与已停用。</p>
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, // 表单用户名。
                        @RequestParam String password, // 表单密码，不回显、不记日志。
                        @RequestParam(required = false) String redirect, // 被拦时记下的后台地址。
                        HttpServletRequest request, // 用来换会话 ID。
                        Model model) { // 失败时回填。
        StoreUser admin;
        try {
            admin = accountService.authenticateAdmin(username, password); // 只接受启用 ADMIN。
        } catch (BizException e) { // 登录失败：回表单。
            model.addAttribute("error", e.getMessage()); // 统一文案。
            model.addAttribute("username", username); // 用户名可回显。
            return "admin/login"; // 停在登录页。
        }
        HttpSession session = request.getSession(true); // 登录前没有会话就在这里建一个。
        request.changeSessionId(); // 换掉会话 ID：旧的登录前 ID 作废。
        session.setAttribute(AdminAuthInterceptor.USER_ID, admin.getId()); // 会话只存 id。
        session.setAttribute(AdminAuthInterceptor.DISPLAY_NAME, admin.getDisplayName()); // 显示名只用于页头。
        return "redirect:" + safeRedirect(redirect); // 回被拦的后台页，默认控制台。
    }

    /** 退出：销毁管理端会话后回登录页。 */
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // 没有会话就不用作废。
        if (session != null) {
            session.invalidate(); // 只清管理端会话，用户端会话不受影响。
        }
        return "redirect:/login"; // 回登录页。
    }

    /** 只接受站内相对路径；外部地址与畸形值一律回控制台。 */
    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() // 没带地址。
                || !redirect.startsWith("/") // 必须是站内绝对路径。
                || redirect.startsWith("//")) { // 协议相对地址会跳到外站。
            return "/"; // 默认回控制台。
        }
        return redirect; // 保留原地址与查询串。
    }
}
