package ai.csap.apidoc.service.method;

import static ai.csap.apidoc.util.IValidate.DOT;
import static ai.csap.apidoc.util.IValidate.EMPTY;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.csap.apidoc.util.ApidocUtils;
import org.springframework.web.method.HandlerMethod;

import com.google.common.collect.Lists;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.FilterData;
import ai.csap.apidoc.type.CamelCaseType;
import ai.csap.apidoc.type.ModelType;
import ai.csap.apidoc.util.ApidocClazzUtils;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.validation.factory.BaseModel;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 参数默认实现抽象层,这里主要是入口
 *
 * @Author ycf
 * @Date 2025/9/9 15:23
 * @Version 1.0
 */
@Getter
public class DefaultAbstractMethod implements FilterData {
    private final IValidateFactory validateFactory;
    private final ScannerPackageConfig packageConfig;

    public DefaultAbstractMethod(IValidateFactory validateFactory, ScannerPackageConfig packageConfig) {
        this.validateFactory = validateFactory;
        this.packageConfig = packageConfig;
    }

    /**
     * model处理方法
     *
     * @param docMethod 文档方法
     * @param classes   需要处理的class数组
     * @param method    原始方法
     * @param request   是否请求参数
     * @return 返回列表实体类
     */
    public List<CsapDocModel> model(CsapDocMethod docMethod, Type[] classes, Method method, boolean request,
                                    StrategyModel strategyModel, boolean isParent) {
        return model(BaseModel.build().key(docMethod.getKey()), docMethod, classes, method, request, null, true, true,
                strategyModel, isParent);
    }

    /**
     * 实体类处理 方法参数最开始调用
     *
     * @param docMethod         方法参数
     * @param classes           类型
     * @param method            方法
     * @param request           是否请求参数
     * @param typeVariableModel 泛型
     * @param handlerMethod     方法执行
     * @param strategyModel     策略
     * @return 实体类列表
     */
    public List<CsapDocModel> model(CsapDocMethod docMethod, Type[] classes, Method method, boolean request,
                                    TypeVariableModel typeVariableModel, HandlerMethod handlerMethod,
                                    StrategyModel strategyModel, boolean isParent) {
        return model(BaseModel.build().method(method)
                              .key(docMethod.getKey()), docMethod, classes, method,
                request, typeVariableModel, true, true, strategyModel, isParent);
    }

