package org.csu.mycartoonstore.web; // 图形验证码：接口、校验与一次性（M10-04）。

import org.csu.mycartoonstore.web.captcha.CaptchaService; // 会话键与参数名。
import org.csu.mycartoonstore.web.support.AbstractIntegrationTest; // 真实 MySQL + 每例重建表。
import org.csu.mycartoonstore.web.support.CaptchaFixture; // 固定验证码。
import org.junit.jupiter.api.DisplayName; // 用例名。
import org.junit.jupiter.api.Test; // JUnit 入口。
import org.springframework.beans.factory.annotation.Autowired; // 注入依赖。
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc; // 开启 MockMvc。
import org.springframework.jdbc.core.JdbcTemplate; // 造账号。
import org.springframework.mock.web.MockHttpSession; // 手工会话。
import org.springframework.security.crypto.password.PasswordEncoder; // 造可登录密码。
import org.springframework.test.web.servlet.MockMvc; // 请求入口;
import org.springframework.test.web.servlet.MvcResult; // 取响应。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.csu.mycartoonstore.testsupport.CsrfTestSupport.csrf; // 构造带令牌的 POST。
import static org.hamcrest.Matchers.containsString; // 页面内容断言。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // 构造 GET。
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // 构造 POST。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content; // 响应断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header; // 响应头断言。
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // 状态码断言;

// M10-04：依据指导书实验内容一「用户模块：注册、登录、验证码」。
// 覆盖：图片接口的格式与会话、错误验证码被拒、正确验证码放行（忽略大小写）、一次性。
// 不覆盖：图片内容识别（不做 OCR）。
@AutoConfigureMockMvc // 不占端口。
class CaptchaTest extends AbstractIntegrationTest {

    @Autowired // 由 Spring 注入。
    private MockMvc mockMvc; // 请求入口。

    @Autowired // 由 Spring 注入。
    private JdbcTemplate jdbc; // 造账号。

    @Autowired // 由 Spring 注入。
    private PasswordEncoder passwordEncoder; // 造可校验的密码。

    @Test // 接口返回 PNG，并把 4 位验证码写进会话。
    @DisplayName("验证码接口：返回 PNG 且会话里有 4 位码")
    void captchaEndpointWritesSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/captcha")) // 浏览器请求图片。
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", containsString("no-store"))) // 不缓存。
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray(); // 图片字节。
        assertThat(body.length).isGreaterThan(100); // 至少不是空图。
        assertThat(body[0]).isEqualTo((byte) 0x89); // PNG 魔数首字节。
        Object code = result.getRequest().getSession(false).getAttribute(CaptchaService.SESSION_KEY); // 会话里的码。
        assertThat(code).isNotNull();
        assertThat(code.toString()).hasSize(4); // 定长 4 位。
    }

    @Test // 验证码错误：停在登录页，不写登录态。
    @DisplayName("登录：验证码错误 → 200 提示，不登录")
    void loginWithWrongCaptcha() throws Exception {
        seedUser("cap_wrong"); // 账号存在。
        MockHttpSession session = new MockHttpSession(); // 会话。
        session.setAttribute(CaptchaService.SESSION_KEY, CaptchaFixture.CODE); // 正确码是 ABCD。

        mockMvc.perform(post("/login").with(csrf()).session(session) // 令牌有，验证码错。
                        .param("username", "cap_wrong")
                        .param("password", "Cartoon123!")
                        .param("captcha", "ZZZZ"))
                .andExpect(status().isOk()) // 回表单。
                .andExpect(content().string(containsString("验证码不正确"))); // 明确提示。

        assertThat(session.getAttribute("userId")).isNull(); // 没有登录态。
    }

    @Test // 验证码正确（小写也认）：正常登录。
    @DisplayName("登录：验证码正确（忽略大小写）→ 302")
    void loginWithCorrectCaptcha() throws Exception {
        seedUser("cap_ok"); // 账号存在。
        MockHttpSession session = new MockHttpSession(); // 显式会话，验证码写在这里。
        session.setAttribute(CaptchaService.SESSION_KEY, CaptchaFixture.CODE); // 会话里是 ABCD。

        mockMvc.perform(post("/login").with(csrf()).session(session) // 令牌与会话都带上。
                        .param("username", "cap_ok")
                        .param("password", "Cartoon123!")
                        .param("captcha", "abcd")) // 小写也要通过。
                .andExpect(status().is3xxRedirection()); // 登录成功。
    }

    @Test // 一次性：第一次用掉后，同一个码不能再用。
    @DisplayName("验证码一次性：用过即失效")
    void captchaIsOneTime() throws Exception {
        seedUser("cap_once"); // 账号存在。
        MockHttpSession session = new MockHttpSession(); // 会话。
        session.setAttribute(CaptchaService.SESSION_KEY, CaptchaFixture.CODE); // ABCD。

        mockMvc.perform(post("/login").with(csrf()).session(session) // 第一次：故意输错，码被消费。
                        .param("username", "cap_once")
                        .param("password", "Cartoon123!")
                        .param("captcha", "ZZZZ"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/login").with(csrf()).session(session) // 第二次：用正确的码也不行了。
                        .param("username", "cap_once")
                        .param("password", "Cartoon123!")
                        .param("captcha", CaptchaFixture.CODE))
                .andExpect(status().isOk()) // 仍停在表单。
                .andExpect(content().string(containsString("验证码不正确")));
    }

    /** 造一个启用顾客，密码用真实 BCrypt。 */
    private void seedUser(String username) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) VALUES (?,?,?,?,?)",
                username, passwordEncoder.encode("Cartoon123!"), "验证码用户", "CUSTOMER", "ENABLED");
    }
}
