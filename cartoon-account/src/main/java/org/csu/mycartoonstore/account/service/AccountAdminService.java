package org.csu.mycartoonstore.account.service; // 后台顾客管理：列表筛选、分页与启停。

import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 条件查询。
import org.csu.mycartoonstore.account.dto.AdminUserRow; // 列表行。
import org.csu.mycartoonstore.account.dto.Profile; // 校验后的三项资料。
import org.csu.mycartoonstore.account.dto.ProfileForm; // 复用用户端的资料校验。
import org.csu.mycartoonstore.account.entity.StoreUser; // 账号实体。
import org.csu.mycartoonstore.account.mapper.StoreUserMapper; // 查询与定向更新。
import org.csu.mycartoonstore.common.exception.BizException; // 400/404。
import org.csu.mycartoonstore.common.page.PageQuery; // 分页归一。
import org.csu.mycartoonstore.common.page.PageResult; // 分页结果。
import org.springframework.security.crypto.password.PasswordEncoder; // 重置密码写 BCrypt。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 启停写库。

import java.security.SecureRandom; // 临时密码随机源。
import java.util.List; // 列表。
import java.util.Set; // 状态白名单。

// M08-04：依据 docs/01 FR-A-07、docs/04 §3.5、docs/06 AC-07（管理端侧）。
// 负责：只列 CUSTOMER、按用户名包含匹配与状态筛选、id 升序分页、启用/禁用；
//       M10-02 增补：改三项资料、重置为一次性临时密码。
// 不做：管理员的启停（ADMIN 不出现在列表，按 id 直接访问也 404）、删号、改用户名与角色。
@Service // 业务服务：后台控制器只调它。
public class AccountAdminService {

    /** 列表只列普通顾客：管理员账号不在这里出现（FR-A-07） */
    private static final String ROLE_CUSTOMER = "CUSTOMER"; // 与账号表 role 取值一致。

    /** 启用 */
    private static final String ENABLED = "ENABLED";

    /** 禁用 */
    private static final String DISABLED = "DISABLED";

    /** 筛选状态白名单：ALL 表示不筛选 */
    private static final Set<String> FILTER_STATUSES = Set.of("ALL", ENABLED, DISABLED);

    /** 可写入的状态：只有启用与禁用 */
    private static final Set<String> WRITABLE_STATUSES = Set.of(ENABLED, DISABLED);

    /** 关键字上限：与 username VARCHAR(50) 一致 */
    private static final int KEYWORD_MAX = 50;

    /** 后台列表每页 10 条：与 docs/04 §4 的"其他列表缺省 10"一致 */
    private static final int PAGE_SIZE = 10;

    /** 临时密码长度：12 位足够抵挡暴力猜测，也便于口头转达 */
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final StoreUserMapper userMapper; // 账号查询与启停。
    private final PasswordEncoder passwordEncoder; // 重置密码要写 BCrypt 散列。
    private final SecureRandom random = new SecureRandom(); // 临时密码的随机源。

    public AccountAdminService(StoreUserMapper userMapper, PasswordEncoder passwordEncoder) { // 构造器注入。
        this.userMapper = userMapper; // 保存 Mapper。
        this.passwordEncoder = passwordEncoder; // 保存编码器。
    }

    /**
     * 顾客列表：用户名包含匹配 + 状态筛选，按 id 升序分页。
     *
     * <p>账号总量很小（教学种子 4 个），先在内存里筛选与分页；分页参数仍由 {@link PageQuery} 校验，
     * 越界页返回空结果而不是报错。若将来账号上千，再把筛选与 LIMIT 下推到 SQL。</p>
     */
    public PageResult<AdminUserRow> list(String rawKeyword, String rawStatus, String rawPage) {
        String keyword = parseKeyword(rawKeyword); // 去空白、限长、转义 LIKE。
        String status = parseFilterStatus(rawStatus); // ALL / ENABLED / DISABLED。
        PageQuery query = PageQuery.of(rawPage, null, PAGE_SIZE); // 页码归一，页大小固定 10。
        List<StoreUser> matched = userMapper.selectList(Wrappers.<StoreUser>lambdaQuery()
                .eq(StoreUser::getRole, ROLE_CUSTOMER) // 只列顾客。
                .eq(!"ALL".equals(status), StoreUser::getStatus, status) // ALL 时不加条件。
                .like(keyword != null, StoreUser::getUsername, keyword) // 已转义，按包含匹配。
                .orderByAsc(StoreUser::getId)); // FR-A-07：默认按 ID 升序。
        int from = (int) Math.min(query.offset(), matched.size()); // 越界页：从头截空。
        int to = Math.min(from + query.pageSize(), matched.size()); // 本页右开区间。
        List<AdminUserRow> rows = matched.subList(from, to).stream().map(this::toRow).toList(); // 只映射需要的五列。
        return PageResult.of(rows, matched.size(), query); // 总数用于分页信息。
    }

