package ai.csap.apidoc.team.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 业务异常,对应契约里的错误码。 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final transient Map<String, Object> extra;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> extra) {
        super(message);
        this.code = code;
        this.status = status;
        this.extra = extra;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public Map<String, Object> extra() { return extra; }

    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", msg); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg); }
    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", msg); }
    public static ApiException validation(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", msg); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, "CONFLICT", msg); }
    public static ApiException locked(String msg) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED", msg); }
    public static ApiException envUnavailable(String envName, String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ENV_UNAVAILABLE",
                // 不带数据库报错原文(可能含库地址、库名);原因见服务端日志和管理员看到的 /envs
                "环境「" + envName + "」暂不可用,请联系管理员检查该环境的数据库连接");
    }
    public static ApiException setupRequired() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "SETUP_REQUIRED", "系统尚未初始化,请先完成初始化设置");
    }
}
