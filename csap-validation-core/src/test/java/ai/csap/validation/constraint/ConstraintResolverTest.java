package ai.csap.validation.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Locale;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ai.csap.validation.annotation.Phone;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.factory.Validate;
import ai.csap.validation.properties.ValidationProperties;
import ai.csap.validation.type.ValidationStrategyType;

/**
 * jakarta.validation 与 javax.validation 两套约束注解都要被识别并执行.
 *
 * <p>本模块按 Hibernate Validator 8(jakarta)编译和测试;javax 注解在这里经过两条路径:
 * 内置约束换成同名 jakarta 约束执行,自定义约束直接反射调用 javax 的 ConstraintValidator。
 * Hibernate Validator 6 + javax 的组合由 example-spring-boot2 的测试覆盖。
 */
class ConstraintResolverTest {
    private static final DefaultValidateFactoryImpl FACTORY = new DefaultValidateFactoryImpl(
            ConstraintHelper.forAllBuiltinConstraints(), new ValidationProperties());
    private static Locale locale;

    @BeforeAll
    static void englishMessages() {
        locale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterAll
    static void restore() {
        Locale.setDefault(locale);
    }

    static class JakartaModel {
        @jakarta.validation.constraints.NotNull(message = "不能为空")
        @jakarta.validation.constraints.Size(min = 3, max = 5)
        @jakarta.validation.constraints.Pattern(regexp = "[a-z]+", message = "只能是小写字母")
        String name;
        @jakarta.validation.constraints.Min(18)
        int age;
        @Phone(code = "1009")
        String phone;
    }

    static class JavaxModel {
        @javax.validation.constraints.NotNull(message = "不能为空")
        @javax.validation.constraints.Size(min = 3, max = 5)
        @javax.validation.constraints.Pattern(regexp = "[a-z]+", message = "只能是小写字母")
        String name;
        @javax.validation.constraints.Min(18)
        Integer age;
        @javax.validation.constraints.NotEmpty
        List<String> tags;
        @Even(code = "2001")
        Integer even;
        @Deprecated
        String plain;
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @javax.validation.Constraint(validatedBy = EvenValidator.class)
    @interface Even {
        String code() default "";

        String message() default "必须是偶数";

        Class<?>[] groups() default {};

        Class<? extends javax.validation.Payload>[] payload() default {};
    }

    public static class EvenValidator implements javax.validation.ConstraintValidator<Even, Integer> {
        @Override
        public boolean isValid(Integer value, javax.validation.ConstraintValidatorContext context) {
            return value == null || value % 2 == 0;
        }
    }

    private static List<Validate.ConstraintValidatorField> rules(Class<?> model, String field) throws Exception {
        java.lang.reflect.Field f = model.getDeclaredField(field);
        return FACTORY.getAllFieldConstraintValidator(f.getType(), f.getAnnotations());
    }

    private static Validate.ConstraintValidatorField rule(List<Validate.ConstraintValidatorField> rules, String name) {
        return rules.stream().filter(r -> r.ruleName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void jakartaBuiltinConstraints() throws Exception {
        List<Validate.ConstraintValidatorField> rules = rules(JakartaModel.class, "name");
        assertThat(rules).extracting(Validate.ConstraintValidatorField::ruleName)
                .containsExactly("NotNull", "Size", "Pattern");
        assertThat(rules).allMatch(r -> r.getType() == ValidationStrategyType.Annotation && "400".equals(r.getCode()));
        assertThat(rule(rules, "NotNull").getValidator().isValid(null)).isFalse();
        assertThat(rule(rules, "Size").getValidator().isValid("ab")).isFalse();
        assertThat(rule(rules, "Size").getValidator().isValid("abc")).isTrue();
        assertThat(rule(rules, "Pattern").getValidator().isValid("ABC")).isFalse();
        assertThat(rule(rules, "Pattern").getMessage()).isEqualTo("只能是小写字母");
        // 未写 message 时插值 HV 自带文案
        assertThat(rule(rules, "Size").getMessage()).isEqualTo("size must be between 3 and 5");
        assertThat(rule(rules, "Size").getValidator().getApi()).isEqualTo(ConstraintFlavor.JAKARTA);

        Validate.ConstraintValidatorField min = rules(JakartaModel.class, "age").get(0);
        assertThat(min.getValidator().isValid(17)).isFalse();
        assertThat(min.getValidator().isValid(18)).isTrue();
        assertThat(min.getMessage()).isEqualTo("must be greater than or equal to 18");
    }

    @Test
    void jakartaCustomConstraintWithCode() throws Exception {
        Validate.ConstraintValidatorField phone = rules(JakartaModel.class, "phone").get(0);
        assertThat(phone.getCode()).isEqualTo("1009");
        assertThat(phone.getValidator().isValid("13800138000")).isTrue();
        assertThat(phone.getValidator().isValid("12345")).isFalse();
    }

    @Test
    void javaxBuiltinConstraints() throws Exception {
        List<Validate.ConstraintValidatorField> rules = rules(JavaxModel.class, "name");
        assertThat(rules).extracting(Validate.ConstraintValidatorField::ruleName)
                .containsExactly("NotNull", "Size", "Pattern");
        assertThat(rule(rules, "NotNull").getValidator().isValid(null)).isFalse();
        assertThat(rule(rules, "NotNull").getMessage()).isEqualTo("不能为空");
        assertThat(rule(rules, "Size").getValidator().isValid("abcdef")).isFalse();
        assertThat(rule(rules, "Size").getValidator().isValid("abcd")).isTrue();
        assertThat(rule(rules, "Size").getMessage()).isEqualTo("size must be between 3 and 5");
        assertThat(rule(rules, "Pattern").getValidator().isValid("abc")).isTrue();
        assertThat(rule(rules, "Pattern").getValidator().isValid("ab1")).isFalse();
        // 规则保留原注解
        assertThat(rule(rules, "Pattern").getAnnotation()).isInstanceOf(javax.validation.constraints.Pattern.class);

        Validate.ConstraintValidatorField min = rules(JavaxModel.class, "age").get(0);
        assertThat(min.getValidator().isValid(17)).isFalse();
        assertThat(min.getValidator().isValid(null)).isTrue();

        Validate.ConstraintValidatorField notEmpty = rules(JavaxModel.class, "tags").get(0);
        assertThat(notEmpty.getValidator().isValid(List.of())).isFalse();
        assertThat(notEmpty.getValidator().isValid(List.of("a"))).isTrue();
    }

    @Test
    void javaxCustomConstraint() throws Exception {
        Validate.ConstraintValidatorField even = rules(JavaxModel.class, "even").get(0);
        assertThat(even.getCode()).isEqualTo("2001");
        assertThat(even.getMessage()).isEqualTo("必须是偶数");
        assertThat(even.getValidator().getApi()).isEqualTo(ConstraintFlavor.JAVAX);
        assertThat(even.getValidator().isValid(3)).isFalse();
        assertThat(even.getValidator().isValid(4)).isTrue();
    }

    @Test
    void nonConstraintAnnotationsAreIgnored() throws Exception {
        assertThat(rules(JavaxModel.class, "plain")).isEmpty();
        assertThat(FACTORY.getAllFieldConstraintValidator(null, new java.lang.annotation.Annotation[0])).isEmpty();
    }

    @Test
    void validatorTypeMustMatchField() throws Exception {
        // @Size 用在 Integer 上没有适用的校验器:跳过,而不是报错
        java.lang.reflect.Field f = JavaxModel.class.getDeclaredField("name");
        assertThat(FACTORY.getAllFieldConstraintValidator(Integer.class, new java.lang.annotation.Annotation[]{
                f.getAnnotation(javax.validation.constraints.Size.class)})).isEmpty();
    }
}
