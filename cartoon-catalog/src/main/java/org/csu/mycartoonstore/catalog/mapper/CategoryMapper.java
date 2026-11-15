package org.csu.mycartoonstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.mycartoonstore.catalog.entity.Category;

/**
 * 分类 Mapper。两级树的层级校验在 Service，这里只做数据访问。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
