package org.csu.mycartoonstore.catalog.dto; // 分类列表的一行：树形展示与删除按钮状态都靠它。

import lombok.Getter; // 生成 getter，模板直接取属性。

// M07-03：依据 docs/05 §4.9「分类列表：两级缩进树；一级行显示其下二级数量；
// 删除按钮在有引用时禁用并说明原因」。
// 一行 = 一个分类 + 它的两个计数；父名单独给出，模板不必再查一次库。
@Getter // 只读视图对象。
public class CategoryRow {

    /** 分类 id */
    private final Long id; // 编辑与删除按它定位。

    /** 编码 */
    private final String code; // 创建后不可改，页面上只展示。

    /** 名称 */
    private final String name; // 同级唯一。

    /** 父分类 id：一级为空 */
    private final Long parentId; // 模板用来判断层级。

    /** 父分类名称：一级为空 */
    private final String parentName; // 二级行显示「属于某作品」。

    /** 排序值 */
    private final int sortOrder; // 越小越靠前。

    /** 层级：1 = 作品，2 = 品类 */
    private final int level; // 模板据此缩进。

    /** 直接子分类数量 */
    private final long childCount; // 一级行显示「N 个品类」。

    /** 直接挂在本分类上的商品数量（含下架） */
    private final long productCount; // 删除按钮据此禁用。

    /** 组装一行：全部字段由 Service 一次查询后算出。 */
    public CategoryRow(Long id, String code, String name, Long parentId, String parentName,
                       int sortOrder, int level, long childCount, long productCount) {
        this.id = id; // 主键。
        this.code = code; // 编码。
        this.name = name; // 名称。
        this.parentId = parentId; // 父 id。
        this.parentName = parentName; // 父名。
        this.sortOrder = sortOrder; // 排序。
        this.level = level; // 层级。
        this.childCount = childCount; // 子分类数。
        this.productCount = productCount; // 商品数。
    }

    /** 能否删除：没有子分类、也没有商品引用才允许 */
    public boolean isDeletable() {
        return childCount == 0 && productCount == 0; // 任一不为 0 都要拒绝。
    }

    /** 删除按钮的禁用原因；可删时为空串 */
    public String getDeleteHint() {
        if (childCount > 0) { // 先看子分类：一级分类大多卡在这里。
            return "该分类下还有 " + childCount + " 个品类"; // 说明具体数量。
        }
        if (productCount > 0) { // 再看商品引用：含下架商品。
            return "已有 " + productCount + " 件商品引用"; // 说明具体数量。
        }
        return ""; // 可删。
    }
}
