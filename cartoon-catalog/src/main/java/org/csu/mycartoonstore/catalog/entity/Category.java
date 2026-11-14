package org.csu.mycartoonstore.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * category：固定两级分类。parent_id 为空表示一级，二级必须指向一级。
 * <p>注意：parent_key 是数据库生成列（COALESCE(parent_id,0) STORED），此实体**不映射**，
 * 插入与更新都不会写它。</p>
 */
@Getter
@Setter
@TableName("category")
public class Category {

    /** id BIGINT AUTO_INCREMENT */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** code VARCHAR(32)，全局唯一，创建后不改；二级以 -COMIC / -MERCH 结尾 */
    private String code;

    /** parent_id，一级为空；二级指向一级 */
    private Long parentId;

    /** name VARCHAR(50)，同一父下唯一 */
    private String name;

    /** sort_order INT，越小越靠前 */
    private Integer sortOrder;

    /** created_at */
    private LocalDateTime createdAt;

    /** updated_at */
    private LocalDateTime updatedAt;
}
