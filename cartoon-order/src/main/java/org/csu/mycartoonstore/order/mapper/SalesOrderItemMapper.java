package org.csu.mycartoonstore.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.mycartoonstore.order.entity.SalesOrderItem;

/**
 * 订单明细 Mapper。按 order_id 查询用 QueryWrapper 即可。
 */
@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {
}
