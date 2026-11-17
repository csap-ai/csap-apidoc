package ai.csap.validation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.core.ApiMethodHandle;
import ai.csap.apidoc.type.ModelType;
import ai.csap.apidoc.util.ValidateException;
import ai.csap.validation.ValidateIntercept;
import ai.csap.validation.ValidationViolationException;
import ai.csap.validation.factory.BaseModel;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.factory.IValidateBaseFactory;
import ai.csap.validation.factory.Validate;
import ai.csap.validation.properties.ValidationMode;
import ai.csap.validation.properties.ValidationProperties;
import ai.csap.validation.type.ValidationStrategyType;
import ai.csap.validation.type.ValidationTipType;

/**
 * 运行时校验:规则语义、提示格式、三种模式、按接口覆盖、规则热更新.
 * 用例参照 Go 版 validate_test.go 中与 Java 规则类型(NotNull / NotEmpty / Pattern)对应的部分。
 */
class ValidationExecutorTest {
    private static final String LIST = DemoController.class.getName() + ".list";
    private static final String CREATE = DemoController.class.getName() + ".create";
    private static final String SEARCH = DemoController.class.getName() + ".search";

    private ValidationProperties properties;
    private DefaultValidateFactoryImpl factory;
    private List<ValidationViolation> seen;

    @ApiModel(description = "user")
    public static class DemoUser {
        private String username;
        private String phone;
        private String nickname;

        public DemoUser(String username, String phone) {
            this.username = username;
            this.phone = phone;
        }

        public DemoUser(String username, String phone, String nickname) {
            this(username, phone);
            this.nickname = nickname;
        }

        public String getNickname() {
            return nickname;
        }

        public String getUsername() {
            return username;
        }

        public String getPhone() {
            return phone;
        }
    }

    public static class DemoController {
        @ApiOperation("list")
        public void list(String id, String limit) {
        }

        @ApiOperation("create")
        public void create(DemoUser user) {
        }

        @ApiOperation("search")
        public void search(String keyword, List<String> tags) {
        }

        public void plain(String id) {
        }
    }

    static Validate.ConstraintValidatorField rule(ValidationStrategyType type, String pattern, String code, String message,
                                                  int level) {
        return new Validate.ConstraintValidatorField().setType(type).setPattern(pattern).setCode(code)
                .setMessage(message).setLevel(level);
    }

    @BeforeEach
    void setUp() {
        IValidateBaseFactory.VALIDATE_MAP.clear();
        properties = new ValidationProperties();
        factory = new DefaultValidateFactoryImpl(ConstraintHelper.forAllBuiltinConstraints(), properties);
        seen = new ArrayList<>();
        BaseModel basic = BaseModel.build().modelType(ModelType.BASE_DATA).field(String.class);
        factory.addRequestValidate(LIST, "String", "id", "ID", basic,
                List.of(rule(ValidationStrategyType.NotEmpty, "NotEmpty", "1001", "ID不能为空", 1)));
        factory.addRequestValidate(LIST, "String", "limit", "条数", basic,
                List.of(rule(ValidationStrategyType.Pattern, "^([1-9][0-9]{0,2}|1000)$", "1002", "limit 取值 1-1000", 1)));
        BaseModel model = BaseModel.build().modelType(ModelType.OBJECT).field(String.class);
        // level 小的先执行:NotEmpty(level 1)先于 Pattern(level 2)
        factory.addRequestValidate(CREATE, "DemoUser", "username", "用户名", model, sorted(
                rule(ValidationStrategyType.Pattern, "^.{3,20}$", "1012", "长度3-20", 2),
                rule(ValidationStrategyType.NotEmpty, "NotEmpty", "1011", "用户名不能为空", 1)));
        factory.addRequestValidate(CREATE, "DemoUser", "phone", "手机号", model,
                List.of(rule(ValidationStrategyType.Pattern, "PATTERN_35", "1013", "手机号格式不对", 1)));
    }

    private static List<Validate.ConstraintValidatorField> sorted(Validate.ConstraintValidatorField... rules) {
        List<Validate.ConstraintValidatorField> list = new ArrayList<>(List.of(rules));
        list.sort(java.util.Comparator.comparingInt(Validate.ConstraintValidatorField::getLevel));
        return list;
    }

    private ValidationExecutor executor() {
        ValidateIntercept intercept = new ValidateIntercept(factory);
        return new ValidationExecutor(properties, () -> List.<ApiMethodHandle>of(intercept),
                () -> List.of(seen::add));
    }

    private static MethodParameter param(String method, int index) {
        for (Method m : DemoController.class.getMethods()) {
            if (m.getName().equals(method)) {
                return new MethodParameter(m, index);
            }
        }
        throw new IllegalArgumentException(method);
    }

    /**
     * 拦截模式下校验一次,返回失败(没有失败返回 null)
     */
    private ValidationViolationException check(String method, int index, Object value) {
        properties.setMode(ValidationMode.ENFORCE);
        try {
            executor().validate(param(method, index), value);
            return null;
        } catch (ValidationViolationException e) {
            return e;
        }
    }

