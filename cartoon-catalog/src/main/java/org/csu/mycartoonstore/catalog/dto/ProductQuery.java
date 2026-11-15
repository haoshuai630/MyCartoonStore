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

    /** 作品（一级分类）id；指定时只查该作品下的全部品类 */
    private Long workId;

    /**
     * 商品类型过滤：ALL 不过滤；COMIC 只查单行本（分类 code 以 -COMIC 结尾）；
     * MERCH 查其余全部周边品类。取值由 CatalogService 的白名单校验，非法值返回 400。
     */
    private String productType;

    /** 商品名称关键字，已去首尾空白；为空表示不搜索 */
    private String keyword;

    /**
     * 排序方式：DEFAULT 按人工排序 sort_order；PRICE_ASC / PRICE_DESC 按价格；
     * NEWEST 按上架时间倒序。SQL 侧用 choose 映射到固定列，非法值返回 400。
     */
    private String sort;

    /** 当前页数量 */
    private int limit;

    /** 偏移量 */
    private long offset;
}
