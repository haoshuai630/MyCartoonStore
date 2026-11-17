package org.csu.mycartoonstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.csu.mycartoonstore.catalog.dto.ProductQuery;
import org.csu.mycartoonstore.catalog.entity.Product;

import java.util.List;

/**
 * 商品 Mapper。
 * <p>分页查询、首页两栏、行锁在 XML；扣库存用注解，保证只有一条 UPDATE。</p>
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** 列表分页：分类 + 类型 + 关键字 + 白名单排序 */
    List<Product> selectPage(@Param("q") ProductQuery query);

    /** 与列表同条件的总数 */
    long countPage(@Param("q") ProductQuery query);

    /** 首页「新品上架」：上架商品按创建时间倒序 */
    List<Product> selectNewest(@Param("limit") int limit);

    /** 首页「精选推荐」：上架商品按 sort_order 升序 */
    List<Product> selectFeatured(@Param("limit") int limit);

    /** 按 id 升序加行锁，供下单时统一顺序锁定商品，避免死锁 */
    List<Product> lockByIds(@Param("ids") List<Long> ids);

    /**
     * 乐观扣库存：库存足够才扣，同时 version + 1。
     *
     * @return 影响行数；0 表示库存不足或商品不存在
     */
    @Update("UPDATE product SET stock = stock - #{qty}, version = version + 1 "
            + "WHERE id = #{id} AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消订单时恢复库存并递增版本：与 deductStock 成对，保证每次取消只加一次 */
    @Update("UPDATE product SET stock = stock + #{qty}, version = version + 1 WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
