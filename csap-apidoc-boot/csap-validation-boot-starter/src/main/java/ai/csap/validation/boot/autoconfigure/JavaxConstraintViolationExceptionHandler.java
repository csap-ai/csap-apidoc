package ai.csap.validation.boot.autoconfigure;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import ai.csap.apidoc.core.ApidocResult;
import ai.csap.validation.advice.ValidateExceptionHandler;
import ai.csap.validation.properties.ValidationProperties;

import javax.validation.ConstraintViolationException;

/**
 * {@code @Validated} 方法参数校验失败(javax.validation,Spring Boot 2)的统一返回.
 *
 * @author ycf
 * @since 1.2.0
 */
@Order(1)
@ControllerAdvice
public class JavaxConstraintViolationExceptionHandler {
    private final ValidationProperties properties;

    public JavaxConstraintViolationExceptionHandler(ValidationProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApidocResult<?>> constraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse(e.getMessage());
        return ResponseEntity.status(ValidateExceptionHandler.httpStatus(properties))
                .body(ApidocResult.error("400", message));
    }
}
