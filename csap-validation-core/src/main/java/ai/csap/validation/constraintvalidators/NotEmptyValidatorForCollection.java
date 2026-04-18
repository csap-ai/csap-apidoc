package ai.csap.validation.constraintvalidators;

import java.util.Collection;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotEmpty;

/**
 * Not empty validator for collection.
 * <p>Created on 2020/3/12
 *
 * @author yangchengfu
 * @since 1.0
 */
public final class NotEmptyValidatorForCollection implements ConstraintValidator<NotEmpty, Collection<?>> {
    @Override
    public boolean isValid(Collection<?> objects, ConstraintValidatorContext constraintValidatorContext) {
        return false;
    }
}
