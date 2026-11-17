package org.csu.mycartoonstore.common.security; // CSRF 同步令牌：会话键、表单参数名、生成与比对。

import java.nio.charset.StandardCharsets; // 令牌按 UTF-8 转字节，比较结果不受平台默认编码影响。
import java.security.MessageDigest; // 恒定时间比较，避免按字节短路泄露令牌内容。
import java.util.UUID; // 令牌取 UUID v4：128 位随机，同一会话内不需要额外去重。

// M09-01：依据 docs/02 §6.3（同步令牌）与 docs/06 AC-31。
// 负责：签发令牌、把表单隐藏域的值与会话里的值做恒定时间比对。
// 不做：会话创建与销毁（CsrfFilter）、页面渲染（fragments/csrf.html）、身份判断（拦截器）。
public final class CsrfToken {

    /** 会话里存令牌的键：两端同名，过滤器、模板与测试共用同一套规则。 */
    public static final String SESSION_KEY = "csrfToken"; // 只放会话，不落库、不写 Cookie。

    /** 写表单隐藏域的参数名：与 docs/04 §1 的 `_csrf` 一致。 */
    public static final String PARAM_NAME = "_csrf"; // 模板、过滤器、测试三处引用同一常量。

    /** 工具类不允许实例化：所有方法都是静态的。 */
    private CsrfToken() { // 私有构造器，防止被当成 Bean 或工具对象使用。
    }

    /** 生成一个新令牌：UUID v4 的字符串形式。 */
    public static String generate() {
        return UUID.randomUUID().toString(); // 每次调用都不同，登录换会话后也会重新签发。
    }

    /**
     * 比对会话令牌与表单令牌是否一致。
     *
     * <p>任一边为空一律判失败：缺令牌不是"放行"，而是请求来源不可信。</p>
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || expected.isEmpty() || actual == null || actual.isEmpty()) { // 空值没有可比的内容。
            return false; // 直接拒绝，避免把"没带"当成"一致"。
        }
        byte[] left = expected.getBytes(StandardCharsets.UTF_8); // 会话里的令牌。
        byte[] right = actual.getBytes(StandardCharsets.UTF_8); // 表单带来的令牌。
        return MessageDigest.isEqual(left, right); // 恒定时间比较：长度不同也走完整个循环。
    }
}
