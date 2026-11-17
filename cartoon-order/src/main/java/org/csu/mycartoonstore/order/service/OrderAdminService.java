package org.csu.mycartoonstore.order.service; // 后台订单查询：全站列表与详情，不做状态迁移。

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 条件拼装。
import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 构造 Lambda 条件。
import org.csu.mycartoonstore.common.exception.BizException; // 非法参数 400、不存在 404。
import org.csu.mycartoonstore.common.page.PageQuery; // 页码与页大小归一。
import org.csu.mycartoonstore.order.domain.OrderStatus; // 状态白名单与标签。
import org.csu.mycartoonstore.order.dto.AdminOrderPage; // 一页结果。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.entity.SalesOrderItem; // 明细快照。
import org.csu.mycartoonstore.order.mapper.SalesOrderItemMapper; // 明细查询。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 订单查询。
import org.springframework.stereotype.Service; // 业务服务。

import java.util.List; // 列表。

// M07-08：依据 docs/01 FR-A-05、docs/04 §3.4、docs/05 §4.9。
// 只做"查全站订单与详情"：状态、完整单号、分页；
// 不做发货（ShipService）、不编辑金额/明细/凭证/状态（docs/01 FR-A-05 明确不能改）。
// 所属用户由 admin-web 用 AccountService 批量取，order 模块不依赖 account。
@Service // 业务服务。
public class OrderAdminService {

    /** 每页 10 条：与其他后台列表一致 */
    private static final int PAGE_SIZE = 10; // docs/04 §4。

    /** 订单号最大长度：VARCHAR(40) */
    private static final int ORDER_NO_MAX = 40; // 超长 400。

    private final SalesOrderMapper orderMapper; // 订单查询。
    private final SalesOrderItemMapper itemMapper; // 明细查询。

    public OrderAdminService(SalesOrderMapper orderMapper, SalesOrderItemMapper itemMapper) { // 构造器注入。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.itemMapper = itemMapper; // 保存明细 Mapper。
    }

    /**
     * 全站订单一页：状态 + 完整订单号 + 分页，按创建时间倒序。
     *
     * <p>与用户端列表的区别只有"不按 userId 过滤"，其余口径（状态白名单、单号精确、分页）一致。</p>
     */
    public AdminOrderPage list(String rawStatus, String rawOrderNo, String rawPage, String rawPageSize) {
        OrderStatus status = OrderStatus.ofFilter(rawStatus); // 非法状态 400。
        String orderNo = normalizeOrderNo(rawOrderNo); // 空串视为不搜索，超长 400。
        PageQuery pageQuery = PageQuery.of(rawPage, rawPageSize, PAGE_SIZE); // 页码与页大小。

        LambdaQueryWrapper<SalesOrder> filter = Wrappers.<SalesOrder>lambdaQuery()
                .eq(status != null, SalesOrder::getStatus, status == null ? null : status.name()) // 状态筛选。
                .eq(orderNo != null, SalesOrder::getOrderNo, orderNo); // 单号精确匹配。
        long total = orderMapper.selectCount(filter); // 同条件总数。
        int size = pageQuery.pageSize(); // 每页数量。
        int totalPages = (int) Math.max(1, (total + size - 1) / size); // 至少 1 页。
        int safePage = Math.min(pageQuery.page(), totalPages); // 超末页回到末页，避免空白页。

        List<SalesOrder> orders = orderMapper.selectList(filter
                .orderByDesc(SalesOrder::getCreatedAt) // 新的在前。
                .orderByDesc(SalesOrder::getId) // 同秒按 id 兜底。
                .last("LIMIT " + size + " OFFSET " + (long) (safePage - 1) * size)); // 只取当前页。
        return new AdminOrderPage(orders, safePage, size, total, totalPages,
                status == null ? "ALL" : status.name(), orderNo); // 交给页面。
    }

    /** 按订单号取订单：不存在 404（全站可看，不校验归属）。 */
    public SalesOrder detail(String orderNo) {
        SalesOrder order = orderMapper.findByOrderNo(orderNo); // 订单号全局唯一。
        if (order == null) {
            throw BizException.notFound("订单不存在"); // 统一 404。
        }
        return order; // 已存在的订单。
    }

    /** 订单明细：按下单顺序 */
    public List<SalesOrderItem> items(long orderId) {
        return itemMapper.selectList(Wrappers.<SalesOrderItem>lambdaQuery()
                .eq(SalesOrderItem::getOrderId, orderId) // 只查该订单。
                .orderByAsc(SalesOrderItem::getId)); // 保持写入顺序。
    }

    /**
     * 该商品被多少条订单明细引用。
     *
     * <p>后台删除商品前调用：明细的 {@code product_id} 有外键指向商品，
     * 有引用时商品只能下架、不能删除，历史订单才不会悬空。</p>
     */
    public long countItemsByProduct(long productId) {
        return itemMapper.selectCount(Wrappers.<SalesOrderItem>lambdaQuery()
                .eq(SalesOrderItem::getProductId, productId)); // 只数条数，不读明细内容。
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
