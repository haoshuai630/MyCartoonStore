package org.csu.mypetstore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.csu.mypetstore.dto.UserReadResult;
import org.csu.mypetstore.entity.User;
import org.csu.mypetstore.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 按登录名查询展示信息。只取五个展示字段，不读 password_hash；
     * 查不到返回空 Optional，数据访问异常不做捕获、继续抛出。
     */
    public Optional<UserReadResult> findByUsername(String username) {
        LambdaQueryWrapper<User> query = Wrappers.<User>lambdaQuery()
                .select(User::getId, User::getUsername, User::getDisplayName, User::getRole, User::getStatus)
                .eq(User::getUsername, username);
        User user = userMapper.selectOne(query);
        return Optional.ofNullable(user).map(UserReadResult::from);
    }
}