    /**
     * 处理方法参数
     *
     * @param docMethod 方法对象
     * @param classes   方法 参数
     * @param method    方法对象
     * @param add       是否添加 model
     * @param isParent  是否顶层请求
     * @return 结果
     */
    public List<CsapDocModel> model(BaseModel baseModel, CsapDocMethod docMethod, Type[] classes, Method method,
                                    boolean request, TypeVariableModel typeVariableModel, boolean next, boolean add,
                                    StrategyModel strategyModel, boolean isParent) {
        if (ArrayUtil.isEmpty(classes)) {
            return null;
        }
        List<CsapDocModel> list = Lists.newArrayList();
        for (int x = 0; x < classes.length; x++) {
            if (isParent) {
                //处理多参数的时候,如果是顶层参数,需要先清除
                baseModel.clearAppendName();
            }
            Type t = classes[x];
            if ("void".equalsIgnoreCase(t.getTypeName())) {
                continue;
            }
            if (request && add && CollectionUtil.isNotEmpty(docMethod.getParamNames())) {
                baseModel.appendName(docMethod.getParamNames().get(x));
            }
            ModelTypepProperties typeProperties = ApidocClazzUtils.getRawClassType(t, typeVariableModel);
            Class<?> cl = typeProperties.getModelType()
                                        .equals(ModelType.T_OBJECT) ? typeProperties.getRawCl() : typeProperties.getCl();
            if (cl == null) {
                continue;
            }
            if (ApidocClazzUtils.VALIDATE_EXCLUDE_MAP.contains(
                    typeProperties.getRawType()) || ApidocClazzUtils.OBJ.contains(typeProperties.getRawType())) {
                continue;
            }
            ApiModel apiModel = cl.getAnnotation(ApiModel.class);
            boolean n = apiModel == null;
            CsapDocModel docModel;
            boolean params = false;
            if (ApidocClazzUtils.DATA_TYPE.contains(cl.getName()) || ApidocClazzUtils.OTHER_DATA_TYPE.contains(
                    cl.getName()) || Enum.class.isAssignableFrom(cl)) {
                //基础数据类型
                params = true;
                if (request) {
                    Parameter[] parameters = method.getParameters();
                    docModel = methodBasicParams(typeProperties, docMethod, docMethod.getParamNames().get(x),
                            parameters[x], baseModel.getMethod(), strategyModel);
                    docMethod.getParamTypes().add(docModel.getParameters().get(0).getParamType().name());
                } else {
                    docModel = methodReturnBasicType(typeProperties, docMethod);
                }
                //TODO 特殊类型处理--如枚举
                if (Enum.class.isAssignableFrom(cl)) {
                    //TODO 特殊类型处理--如枚举
                    docModel.getParameters().get(0).setExtendDescr(enumData(cl));
                }
            } else {
                //对象类型
                docModel = CsapDocModel.builder().description(n ? "" : apiModel.description()).name(cl.getName())
                                       .value(n ? cl.getName() : apiModel.value())
                                       .modelType(typeProperties.getModelType()).parameters(Lists.newArrayList())
                                       .group(n ? GROUP : set(apiModel.group(), docMethod.getGroup()))
                                       .version(n ? VERSION : set(apiModel.version(), docMethod.getVersion())).build();
            }
            if (add) {
                List<CsapDocModel> docModels = request ? docMethod.getRequest() : docMethod.getResponse();
                docModels.add(docModel);
            }
            list.add(docModel);
            if (params) {
                continue;
            }
            if (!ApidocClazzUtils.OBJ.contains(cl.getName())) {
                if (CollectionUtil.isEmpty(baseModel.getTypes()) && CollectionUtil.isNotEmpty(
                        typeProperties.getClList())) {
                    baseModel.setTypes(typeProperties.getClList());
                } else if (cl.getGenericSuperclass() instanceof ParameterizedType) {
                    baseModel.setTypes(
                            Arrays.asList(((ParameterizedType) cl.getGenericSuperclass()).getActualTypeArguments()));
                }
                getModelField(baseModel.modelClass(cl).appendKeyName(cl.getSimpleName()),
                        ApidocClazzUtils.getAllFields(cl), docMethod, docModel, method, request,
                        typeVariableModel, next, strategyModel);
            }
        }
        return list;
    }


    /**
     * 方法的单参数 基础数据类型
     * 支持两种 @ApiProperty 使用方式：
     * 1. 参数级（优先级最高）：直接标注在参数上
     * 2. 方法级（向后兼容）：标注在方法上，通过 name 匹配
     *
     * @param typepProperties 模型属性
     * @param method          方法
     * @param docMethod       文档方法属性
     * @param parameter       单参数信息
     * @param paramName       参数名称
     * @return CsapDocModel
     */
    public CsapDocModel methodBasicParams(ModelTypepProperties typepProperties, CsapDocMethod docMethod,
                                          String paramName, Parameter parameter, Method method,
                                          StrategyModel strategyModel) {
        CsapDocModel csapDocModel = CsapDocModel.builder()
                .modelType(ModelType.BASE_DATA)
                .name(typepProperties.getCl().getName())
                .parameters(Lists.newArrayList())
                .build();

        // 获取参数注解和验证信息
        ApiProperty property = ApidocUtils.getParameterAnnotation(parameter, ApiProperty.class);
        ParamGroupMethodProperty.ParamDataValidate paramDataValidate = ApidocContext
                .paramStrategy(strategyModel.getParamType())
                .requestBasicParams(strategyModel, paramName, docMethod, method, paramName, parameter);

        // 计算 required 属性（优先级：property.required > paramDataValidate.required）
        Boolean required = paramDataValidate != null && paramDataValidate.getRequired()
                || property != null && property.required();

        // 计算 description（优先级：paramDataValidate.description > property.description）
        String description = paramDataValidate != null && !StrUtil.isEmpty(paramDataValidate.getDescription())
                ? paramDataValidate.getDescription() : (property != null ? property.description() : "");

        // 添加验证规则
        if (paramDataValidate != null && paramDataValidate.getRequired()) {
            String value = property != null ? property.value() : EMPTY;
            BaseModel baseModel = BaseModel.build().modelType(ModelType.BASE_DATA).field(parameter.getType());
            getValidateFactory().addRequestValidate(docMethod.getKey(), parameter.getType().getSimpleName(),
                    paramName, value, baseModel, paramDataValidate.getValidate());
        }

        // 构建参数文档
        CsapDocParameter csapDocParameter = CsapDocParameter.builder()
                .dataType(typepProperties.getSubStr())
                .keyName(paramName + DOT + paramName)
                .longDataType(parameter.getType().getName())
                .name((property != null && StrUtil.isNotEmpty(property.name())) ? property.name() : paramName)
                .description(description)
                .required(required)
                .hidden(property != null && property.hidden())
                .defaultValue(property != null ? property.defaultValue() : "")
                .example(property != null ? property.example() : "")
                .value(property != null ? property.value() : "")
                .group(property != null ? set(property.group(), docMethod.getGroup()) : GROUP)
                .version(property != null ? set(property.version(), docMethod.getVersion()) : VERSION)
                .position(property != null ? property.position() : 0)
                .length(property != null ? property.length() : 0)
                .decimals(property != null ? property.decimals() : 0)
                .paramType(paramDataValidate != null && paramDataValidate.getParamType() != null
                        ? paramDataValidate.getParamType() : docMethod.getParamType())
                .build();

        csapDocParameter.setKey(getKey(csapDocParameter));
        csapDocModel.getParameters().add(csapDocParameter);
        return csapDocModel;
    }


