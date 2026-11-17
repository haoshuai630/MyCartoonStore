package org.csu.mycartoonstore.web.controller; // 模拟支付页：本人待支付订单的支付页与成功/失败两个动作。

import jakarta.servlet.http.HttpSession; // 取当前登录用户。
import org.csu.mycartoonstore.common.exception.BizException; // 未登录兜底 401、已取消 409。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 401 错误码。
import org.csu.mycartoonstore.order.domain.OrderStatus; // 待支付状态的唯一来源。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 页面展示订单号与总额。
import org.csu.mycartoonstore.order.service.OrderQueryService; // 按订单号查本人订单（404 口径复用）。
import org.csu.mycartoonstore.payment.domain.PaymentResult; // 三种支付结局。
import org.csu.mycartoonstore.payment.service.PaymentService; // 支付成功/失败的状态迁移。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 页面数据。
import org.springframework.web.bind.annotation.GetMapping; // 支付页。
import org.springframework.web.bind.annotation.PathVariable; // 订单号来自路径。
import org.springframework.web.bind.annotation.PostMapping; // 两个模拟动作。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 跳转时带一次性提示。

// M06-02：依据 docs/04 §2.4、docs/05 §4.8、docs/01 FR-P-01 第 1/2/7 条。
// 只负责"进入支付页 + 把两个动作转给 PaymentService + 跳转"：状态判断在服务层，金额取订单总额。
// 不出现真实支付渠道；不做 CSRF（M09 统一加）。
@Controller // 注册为控制器。
public class PaymentController {

    private final PaymentService paymentService; // 成功/失败的状态迁移。
    private final OrderQueryService orderQueryService; // 读本人订单：不存在与他人统一 404。

    public PaymentController(PaymentService paymentService, OrderQueryService orderQueryService) { // 构造器注入。
        this.paymentService = paymentService; // 保存支付服务。
        this.orderQueryService = orderQueryService; // 保存订单查询。
    }

    /**
     * 支付页：只有待支付能进入；已支付（含发货/完成）直接回详情；已取消 409。
     */
    @GetMapping("/payment/{orderNo}") // 受保护路径，拦截器已保证登录。
    public String page(@PathVariable String orderNo, HttpSession session, Model model,
                       RedirectAttributes redirect) {
        long userId = currentUserId(session); // 当前用户。
        SalesOrder order = orderQueryService.detail(userId, orderNo); // 他人/不存在在这里 404。
        if (order.getPaidAt() != null) { // 已支付、已发货、已完成都已有支付时间。
            redirect.addFlashAttribute("message", "订单已支付，无需重复支付"); // 详情页顶部提示。
            return "redirect:/orders/" + orderNo; // 不再展示支付页。
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) { // 只剩已取消会到这里。
            throw BizException.conflict("订单已取消，不可支付"); // 409 而不是渲染一张假支付页。
        }
        model.addAttribute("order", order); // 页面只需要订单号、创建时间与总额。
        return "payment"; // 渲染支付页。
    }

    /**
     * 模拟支付成功：跳回订单详情，展示数据库里的真实状态与凭证。
     */
    @PostMapping("/payment/{orderNo}/success") // 写操作，POST。
    public String success(@PathVariable String orderNo, HttpSession session, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 当前用户。
        PaymentResult result = paymentService.paySuccess(userId, orderNo); // 幂等与 409 都在服务层。
        redirect.addFlashAttribute("message", result == PaymentResult.ALREADY_PAID
                ? "订单已支付，无需重复支付" : "支付成功，订单已支付"); // 两种提示。
        return "redirect:/orders/" + orderNo; // PRG：刷新不会重复提交。
    }

    /**
     * 模拟支付失败：不写库，回支付页提示可重试；已经付过则改跳详情。
     */
    @PostMapping("/payment/{orderNo}/failure") // 写操作，POST。
    public String failure(@PathVariable String orderNo, HttpSession session, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 当前用户。
        PaymentResult result = paymentService.payFailure(userId, orderNo); // 待支付返回 FAILED，不写库。
        if (result == PaymentResult.ALREADY_PAID) { // 已经付过：失败动作不再有意义，回详情。
            redirect.addFlashAttribute("message", "订单已支付，无需重复支付"); // 与重复支付同一文案。
            return "redirect:/orders/" + orderNo; // 不回支付页。
        }
        redirect.addFlashAttribute("message", "支付失败，可重试"); // FR-P-01 第 4 条。
        return "redirect:/payment/" + orderNo; // 留在支付页，用户可以重试或取消。
    }

    /** 当前登录用户 id：拦截器已保证有会话，这里兜底 401。 */
    private long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(LoginInterceptor.USER_ID); // 取拦截器写入的 userId。
        if (userId instanceof Long id) {
            return id; // 正常路径。
        }
        throw new BizException(ErrorCode.UNAUTHORIZED); // 异常路径：401。
    }
}
