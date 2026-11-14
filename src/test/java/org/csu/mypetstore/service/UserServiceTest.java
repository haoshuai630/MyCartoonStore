package org.csu.mypetstore.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.csu.mypetstore.dto.UserReadResult;
import org.csu.mypetstore.entity.User;
import org.csu.mypetstore.mapper.UserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试：Mockito 模拟 UserMapper，手工构造真实 UserService，
 * 不启动 Spring 容器、不建 Web 服务、不连数据库。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    /**
     * Lambda 条件构造器要把 User::getUsername 解析成列名，依赖 MyBatis-Plus 的实体列缓存。
     * 这里只手工初始化 User 的 TableInfo，不为此启动完整应用。
     */
    @BeforeAll
    static void initUserTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), User.class);
    }

    @BeforeEach
    void createServiceWithMockedMapper() {
        userService = new UserService(userMapper);
    }

    @Test
    @DisplayName("查到用户时转换为 UserReadResult，且查询不读取密码散列列")
    void mapsHitToReadResultWithoutPasswordColumn() {
        User stored = fullRowFromDatabase();
        AtomicReference<LambdaQueryWrapper<User>> issuedQuery = new AtomicReference<>();
        when(userMapper.selectOne(any())).thenAnswer(invocation -> {
            issuedQuery.set(invocation.getArgument(0));
            return stored;
        });

        Optional<UserReadResult> result = userService.findByUsername("alice");

        assertThat(result).contains(new UserReadResult(1L, "alice", "林同学", "CUSTOMER", "ENABLED"));
        // 实体里带着密码散列，转换后必须丢掉
        assertThat(result.orElseThrow().toString()).doesNotContain("$2a$10$").doesNotContain("passwordHash");

        String selectedColumns = issuedQuery.get().getSqlSelect();
        assertThat(selectedColumns).contains("display_name").doesNotContain("password_hash");
        assertThat(issuedQuery.get().getTargetSql()).contains("username =");
        assertThat(issuedQuery.get().getParamNameValuePairs().containsValue("alice")).isTrue();
    }

    @Test
    @DisplayName("查不到用户时返回空 Optional，而不是 null")
    void returnsEmptyOptionalWhenNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThat(userService.findByUsername("not-exists")).isEmpty();

        verify(userMapper).selectOne(any());
    }

    @Test
    @DisplayName("Mapper 抛数据访问异常时原样继续抛出，不转成空结果")
    void propagatesDataAccessExceptionUnchanged() {
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("db down");
        when(userMapper.selectOne(any())).thenThrow(boom);

        Throwable thrown = catchThrowable(() -> userService.findByUsername("alice"));

        // 同一个异常实例：既没有被捕获，也没有被包装或降级成 Optional.empty()
        assertThat(thrown).isSameAs(boom);
    }

    /** 模拟数据库返回的完整一行，含密码散列和时间戳 */
    private static User fullRowFromDatabase() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("$2a$10$L2rlMCJruRZDIL2pXB.BBO");
        user.setDisplayName("林同学");
        user.setRole("CUSTOMER");
        user.setStatus("ENABLED");
        user.setEmail("alice@example.test");
        user.setPhone(null);
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return user;
    }
}
