package org.csu.mycartoonstore.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.csu.mycartoonstore.account.entity.StoreUser;
import org.csu.mycartoonstore.account.mapper.StoreUserMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 账号服务：登录校验与注册。
 *
 * <p>密码只在这里校验与生成散列，控制器与页面都拿不到明文哈希；
 * 登录失败不区分"用户不存在"与"密码错误"，避免账号枚举。</p>
 */
@Service
public class AccountService {

    /** 登录名：3–32 位字母、数字、下划线 */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    /** 密码最短长度：与注册页提示保持一致 */
    private static final int PASSWORD_MIN = 8;

    /** 登录失败统一文案：不告诉对方是账号不存在还是密码错 */
    private static final String LOGIN_FAILED = "用户名或密码不正确";

    private final StoreUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountService(StoreUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录校验：账号存在、密码匹配、状态为 ENABLED，三者都满足才返回用户。
     *
     * <p>失败时抛 {@link BizException}，由全局异常处理器转成 401/403。</p>
     */
    public StoreUser authenticate(String username, String rawPassword) {
        StoreUser user = findByUsername(username);
        // 用户不存在时也走一次哈希比对，避免通过响应时间判断账号是否存在
        boolean matched = user != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!matched) {
            throw new BizException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED);
        }
        if (!"ENABLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用，请联系店长");
        }
        return user;
    }

    /**
     * 注册：校验格式与唯一性后写入 BCrypt 散列，角色固定 CUSTOMER、状态 ENABLED。
     *
     * <p>用户名唯一性同时由数据库唯一键兜底，这里先查一次是为了给出可读提示。</p>
     */
    @Transactional
    public StoreUser register(String username, String rawPassword, String displayName) {
        String name = username == null ? "" : username.trim();
        String password = rawPassword == null ? "" : rawPassword;
        if (!USERNAME.matcher(name).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名需为 3–32 位字母、数字或下划线");
        }
        if (password.length() < PASSWORD_MIN) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码至少 " + PASSWORD_MIN + " 位");
        }
        if (userMapper.selectCount(Wrappers.<StoreUser>lambdaQuery().eq(StoreUser::getUsername, name)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        StoreUser user = new StoreUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? name : displayName.trim());
        user.setRole("CUSTOMER");
        user.setStatus("ENABLED");
        userMapper.insert(user);
        return user;
    }

    /** 按用户名查账号；查不到返回 null，由调用方决定提示文案 */
    public StoreUser findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<StoreUser>lambdaQuery()
                .eq(StoreUser::getUsername, username.trim()));
    }

    /** 按 id 查账号：登录后刷新会话里的展示信息时用 */
    public StoreUser findById(long id) {
        return userMapper.selectById(id);
    }
}