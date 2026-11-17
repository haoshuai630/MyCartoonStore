package org.csu.mycartoonstore.admin.controller; // 管理端统一异常页。

import jakarta.servlet.http.HttpServletResponse; // 设置 HTTP 状态码。
import org.csu.mycartoonstore.common.exception.BizException; // 业务异常带状态与文案。
import org.springframework.ui.Model; // 把文案交给模板。
import org.springframework.web.bind.annotation.ControllerAdvice; // 全局生效。
import org.springframework.web.bind.annotation.ExceptionHandler; // 只接业务异常。

// M07-02：管理端第一份错误页。
// 只翻译 BizException（400/404/409 等业务失败）；技术异常仍走 Spring 默认错误页，不泄露堆栈。
@ControllerAdvice // 对管理端所有 Controller 生效。
public class AdminExceptionHandler {

    /** 业务失败：状态码取异常的 code，页面显示可读文案。 */
    @ExceptionHandler(BizException.class)
    public String handleBiz(BizException e, Model model, HttpServletResponse response) {
        response.setStatus(e.code().status()); // 400 / 404 / 409 等。
        model.addAttribute("message", e.getMessage()); // 用户可读文案。
        model.addAttribute("status", e.code().status()); // 模板里显示状态码。
        return "error/error"; // 管理端错误页。
    }
}
