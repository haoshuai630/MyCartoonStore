package org.csu.mypetstore.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * store_user 表实体：教学账号（登录身份 + 角色）。
 * 只显式声明表名和主键，其余下划线列与驼峰属性的对应交给 MyBatis-Plus 的驼峰互转规则。
 */
@Getter
@Setter
@TableName("store_user")
public class User {

    /** id BIGINT AUTO_INCREMENT，主键由数据库生成并回填到该 Long 属性 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** username VARCHAR(50) NOT NULL，登录名，不区分大小写唯一 */
    private String username;

    /** password_hash VARCHAR(255) NOT NULL，BCrypt 散列，不保存明文 */
    private String passwordHash;

    /** display_name VARCHAR(50) NOT NULL */
    private String displayName;

    /** role VARCHAR(16) NOT NULL：CUSTOMER 或 ADMIN */
    private String role;

    /** status VARCHAR(16) NOT NULL DEFAULT 'ENABLED'：ENABLED / DISABLED */
    private String status;

    /** email VARCHAR(100) 可空 */
    private String email;

    /** phone VARCHAR(30) 可空 */
    private String phone;

    /** created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP，由数据库维护，插入时留 null */
    private LocalDateTime createdAt;

    /** updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP，由数据库维护 */
    private LocalDateTime updatedAt;
}
