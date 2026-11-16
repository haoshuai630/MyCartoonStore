package org.csu.mycartoonstore.web.controller; // 订单详情：按订单号查本人订单并渲染。

import jakarta.servlet.http.HttpSession; // 取当前用户。
import org.csu.mycartoonstore.common.exception.BizException; // 未登录兜底 401。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.service.OrderQueryService; // 查询本人订单与明细。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 页面数据。
import org.springframework.web.bind.annotation.GetMapping; // 只读详情。
import org.springframework.web.bind.annotation.PathVariable; // 订单号来自路径。

// M05-03：下单成功后跳到这里；M05-04 再补列表、筛选与更完整的详情。
// 只做"显示本人订单"，支付/取消/发货等操作留给 M06/M07/M05-05。
@Controller // 注册为控制器。
public class OrderController {

    private final OrderQueryService orderQueryService; // 查询服务。

    public OrderController(OrderQueryService orderQueryService) { // 构造器注入。
        this.orderQueryService = orderQueryService; // 保存查询服务。
    }

    /**
     * 订单详情：不存在与他人订单一律 404（同码同文案）。
     */
    @GetMapping("/orders/{orderNo}") // 受保护路径，已由登录拦截器覆盖。
    public String detail(@PathVariable String orderNo, HttpSession session, Model model) {
        long userId = currentUserId(session); // 当前用户。
        SalesOrder order = orderQueryService.detail(userId, orderNo); // 归属校验在服务层。
        model.addAttribute("order", order); // 订单头与收货快照。
        model.addAttribute("items", orderQueryService.items(order.getId())); // 明细快照。
        return "order-detail"; // 渲染详情页。
    }

    /** 当前登录用户 id：拦截器已保证有会话，这里兜底 401。 */
    private long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(LoginInterceptor.USER_ID); // 取拦截器写入的 userId。
        if (userId instanceof Long id) {
            return id; // 正常路径。
        }
        throw new BizException(ErrorCode.UNAUTHORIZED); // 异常路径：401 而不是 500。
    }
}
