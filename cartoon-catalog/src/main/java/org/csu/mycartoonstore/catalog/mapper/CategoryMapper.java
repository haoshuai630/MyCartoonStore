package org.csu.mycartoonstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.csu.mycartoonstore.catalog.dto.WorkSummary;
import org.csu.mycartoonstore.catalog.entity.Category;

import java.util.List;

/**
 * 分类 Mapper。两级树的层级校验在 Service，这里只做数据访问。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 作品墙数据：每个一级分类带上品类数与在售商品数。
     *
     * <p>一次查询算完，避免页面里为每部作品再查两次库。</p>
     */
    @Select("""
            SELECT p.id AS id, p.code AS code, p.name AS name,
                   (SELECT COUNT(*) FROM category c WHERE c.parent_id = p.id) AS category_count,
                   (SELECT COUNT(*) FROM product pr
                      JOIN category c2 ON pr.category_id = c2.id
                     WHERE c2.parent_id = p.id AND pr.status = 'ON_SALE') AS product_count
              FROM category p
             WHERE p.parent_id IS NULL
             ORDER BY p.sort_order, p.id
            """)
    List<WorkSummary> selectWorkSummaries();

    /** 某部作品的在售商品数（详情页横幅用） */
    @Select("""
            SELECT COUNT(*) FROM product pr
              JOIN category c2 ON pr.category_id = c2.id
             WHERE c2.parent_id = #{workId} AND pr.status = 'ON_SALE'
            """)
    int countOnSaleByWork(long workId);
}