package org.csu.mycartoonstore.catalog.dto;

import org.csu.mycartoonstore.catalog.entity.Category;

import java.util.List;

/**
 * 作品页头部：作品本身、它的品类列表、在售商品数。
 */
public record WorkDetail(Category work, List<Category> categories, int productCount) {
}