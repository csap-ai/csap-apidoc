package ai.csap.apidoc.util;

import java.io.Serializable;
import java.util.Objects;

import lombok.Getter;

/**
 * 公用的异常处理.
 * <p>Created on 2017/10/23
 *
 * @author yangchengfu
 * @since 1.0
 */
@Getter
public class ValidateException extends RuntimeException implements IValidate<Serializable, Object> {
    /**
     * 编码
     */
    private String code = "";
    /**
     * 返回的信息
     */
    private String message;
    /**
     * 返回的扩展数据
     */
    private Object data;

    public ValidateException() {
        super();
    }


    public ValidateException(IValidate<? extends Serializable, Object> base) {
        this(base.getCode(), base.getMessage(), base.getData());
    }

    public ValidateException(IValidate<? extends Serializable, Object> base, Throwable throwable) {
        this(base.getCode(), base.getMessage(), base.getData(), throwable);
    }

    public ValidateException(String message) {
        this(DEFAULT_CODE, message);
    }

    public ValidateException(Throwable throwable) {
        super(throwable);
    }

    public ValidateException(Object code, String message) {
        super(message);
        if (Objects.nonNull(code)) {
            this.code = code.toString();
        }
        this.message = message;
    }

    public ValidateException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ValidateException(Object code, String message, Object data) {
        super(message);
        if (Objects.nonNull(code)) {
            this.code = code.toString();
        }
        this.message = message;
        this.data = data;
    }

    public ValidateException(String message, Object data) {
        this(DEFAULT_CODE, message, data);
    }

    public ValidateException(Throwable throwable, Object data) {
        this(null, data, throwable);
    }

    public ValidateException(String message, Throwable throwable) {
        this(message, (Object) null, throwable);
    }

    public ValidateException(String message, Object data, Throwable throwable) {
        this(DEFAULT_CODE, message, data, throwable);
    }

    public ValidateException(Object code, String message, Throwable throwable) {
        this(code, message, throwable, null);
    }

    public ValidateException(Object code, String message, Object data, Throwable throwable) {
        super(message, throwable);
        this.message = message;
        this.data = data;
        this.code = code.toString();
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getCode() {
        return Objects.isNull(code) ? DEFAULT_CODE : code;
    }


}
