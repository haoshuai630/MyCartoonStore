package org.csu.mycartoonstore.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.csu.mycartoonstore.account.dto.Profile; // 已校验的三项资料。
import org.csu.mycartoonstore.account.dto.ProfileForm; // 资料字段校验与去空白。
import org.csu.mycartoonstore.account.entity.StoreUser;
import org.csu.mycartoonstore.account.mapper.StoreUserMapper;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection; // 批量取显示名。
import java.util.Map; // 后台订单列表的用户名索引。
import java.util.regex.Pattern; // 用户名格式。
import java.util.stream.Collectors; // 收集成 Map。

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

    /** 用户端角色：只有普通顾客能从用户端登录 */
    private static final String ROLE_CUSTOMER = "CUSTOMER"; // 两端共用账号表，用角色区分入口。

    /** 管理端角色：只有管理员能从管理端登录 */
    private static final String ROLE_ADMIN = "ADMIN"; // 用户端也不接受管理员，防止两头混用。

    /** 启用状态：登录只接受启用账号 */
    private static final String STATUS_ENABLED = "ENABLED"; // 禁用账号密码正确也不放行。

    /** 登录失败统一文案：不告诉对方是账号不存在、密码错、角色不符还是已停用 */
    private static final String LOGIN_FAILED = "账号或密码错误，或账号不可用";

    private final StoreUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountService(StoreUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户端登录：只接受启用状态的 CUSTOMER。
     *
     * <p>管理员不能从用户端登录，避免在用户端以管理身份购买（docs/01 §2、AC-05）。</p>
     */
    public StoreUser authenticateCustomer(String username, String rawPassword) { // 用户端登录入口专用。
        return authenticate(username, rawPassword, ROLE_CUSTOMER); // 角色不符与密码错同文案。
    }

    /**
     * 管理端登录：只接受启用状态的 ADMIN。
     *
     * <p>CUSTOMER 不能从管理端登录（FR-A-01）；失败文案与用户端一致。</p>
     */
    public StoreUser authenticateAdmin(String username, String rawPassword) { // 管理端登录入口专用。
        return authenticate(username, rawPassword, ROLE_ADMIN); // CUSTOMER 在这里被角色条件挡掉。
    }

    /**
     * 登录校验：账号存在、密码匹配、角色与入口一致、状态为 ENABLED，全部满足才返回用户。
     *
     * <p>任一条件不满足都抛同一个 401 与同一句文案，不区分"账号不存在 / 密码错 / 角色不符 / 已停用"，
     * 避免被用来枚举账号或探测账号状态（FR-U-01、AC-05）。</p>
     */
    private StoreUser authenticate(String username, String rawPassword, String requiredRole) {
        StoreUser user = findByUsername(username); // 不存在时返回 null。
        boolean matched = user != null && rawPassword != null // 账号与密码都要有值。
                && passwordEncoder.matches(rawPassword, user.getPasswordHash()); // 真实 BCrypt 比对。
        boolean allowed = matched // 密码对。
                && requiredRole.equals(user.getRole()) // 角色必须与本次登录入口一致。
                && STATUS_ENABLED.equals(user.getStatus()); // 账号仍处于启用状态。
        if (!allowed) {
            throw new BizException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED); // 所有失败同码同文案。
        }
        return user; // 登录成功，调用方只取 id 与显示名进会话。
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
        String display = ProfileForm.optionalDisplayName(displayName); // 空则回退用户名；超长直接 400。
        if (userMapper.selectCount(Wrappers.<StoreUser>lambdaQuery().eq(StoreUser::getUsername, name)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        StoreUser user = new StoreUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(display == null ? name : display); // 显示名允许不填，但不允许空串。
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

    /**
     * 批量取显示名：后台订单列表/详情一次查完，避免逐行查账号。
     *
     * <p>只返回存在的账号；被删账号在页面显示为空，由调用方兜底。</p>
     */
    public Map<Long, String> displayNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of(); // 没有 id 就不查库。
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StoreUser::getId, StoreUser::getDisplayName, (a, b) -> a)); // id → 显示名。
    }

    /** 按 id 查账号：登录后刷新会话里的展示信息时用 */
    public StoreUser findById(long id) {
        return userMapper.selectById(id);
    }

    /**
     * 读本人资料：只按会话里的 id 取，不接收用户名或外部传入的 id。
     *
     * <p>账号不存在时 404——会话还在但账号已被删，按"没有这个人"处理，不渲染半个页面。</p>
     */
    public StoreUser profile(long userId) {
        return requireUser(userId);
    }

    /**
     * 保存本人资料：只写 display_name / email / phone 三列。
     *
     * <p>用定向 UPDATE 而不是整行回写：并发改密或管理端启停时不会互相覆盖；
     * 用户名、角色、状态与密码不在 SET 列表里，因此本人无法通过表单改它们（FR-U-03）。</p>
     *
     * @return 写库后的账号，调用方用其中的显示名刷新页头
     */
    @Transactional
    public StoreUser updateProfile(long userId, String rawDisplayName, String rawEmail, String rawPhone) {
        StoreUser user = requireUser(userId); // 先确认账号仍存在。
        Profile profile = ProfileForm.parse(rawDisplayName, rawEmail, rawPhone); // 去空白 + 列宽校验。
        int rows = userMapper.updateProfile(userId, profile.getDisplayName(),
                profile.getEmail(), profile.getPhone()); // 定向更新三列。
        if (rows == 0) {
            throw BizException.notFound("账号不存在"); // 查询之后被删的极端情况。
        }
        user.setDisplayName(profile.getDisplayName()); // 让返回值与库一致。
        user.setEmail(profile.getEmail()); // 同上。
        user.setPhone(profile.getPhone()); // 同上。
        return user; // 调用方负责刷新会话里的显示名。
    }

    /** 按 id 取账号，不存在时 404：资料页与资料保存共用。 */
    private StoreUser requireUser(long userId) {
        StoreUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        return user;
    }
}