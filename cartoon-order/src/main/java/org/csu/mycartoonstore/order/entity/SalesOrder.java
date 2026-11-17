package org.csu.mycartoonstore.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.csu.mycartoonstore.order.domain.OrderStatus; // 状态标签映射。

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * sales_order：订单主表，含收货快照与模拟支付信息。
 * <p>状态只有五种；支付三字段（payment_method / payment_no / paid_at）成组出现，
 * 由数据库 CHECK 强制，Service 不能写出半套。</p>
 */
@Getter
@Setter
@TableName("sales_order")
public class SalesOrder {

    /** id BIGINT AUTO_INCREMENT */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** order_no VARCHAR(40)，服务端生成，唯一 */
    private String orderNo;

    /** user_id，下单用户 */
    private Long userId;

    /** status：PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED */
    private String status;

    /** total_amount DECIMAL(12,2)，等于全部明细行金额之和 */
    private BigDecimal totalAmount;

    /** receiver_name，下单时快照 */
    private String receiverName;

    /** receiver_phone，下单时快照 */
    private String receiverPhone;

    /** receiver_address，下单时快照 */
    private String receiverAddress;

    /** payment_method，成功后固定 MOCK */
    private String paymentMethod;

    /** payment_no，成功凭证，未支付为空 */
    private String paymentNo;

    /** paid_at */
    private LocalDateTime paidAt;

    /** cancelled_at */
    private LocalDateTime cancelledAt;

    /** shipped_at */
    private LocalDateTime shippedAt;

    /** completed_at */
    private LocalDateTime completedAt;

    /** created_at */
    private LocalDateTime createdAt;

    /** updated_at */
    private LocalDateTime updatedAt;

    /** 状态中文标签：模板直接展示，不在页面里写映射 */
    public String getStatusLabel() {
        return OrderStatus.labelOf(status); // 五种状态的统一文案。
    }
}
