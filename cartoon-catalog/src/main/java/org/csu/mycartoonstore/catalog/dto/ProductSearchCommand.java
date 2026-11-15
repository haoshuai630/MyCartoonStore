package org.csu.mycartoonstore.catalog.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 列表页原始请求参数（都是字符串，由 Service 归一与校验）。
 *
 * <p>不让 Controller 直接解析，是为了把"非法参数 → 400"集中在一处，
 * 也方便单测直接构造各种脏输入。</p>
 */
@Getter
@Setter
public class ProductSearchCommand {

    /** 分类 id，可为空 */
    private String categoryId;

    /** 作品（一级分类）id；作品页用它限定范围 */
    private String workId;

    /** ALL / COMIC / MERCH，可为空 */
    private String productType;

    /** DEFAULT / PRICE_ASC / PRICE_DESC / NEWEST，可为空 */
    private String sort;

    /** 名称关键字，可为空 */
    private String keyword;

    /** 页码，可为空 */
    private String page;

    /** 每页数量，可为空 */
    private String pageSize;
}
