package org.csu.mycartoonstore.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

    /**
     * 资料保存：只更新三列，不改用户名、角色、状态与密码。
     *
     * <p>用定向 UPDATE 而不是整行回写，避免与并发改密 / 管理端启停互相覆盖（FR-U-03）。</p>
     */
    @Update("UPDATE store_user SET display_name = #{displayName}, email = #{email}, phone = #{phone} "
            + "WHERE id = #{id}")
    int updateProfile(@Param("id") long id,
                      @Param("displayName") String displayName,
                      @Param("email") String email,
                      @Param("phone") String phone);

    /**
     * 改密：只写 password_hash 一列。
     *
     * <p>定向 UPDATE 让改密与资料保存、管理端启停互不覆盖；散列由 Service 生成。</p>
     */
    @Update("UPDATE store_user SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    /**
     * 启停：只写 status 一列。
     *
     * <p>定向 UPDATE 不覆盖资料与密码；角色判断在 Service，SQL 只按 id 写。</p>
     */
    @Update("UPDATE store_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
