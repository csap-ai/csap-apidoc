package ai.csap.validation.boot.autoconfigure.web;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import ai.csap.apidoc.type.ModelType;
import ai.csap.validation.factory.BaseModel;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;
import ai.csap.validation.runtime.ValidationViolation;
import ai.csap.validation.runtime.ViolationRecorder;
import ai.csap.validation.type.ValidationStrategyType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 测试应用.规则按 csap-apidoc-core 生成文档时的方式登记:
 * list.id 相当于 YAML / SQLite 中写的 NotEmpty 规则,create 的字段规则来自 DemoUser 上的约束注解。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(DemoController.class)
public class DemoApplication {
    public static final String LIST = DemoController.class.getName() + ".list";
    public static final String CREATE = DemoController.class.getName() + ".create";

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public CapturingRecorder capturingRecorder() {
        return new CapturingRecorder();
    }

    @Bean
    public Object csapTestRules(IValidateFactory factory) {
        IValidateFactory.VALIDATE_MAP.clear();
        BaseModel basic = BaseModel.build().modelType(ModelType.BASE_DATA).field(String.class);
        factory.addRequestValidate(LIST, "String", "id", "ID", basic, List.of(new Validate.ConstraintValidatorField()
                .setType(ValidationStrategyType.NotEmpty).setCode("1001").setMessage("ID不能为空")));
        for (String name : List.of("username", "phone", "age")) {
            Field field = org.springframework.util.ReflectionUtils.findField(DemoUser.class, name);
            factory.addRequestValidate(CREATE, "DemoUser", name, name,
                    BaseModel.build().modelType(ModelType.OBJECT).field(field.getType()),
                    factory.getAllFieldConstraintValidator(field.getType(), field.getAnnotations()));
        }
        return new Object();
    }

    /**
     * 自定义记录器,与内置的日志 / Micrometer 记录器并存
     */
    public static class CapturingRecorder implements ViolationRecorder {
        public final List<ValidationViolation> violations = new ArrayList<>();

        @Override
        public synchronized void record(ValidationViolation violation) {
            violations.add(violation);
        }
    }
}
