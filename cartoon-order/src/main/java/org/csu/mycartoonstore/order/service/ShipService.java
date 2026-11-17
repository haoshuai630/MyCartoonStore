package org.csu.mycartoonstore.order.service; // 发货：PAID → SHIPPED，保留首次发货时间。

import org.csu.mycartoonstore.common.exception.BizException; // 404/409。
import org.csu.mycartoonstore.order.domain.OrderStatus; // 状态枚举。
import org.csu.mycartoonstore.order.domain.ShipResult; // 两种结局。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 行锁与写回。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 同一事务。

import java.time.Clock; // 发货时间来源，测试可固定。
import java.time.LocalDateTime; // shipped_at 类型。

// M07-08：依据 docs/01 FR-A-06、docs/02 §7.6、docs/06 AC-29。
// 只做一件事：把 PAID 订单迁到 SHIPPED 并写首次 shipped_at。
// 不改金额、库存、支付信息；不录入物流公司与运单号（docs/01 FR-A-06）。
// 重复发货按"已处理"返回，保持首次时间（与 M06 支付幂等同一口径）。
@Service // 业务服务：后台控制器只调它。
public class ShipService {

    /** 唯一可发货的起点 */
    private static final String PAID = OrderStatus.PAID.name(); // 支付成功后的状态。

    /** 已发货：重复提交不再改时间 */
    private static final String SHIPPED = OrderStatus.SHIPPED.name(); // 已发货。

    /** 已完成：已经签收，发货动作没有意义 */
    private static final String COMPLETED = OrderStatus.COMPLETED.name(); // 已完成。

    private final SalesOrderMapper orderMapper; // 锁订单行与写回状态。
    private final Clock clock; // 发货时间来源。

    public ShipService(SalesOrderMapper orderMapper, Clock clock) { // 构造器注入。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.clock = clock; // 保存统一时钟。
    }

    /**
     * 发货：先锁订单行，再按状态决定迁移、幂等或拒绝。
     *
     * <p>并发下两个管理员同时发货，第二个事务看到 SHIPPED 走幂等分支，
     * 不会把首次发货时间覆盖成更晚的时间（AC-29）。</p>
     */
    @Transactional // 锁行、判状态、写回同一事务。
    public ShipResult ship(String orderNo) {
        SalesOrder order = orderMapper.lockByOrderNo(orderNo); // FOR UPDATE：同一订单串行。
        if (order == null) {
            throw BizException.notFound("订单不存在"); // 404。
        }
        if (SHIPPED.equals(order.getStatus()) || COMPLETED.equals(order.getStatus())) { // 已经发过/已完成。
            return ShipResult.ALREADY_SHIPPED; // 提示已处理，保持首次时间。
        }
        if (!PAID.equals(order.getStatus())) { // 待支付、已取消都不能发货。
            throw BizException.conflict("当前状态不可发货"); // 409。
        }
        order.setStatus(SHIPPED); // 迁移到已发货。
        order.setShippedAt(LocalDateTime.now(clock)); // 首次发货时间。
        orderMapper.updateById(order); // 只写非空字段：金额与支付信息不动。
        return ShipResult.SHIPPED; // 本次真的发货了。
    }
}
