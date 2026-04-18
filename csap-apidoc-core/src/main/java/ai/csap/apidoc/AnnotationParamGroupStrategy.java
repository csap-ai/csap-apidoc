package ai.csap.apidoc;

import static ai.csap.apidoc.util.IValidate.DOT;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import ai.csap.apidoc.util.ApidocUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.FilterData;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.validation.factory.IValidateFactory;


/**
 * 注解参数
 *
 * @Author ycf
 * @Date 2021/11/17 11:27 上午
 * @Version 1.0
 */
public class AnnotationParamGroupStrategy implements ParamGroupStrategy, FilterData {
    private final IValidateFactory validateFactory;

    public AnnotationParamGroupStrategy(IValidateFactory validateFactory) {
        this.validateFactory = validateFactory;
    }

    @Override
    public String getName() {
        return ApiStrategyType.ANNOTATION.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.ANNOTATION.getSuffix();
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }

    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> getHandle(StrategyModel strategyModel,
                                                                        CsapDocMethod method) {
        return Map.of();
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate requestBasicParams(StrategyModel strategyModel,
                                                                         String keyName,
                                                                         CsapDocMethod docMethod,
                                                                         Method method,
                                                                         String paramName,
                                                                         Parameter parameter) {
        ApiProperty property = ApidocUtils.getParameterAnnotation(parameter, ApiProperty.class);
        if (property == null) {
            return null;
        }
        return ParamGroupMethodProperty.ParamDataValidate.builder()
                                                         .required(property.required())
                                                         .paramType(Objects.nonNull(parameter.getAnnotation(PathVariable.class)) ? ParamType.PATH : property.paramType())
                                                         .validate(validateFactory.getAllFieldConstraintValidator(parameter.getType(), parameter.getAnnotations()))
                                                         .build();
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramRequestGroup(StrategyModel strategyModel, String keyName,
                                                                        CsapDocMethod docMethod, Method method,
                                                                        Field field) {
        return filter(field.getAnnotation(ApiModelProperty.class)
                           .groups(), docMethod, i -> ParamGroupMethodProperty.ParamDataValidate.builder()
                                                                                                .required(i.request()
                                                                                                           .required())
                                                                                                .paramType(i.request()
                                                                                                            .paramType())
                                                                                                .validate(validateFactory.getAllFieldConstraintValidator(field.getType(), field.getAnnotations()))
                                                                                                .build());
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramResponseGroup(StrategyModel strategyModel, String keyName,
                                                                         CsapDocMethod docMethod, Method method,
                                                                         Field field) {
        return filter(field.getAnnotation(ApiModelProperty.class)
                           .groups(), docMethod, i -> ParamGroupMethodProperty.ParamDataValidate.builder()
                                                                                                .required(i.response()
                                                                                                           .required())
                                                                                                .include(i.response()
                                                                                                          .include())
                                                                                                .build());
    }

    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> handle(StrategyModel strategyModel,
                                                                     ApidocStrategyName strategyType, String path,
                                                                     String prefixName) {
        return Map.of();
    }

    @Override
    public void writeAndMerge(StrategyModel strategyModel, Map<String, Map<String, ParamGroupMethodProperty>> dataMap,
                              ApidocStrategyName strategyType,
                              String className, String methodName, Boolean request) {

    }


    /**
     * 过滤信息.
     *
     * @param groups 分组
     * @param method 方法
     * @param map    函数
     * @return 参数信息
     */
    private ParamGroupMethodProperty.ParamDataValidate filter(Group[] groups, CsapDocMethod method,
                                                              Function<Group, ParamGroupMethodProperty.ParamDataValidate> map) {
        return Stream.of(groups).filter(i -> isEq(i, method)).map(map).findFirst().orElse(null);
    }

    /**
     * 条件判断
     *
     * @param group  分组
     * @param method 方法
     * @return 是否符合
     */
    public boolean isEq(Group group, CsapDocMethod method) {
        //是否有点--标识带有类名
        if (group.value().contains(DOT)) {
            return group.value().equals(method.getSimpleName() + DOT + method.getName());
        } else {
            return group.value().equals(method.getName());
        }
    }

}
