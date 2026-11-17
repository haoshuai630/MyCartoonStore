package org.csu.mycartoonstore.catalog.dto; // 后台商品列表的查询条件：与前台列表分开，因为这里要看下架商品。

import lombok.Getter; // 生成 getter。
import lombok.Setter; // 生成 setter。

// M07-05：依据 docs/01 FR-A-03、docs/04 §3.3。
// 与 ProductQuery 的差别：
//   1. 状态可筛选（ALL / DRAFT / ON_SALE），前台永远只看 ON_SALE；
//   2. 排序固定 id 倒序（最近录入在前），不开放给页面选择；
//   3. 分类既可以给一级（取子树），也可以给二级（精确）。
@Getter // 读取。
@Setter // 组装。
public class AdminProductQuery {

    /** 一级或二级分类 id；为空表示全部 */
    private Long categoryId; // 一级时取子树。

    /** 商品名称关键字，已去首尾空白并转义 LIKE 通配符 */
    private String keyword; // 为空表示不搜索。

    /** ALL / DRAFT / ON_SALE */
    private String status; // 非法值在 Service 转 400。

    /** 当前页数量 */
    private int limit; // 由 PageQuery 归一。

    /** 偏移量 */
    private long offset; // (page-1)*pageSize。
}
