package org.csu.mycartoonstore.order.dto; // 收货表单校验：必填、去空白、按列宽限长。

import org.csu.mycartoonstore.common.exception.BizException; // 校验失败转 400，文案带字段名。

// M05-01：依据 docs/01 FR-O-01 与 docs/03 的列宽（50 / 30 / 255）。
// 只做校验与去空白，不碰数据库；电话不限定 11 位手机号。
public final class ReceiverForm {

    /** 收货人长度上限，与 receiver_name VARCHAR(50) 一致 */
    static final int NAME_MAX = 50;

    /** 联系电话长度上限，与 receiver_phone VARCHAR(30) 一致 */
    static final int PHONE_MAX = 30;

    /** 详细地址长度上限，与 receiver_address VARCHAR(255) 一致 */
    static final int ADDRESS_MAX = 255;

    private ReceiverForm() { // 工具类不允许实例化。
    }

    /** 校验并返回去空白后的收货信息，任何一项不合法都抛 400。 */
    public static Receiver parse(String rawName, String rawPhone, String rawAddress) {
        String name = require(rawName, "收货人", NAME_MAX); // 必填 + ≤50。
        String phone = require(rawPhone, "联系电话", PHONE_MAX); // 必填 + ≤30。
        // 轻校验：电话至少含一位数字，拦掉纯文字乱填；不限定手机号格式。
        if (phone.codePoints().noneMatch(Character::isDigit)) {
            throw BizException.badRequest("联系电话必须包含数字"); // 提示仍不规定号码格式。
        }
        String address = require(rawAddress, "详细地址", ADDRESS_MAX); // 必填 + ≤255。
        return new Receiver(name, phone, address); // 三项都通过才构造。
    }

    /** 去首尾空白后必须非空且不超长；超长拒绝而不是截断。 */
    private static String require(String raw, String field, int max) {
        // null 当空串：必填判断只写一处，不散落到三个调用点。
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            throw BizException.badRequest(field + "不能为空"); // 空与全空白都算没填。
        }
        // 超长直接拒：截断会让用户以为存进去了。
        if (value.length() > max) {
            throw BizException.badRequest(field + "不能超过 " + max + " 个字符"); // 提示带字段名与上限。
        }
        return value; // 返回去空白后的值。
    }
}
