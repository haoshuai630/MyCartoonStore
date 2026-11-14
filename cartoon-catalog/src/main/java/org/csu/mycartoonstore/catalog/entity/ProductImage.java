package org.csu.mycartoonstore.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * product_image：商品详情相册。相册不替代 product.cover_image。
 * <p>详情页按 sort_order、id 升序取图；无相册时回退封面。</p>
 */
@Getter
@Setter
@TableName("product_image")
public class ProductImage {

    /** id BIGINT AUTO_INCREMENT */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** product_id，所属商品 */
    private Long productId;

    /** image_path VARCHAR(255)，站内相对路径 */
    private String imagePath;

    /** alt_text VARCHAR(120)，可空 */
    private String altText;

    /** sort_order INT，升序展示 */
    private Integer sortOrder;
}
