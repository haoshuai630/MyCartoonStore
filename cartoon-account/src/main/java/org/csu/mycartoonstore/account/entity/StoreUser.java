package org.csu.mycartoonstore.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * store_user：教学账号（登录身份 + 角色）。
 * <p>只映射列，不写业务方法；角色与状态的合法性由 Service 与数据库 CHECK 共同保证。</p>
 */
@Getter
@Setter
@TableName("store_user")
public class StoreUser {

    /** id BIGINT AUTO_INCREMENT，主键由数据库生成并回填 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** username VARCHAR(50) NOT NULL，登录名，创建后不改 */
    private String username;

    /** password_hash VARCHAR(255) NOT NULL，BCrypt 散列，禁止明文 */
    private String passwordHash;

    /** display_name VARCHAR(50) NOT NULL */
    private String displayName;

    /** role VARCHAR(16)：CUSTOMER / ADMIN */
    private String role;

    /** status VARCHAR(16)：ENABLED / DISABLED */
    private String status;

    /** email VARCHAR(100)，可空 */
    private String email;

    /** phone VARCHAR(30)，可空 */
    private String phone;

    /** created_at，数据库默认值维护，插入时留 null */
    private LocalDateTime createdAt;

    /** updated_at，数据库 ON UPDATE 维护 */
    private LocalDateTime updatedAt;
}
