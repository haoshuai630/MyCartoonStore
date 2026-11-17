package org.csu.mycartoonstore.catalog.dto; // 分类表单：新增与编辑共用一份提交结构。

import lombok.Getter; // 生成 getter。
import lombok.Setter; // 生成 setter：控制器按表单字段回填。

// M07-03：依据 docs/01 FR-A-02、docs/04 §3.2。
// 新增时要 code / parentId / name / sortOrder；编辑时只有 name / sortOrder 生效（code 与父级不可变）。
// 页面回显用同一对象：编辑页把当前值填进来，但服务端不信任提交回来的 code / parentId。
@Getter // 只读访问。
@Setter // 表单绑定需要写。
public class CategoryForm {

    /** 分类 id：编辑时有值，新增时为空 */
    private Long id; // 由路径参数设置，不来自表单。

    /** 编码：新增必填，编辑时只读展示 */
    private String code; // 1–32 位大写字母、数字与连字符。

    /** 名称：1–50 字符，同级唯一 */
    private String name; // 去首尾空白后入库。

    /** 父分类 id：二级分类必填，必须指向一级分类 */
    private Long parentId; // 一级分类为空。

    /** 排序：非负整数，越小越靠前 */
    private Integer sortOrder; // 空按 0。
}
