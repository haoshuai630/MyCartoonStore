package org.csu.mycartoonstore.web.controller; // 结算确认页：渲染确认信息并把快照放进会话。

import jakarta.servlet.http.HttpSession; // 会话里保存确认快照，供 M05-03 提交时比对。
import org.csu.mycartoonstore.cart.dto.CartSummary; // 页面左侧的商品清单与合计。
import org.csu.mycartoonstore.cart.service.CartService; // 读车取展示数据。
import org.csu.mycartoonstore.common.exception.BizException; // 空车/失效时据异常回购物车。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 兜底未登录给 401。
import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 生成并存入会话的快照类型。
import org.csu.mycartoonstore.order.service.CheckoutService; // 生成快照、判断时限与变化。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 把页面数据放进模型。
import org.springframework.web.bind.annotation.GetMapping; // 只注册 GET，POST 留给 M05-03。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 重定向时带一次性提示。

// M05-02：依据 docs/04 §2.3、docs/05 §4.5、AC-12/AC-13 的页面部分。
// 负责渲染确认页、把快照写进会话、空车/失效时回购物车；不创建订单（M05-03）。
// 提交按钮先禁用并注明"下单在 M05-03 接入"，避免点了没有结果的半成品入口。
@Controller // 注册为控制器，由组件扫描发现。
public class CheckoutController {

    /** 会话里保存确认快照的键；M05-03 提交时按同一个键取。 */
    public static final String SNAPSHOT_KEY = "checkoutSnapshot";

    private final CheckoutService checkoutService; // 生成快照：空车/失效拦截、总额、确认时刻。
    private final CartService cartService; // 页面展示用：行、单价、小计、合计。

    public CheckoutController(CheckoutService checkoutService, CartService cartService) { // 构造器注入。
        this.checkoutService = checkoutService; // 保存结算服务。
        this.cartService = cartService; // 保存购物车服务。
    }

    /**
     * 打开结算确认页。
     * <p>空车或存在失效条目时重定向回购物车并提示，而不是甩一张错误页。</p>
     */
    @GetMapping("/checkout") // 只处理 GET；提交订单是 M05-03 的 POST。
    public String page(HttpSession session, Model model, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 会话里的当前用户。
        CheckoutSnapshot snapshot; // 本次确认的快照。
        try {
            snapshot = checkoutService.prepare(userId); // 空车/失效会在这里抛 400/409。
        } catch (BizException e) {
            redirect.addFlashAttribute("error", e.getMessage()); // 回购物车时显示原因。
            return "redirect:/cart"; // 比错误页更接近用户要做的下一步。
        }
        session.setAttribute(SNAPSHOT_KEY, snapshot); // 存入会话，提交时用来比对。
        CartSummary cart = cartService.summary(userId); // 页面展示需要行的名称与金额。
        model.addAttribute("cart", cart); // 左侧商品清单与合计。
        model.addAttribute("snapshot", snapshot); // 右侧总额与确认时刻。
        model.addAttribute("confirmMinutes", CheckoutService.CONFIRM_TTL.toMinutes()); // 页面提示 10 分钟。
        return "checkout"; // 渲染结算确认页。
    }

    /** 当前登录用户 id：受保护路径已由拦截器保证有会话，这里兜底给 401。 */
    private long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(LoginInterceptor.USER_ID); // 取拦截器写入的 userId。
        if (userId instanceof Long id) {
            return id; // 正常路径：返回用户 id。
        }
        throw new BizException(ErrorCode.UNAUTHORIZED); // 异常路径：401 而不是 500。
    }
}
