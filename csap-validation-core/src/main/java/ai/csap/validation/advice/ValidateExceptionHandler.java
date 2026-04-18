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

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 公用的异常处理
 *
 * @author ycf
 */
@Order(1)
@ControllerAdvice
@Slf4j
public class ValidateExceptionHandler {

    /**
     * 内部自定义异常错误
     *
     * @param e ValidateException
     * @return ResponseEntity
     */
    @ExceptionHandler({ValidateException.class})
    public ResponseEntity<ApidocResult<?>> handlerSellerException(ValidateException e) {
        log.error("ValidateException{}", e.getMessage(), e);
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
     * 单参数和自定义参数异常处理
     *
     * @param e ConstraintViolationException
     * @return ResponseEntity
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApidocResult<?>> constraintViolationException(ConstraintViolationException e) {
        return getResponse(ApidocResult.error("400", e.getMessage().split(",")[0].split(":")[1]));
    }

    /**
     * 通用的response
     *
     * @param apidocResult 返回的参数
     * @return ResponseEntity
     */
    private ResponseEntity<ApidocResult<?>> getResponse(ApidocResult<?> apidocResult) {
        return ResponseEntity.ok(apidocResult);
    }
}
