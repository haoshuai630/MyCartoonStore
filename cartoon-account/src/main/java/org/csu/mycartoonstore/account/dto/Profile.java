package org.csu.mycartoonstore.account.dto; // 校验通过的资料：写库前只携带三项可改字段。

import lombok.Getter; // 生成 getter，三项只读。

// M08-01：依据 docs/01 FR-U-03（本人只可改 display_name / email / phone）。
// 负责：携带"已经去空白并按列宽校验"的三项，供 AccountService 直接写库；
// 不做：格式校验（在 ProfileForm）、归属判断与持久化（在 AccountService）。
@Getter // 构造后只读：调用方不能拿它改别的字段。
public class Profile {

    /** 显示名，去首尾空白后 1–50 字符 */
    private final String displayName;

    /** 邮箱，可空；去首尾空白后 1–100 字符 */
    private final String email;

    /** 电话，可空；去首尾空白后 1–30 字符 */
    private final String phone;

    /** 三项都已校验：这里不再重复判断。 */
    public Profile(String displayName, String email, String phone) {
        this.displayName = displayName; // 必填项，ProfileForm 保证非空。
        this.email = email; // 可空：null 表示未填。
        this.phone = phone; // 可空：null 表示未填。
    }
}
