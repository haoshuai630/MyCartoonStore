package org.csu.mycartoonstore.catalog.dto; // 分类商品计数：列表页一次查出，避免每行再查一次。

import lombok.Getter; // 生成 getter。
import lombok.Setter; // MyBatis 需要写字段。

// M07-03：SQL 里按 category_id 分组计数，这里只承载两列。
// 与 CategoryRow 分开：一个是查询结果，一个是视图对象。
@Getter // 读取。
@Setter // 结果映射。
public class CategoryUsage {

    /** 分类 id */
    private Long categoryId; // 分组键。

    /** 该分类下的商品数量（含 DRAFT） */
    private long productCount; // 删除校验与列表展示共用。
}
