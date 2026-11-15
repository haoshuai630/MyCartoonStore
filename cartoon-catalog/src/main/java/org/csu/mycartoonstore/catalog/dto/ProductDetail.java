package org.csu.mycartoonstore.catalog.dto;

import org.csu.mycartoonstore.catalog.entity.Category;
import org.csu.mycartoonstore.catalog.entity.Product;
import org.csu.mycartoonstore.catalog.entity.ProductImage;

import java.util.List;

/**
 * 商品详情视图：商品本身 + 两级分类 + 相册。
 *
 * @param product      商品
 * @param topCategory  一级分类（作品）
 * @param leafCategory 二级分类（单行本 / 官方周边）
 * @param images       相册，按 sort_order、id 升序；可能为空
 */
public record ProductDetail(Product product,
                            Category topCategory,
                            Category leafCategory,
                            List<ProductImage> images) {

    /** 没有相册时，详情页回退封面 */
    public boolean hasGallery() {
        return images != null && !images.isEmpty();
    }
}
