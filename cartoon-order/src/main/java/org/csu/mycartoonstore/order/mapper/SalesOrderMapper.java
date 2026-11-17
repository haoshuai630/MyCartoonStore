package org.csu.mycartoonstore.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.csu.mycartoonstore.order.entity.SalesOrder;

/**
 * 订单 Mapper。
 * <p>行锁用于支付/取消/发货：同一订单只允许一种状态迁移成功。</p>
 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    @Select("SELECT * FROM sales_order WHERE order_no = #{orderNo} FOR UPDATE")
    SalesOrder lockByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM sales_order WHERE order_no = #{orderNo}")
    SalesOrder findByOrderNo(@Param("orderNo") String orderNo);

    /** 订单号查重，生成订单号时用 */
    @Select("SELECT COUNT(*) FROM sales_order WHERE order_no = #{orderNo}")
    int countByOrderNo(@Param("orderNo") String orderNo);

    /** 支付凭证查重，生成 payment_no 时用 */
    @Select("SELECT COUNT(*) FROM sales_order WHERE payment_no = #{paymentNo}")
    int countByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 只改收货三列（M10-03 后台修改订单）。
     *
     * <p>金额、明细、状态、支付与时间列都不在 SET 里：后台能改的只有收货信息。</p>
     */
    @Update("UPDATE sales_order SET receiver_name = #{receiverName}, receiver_phone = #{receiverPhone}, "
            + "receiver_address = #{receiverAddress} WHERE id = #{id}")
    int updateReceiver(@Param("id") long id,
                       @Param("receiverName") String receiverName,
                       @Param("receiverPhone") String receiverPhone,
                       @Param("receiverAddress") String receiverAddress);
}
