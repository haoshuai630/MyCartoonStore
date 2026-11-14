package org.csu.mypetstore.dto;

import org.csu.mypetstore.entity.User;

/**
 * 用户读取结果：只暴露展示需要的五个字段，不含 password_hash。
 * record 的组件就是这五个，Java 层再也不会携带密码散列。
 */
public record UserReadResult(Long id, String username, String displayName, String role, String status) {

    /** 由实体转换为展示结果，字段一一对应 */
    public static UserReadResult from(User user) {
        return new UserReadResult(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus());
    }
}