    /**
     * 获取方法可用字段
     *
     * @param fields    字段列表
     * @param docMethod 方法对象
     * @param docModel  model对象
     * @param method    方法
     * @param request   是否request参数
     */
    public void getModelField(BaseModel types, List<Field> fields, CsapDocMethod docMethod, CsapDocModel docModel,
                              Method method, boolean request, TypeVariableModel typeVariableModel, boolean next,
                              StrategyModel strategyModel) {
        for (Field field : fields) {
            ApiModelProperty property = field.getAnnotation(ApiModelProperty.class);
            if (property == null) {
                continue;
            }
            if (property.hidden()) {
                continue;
            }
            if (request ? property.ignoreReq() : property.ignoreRep()) {
                continue;
            }
            String fileName = StrUtil.isNotEmpty(property.name()) ? property.name() : field.getName();
            boolean filterField = request ? request(method.getName(), fileName, docModel) : response(method.getName(),
                    fileName,
                    docModel);
            if (filterField || (request ? property.forceReq() : property.forceRep())) {
                group(types, field, docMethod, docModel, method, request, typeVariableModel, property, Boolean.TRUE,
                        next, strategyModel);
            } else {
                group(types, field, docMethod, docModel, method, request, typeVariableModel, property, Boolean.FALSE,
                        next, strategyModel);

            }
        }
    }

    /**
     * 请求参数匹配处理
     *
     * @param methodName 方法名称
     * @param fieldName  字段名称
     * @param docModel   对象参数
     * @return 是否匹配
     */
    protected boolean request(String methodName, String fieldName, CsapDocModel docModel) {
        return getPackageConfig().getConfig().getResult().containsPageMethod(methodName) && getPackageConfig()
                .getConfig().getResult().getRequestField().contains(fieldName);
    }

    /**
     * 返回参数匹配处理
     *
     * @param methodName 方法名称
     * @param fieldName  字段名称
     * @param docModel   对象参数
     * @return 是否匹配
     */
    protected boolean response(String methodName, String fieldName, CsapDocModel docModel) {
        boolean r = getPackageConfig().getConfig().getResult()
                                      .containsResultPageMethod(methodName) && getPackageConfig().getConfig()
                                                                                                 .getResult()
                                                                                                 .getResponseField()
                                                                                                 .contains(
                                                                                                         fieldName) && !getPackageConfig()
                .getConfig().getResult().getResponseExcludes().contains(fieldName);
        if (CollectionUtil.isEmpty(getPackageConfig().getConfig().getResult().getResponseClassName())) {
            return r;
        }
        return r && getPackageConfig().getConfig().getResult().getResponseClassName().contains(docModel.getName());
    }

