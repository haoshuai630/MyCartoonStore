package org.csu.mycartoonstore.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.mycartoonstore.account.entity.StoreUser;

/**
 * 账号 Mapper。
 * <p>按用户名查询用于登录；行锁用于购物车与下单前的"先锁用户"串行化。</p>
 */
@Mapper
public interface StoreUserMapper extends BaseMapper<StoreUser> {

    @Select("SELECT * FROM store_user WHERE username = #{username}")
    StoreUser findByUsername(@Param("username") String username);

    /** 锁用户行：同用户的购物车写操作与下单必须先拿这把锁 */
    @Select("SELECT * FROM store_user WHERE id = #{id} FOR UPDATE")
    StoreUser lockById(@Param("id") Long id);
}
