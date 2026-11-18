package ai.csap.apidoc.runtime.filter.model;

/**
 * 外层统一包装(对应 Go 测试的 apiResult)
 */
public class ApiResult<T> {
    private String code = "0";
    private String message = "成功";
    private T data;
    private String language = "ZH";
    private long time = 1;
    private boolean success = true;

    public ApiResult() {
    }

    public ApiResult(T data) {
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getLanguage() {
        return language;
    }

    public long getTime() {
        return time;
    }

    public boolean isSuccess() {
        return success;
    }
}
