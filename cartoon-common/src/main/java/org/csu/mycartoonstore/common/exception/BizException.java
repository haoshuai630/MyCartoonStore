package org.csu.mycartoonstore.common.exception;

/**
 * 业务异常：可预期的失败（参数非法、越权、状态冲突…）。
 * <p>由全局异常处理器翻译成对应 HTTP 状态与错误页，不打印堆栈到用户界面。</p>
 */
public class BizException extends RuntimeException {

    private final ErrorCode code;

    public BizException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public BizException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }
}
