package org.csu.mycartoonstore.testsupport; // 测试侧：给 MockMvc 的写请求补上 CSRF 令牌。

import jakarta.servlet.http.HttpSession; // 令牌要写进请求上的会话。
import org.csu.mycartoonstore.common.security.CsrfToken; // 会话键与参数名跟生产代码共用同一来源。
import org.springframework.test.web.servlet.request.RequestPostProcessor; // MockMvc 的请求后置处理接口。

// M09-03：依据 docs/02 §6.3、docs/06 AC-31。
// 负责：让测试里的写请求同时带上会话令牌与表单参数，过滤器才判定通过。
// 不做：绕过校验（令牌仍要与会话一致）、页面渲染断言（由各页面用例覆盖）。
public final class CsrfTestSupport {

    /** 固定令牌：测试不需要随机性，固定值让断言和失败信息都更好读。 */
    public static final String TOKEN = "test-csrf-token"; // 只在本进程的测试会话里使用。

    /** 工具类不实例化：全部是静态方法。 */
    private CsrfTestSupport() { // 私有构造器。
    }

    /** 给请求补上会话令牌与 `_csrf` 参数：`.with(csrf())`。 */
    public static RequestPostProcessor csrf() {
        return request -> { // 后置处理在 MockMvc 组装完路径、参数、会话之后执行。
            HttpSession session = request.getSession(); // 复用 .session(...) 传入的会话，没有就建一个。
            session.setAttribute(CsrfToken.SESSION_KEY, TOKEN); // 与生产代码同一个会话键。
            request.addParameter(CsrfToken.PARAM_NAME, TOKEN); // 与模板隐藏域同一个参数名。
            return request; // 返回同一个请求，链式调用继续。
        };
    }
}
