package org.csu.mycartoonstore.catalog.dto;

/**
 * 作品墙的一张卡片：一级分类 + 它下面的品类数与在售商品数。
 *
 * <p>用 SQL 一次算出来，避免模板里为每部作品再查两次库。</p>
 */
public record WorkSummary(long id, String code, String name, int categoryCount, int productCount) {
}