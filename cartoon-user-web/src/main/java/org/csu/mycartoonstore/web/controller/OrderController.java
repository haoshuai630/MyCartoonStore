package org.csu.mycartoonstore.web.controller; // 订单列表与详情：本人订单的只读页面。

import jakarta.servlet.http.HttpSession; // 取当前用户。
import org.csu.mycartoonstore.common.exception.BizException; // 未登录兜底 401。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.order.domain.CompleteResult; // 确认收货两种结局。
import org.csu.mycartoonstore.order.domain.OrderStatus; // 状态标签。
import org.csu.mycartoonstore.order.dto.OrderPage; // 列表一页。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.service.OrderCompletionService; // 确认收货。
import org.csu.mycartoonstore.order.service.OrderListService; // 列表查询。
import org.csu.mycartoonstore.order.service.OrderQueryService; // 详情查询。
import org.csu.mycartoonstore.order.service.OrderService; // 取消订单。
import org.csu.mycartoonstore.web.interceptor.LoginInterceptor; // 会话里 userId 的键。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 页面数据。
import org.springframework.web.bind.annotation.GetMapping; // 只读页面。
import org.springframework.web.bind.annotation.PathVariable; // 订单号来自路径。
import org.springframework.web.bind.annotation.PostMapping; // 取消订单。
import org.springframework.web.bind.annotation.RequestParam; // 状态、单号、页码。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 取消后的一次性提示。

import java.util.ArrayList; // 状态页签。
import java.util.LinkedHashMap; // 标签顺序固定。
import java.util.List; // 页签列表。
import java.util.Map; // 标签表。

// M05-03 建立最小详情；M05-04 补列表（状态页签、完整单号、分页、空状态）；M08-03 补确认收货。
// 只做"看"与两种状态动作（取消、确认收货）：支付/发货在 M06 / M07。
@Controller // 注册为控制器。
public class OrderController {

    private final OrderQueryService orderQueryService; // 详情查询。
    private final OrderListService orderListService; // 列表查询。
    private final OrderService orderService; // 取消订单。
    private final OrderCompletionService completionService; // 确认收货。

    public OrderController(OrderQueryService orderQueryService, OrderListService orderListService,
                           OrderService orderService, OrderCompletionService completionService) { // 构造器注入。
        this.orderQueryService = orderQueryService; // 保存详情服务。
        this.orderListService = orderListService; // 保存列表服务。
        this.orderService = orderService; // 保存取消服务。
        this.completionService = completionService; // 保存确认收货服务。
    }

    /**
     * 我的订单：状态页签 + 完整单号搜索 + 分页，条件互相保留。
     */
    @GetMapping("/orders") // 受保护路径。
    public String list(@RequestParam(required = false) String status, // 状态码或 ALL。
                       @RequestParam(required = false) String orderNo, // 完整订单号。
                       @RequestParam(required = false) String page, // 页码。
                       HttpSession session, Model model) {
        long userId = currentUserId(session); // 当前用户。
        OrderPage pageData = orderListService.list(userId, status, orderNo, page); // 非法参数在这里 400。
        model.addAttribute("pageData", pageData); // 卡片、分页与角标。
        model.addAttribute("tabs", statusTabs(pageData)); // 六个页签（编码、标签、真实数量）。
        model.addAttribute("currentLabel",
                "ALL".equals(pageData.getStatus()) ? "全部" : OrderStatus.labelOf(pageData.getStatus())); // 空状态文案用。
        return "orders"; // 渲染列表页。
    }

    /**
     * 订单详情：不存在与他人订单一律 404（同码同文案）。
     */
    @GetMapping("/orders/{orderNo}") // 受保护路径。
    public String detail(@PathVariable String orderNo, HttpSession session, Model model) {
        long userId = currentUserId(session); // 当前用户。
        SalesOrder order = orderQueryService.detail(userId, orderNo); // 归属校验在服务层。
        model.addAttribute("order", order); // 订单头与收货快照。
        model.addAttribute("items", orderQueryService.items(order.getId())); // 明细快照。
        model.addAttribute("statusLabel", OrderStatus.labelOf(order.getStatus())); // 状态中文。
        return "order-detail"; // 渲染详情页。
    }

    /**
     * 取消待支付订单：幂等，重复取消不重复恢复库存。
     */
    @PostMapping("/orders/{orderNo}/cancel") // 写操作，POST。
    public String cancel(@PathVariable String orderNo, HttpSession session, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 当前用户。
        boolean cancelled = orderService.cancel(userId, orderNo); // 已取消过会返回 false。
        redirect.addFlashAttribute("message",
                cancelled ? "订单已取消，库存已恢复" : "订单已取消，无需重复操作"); // 两种提示。
        return "redirect:/orders/" + orderNo; // 回详情，刷新不会重复提交。
    }

    /**
     * 确认收货：仅本人已发货订单可完成；重复提交提示已处理，不改首次完成时间。
     */
    @PostMapping("/orders/{orderNo}/complete") // 写操作，POST。
    public String complete(@PathVariable String orderNo, HttpSession session, RedirectAttributes redirect) {
        long userId = currentUserId(session); // 当前用户。
        CompleteResult result = completionService.complete(userId, orderNo); // 归属与状态都在服务层。
        redirect.addFlashAttribute("message",
                result == CompleteResult.COMPLETED ? "已确认收货，订单完成" : "订单已完成，无需重复操作"); // 两种提示。
        return "redirect:/orders/" + orderNo; // 回详情，刷新不会重复提交。
    }

    /** 页签：编码、中文、真实数量；在控制器算好，模板只负责显示。 */
    private List<Map<String, Object>> statusTabs(OrderPage pageData) {
        List<Map<String, Object>> tabs = new ArrayList<>(1 + OrderStatus.values().length); // 六个页签。
        for (String code : statusCodes()) { // 全部 + 五种状态。
            Map<String, Object> tab = new LinkedHashMap<>(); // 一个页签。
            tab.put("code", code); // 查询参数。
            tab.put("label", "ALL".equals(code) ? "全部" : OrderStatus.labelOf(code)); // 中文标签。
            tab.put("count", pageData.getStatusCounts().getOrDefault(code, 0L)); // 真实数量。
            tabs.add(tab); // 收集。
        }
        return tabs; // 交给模板。
    }

    /** 页签顺序：全部 + 五种状态。 */
    private List<String> statusCodes() {
        List<String> codes = new ArrayList<>(1 + OrderStatus.values().length); // 预留 6 个位置。
        codes.add("ALL"); // 第一个是全部。
        for (OrderStatus status : OrderStatus.values()) {
            codes.add(status.name()); // 依次加五种状态。
        }
        return codes; // 固定顺序。
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