    /**
     * 启用 / 禁用顾客：只处理 CUSTOMER，重复设置同一状态返回 false。
     *
     * @return true = 本次状态真的变了；false = 之前已是该状态
     */
    @Transactional // 查角色与写状态同一事务。
    public boolean changeStatus(long targetId, String rawStatus) {
        String status = parseWritableStatus(rawStatus); // 只接受 ENABLED / DISABLED。
        StoreUser user = userMapper.selectById(targetId); // 目标账号。
        if (user == null || !ROLE_CUSTOMER.equals(user.getRole())) {
            throw BizException.notFound("顾客不存在"); // 管理员与不存在的 id 同一处理。
        }
        if (status.equals(user.getStatus())) {
            return false; // 幂等：不写库，页面提示当前状态。
        }
        int rows = userMapper.updateStatus(targetId, status); // 只改 status 一列。
        if (rows == 0) {
            throw BizException.notFound("顾客不存在"); // 查询之后被删的极端情况。
        }
        return true; // 状态已改变。
    }

    /**
     * 修改顾客资料：只改显示名、邮箱、电话三列，只处理 CUSTOMER。
     *
     * <p>校验复用用户端资料页的 {@link ProfileForm}，两处口径不会走偏；
     * 用户名、角色、状态不在可改范围内。</p>
     */
    @Transactional // 查角色与写三列同一事务。
    public Profile updateProfile(long targetId, String displayName, String email, String phone) {
        requireCustomer(targetId); // 管理员与不存在的 id 统一 404。
        Profile profile = ProfileForm.parse(displayName, email, phone); // 去空白 + 列宽校验。
        int rows = userMapper.updateProfile(targetId, profile.getDisplayName(),
                profile.getEmail(), profile.getPhone()); // 只写三列。
        if (rows == 0) { // 查询之后被删的极端情况。
            throw BizException.notFound("顾客不存在");
        }
        return profile; // 调用方拿显示名做提示。
    }

    /**
     * 重置顾客密码：生成一次性临时密码，写 BCrypt 散列，返回明文供页面显示一次。
     *
     * <p>不接受管理员手填新密码：管理员不该知道顾客的长期密码；临时密码只在这一次提示里出现，
     * 不写日志、不进会话。</p>
     */
    @Transactional // 查角色与写散列同一事务。
    public String resetPassword(long targetId) {
        requireCustomer(targetId); // 只处理顾客。
        String temp = generateTempPassword(); // 12 位大小写字母与数字。
        int rows = userMapper.updatePassword(targetId, passwordEncoder.encode(temp)); // 只写 password_hash。
        if (rows == 0) {
            throw BizException.notFound("顾客不存在");
        }
        return temp; // 明文只返回一次。
    }

    /** 目标必须是存在的 CUSTOMER；管理员与不存在一律 404，不暴露角色。 */
    private StoreUser requireCustomer(long targetId) {
        StoreUser user = userMapper.selectById(targetId); // 主键查询。
        if (user == null || !ROLE_CUSTOMER.equals(user.getRole())) {
            throw BizException.notFound("顾客不存在"); // 与不存在同码同文案。
        }
        return user; // 已确认的顾客。
    }

    /** 临时密码：12 位，字符集去掉了易混淆的 0/O/1/l/I。 */
    private String generateTempPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"; // 去掉易混字符。
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH); // 定长。
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length()))); // 等概率取字符。
        }
        return builder.toString(); // 交给调用方展示与写库。
    }

    /** 关键字：去空白、超长 400、LIKE 通配符转义（用户输入的 % 与 _ 按普通字符处理）。 */
    private String parseKeyword(String raw) {
        if (raw == null) {
            return null; // 不搜索。
        }
        String keyword = raw.trim(); // 去首尾空白。
        if (keyword.isEmpty()) {
            return null; // 空关键字等价于不搜索。
        }
        if (keyword.length() > KEYWORD_MAX) {
            throw BizException.badRequest("搜索关键字不能超过 " + KEYWORD_MAX + " 个字符"); // 400。
        }
        return escapeLike(keyword); // 转义后交给 LIKE。
    }

    /** 筛选状态：空按 ALL，其他值必须在白名单里。 */
    private String parseFilterStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ALL"; // 不筛选。
        }
        String status = raw.trim(); // 去空白。
        if (!FILTER_STATUSES.contains(status)) {
            throw BizException.badRequest("账号状态不合法"); // 400。
        }
        return status; // ALL / ENABLED / DISABLED。
    }

    /** 写入状态：只接受启用与禁用。 */
    private String parseWritableStatus(String raw) {
        if (raw == null) {
            throw BizException.badRequest("账号状态不合法"); // 缺参数也是 400。
        }
        String status = raw.trim(); // 去空白。
        if (!WRITABLE_STATUSES.contains(status)) {
            throw BizException.badRequest("账号状态不合法"); // 其他值一律拒绝。
        }
        return status; // ENABLED / DISABLED。
    }

    /** 转义 LIKE 通配符：反斜杠先加倍，再转义 % 与 _。 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 实体转列表行：只带页面需要的列（密码散列不出 Service）。 */
    private AdminUserRow toRow(StoreUser user) {
        AdminUserRow row = new AdminUserRow();
        row.setId(user.getId());
        row.setUsername(user.getUsername());
        row.setDisplayName(user.getDisplayName());
        row.setEmail(user.getEmail()); // M10-02：编辑表单回填。
        row.setPhone(user.getPhone()); // M10-02：编辑表单回填。
        row.setStatus(user.getStatus());
        row.setCreatedAt(user.getCreatedAt());
        return row;
    }
}
