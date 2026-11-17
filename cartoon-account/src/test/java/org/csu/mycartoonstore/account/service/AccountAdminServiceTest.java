package org.csu.mycartoonstore.account.service; // 后台顾客管理：列表筛选、分页与启停规则。

import org.csu.mycartoonstore.account.dto.AdminUserRow; // 列表行。
import org.csu.mycartoonstore.account.entity.StoreUser; // 账号实体。
import org.csu.mycartoonstore.account.mapper.StoreUserMapper; // 查询与定向更新。
import org.csu.mycartoonstore.common.exception.BizException; // 业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 错误码。
import org.csu.mycartoonstore.common.page.PageResult; // 分页结果。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.junit.jupiter.api.extension.ExtendWith; // Mockito 扩展。
import org.mockito.Mock; // 协作方。
import org.mockito.junit.jupiter.MockitoExtension; // Mockito 生命周期。

import java.time.LocalDateTime; // 注册时间。
import java.util.ArrayList; // 造批量账号。
import java.util.List; // 列表。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.any; // 任意查询条件。
import static org.mockito.ArgumentMatchers.anyLong; // 任意 id。
import static org.mockito.ArgumentMatchers.anyString; // 任意状态。
import static org.mockito.Mockito.never; // 不该发生的调用。
import static org.mockito.Mockito.verify; // 行为断言。
import static org.mockito.Mockito.when; // 打桩。

// M08-04：依据 docs/01 FR-A-07、docs/06 AC-07（管理端侧）。
// 覆盖：只列 CUSTOMER、id 升序分页、关键字/状态参数校验、启停与幂等、管理员与不存在 404。
// 不覆盖：筛选条件实际下推到 SQL 的效果（AdminUserPageTest 走真实库）。
@ExtendWith(MockitoExtension.class) // 用 Mockito 驱动。
class AccountAdminServiceTest {

    @Mock // 账号 Mapper。
    private StoreUserMapper userMapper;

    /** 被测服务 */
    private AccountAdminService service() {
        return new AccountAdminService(userMapper); // 每次构造新实例。
    }

    @Test // 只列顾客、按 id 升序、映射五列。
    @DisplayName("列表：映射用户名、显示名、状态与注册时间")
    void listMapsRows() {
        when(userMapper.selectList(any())).thenReturn(List.of(
                user(2L, "alice", "爱丽丝", "ENABLED"),
                user(3L, "bob", "鲍勃", "DISABLED")));

        PageResult<AdminUserRow> page = service().list(null, null, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(AdminUserRow::getUsername).containsExactly("alice", "bob");
        assertThat(page.items().get(1).getDisplayName()).isEqualTo("鲍勃");
        assertThat(page.items().get(1).getStatus()).isEqualTo("DISABLED");
        assertThat(page.items().get(0).getCreatedAt()).isNotNull();
    }

    @Test // 内存分页：第 2 页取剩下的 5 行。
    @DisplayName("列表：第 2 页只返回剩余的 5 行")
    void listPaginates() {
        when(userMapper.selectList(any())).thenReturn(users(15));

        PageResult<AdminUserRow> page = service().list(null, null, "2");

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(15);
        assertThat(page.items()).hasSize(5);
        assertThat(page.items().get(0).getUsername()).isEqualTo("user11");
    }

    @Test // 越界页：空结果，不报错。
    @DisplayName("列表：超出末页返回空页")
    void listBeyondLastPage() {
        when(userMapper.selectList(any())).thenReturn(users(3));

        PageResult<AdminUserRow> page = service().list(null, null, "9");

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(3);
    }

    @Test // 关键字超长：400。
    @DisplayName("列表：关键字超过 50 字 → 400")
    void listKeywordTooLong() {
        assertThatThrownBy(() -> service().list("a".repeat(51), null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过 50");
    }

    @Test // 状态不在白名单：400。
    @DisplayName("列表：状态非法 → 400")
    void listInvalidStatus() {
        assertThatThrownBy(() -> service().list(null, "BANNED", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test // 页码非整数：400。
    @DisplayName("列表：页码非法 → 400")
    void listInvalidPage() {
        assertThatThrownBy(() -> service().list(null, null, "abc"))
                .isInstanceOf(BizException.class)
                .hasMessage("页码必须是整数");
    }

    @Test // 禁用：返回 true 并只写 status。
    @DisplayName("禁用顾客：返回已改变，只写状态列")
    void changeStatusDisables() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "alice", "爱丽丝", "ENABLED"));
        when(userMapper.updateStatus(2L, "DISABLED")).thenReturn(1);

        assertThat(service().changeStatus(2L, "DISABLED")).isTrue();
        verify(userMapper).updateStatus(2L, "DISABLED"); // 只写状态，不整行回写。
    }

    @Test // 重复禁用：不写库，返回 false。
    @DisplayName("重复禁用：幂等，不写库")
    void changeStatusRepeat() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "alice", "爱丽丝", "DISABLED"));

        assertThat(service().changeStatus(2L, "DISABLED")).isFalse();
        verify(userMapper, never()).updateStatus(anyLong(), anyString()); // 状态没变就不写。
    }

    @Test // 管理员不能在这里被启停。
    @DisplayName("目标是管理员：404")
    void changeStatusAdminRejected() {
        StoreUser admin = user(1L, "root", "管理员", "ENABLED");
        admin.setRole("ADMIN"); // 与顾客只差角色。
        when(userMapper.selectById(1L)).thenReturn(admin);

        assertThatThrownBy(() -> service().changeStatus(1L, "DISABLED"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(userMapper, never()).updateStatus(anyLong(), anyString()); // 管理员不写库。
    }

    @Test // 不存在：404。
    @DisplayName("目标不存在：404")
    void changeStatusMissing() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service().changeStatus(99L, "ENABLED"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test // 写入状态只能是启用/禁用。
    @DisplayName("启停：状态值非法 → 400")
    void changeStatusInvalid() {
        assertThatThrownBy(() -> service().changeStatus(2L, "BANNED"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    /** 造顾客：id、用户名、显示名、状态 */
    private StoreUser user(long id, String username, String displayName, String status) {
        StoreUser u = new StoreUser();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName(displayName);
        u.setRole("CUSTOMER");
        u.setStatus(status);
        u.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        return u;
    }

    /** 造 n 个顾客：用户名依次为 user1…userN */
    private List<StoreUser> users(int n) {
        List<StoreUser> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            list.add(user(i, "user" + i, "顾客" + i, i % 2 == 0 ? "DISABLED" : "ENABLED"));
        }
        return list;
    }
}
