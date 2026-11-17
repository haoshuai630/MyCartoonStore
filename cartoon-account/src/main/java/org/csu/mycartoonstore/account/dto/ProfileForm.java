package org.csu.mycartoonstore.account.dto; // 资料表单校验：显示名必填，邮箱与电话可空，按列宽限长。

import org.csu.mycartoonstore.common.exception.BizException; // 校验失败转 400，文案带字段名。

// M08-01：依据 docs/01 FR-U-03 与 docs/03 §2.1 的列宽（50 / 100 / 30）。
// 负责：把三个输入去首尾空白、按列宽校验，产出可直接写库的 Profile；
// 不做：邮箱/手机格式校验（FR-U-03 只给上限）、越权判断与写库（在 AccountService）。
public final class ProfileForm {

    /** 显示名上限，与 display_name VARCHAR(50) 一致 */
    static final int DISPLAY_NAME_MAX = 50;

    /** 邮箱上限，与 email VARCHAR(100) 一致 */
    static final int EMAIL_MAX = 100;

    /** 电话上限，与 phone VARCHAR(30) 一致 */
    static final int PHONE_MAX = 30;

    private ProfileForm() { // 工具类不允许实例化：只有静态方法。
    }

    /** 资料页：显示名必填，邮箱与电话可空（空串按 NULL 存）。 */
    public static Profile parse(String rawDisplayName, String rawEmail, String rawPhone) {
        String displayName = require(rawDisplayName, "显示名", DISPLAY_NAME_MAX); // 空与全空白都算没填。
        String email = optional(rawEmail, "邮箱", EMAIL_MAX); // 可空：空串归一为 null。
        String phone = optional(rawPhone, "电话", PHONE_MAX); // 可空：空串归一为 null。
        return new Profile(displayName, email, phone); // 三项都通过才构造。
    }

    /**
     * 注册页：显示名可空，空则返回 null，由调用方回退为用户名。
     *
     * <p>与 {@link #parse} 的差别只在"是否允许空"；上限仍是 50，避免直发 POST 绕过模板的 maxlength。</p>
     */
    public static String optionalDisplayName(String raw) {
        String value = raw == null ? "" : raw.strip(); // null 当空串，统一判断。
        if (value.isEmpty()) {
            return null; // 注册允许不填，调用方拿用户名兜底。
        }
        if (value.length() > DISPLAY_NAME_MAX) {
            throw BizException.badRequest("显示名不能超过 " + DISPLAY_NAME_MAX + " 个字符"); // 超长拒绝而不是截断。
        }
        return value; // 去空白后的显示名。
    }

    /** 必填字段：去空白后必须非空且不超长；超长拒绝而不是截断。 */
    private static String require(String raw, String field, int max) {
        String value = raw == null ? "" : raw.strip(); // null 当空串，必填判断只写一处。
        if (value.isEmpty()) {
            throw BizException.badRequest(field + "不能为空"); // 空与全空白同一条提示。
        }
        if (value.length() > max) {
            throw BizException.badRequest(field + "不能超过 " + max + " 个字符"); // 提示带字段名与上限。
        }
        return value; // 返回去空白后的值。
    }

    /** 可空字段：空串归一为 null；非空时同样按列宽限长。 */
    private static String optional(String raw, String field, int max) {
        String value = raw == null ? "" : raw.strip(); // null 与全空白等价。
        if (value.isEmpty()) {
            return null; // 可空字段：不写值就不占列宽。
        }
        if (value.length() > max) {
            throw BizException.badRequest(field + "不能超过 " + max + " 个字符"); // 超长直接拒，不做静默截断。
        }
        return value; // 去空白后的值。
    }
}
