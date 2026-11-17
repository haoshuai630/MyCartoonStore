package org.csu.mycartoonstore.admin.controller; // 管理端控制台：身份 + 导航入口。

import jakarta.servlet.http.HttpSession; // 取当前管理员的显示名。
import org.csu.mycartoonstore.admin.interceptor.AdminAuthInterceptor; // 会话键常量。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 传给模板。
import org.springframework.web.bind.annotation.GetMapping; // 只读页面。

// M07-02：依据 docs/01 FR-A-01、docs/05 §2.2 / §4.9。
// 控制台只回答"当前是谁、能去哪"，不显示任何统计数字（docs/05 §4.9 明确要求）。
// 三个业务入口随后续任务接入：分类 M07-04、商品 M07-06、订单 M07-08。
@Controller // 返回模板名。
public class AdminHomeController {

    /** 控制台：/admin 与 /admin/ 都落到这里 */
    @GetMapping({"", "/"})
    public String home(HttpSession session, Model model) { // 拦截器保证走到这里时已是启用管理员。
        model.addAttribute("adminName", session.getAttribute(AdminAuthInterceptor.DISPLAY_NAME)); // 页头显示名。
        return "admin/main"; // 控制台模板。
    }
}
