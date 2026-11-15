package org.csu.mycartoonstore.catalog.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 商品列表查询条件。由 Controller 组装，Service 校验后传给 Mapper。
 * <p>排序值与商品类型只允许白名单值，Service 侧校验，SQL 侧用 choose 映射到固定列。</p>
 */
@Getter
@Setter
public class ProductQuery {

    /** 一级或二级分类 id；为空表示全部 */
    private Long categoryId;

    /** ALL / COMIC / MERCH */
    private String productType;

    /** 商品名称关键字，已去首尾空白；为空表示不搜索 */
    private String keyword;

    /** DEFAULT / PRICE_ASC / PRICE_DESC / NEWEST */
    private String sort;

    /** 当前页数量 */
    private int limit;

    /** 偏移量 */
    private long offset;
}
