package org.csu.mycartoonstore.web.controller; // 把会话里的 CSRF 令牌送到每个页面。

import jakarta.servlet.http.HttpServletRequest; // 读当前请求的会话。
import jakarta.servlet.http.HttpSession; // 令牌存在会话里，不落库。
import org.csu.mycartoonstore.common.security.CsrfToken; // 会话键与参数名的唯一来源。
import org.springframework.web.bind.annotation.ControllerAdvice; // 对所有控制器生效。
import org.springframework.web.bind.annotation.ModelAttribute; // 每次渲染前注入模型属性。

// M09-02：依据 docs/02 §6.3。
// 负责：给模板注入 csrfToken，让 fragments/csrf.html 能渲染隐藏域。
// 不做：令牌签发与校验（CsrfFilter 已保证读请求的会话里有值）、隐藏域渲染（片段文件）。
@ControllerAdvice // 全站生效：登录、购物车、订单、后台表单都要用。
public class CsrfAdvice {

    /** 当前会话的令牌：没有会话或没签发过时返回 null，页面隐藏域为空即无法通过校验。 */
    @ModelAttribute("csrfToken")
    public String csrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // 不新建会话：过滤器已经发过了。
        if (session == null) { // 异常路径（例如被直接转发的错误页）。
            return null; // 宁可让隐藏域为空，也不在这里伪造令牌。
        }
        Object token = session.getAttribute(CsrfToken.SESSION_KEY); // 过滤器签发的值。
        return token == null ? null : token.toString(); // 模板只需要字符串。
    }
}
