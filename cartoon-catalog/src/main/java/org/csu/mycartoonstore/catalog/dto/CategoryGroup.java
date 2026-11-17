package org.csu.mycartoonstore.catalog.dto; // 分类列表的一组：一个作品 + 它的品类。

import lombok.Getter; // 生成 getter。

import java.util.List; // 品类列表。

// M07-10：分类页改成"按作品折叠"后，模板需要成组的数据，
// 而不是一串靠 level 判断的扁平行。
@Getter // 只读视图对象。
public class CategoryGroup {

    /** 作品（一级分类）那一行 */
    private final CategoryRow work; // 名称、编码、排序、品类数、商品数。

    /** 该作品下的品类（二级分类） */
    private final List<CategoryRow> children; // 展开后按排序展示。

    /** 组装一组。 */
    public CategoryGroup(CategoryRow work, List<CategoryRow> children) {
        this.work = work; // 一级行。
        this.children = children; // 二级行。
    }

    /** 品类数量：摘要行直接用 */
    public int getChildCount() {
        return children.size(); // 与 work.childCount 一致，避免模板再算。
    }
}
