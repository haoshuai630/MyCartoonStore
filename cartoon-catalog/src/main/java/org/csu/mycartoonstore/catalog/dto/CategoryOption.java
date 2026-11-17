package org.csu.mycartoonstore.catalog.dto; // 商品表单里的分类下拉项。

import lombok.Getter; // 生成 getter。

// M07-05：商品只能挂二级分类，下拉里显示「作品 / 品类」，
// 让管理员一眼看出归属，不必记编码。
@Getter // 只读。
public class CategoryOption {

    /** 二级分类 id：提交给商品表单 */
    private final Long id; // 商品的 category_id。

    /** 显示文本：作品名 / 品类名 */
    private final String label; // 已拼好，模板直接输出。

    /** 组装一个选项。 */
    public CategoryOption(Long id, String label) {
        this.id = id; // 分类 id。
        this.label = label; // 显示文本。
    }
}