    @Test
    void basicParameterRules() {
        Object[][] cases = {
                {0, null, "1001", "字段id,ID不能为空"},
                {0, "", "1001", "字段id,ID不能为空"},
                {0, "1", null, null},
                {1, "0", "1002", "字段limit,limit 取值 1-1000"},
                {1, "1000", null, null},
                // Pattern 遇到 null 不判定失败
                {1, null, null, null},
        };
        for (Object[] c : cases) {
            ValidationViolationException e = check("list", (Integer) c[0], c[1]);
            if (c[2] == null) {
                assertThat(e).as("%s", c[1]).isNull();
            } else {
                assertThat(e).as("%s", c[1]).isNotNull();
                assertThat(e.getCode()).isEqualTo(c[2]);
                assertThat(e.getMessage()).isEqualTo(c[3]);
            }
        }
    }

    @Test
    void modelRulesRunByLevel() {
        assertThat(check("create", 0, new DemoUser(null, "13800000000")).getCode()).isEqualTo("1011");
        assertThat(check("create", 0, new DemoUser("ab", null)).getCode()).isEqualTo("1012");
        // Pattern 写内置正则名 PATTERN_35(手机号)
        ValidationViolationException phone = check("create", 0, new DemoUser("abc", "12345"));
        assertThat(phone.getCode()).isEqualTo("1013");
        assertThat(phone.getField()).isEqualTo("phone");
        assertThat(phone.getRule()).isEqualTo("Pattern");
        assertThat(check("create", 0, new DemoUser("abc", "19912345678"))).isNull();
    }

    @Test
    void tipTypes() {
        Map<ValidationTipType, String> want = Map.of(
                ValidationTipType.TIP_1, "字段id,ID不能为空",
                ValidationTipType.TIP_11, "字段idID不能为空",
                ValidationTipType.TIP_2, "id,ID不能为空",
                ValidationTipType.TIP_22, "idID不能为空",
                ValidationTipType.TIP_3, "ID,ID不能为空",
                ValidationTipType.TIP_33, "IDID不能为空",
                ValidationTipType.TIP_3_1, "ID不能为空");
        want.forEach((tip, message) -> {
            properties.setTipType(tip);
            assertThat(check("list", 0, "").getMessage()).as(tip.name()).isEqualTo(message);
        });
    }

    @Test
    void offIsDefault() {
        assertThat(properties.effectiveMode()).isEqualTo(ValidationMode.OFF);
        assertThat(properties.isActive()).isFalse();
        executor().validate(param("list", 0), null);
        assertThat(seen).isEmpty();
    }

    @Test
    void observeRecordsAndPasses() {
        properties.setMode(ValidationMode.OBSERVE);
        executor().validate(param("list", 0), null);
        assertThat(seen).hasSize(1);
        ValidationViolation v = seen.get(0);
        assertThat(v.getEndpoint()).isEqualTo(LIST);
        assertThat(v.getField()).isEqualTo("id");
        assertThat(v.getRule()).isEqualTo("NotEmpty");
        assertThat(v.getCode()).isEqualTo("1001");
        assertThat(v.getMode()).isEqualTo(ValidationMode.OBSERVE);
    }

