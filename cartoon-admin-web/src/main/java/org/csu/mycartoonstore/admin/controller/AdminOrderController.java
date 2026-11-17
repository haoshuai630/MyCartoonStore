package org.csu.mycartoonstore.admin.controller; // 订单处理页面：全站列表、详情与发货。

import org.csu.mycartoonstore.account.service.AccountService; // 批量取所属用户的显示名。
import org.csu.mycartoonstore.order.domain.ShipResult; // 发货结局。
import org.csu.mycartoonstore.order.dto.AdminOrderPage; // 一页订单。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.service.OrderAdminService; // 全站查询。
import org.csu.mycartoonstore.order.service.ShipService; // 发货。
import org.springframework.stereotype.Controller; // MVC 控制器。
import org.springframework.ui.Model; // 传模板。
import org.springframework.web.bind.annotation.GetMapping; // 列表与详情。
import org.springframework.web.bind.annotation.PathVariable; // 订单号。
import org.springframework.web.bind.annotation.PostMapping; // 发货。
import org.springframework.web.bind.annotation.RequestMapping; // 路由前缀。
import org.springframework.web.bind.annotation.RequestParam; // 筛选参数。
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // 一次性提示。

import java.util.List; // 批量取用户名的入参。
import java.util.Set; // 本页涉及的用户 id。
import java.util.stream.Collectors; // 收集 id。

// M07-08：依据 docs/04 §3.4、docs/05 §4.9、docs/06 AC-29。
// 只做参数绑定与页面选择；状态机、行锁、幂等都在 order 模块的 Service。
// "所属用户"由本层用 AccountService 批量取：order 模块不依赖 account（docs/09 §2.3）。
@Controller // 返回模板名。
@RequestMapping("/orders") // 订单路由前缀。
public class AdminOrderController {

    private final OrderAdminService orderAdminService; // 全站订单查询。

    private final ShipService shipService; // 发货。

    private final AccountService accountService; // 批量取显示名。

    public AdminOrderController(OrderAdminService orderAdminService,
                                ShipService shipService,
                                AccountService accountService) { // 构造器注入。
        this.orderAdminService = orderAdminService; // 保存查询服务。
        this.shipService = shipService; // 保存发货服务。
        this.accountService = accountService; // 保存账号服务。
    }

    /** 全站订单列表：订单号、状态、分页。 */
    @GetMapping
    public String list(@RequestParam(required = false) String orderNo, // 完整订单号。
                       @RequestParam(required = false) String status, // ALL + 五种状态。
                       @RequestParam(required = false) String page, // 页码。
                       @RequestParam(required = false) String pageSize, // 页大小。
                       Model model) {
        AdminOrderPage result = orderAdminService.list(status, orderNo, page, pageSize); // 归一 + 查询。
        Set<Long> userIds = result.getOrders().stream()
                .map(SalesOrder::getUserId) // 本页涉及的用户。
                .collect(Collectors.toSet());
        model.addAttribute("page", result); // 当前页。
        model.addAttribute("users", accountService.displayNames(userIds)); // id → 显示名，一次查完。
        model.addAttribute("status", result.getStatus()); // 回显状态。
        model.addAttribute("orderNo", result.getOrderNo()); // 回显单号。
        model.addAttribute("active", "orders"); // 导航高亮。
        return "admin/order-list"; // 列表模板。
    }

    /** 订单详情：明细快照、收货快照、支付信息与所属用户。 */
    @GetMapping("/{orderNo}")
    public String detail(@PathVariable String orderNo, Model model) {
        SalesOrder order = orderAdminService.detail(orderNo); // 不存在 404。
        model.addAttribute("order", order); // 主表。
        model.addAttribute("items", orderAdminService.items(order.getId())); // 明细快照。
        model.addAttribute("userName", accountService.displayNames(List.of(order.getUserId()))
                .get(order.getUserId())); // 所属用户显示名。
        model.addAttribute("active", "orders");
        return "admin/order-detail"; // 详情模板。
    }

    /** 发货：仅 PAID；重复发货提示"已处理"并回详情。 */
    @PostMapping("/{orderNo}/ship")
    public String ship(@PathVariable String orderNo, RedirectAttributes redirect) {
        ShipResult result = shipService.ship(orderNo); // 行锁 + 状态迁移；不可发货时抛 409。
        redirect.addFlashAttribute("message",
                result == ShipResult.SHIPPED ? "已发货" : "该订单已处理，无需重复发货"); // 区分首次与重复。
        return "redirect:/orders/" + orderNo; // 回详情看数据库真实状态。
    }
}
