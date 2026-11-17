package org.csu.mycartoonstore.catalog.dto; // 后台商品表单：新增与编辑共用。

import lombok.Getter; // 生成 getter。
import lombok.Setter; // 表单绑定需要写。

// M07-05：依据 docs/01 FR-A-03、docs/04 §3.3。
// price / stock 用字符串接收：页面填错时能给出"必须是数字/两位小数"的具体文案，
// 而不是让 Spring 的类型转换抛 BindException。解析与范围校验都在 Service。
@Getter // 读取。
@Setter // 绑定。
public class ProductForm {

    /** 商品 id：编辑时有值 */
    private Long id; // 由路径参数设置。

    /** SKU：新增必填、创建后不可改 */
    private String sku; // 1–40 位大写字母、数字与连字符。

    /** 商品名：1–120 */
    private String name; // 去首尾空白。

    /** 所属二级分类 */
    private Long categoryId; // 必须指向二级分类。

    /** 价格（原始字符串） */
    private String price; // >0，最多两位小数。

    /** 初始库存（原始字符串）：只在新增时生效 */
    private String stock; // 编辑页不出现该输入。

    /** 简介：可空，≤500 */
    private String summary; // 列表与详情用。

    /** 描述：可空纯文本 */
    private String description; // 模板转义输出。

    /** 封面站内路径：必填，≤255 */
    private String coverImage; // 例如 images/products/STARLINE-C01/cover.svg。

    /** 排序：非负，缺省 0 */
    private Integer sortOrder; // 越小越靠前。

    /** 乐观锁版本：编辑时由隐藏域带回 */
    private Long version; // 保存时校验。
}
