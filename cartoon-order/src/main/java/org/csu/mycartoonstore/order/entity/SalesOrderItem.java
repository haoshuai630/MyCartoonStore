package org.csu.mycartoonstore.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * sales_order_item：订单明细，保存成交快照，不随商品后续变化。
 * <p>同订单同商品只有一条；line_amount = unit_price × quantity 由数据库 CHECK 强制。</p>
 */
@Getter
@Setter
@TableName("sales_order_item")
public class SalesOrderItem {

    /** id BIGINT AUTO_INCREMENT */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** order_id，所属订单 */
    private Long orderId;

    /** product_id，商品（历史订单仍保留引用） */
    private Long productId;

    /** sku_snapshot，成交时 SKU 快照 */
    private String skuSnapshot;

    /** product_name_snapshot，成交时商品名快照 */
    private String productNameSnapshot;

    /** cover_image_snapshot，成交时封面路径快照 */
    private String coverImageSnapshot;

    /** unit_price DECIMAL(12,2)，成交单价 */
    private BigDecimal unitPrice;

    /** quantity INT，1–99 */
    private Integer quantity;

    /** line_amount DECIMAL(12,2)，= unit_price × quantity */
    private BigDecimal lineAmount;
}