    @Test
    void enforceRecordsAndThrows() {
        properties.setMode(ValidationMode.ENFORCE);
        assertThatThrownBy(() -> executor().validate(param("list", 0), null))
                .isInstanceOf(ValidationViolationException.class);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getMode()).isEqualTo(ValidationMode.ENFORCE);
    }

    @Test
    void endpointOverride() {
        properties.setMode(ValidationMode.ENFORCE);
        properties.getEndpoints().put(LIST, ValidationMode.OFF);
        executor().validate(param("list", 0), null);
        assertThat(seen).isEmpty();

        properties.setMode(null);
        properties.getEndpoints().clear();
        properties.getEndpoints().put(LIST, ValidationMode.OBSERVE);
        assertThat(properties.isActive()).isTrue();
        ValidationExecutor executor = executor();
        executor.validate(param("list", 0), null);
        assertThat(seen).hasSize(1);
        // 其他接口仍取全局 off
        executor.validate(param("create", 0), new DemoUser(null, null));
        assertThat(seen).hasSize(1);
    }

    @Test
    void legacyEnabledSwitch() {
        properties.setEnabled(true);
        assertThat(properties.effectiveMode()).isEqualTo(ValidationMode.ENFORCE);
        properties.setMode(ValidationMode.OBSERVE);
        assertThat(properties.effectiveMode()).isEqualTo(ValidationMode.OBSERVE);
        properties.setEnabled(false);
        properties.getEndpoints().put(LIST, ValidationMode.ENFORCE);
        assertThat(properties.effectiveMode()).isEqualTo(ValidationMode.OFF);
        assertThat(properties.modeFor(LIST)).isEqualTo(ValidationMode.OFF);
        assertThat(properties.isActive()).isFalse();
    }

    @Test
    void onlyApiOperationMethods() {
        properties.setMode(ValidationMode.ENFORCE);
        ValidationExecutor executor = executor();
        assertThat(executor.modeFor(param("plain", 0))).isEqualTo(ValidationMode.OFF);
        assertThat(executor.modeFor(param("list", 0))).isEqualTo(ValidationMode.ENFORCE);
    }

    @Test
    void rulesHotReload() {
        properties.setMode(ValidationMode.ENFORCE);
        ValidationExecutor executor = executor();
        MethodParameter id = param("list", 0);
        assertThatThrownBy(() -> executor.validate(id, "")).isInstanceOf(ValidationViolationException.class);
        // DevTools 修改规则:ApiDocService 先 clear 再重新登记
        factory.clear(LIST);
        executor.validate(id, "");
        factory.addRequestValidate(LIST, "String", "id", "ID", BaseModel.build().modelType(ModelType.BASE_DATA).field(String.class),
                List.of(rule(ValidationStrategyType.Pattern, "^\\d+$", "1003", "只能是数字", 1)));
        executor.validate(id, "12");
        assertThatThrownBy(() -> executor.validate(id, "x")).hasMessage("字段id,只能是数字");
    }

    @Test
    void validatorFailureDoesNotBreakRequest() {
        properties.setMode(ValidationMode.ENFORCE);
        ApiMethodHandle broken = (p, v) -> {
            throw new IllegalStateException("boom");
        };
        ValidationExecutor executor = new ValidationExecutor(properties, () -> List.of(broken), () -> List.of(seen::add));
        executor.validate(param("list", 0), null);
        assertThat(seen).isEmpty();
    }

    @Test
    void customHandleValidateExceptionIsAViolation() {
        properties.setMode(ValidationMode.OBSERVE);
        ApiMethodHandle custom = (p, v) -> {
            throw new ValidateException("3001", "自定义");
        };
        new ValidationExecutor(properties, () -> List.of(custom), () -> List.of(seen::add))
                .validate(param("list", 0), null);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getCode()).isEqualTo("3001");
        assertThat(seen.get(0).getRule()).isEqualTo("ValidateException");
    }

    /**
     * 只配了必填、没配校验规则:值为空即失败,默认 code 400、提示「不能为空」(与 Go 版一致)
     */
    @Test
    void requiredWithoutRules() {
        BaseModel basic = BaseModel.build().modelType(ModelType.BASE_DATA).field(String.class);
        factory.addRequestValidate(SEARCH, "String", "keyword", "关键字", basic, List.of());
        factory.addRequestValidate(SEARCH, "String", "tags", "标签", basic, null);
        factory.addRequestValidate(CREATE, "DemoUser", "nickname", "昵称",
                BaseModel.build().modelType(ModelType.OBJECT).field(String.class), new ArrayList<>());

        Object[][] cases = {
                {"search", 0, null, "字段keyword,不能为空"},
                {"search", 0, "", "字段keyword,不能为空"},
                {"search", 0, " ", null},
                {"search", 0, "k", null},
                {"search", 1, List.of(), "字段tags,不能为空"},
                {"search", 1, List.of("a"), null},
                {"create", 0, new DemoUser("abc", "19912345678", null), "字段nickname,不能为空"},
                {"create", 0, new DemoUser("abc", "19912345678", ""), "字段nickname,不能为空"},
                {"create", 0, new DemoUser("abc", "19912345678", "nick"), null},
        };
        for (Object[] c : cases) {
            ValidationViolationException e = check((String) c[0], (Integer) c[1], c[2]);
            if (c[3] == null) {
                assertThat(e).as("%s %s", c[0], c[2]).isNull();
            } else {
                assertThat(e).as("%s %s", c[0], c[2]).isNotNull();
                assertThat(e.getCode()).isEqualTo("400");
                assertThat(e.getMessage()).isEqualTo(c[3]);
                assertThat(e.getRule()).isEqualTo("Required");
            }
        }
        // 有规则时仍按规则,不额外检查必填
        assertThat(check("create", 0, new DemoUser(null, "13800000000", "nick")).getCode()).isEqualTo("1011");
    }

    @Test
    void requiredCodeAndMessageConfigurable() {
        factory.addRequestValidate(SEARCH, "String", "keyword", "关键字",
                BaseModel.build().modelType(ModelType.BASE_DATA).field(String.class), List.of());
        properties.setRequiredCode("4000");
        properties.setRequiredMessage("必填");
        properties.setTipType(ValidationTipType.TIP_3);
        ValidationViolationException e = check("search", 0, null);
        assertThat(e.getCode()).isEqualTo("4000");
        assertThat(e.getMessage()).isEqualTo("关键字,必填");
    }

    @Test
    void requiredWithoutRulesOnlyWhenModeOn() {
        factory.addRequestValidate(SEARCH, "String", "keyword", "关键字",
                BaseModel.build().modelType(ModelType.BASE_DATA).field(String.class), List.of());
        executor().validate(param("search", 0), null);
        assertThat(seen).isEmpty();

        properties.setMode(ValidationMode.OBSERVE);
        executor().validate(param("search", 0), null);
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getRule()).isEqualTo("Required");
        assertThat(seen.get(0).getCode()).isEqualTo("400");
    }
}