    /**
     * 处理 group
     *
     * @param field             字段信息
     * @param docMethod         方法
     * @param docModel          文档模型
     * @param method            方法具体实例
     * @param request           是否请求参数
     * @param typeVariableModel 泛型使用（带有真实对象）
     * @param property          属性
     * @param force             强制存在
     * @param next              是否执行下一步
     */
    protected void group(BaseModel types, Field field, CsapDocMethod docMethod,
                         CsapDocModel docModel, Method method,
                         boolean request, TypeVariableModel typeVariableModel,
                         ApiModelProperty property, Boolean force,
                         boolean next, StrategyModel strategyModel) {
        boolean exits;
        CsapDocParameter docParameter = CsapDocParameter.builder().defaultValue(property.defaultValue())
                                                        .description(property.description()).hidden(property.hidden())
                                                        .example(property.example()).name(StrUtil.isEmpty(
                        property.name()) ? field.getName() : property.name()).position(property.position())
                                                        .value(property.value()).paramType(property.paramType())
                                                        .required(property.required())
                                                        .group(ArrayUtil.isEmpty(property.group()) ? GROUP : set(
                                                                property.group(), docModel.getGroup())).version(
                        ArrayUtil.isEmpty(property.version()) ? VERSION : set(property.version(),
                                docModel.getVersion()))
                                                        .length(property.length()).decimals(property.decimals())
                                                        .extendDescr(enumData(field.getType())).build();
        if (docMethod.getParamType().compareTo(ParamType.DEFAULT) != 0) {
            docParameter.setParamType(docMethod.getParamType());
        }
        docParameter.setKey(getKey(docParameter));
        ModelTypepProperties fileType = getFildType(types.getTypes(), field, typeVariableModel);
        docParameter.setDataType(fileType.getSubStr());
        docParameter.setModelType(fileType.getModelType());
        Validate.ValidateField validateField = null;
        if (!request) {
            String keyName = types.getAppendName() + docParameter.getName();
            ParamGroupMethodProperty.ParamDataValidate responseGroup = ApidocContext
                    .paramStrategy(strategyModel.getParamType())
                    .paramResponseGroup(strategyModel, keyName, docMethod, types.getMethod(), field);
            exits = force || responseGroup != null && responseGroup.getInclude();
            if (exits) {
                docParameter.setKeyName(keyName);
                docModel.getParameters().add(docParameter);
                getValidateFactory().addIncludes(docMethod.getKey(), docParameter.getName(), types.getModelClass());

                // 处理 responseType 配置：如果不是 none，需要添加转换后的字段
                CamelCaseType responseType = getPackageConfig().getConfig().getResponseType();
                if (responseType != null && responseType != CamelCaseType.none) {
                    String fieldName = docParameter.getName();
                    String convertedName = null;

                    // 判断当前字段格式：包含下划线认为是下划线格式，包含大写字母且不包含下划线认为是驼峰格式
                    boolean isUnderlineCase = fieldName.contains("_");
                    boolean isCamelCase = !isUnderlineCase && fieldName.matches(".*[A-Z].*");

                    // 根据配置添加转换后的字段
                    if (responseType == CamelCaseType.toCamelCase && isUnderlineCase) {
                        // 当前是下划线格式，配置要求转驼峰，添加驼峰格式字段
                        convertedName = StrUtil.toCamelCase(fieldName);
                    } else if (responseType == CamelCaseType.toUnderlineCase && isCamelCase) {
                        // 当前是驼峰格式，配置要求转下划线，添加下划线格式字段
                        convertedName = StrUtil.toUnderlineCase(fieldName);
                    }

                    // 如果转换后的字段名与原字段名不同，添加转换后的字段验证信息
                    if (convertedName != null && !convertedName.equals(fieldName)) {
                        getValidateFactory().addIncludes(docMethod.getKey(), convertedName, types.getModelClass());
                    }
                }
            } else {
                getValidateFactory().addExcludes(docMethod.getKey(), docParameter.getName(), types.getModelClass());
            }
            if (responseGroup != null) {
                docParameter.setRequired(responseGroup.getRequired());
            } else {
                docParameter.setRequired(property.required());
            }
        } else {
            String keyName = types.getAppendName() + docParameter.getName();
            ParamGroupMethodProperty.ParamDataValidate requestGroup = ApidocContext
                    .paramStrategy(strategyModel.getParamType())
                    .paramRequestGroup(strategyModel, keyName, docMethod, types.getMethod(), field);
            exits = force || requestGroup != null;
            if (exits) {
                docParameter.setKeyName(keyName);
                docModel.getParameters().add(docParameter);
                docMethod.getParamTypes().add(docParameter.getParamType().name());
                boolean required = property.required();
                List<Validate.ConstraintValidatorField> validate = null;
                if (requestGroup != null) {
                    required = requestGroup.getRequired();
                    if (requestGroup.getParamType() != null) {
                        docParameter.setParamType(requestGroup.getParamType());
                    }
                    validate = requestGroup.getValidate();
                } else {
                    validate = getValidateFactory().getAllFieldConstraintValidator(field.getType(),
                            field.getAnnotations());
                }
                if (required) {
                    BaseModel baseModel = types.field(field.getType()).modelType(docModel.getModelType());
                    validateField = getValidateFactory().addRequestValidate(docMethod.getKey(), types
                                    .keyName(types.getModelClass().getSimpleName()).toStringKeyName(), docParameter.getName(),
                            docParameter.getValue(), baseModel,
                            validate);
                }
                if (Objects.nonNull(validate)) {
                    docParameter.setValidate(validate.stream().map(i -> CsapDocParameter.ValidateAttribute.builder()
                                                                                                          .code(i.getCode())
                                                                                                          .level(i.getLevel())
                                                                                                          .descr(i.getDescr())
                                                                                                          .type(Objects.nonNull(
                                                                                                                  i.getType()) ? i
                                                                                                                  .getType()
                                                                                                                  .getDescr() : null)
                                                                                                          .message(
                                                                                                                  i.getMessage())
                                                                                                          .pattern(
                                                                                                                  i.getPattern())
                                                                                                          .build())
                                                     .collect(Collectors.toList()));
                }
                docParameter.setRequired(required);
            }
        }
        //处理对象使用
        if (exits && !ApidocClazzUtils.DATA_TYPE.contains(fileType.getRawType())) {
            if (Enum.class.isAssignableFrom(fileType.getRawCl())) {
                // TODO 特殊类型处理--如枚举
                docParameter.setExtendDescr(enumData(fileType.getRawCl()));
                return;
            }
            if (ApidocClazzUtils.LIST.contains(fileType.getRawType())) {
                Type[] type = ((ParameterizedType) fileType.getType()).getActualTypeArguments();
                if (ArrayUtil.isNotEmpty(type)) {
                    Class<?>[] cl = new Class<?>[]{fileType.getCl()};
                    if (!next) {
                        return;
                    }
                    if (ApidocClazzUtils.DATA_TYPE.contains(
                            cl[0].getName()) || ApidocClazzUtils.OTHER_DATA_TYPE.contains(cl[0].getName())) {
                        //处理基本数据类
                        return;
                    }
                    if (cl[0].getName().equals(docModel.getName())) {
                        next = false;
                    }
                    List<CsapDocModel> list = model(
                            types.clone().appendName(field.getName()).appendKeyName(field.getName())
                                 .modelType(fileType.getModelType())
                                 .validateField(validateField == null ? null : validateField.getChildren())
                                 .types(fileType.getClList()), docMethod, cl, method, request, typeVariableModel, next,
                            false, strategyModel, false);
                    if (CollectionUtil.isNotEmpty(list)) {
                        list.get(0).setModelType(ModelType.ARRAY);
                        docParameter.setChildren(list.get(0));
                    }
                }
            } else {
                if (!next) {
                    return;
                }
                if (fileType.getRawType().equals(docModel.getName())) {
                    next = false;
                }
                Type[] types1;
                if (Map.class.isAssignableFrom(fileType.getRawCl())) {
                    if (CollectionUtil.isEmpty(fileType.getClList()) || fileType.getClList().size() != 2) {
                        return;
                    }
                    types1 = new Type[]{fileType.getClList().get(1)};
                } else if (ApidocClazzUtils.OBJ.contains(fileType.getRawType())) {
                    return;
                } else {
                    types1 = new Type[]{fileType.getModelType()
                                                .equals(ModelType.T_OBJECT) ? fileType.getRawCl() : fileType.getCl()};
                }
                List<CsapDocModel> list = model(types.clone().appendName(field.getName()).appendKeyName(field.getName())
                                                     .modelType(fileType.getModelType()).types(fileType.getClList())
                                                     .validateField(
                                                             validateField == null ? null : validateField.getChildren()),
                        docMethod, types1, method, request, typeVariableModel, next, false,
                        strategyModel, false);
                if (CollectionUtil.isNotEmpty(list)) {
                    docParameter.setChildren(list.get(0));
                }
            }
        }
    }
}
