package ai.csap.validation.advice;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.DataTypeValidate;
import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.core.ApidocResultEnum;
import ai.csap.apidoc.util.ValidateException;
import ai.csap.validation.ValidationViolationException;
import ai.csap.validation.properties.ValidationProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 公用的异常处理.
 *
 * <p>响应体为 {@link ApidocResult}({@code code} + {@code message}),HTTP 状态码默认 200,
 * 可用 {@code csap.validation.http-status} 修改。
 * <p>{@code ConstraintViolationException} 分属 jakarta.validation / javax.validation 两套 API,
 * 由 starter 按 classpath 分别注册处理器,这里不引用任何一套,保证 Spring Boot 2 / 3 都能加载本类。
 *
 * @author ycf
 */
@Order(1)
@ControllerAdvice
@Slf4j
public class ValidateExceptionHandler {
    private final ValidationProperties properties;

    public ValidateExceptionHandler() {
        this(new ValidationProperties());
    }

    public ValidateExceptionHandler(ValidationProperties properties) {
        this.properties = properties;
    }

    /**
     * 内部自定义异常错误
     *
     * @param e ValidateException
     * @return ResponseEntity
     */
    @ExceptionHandler({ValidateException.class})
    public ResponseEntity<ApidocResult<?>> handlerSellerException(ValidateException e) {
        if (!(e instanceof ValidationViolationException)) {
            // ValidationViolationException 已由 ViolationRecorder 记录
            log.warn("ValidateException code={} message={}", e.getCode(), e.getMessage());
        }
        return getResponse(ApidocResult.error(e.getCode(), e.getMessage(), e.getData()));
    }

    /**
     * 处理验证字段的错误
     *
     * @param e
     * @return
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApidocResult<?>> bindException(BindException e) {
        log.error("bindException{}", e.getMessage(), e);
        return getResponse(ApidocResult.error("411", String.format("参数类型错误[字段:%s,值为:%s]", e.getFieldError().getField(), e.getFieldError().getRejectedValue())));
    }

    /**
     * 参数类型转换错误
     *
     * @param exception 错误
     * @return 错误信息
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApidocResult<?>> parameterTypeException(MethodArgumentTypeMismatchException exception) {
        log.error(exception.getCause().getLocalizedMessage());

        if (exception.getParameter().getParameter().isAnnotationPresent(DataTypeValidate.class)) {
            DataTypeValidate dataTypeValidate = exception.getParameter().getParameter().getAnnotation(DataTypeValidate.class);
            return getResponse(ApidocResult.error(dataTypeValidate.code(), dataTypeValidate.message()));
        } else {
            String msg = ApidocResultEnum.DATA_VALIDATE_ERROT.getDesc();
            if (exception.getParameter().getParameter().isAnnotationPresent(ApiModelProperty.class)) {
                msg = exception.getParameter().getParameter().getAnnotation(ApiModelProperty.class).value() + "," + msg;
            }
            return getResponse(ApidocResult.error(ApidocResultEnum.DATA_VALIDATE_ERROT.getCode(), msg));
        }
    }

    /**
     * 处理POST请求字段验证的错误
     *
     * @param e
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApidocResult<?>> bindException(MethodArgumentNotValidException e) {
        log.error(e.getMessage(), e);
        return getResponse(ApidocResult.error("413", e.getBindingResult().getFieldError().getDefaultMessage()));
    }

    /**
     * 通用的response
     *
     * @param apidocResult 返回的参数
     * @return ResponseEntity
     */
    protected ResponseEntity<ApidocResult<?>> getResponse(ApidocResult<?> apidocResult) {
        return ResponseEntity.status(httpStatus(properties)).body(apidocResult);
    }

    /**
     * 校验失败响应的 HTTP 状态码.
     * <p>返回 int 并使用 {@code ResponseEntity.status(int)}:Spring 6 删除了 {@code status(HttpStatus)},
     * 按 Spring 5.2 编译的代码只能用两边都有的重载。
     *
     * @param properties 配置
     * @return 状态码,不合法时为 200
     */
    public static int httpStatus(ValidationProperties properties) {
        int status = properties == null ? 200 : properties.getHttpStatus();
        return status >= 100 && status <= 599 ? status : 200;
    }
}
