package org.csu.mycartoonstore.order.service; // 订单列表查询：本人、状态、完整单号、分页与角标。

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 条件拼装。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 构造 Lambda 条件。
import org.csu.mycartoonstore.common.exception.BizException; // 非法参数 400。
import org.csu.mycartoonstore.order.domain.OrderStatus; // 状态校验与标签。
import org.csu.mycartoonstore.order.dto.OrderCard; // 卡片。
import org.csu.mycartoonstore.order.dto.OrderPage; // 一页结果。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.entity.SalesOrderItem; // 明细快照。
import org.csu.mycartoonstore.order.mapper.SalesOrderItemMapper; // 批量查明细。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 查订单。
import org.springframework.stereotype.Service; // 业务服务。

import java.util.ArrayList; // 组装卡片。
import java.util.HashMap; // 角标。
import java.util.List; // 列表。
import java.util.Map; // 分组。
import java.util.stream.Collectors; // 分组。

// M05-04：依据 docs/05 §4.6、docs/06 AC-19/AC-30。
// 只做"查本人订单"：状态过滤、完整单号精确匹配、分页、状态角标。
// 不做支付、取消、发货等写操作（M05-05 / M06 / M07）。
@Service // 业务服务。
public class OrderListService {

    /** 每页 10 条：与 docs/05 §4.6 的分页一致。 */
    private static final int PAGE_SIZE = 10;

    /** 订单号最大长度：数据库 VARCHAR(40)。 */
    private static final int ORDER_NO_MAX = 40;

    private final SalesOrderMapper orderMapper; // 订单查询。
    private final SalesOrderItemMapper itemMapper; // 卡片缩略图与件数。

    public OrderListService(SalesOrderMapper orderMapper, SalesOrderItemMapper itemMapper) { // 构造器注入。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.itemMapper = itemMapper; // 保存明细 Mapper。
    }

    /**
     * 查询本人订单一页。
     *
     * @param rawStatus  状态码或 ALL
     * @param rawOrderNo 完整订单号（精确匹配，可空）
     * @param rawPage    页码（1 起，可空）
     */
    public OrderPage list(long userId, String rawStatus, String rawOrderNo, String rawPage) {
        OrderStatus status = OrderStatus.ofFilter(rawStatus); // 非法状态 400。
        String orderNo = normalizeOrderNo(rawOrderNo); // 空串按未搜索；超长 400。
        int page = parsePage(rawPage); // 非法页码 400。

        LambdaQueryWrapper<SalesOrder> filter = Wrappers.<SalesOrder>lambdaQuery()
                .eq(SalesOrder::getUserId, userId) // 只查本人，越权自然查不到。
                .eq(status != null, SalesOrder::getStatus, status == null ? null : status.name()) // 状态页签。
                .eq(orderNo != null, SalesOrder::getOrderNo, orderNo); // 完整单号精确匹配。
        long total = orderMapper.selectCount(filter); // 当前条件总数。
        int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE); // 至少 1 页。
        int safePage = Math.min(page, totalPages); // 超过末页时回到末页，避免空白页。

        // 只取当前页：排序与分页都交给 SQL，避免把所有订单读进内存。
        List<SalesOrder> orders = orderMapper.selectList(filter
                .orderByDesc(SalesOrder::getCreatedAt) // 新的在前。
                .orderByDesc(SalesOrder::getId) // 同秒下单按 id 兜底。
                .last("LIMIT " + PAGE_SIZE + " OFFSET " + (safePage - 1) * PAGE_SIZE));
        List<OrderCard> cards = toCards(orders); // 明细一次批量查，避免 N+1。
        return new OrderPage(cards, safePage, PAGE_SIZE, total, totalPages,
                statusCounts(userId), status == null ? "ALL" : status.name(), orderNo);
    }

    /** 六种角标数量：ALL + 五种状态，全部来自真实数据。 */
    public Map<String, Long> statusCounts(long userId) {
        Map<String, Long> counts = new HashMap<>();
        counts.put("ALL", orderMapper.selectCount(Wrappers.<SalesOrder>lambdaQuery()
                .eq(SalesOrder::getUserId, userId))); // 全部。
        for (OrderStatus status : OrderStatus.values()) { // 五种状态各查一次。
            counts.put(status.name(), orderMapper.selectCount(Wrappers.<SalesOrder>lambdaQuery()
                    .eq(SalesOrder::getUserId, userId)
                    .eq(SalesOrder::getStatus, status.name())));
        }
        return counts; // 交给页面显示角标。
    }

    /** 把订单转成卡片：首件商品缩略图、种类数、总件数。 */
    private List<OrderCard> toCards(List<SalesOrder> orders) {
        if (orders.isEmpty()) {
            return List.of(); // 空页直接返回。
        }
        List<Long> orderIds = orders.stream().map(SalesOrder::getId).toList(); // 本页订单 id。
        Map<Long, List<SalesOrderItem>> itemsByOrder = itemMapper.selectList(
                        Wrappers.<SalesOrderItem>lambdaQuery()
                                .in(SalesOrderItem::getOrderId, orderIds) // 一次查完本页明细。
                                .orderByAsc(SalesOrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(SalesOrderItem::getOrderId)); // 按订单分组。

        List<OrderCard> cards = new ArrayList<>(orders.size()); // 按查询顺序组装。
        for (SalesOrder order : orders) {
            List<SalesOrderItem> items = itemsByOrder.getOrDefault(order.getId(), List.of()); // 该订单明细。
            SalesOrderItem first = items.isEmpty() ? null : items.get(0); // 首件做缩略图。
            int quantity = items.stream().mapToInt(SalesOrderItem::getQuantity).sum(); // 总件数。
            cards.add(new OrderCard(order.getOrderNo(), order.getStatus(), order.getTotalAmount(),
                    order.getCreatedAt(),
                    first == null ? "" : first.getProductNameSnapshot(), // 首件名称。
                    first == null ? "" : first.getCoverImageSnapshot(), // 首件封面。
                    items.size(), quantity)); // 种类数与总件数。
        }
        return cards; // 返回卡片。
    }

    /** 页码：空按 1；非数字或小于 1 给 400。 */
    private int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1; // 默认第一页。
        }
        try {
            int page = Integer.parseInt(raw.trim()); // 只接受整数。
            if (page < 1) {
                throw BizException.badRequest("页码不合法"); // 0 与负数都不行。
            }
            return page; // 合法页码。
        } catch (NumberFormatException e) {
            throw BizException.badRequest("页码不合法"); // 非数字也 400。
        }
    }

    /** 订单号：去空白；空串视为未搜索；超过列宽给 400。 */
    private String normalizeOrderNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null; // 未搜索。
        }
        String orderNo = raw.trim(); // 去首尾空白。
        if (orderNo.length() > ORDER_NO_MAX) {
            throw BizException.badRequest("订单号过长"); // 超过 VARCHAR(40)。
        }
        return orderNo; // 精确匹配用。
    }
}
