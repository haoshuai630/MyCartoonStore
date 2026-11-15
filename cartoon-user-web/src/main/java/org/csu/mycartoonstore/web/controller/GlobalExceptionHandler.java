package org.csu.mycartoonstore.web.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.csu.mycartoonstore.common.exception.BizException;
import org.csu.mycartoonstore.common.exception.ErrorCode;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 把业务异常翻译成页面 + HTTP 状态码。
 *
 * <p>没有这层，BizException 会冒到容器变成 500 堆栈页；参数错误应该是 400，
 * 对象不存在应该是 404。M02-06 再统一美化错误页的样式。</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public String handleBiz(BizException e, Model model, HttpServletResponse response) {
        response.setStatus(e.code() == ErrorCode.NOT_FOUND
                ? HttpServletResponse.SC_NOT_FOUND
                : HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("message", e.getMessage());
        return "error/error";
    }
}