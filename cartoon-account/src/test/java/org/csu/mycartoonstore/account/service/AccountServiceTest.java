package org.csu.mycartoonstore.account.service;

import org.csu.mycartoonstore.account.entity.StoreUser;
import org.csu.mycartoonstore.account.mapper.StoreUserMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 账号服务测试：密码校验、状态判断、注册校验。
 *
 * <p>用手写 Mock 代替 inline mock maker，规避 JDK 27 下的自附加问题；
 * 密码编码器用真实的 BCrypt，保证散列与校验确实能对上。</p>
 */
class AccountServiceTest {

    private StoreUserMapper userMapper;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AccountService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(StoreUserMapper.class);
        service = new AccountService(userMapper, encoder);
    }

    @Test
    @DisplayName("用户端：启用 CUSTOMER 密码正确 → 登录成功")
    void customerLoginOk() {
        when(userMapper.selectOne(any())).thenReturn(user("alice", "Cartoon123!", "CUSTOMER", "ENABLED"));

        StoreUser result = service.authenticateCustomer("alice", "Cartoon123!");

        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("用户端：密码错误 → 401，统一文案")
    void customerWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(user("alice", "Cartoon123!", "CUSTOMER", "ENABLED"));

        assertThatThrownBy(() -> service.authenticateCustomer("alice", "wrong-pass"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("用户端：账号不存在 → 与密码错误同码同文案")
    void customerUnknownUser() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.authenticateCustomer("nobody", "whatever"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号或密码错误，或账号不可用");
    }

    @Test
    @DisplayName("用户端：禁用账号 → 与密码错误同码同文案，不透露已停用")
    void customerDisabledRejected() {
        when(userMapper.selectOne(any())).thenReturn(user("carol", "Cartoon123!", "CUSTOMER", "DISABLED"));

        assertThatThrownBy(() -> service.authenticateCustomer("carol", "Cartoon123!"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("用户端：管理员不能登录（AC-05）")
    void adminCannotLogInAsCustomer() {
        when(userMapper.selectOne(any())).thenReturn(user("admin", "Cartoon123!", "ADMIN", "ENABLED"));

        assertThatThrownBy(() -> service.authenticateCustomer("admin", "Cartoon123!"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号或密码错误，或账号不可用");
    }

    @Test
    @DisplayName("管理端：启用 ADMIN 密码正确 → 登录成功")
    void adminLoginOk() {
        when(userMapper.selectOne(any())).thenReturn(user("admin", "Cartoon123!", "ADMIN", "ENABLED"));

        StoreUser result = service.authenticateAdmin("admin", "Cartoon123!");

        assertThat(result.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("管理端：CUSTOMER 不能登录（FR-A-01）")
    void customerCannotLogInAsAdmin() {
        when(userMapper.selectOne(any())).thenReturn(user("alice", "Cartoon123!", "CUSTOMER", "ENABLED"));

        assertThatThrownBy(() -> service.authenticateAdmin("alice", "Cartoon123!"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("管理端：禁用 ADMIN → 401，统一文案")
    void disabledAdminRejected() {
        when(userMapper.selectOne(any())).thenReturn(user("admin", "Cartoon123!", "ADMIN", "DISABLED"));

        assertThatThrownBy(() -> service.authenticateAdmin("admin", "Cartoon123!"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号或密码错误，或账号不可用");
    }

    @Test
    @DisplayName("注册成功：入库的是 BCrypt 散列，不是明文")
    void registerStoresHash() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        StoreUser created = service.register("newbie", "Password123", "新用户");

        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(created.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(encoder.matches("Password123", created.getPasswordHash())).isTrue();
        assertThat(created.getRole()).isEqualTo("CUSTOMER");
        assertThat(created.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("用户名重复：409，不覆盖已有账号")
    void registerDuplicate() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.register("alice", "Password123", "爱丽丝"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("用户名格式非法：400")
    void registerBadUsername() {
        assertThatThrownBy(() -> service.register("a", "Password123", "短名"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("密码太短：400")
    void registerShortPassword() {
        assertThatThrownBy(() -> service.register("newbie", "123", "新用户"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少");
    }

    /** 造一个用户：密码用真实 BCrypt 编码，保证 matches 能通过；角色与状态由用例指定 */
    private StoreUser user(String username, String rawPassword, String role, String status) {
        StoreUser u = new StoreUser();
        u.setId(1L);
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setDisplayName(username);
        u.setRole(role);
        u.setStatus(status);
        return u;
    }
}