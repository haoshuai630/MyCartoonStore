package org.csu.mycartoonstore.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * product：商品，一个商品对应一个可销售 SKU；下架代替删除。
 * <p>version 用于乐观锁：所有写操作（资料、价格、上下架、补货、下单扣减、取消恢复）都必须显式 +1。</p>
 */
@Getter
@Setter
@TableName("product")
public class Product {

    /** id BIGINT AUTO_INCREMENT，页面定位用 ID，SKU 只展示 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** sku VARCHAR(40)，唯一，创建后不改 */
    private String sku;

    /** category_id，必须是二级分类 */
    private Long categoryId;

    /** name VARCHAR(120) */
    private String name;

    /** summary VARCHAR(500)，列表与详情简介 */
    private String summary;

    /** description TEXT，纯文本，模板转义输出 */
    private String description;

    /** cover_image VARCHAR(255)，站内相对路径 */
    private String coverImage;

    /** price DECIMAL(12,2)，> 0，两位小数 */
    private BigDecimal price;

    /** stock INT，可销售库存；下单扣减、取消恢复 */
    private Integer stock;

    /** status VARCHAR(16)：DRAFT / ON_SALE */
    private String status;

    /** sort_order INT，越小越靠前 */
    private Integer sortOrder;

    /** version BIGINT，乐观锁版本号 */
    private Long version;

    /** created_at */
    private LocalDateTime createdAt;

    /** updated_at */
    private LocalDateTime updatedAt;
}
