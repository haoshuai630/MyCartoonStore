package org.csu.mycartoonstore.web.support; // 测试侧：给登录/注册请求补上验证码。

import jakarta.servlet.http.HttpSession; // 验证码存会话。
import org.csu.mycartoonstore.web.captcha.CaptchaService; // 会话键与参数名与生产一致。
import org.springframework.test.web.servlet.request.RequestPostProcessor; // MockMvc 请求后置处理。

// M10-04：依据指导书实验内容一「验证码」。
// 负责：让测试里的登录/注册同时带上会话验证码与表单参数，跳过图片这一步。
// 不做：绕过校验（值仍要与会话一致）、画图断言（CaptchaTest 单独覆盖）。
public final class CaptchaFixture {

    /** 固定验证码：测试不关心随机性，固定值让断言可读。 */
    public static final String CODE = "ABCD"; // 4 位，可直接写进参数。

    private CaptchaFixture() { // 工具类。
    }

    /** 给请求补上会话验证码与 `captcha` 参数：`.with(captcha())`。 */
    public static RequestPostProcessor captcha() {
        return request -> { // 在 MockMvc 组装完参数与会话之后执行。
            HttpSession session = request.getSession(); // 复用已有会话。
            session.setAttribute(CaptchaService.SESSION_KEY, CODE); // 会话里的值。
            request.addParameter(CaptchaService.PARAM_NAME, CODE); // 表单里的值。
            return request; // 返回同一个请求。
        };
    }
}
