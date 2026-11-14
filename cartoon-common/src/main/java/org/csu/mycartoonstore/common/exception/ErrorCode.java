package org.csu.mycartoonstore.common.exception;

/**
 * 业务错误码：同时承载 HTTP 状态码与默认用户文案。
 * <p>Controller 层不直接拼状态码，统一由全局异常处理器按 {@link #status()} 转换。</p>
 */
public enum ErrorCode {

    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "无权访问该内容"),
    NOT_FOUND(404, "内容不存在或已不可用"),
    CONFLICT(409, "数据已发生变化，请刷新后重试"),
    INTERNAL(500, "系统繁忙，请稍后重试");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
