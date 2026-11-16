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
 * <p>没有这层，BizException 会冒到容器变成 500 堆栈页。
 * 状态码直接取 {@link ErrorCode#status()}：400 参数错、401 未登录、403 越权、
 * 404 不存在、409 数据已变化，一一对应，不再把所有失败都压成 400。</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public String handleBiz(BizException e, Model model, HttpServletResponse response) {
        response.setStatus(e.code().status());
        model.addAttribute("message", e.getMessage());
        return "error/error";
    }
}