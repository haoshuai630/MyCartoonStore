package org.csu.mycartoonstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.mycartoonstore.catalog.entity.ProductImage;

/**
 * 商品相册 Mapper。详情页按 sort_order、id 升序查询，用 QueryWrapper 即可。
 */
@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {
}
