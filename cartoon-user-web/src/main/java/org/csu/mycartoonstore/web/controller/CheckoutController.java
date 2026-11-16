package org.csu.mycartoonstore.web.controller; // 结算确认页：渲染确认信息并把快照放进会话。

import jakarta.servlet.http.HttpSession; // 会话里保存确认快照，供 M05-03 提交时比对。
import org.csu.mycartoonstore.cart.dto.CartSummary; // 页面左侧的商品清单与合计。
import org.csu.mycartoonstore.cart.service.CartService; // 读车取展示数据。
import org.csu.mycartoonstore.common.exception.BizException; // 空车/失效时据异常回购物车。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 兜底未登录给 401。
import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 生成并存入会话的快照类型。
import org.csu.mycartoonstore.order.dto.PlacedOrder; // 下单结果：订单 + 明细。
import org.csu.mycartoonstore.order.dto.Receiver; // 校验后的收货信息。
import org.csu.mycartoonstore.order.dto.ReceiverForm; // 收货字段校验。
import org.csu.mycartoonstore.order.service.CheckoutService; // 生成快照、判断时限与变化。
import org.csu.mycartoonstore.order.service.OrderService; // 下单事务。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 把页面数据放进模型。
import org.springframework.web.bind.annotation.GetMapping; // 打开确认页。
import org.springframework.web.bind.annotation.PostMapping; // 提交订单。
import org.springframework.web.bind.annotation.RequestParam; // 收货表单字段。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 重定向时带一次性提示。

// M05-02：依据 docs/04 §2.3、docs/05 §4.5、AC-12/AC-13 的页面部分。
// 负责渲染确认页、把快照写进会话、空车/失效时回购物车；M05-03 起承接提交订单。
@Controller // 注册为控制器，由组件扫描发现。
public class CheckoutController {

    /** 会话里保存确认快照的键；提交时按同一个键取。 */
    public static final String SNAPSHOT_KEY = "checkoutSnapshot";

    private final CheckoutService checkoutService; // 生成快照：空车/失效拦截、总额、确认时刻。
    private final CartService cartService; // 页面展示用：行、单价、小计、合计。
    private final OrderService orderService; // 提交订单：校验快照并写订单、扣库存、删条目。

    public CheckoutController(CheckoutService checkoutService, CartService cartService,
                              OrderService orderService) { // 构造器注入。
        this.checkoutService = checkoutService; // 保存结算服务。
        this.cartService = cartService; // 保存购物车服务。
        this.orderService = orderService; // 保存下单服务。
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

    /**
     * 提交订单：校验收货字段 → 事务下单 → 清快照 → 跳订单详情。
     *
     * <p>快照超时、车已变化、库存不足都由服务层抛 409，
     * 由全局异常处理器显示错误页；成功后用 PRG 跳转，刷新不会重复下单。</p>
     */
    @PostMapping("/checkout") // 提交订单。
    public String place(HttpSession session,
                        @RequestParam String receiverName, // 收货人。
                        @RequestParam String receiverPhone, // 联系电话。
                        @RequestParam String receiverAddress) { // 详细地址。
        long userId = currentUserId(session); // 当前用户。
        // 三项必填/去空白/限长/电话含数字，不合法直接 400。
        Receiver receiver = ReceiverForm.parse(receiverName, receiverPhone, receiverAddress);
        CheckoutSnapshot snapshot = (CheckoutSnapshot) session.getAttribute(SNAPSHOT_KEY); // 确认页留下的快照。
        PlacedOrder placed = orderService.placeOrder(userId, snapshot, receiver); // 一个事务内完成下单。
        session.removeAttribute(SNAPSHOT_KEY); // 下单成功，快照作废。
        return "redirect:/orders/" + placed.getOrder().getOrderNo(); // 跳详情，刷新不会重复提交。
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
