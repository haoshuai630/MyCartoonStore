package org.csu.mycartoonstore.catalog.dto; // 后台商品列表的一行：商品字段 + 分类名称。

import lombok.Getter; // 生成 getter。
import lombok.Setter; // MyBatis 结果映射需要写。

import java.math.BigDecimal; // 价格。

// M07-05：后台列表要显示"分类名称"，而 Product 实体只有 category_id。
// 用一次 JOIN 把名称带出来，避免页面里逐行再查分类。
@Getter // 读取。
@Setter // 结果映射。
public class AdminProductRow {

    /** 商品 id（页面定位用） */
    private Long id; // 编辑入口按它跳。

    /** SKU：创建后不可改 */
    private String sku; // 只展示。

    /** 商品名 */
    private String name; // 可编辑。

    /** 二级分类名称 */
    private String categoryName; // 来自 JOIN。

    /** 价格 */
    private BigDecimal price; // 展示两位小数。

    /** 可销售库存 */
    private int stock; // 只展示，补货走 M07-07。

    /** DRAFT / ON_SALE */
    private String status; // 页面显示「上架 / 下架」。

    /** 排序值 */
    private int sortOrder; // 越小越靠前。

    /** 乐观锁版本：编辑表单要带上 */
    private long version; // 冲突检测用。
}
