package org.csu.mycartoonstore.account.dto; // 新密码校验：长度、字节数、字符类别与空白。

import org.csu.mycartoonstore.common.exception.BizException; // 校验失败转 400，文案说明具体哪条不满足。

// M08-02：依据 docs/01 FR-U-04（8–32 字符、UTF-8 ≤72 字节、至少两类字符、不接受空白、不得与旧密码相同）。
// 负责：新密码与确认密码的规则校验，返回可直接交给 BCrypt 的原文；
// 不做：与原密码比对、散列与写库（在 AccountService），也不做"两次输入一致"之外的字段判断。
public final class PasswordForm {

    /** 新密码最短长度：与登录/注册页提示保持一致 */
    static final int MIN_LENGTH = 8;

    /** 新密码最长长度：超过 32 字符没有实际收益，也超出教学场景需要 */
    static final int MAX_LENGTH = 32;

    /** UTF-8 字节上限：BCrypt 只处理前 72 字节，超长必须拒绝而不是静默截断 */
    static final int MAX_BYTES = 72;

    private PasswordForm() { // 工具类不允许实例化：只有静态方法。
    }

    /**
     * 校验新密码并返回原文（密码不做去空白，空白本身就是不合法输入）。
     */
    public static String requireNew(String raw) {
        String value = raw == null ? "" : raw; // 密码不 strip：前后空格也要被拒绝而不是被吃掉。
        int length = value.codePointCount(0, value.length()); // 按字符数而不是字节数判断长度。
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw BizException.badRequest("新密码需为 " + MIN_LENGTH + "–" + MAX_LENGTH + " 个字符");
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw BizException.badRequest("新密码的 UTF-8 编码不能超过 " + MAX_BYTES + " 字节"); // 中文/emoji 容易被字节数卡住。
        }
        if (value.codePoints().anyMatch(Character::isWhitespace)) {
            throw BizException.badRequest("新密码不能包含空白字符"); // 空格、制表符、换行都不接受。
        }
        int classes = characterClasses(value); // 字母 / 数字 / 符号各算一类。
        if (classes < 2) {
            throw BizException.badRequest("新密码需至少包含字母、数字、符号中的两类"); // 纯字母或纯数字都太弱。
        }
        return value; // 原文交给 BCrypt，不在校验层散列。
    }

    /** 两次输入必须一致：与注册页同一口径，放在控制器调用，Service 不感知表单结构。 */
    public static void requireSame(String newPassword, String confirmPassword) {
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw BizException.badRequest("两次输入的新密码不一致"); // 提示不透露任何一个输入值。
        }
    }

    /** 统计出现过的字符类别：字母、数字、其他（符号）各最多算一类。 */
    private static int characterClasses(String value) {
        boolean letter = false; // 是否出现过字母。
        boolean digit = false; // 是否出现过数字。
        boolean symbol = false; // 是否出现过符号（非字母、非数字、非空白）。
        for (int codePoint : value.codePoints().toArray()) {
            if (Character.isLetter(codePoint)) {
                letter = true; // 字母。
            } else if (Character.isDigit(codePoint)) {
                digit = true; // 数字。
            } else {
                symbol = true; // 其余都按符号计。
            }
        }
        return (letter ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0); // 三类里出现过几类。
    }
}
