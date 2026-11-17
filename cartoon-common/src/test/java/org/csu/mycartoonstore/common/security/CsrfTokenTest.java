package org.csu.mycartoonstore.common.security; // 令牌生成与比对的单元测试。

import org.junit.jupiter.api.DisplayName; // 用例名用中文说明意图。
import org.junit.jupiter.api.Test; // JUnit 5 用例。

import java.util.HashSet; // 检查生成结果不重复。
import java.util.Set; // 同上。

import static org.assertj.core.api.Assertions.assertThat; // 断言库。

/** 令牌本身的两件事：够随机、比对够严格。 */
class CsrfTokenTest {

    @Test
    @DisplayName("生成 100 个令牌互不相同且非空")
    void generateIsRandom() {
        Set<String> seen = new HashSet<>(); // 收集已生成的令牌。
        for (int i = 0; i < 100; i++) { // 100 次足够暴露固定值或短随机源。
            String token = CsrfToken.generate(); // 每次调用都应当是新值。
            assertThat(token).isNotBlank(); // 空令牌会让页面隐藏域失效。
            assertThat(seen.add(token)).isTrue(); // 重复即失败。
        }
    }

    @Test
    @DisplayName("同一个令牌与自身比对通过")
    void matchesSameToken() {
        String token = CsrfToken.generate(); // 任意一个合法令牌。

        assertThat(CsrfToken.matches(token, token)).isTrue(); // 页面回传的正是签发出去的那个。
    }

    @Test
    @DisplayName("不同令牌比对失败")
    void rejectsDifferentToken() {
        assertThat(CsrfToken.matches(CsrfToken.generate(), CsrfToken.generate())).isFalse(); // 另一个会话的令牌不能通用。
    }

    @Test
    @DisplayName("null 与空串一律判失败")
    void rejectsNullAndEmpty() {
        String token = CsrfToken.generate(); // 一个有效的参照值。

        assertThat(CsrfToken.matches(null, token)).isFalse(); // 会话里没令牌。
        assertThat(CsrfToken.matches(token, null)).isFalse(); // 表单没带令牌。
        assertThat(CsrfToken.matches("", token)).isFalse(); // 会话值为空。
        assertThat(CsrfToken.matches(token, "")).isFalse(); // 表单值为空。
        assertThat(CsrfToken.matches(null, null)).isFalse(); // 两边都空。
    }

    @Test
    @DisplayName("长度不同的令牌比对失败")
    void rejectsDifferentLength() {
        assertThat(CsrfToken.matches("abc", "abcd")).isFalse(); // 前缀相同也不能放过。
    }
}
