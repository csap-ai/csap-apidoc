package ai.csap.apidoc.devtools;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.devtools.model.api.MethodModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.util.ExceptionUtils;
import ai.csap.validation.type.ValidatePatternType;

import cn.hutool.core.collection.CollectionUtil;
import lombok.RequiredArgsConstructor;

/**
 * @Author ycf
 * @Date 2021/11/8 4:13 下午
 * @Version 1.0
 */
@RequestMapping("csap/yaml")
@RestController
@RequiredArgsConstructor
public class ApidocStrategyController {
    private final EnableApidocConfig enableApidocConfig;
    private final ApidocDevtools apidocDevtools;

    /**
     * 获取接口策略
     *
     * @return 结果
     */
    private ApidocStrategy getApidocStrategy() {
        if (enableApidocConfig.getType().equals(ApiStrategyType.JSON)) {
            return ApidocContext.apidocStrategy(ApiStrategyType.JSON);
        } else if (enableApidocConfig.getType().equals(ApiStrategyType.YAML)) {
            return ApidocContext.apidocStrategy(ApiStrategyType.YAML);
        } else {
            throw ExceptionUtils.mpe("当前配置不支持yaml或者/json形式的文档");
        }
    }

    /**
     * 写入文档
     *
     * @param yamlProperties 属性
     * @return 结果
     */
    @PostMapping("write")
    public ApidocResult<Boolean> write(@RequestBody StandardProperties yamlProperties) {
        return ApidocResult.success(getApidocStrategy().write(yamlProperties));
    }

    /**
     * 写入选择的类文档
     *
     * @param classSet 选择的类列表
     * @return 结果
     */
    @PostMapping("writeSelect")
    public ApidocResult<Boolean> writeSelect(@RequestBody Set<String> classSet) {
        if (CollectionUtil.isEmpty(classSet)) {
            return ApidocResult.success(getApidocStrategy().write(apidocDevtools.writeSelectController()));
        } else {
            return ApidocResult.success(getApidocStrategy().write(apidocDevtools.writeSelectController(classSet)));
        }
    }

    /**
     * 只写入字段和验证信息
     *
     * @param methodFieldMap 字段信息
     * @return 结果
     */
    @PostMapping("writeFieldMethod")
    public ApidocResult<Boolean> writeFieldMethod(@RequestBody Map<String, Map<String, ParamGroupMethodProperty>> methodFieldMap) {
        return ApidocResult.success(getApidocStrategy().write(StandardProperties.builder().methodFieldMap(methodFieldMap).build()));
    }

    /**
     * 获取扫描类下的所有api
     *
     * @param className 类名称
     * @return 结果
     */
    @GetMapping("scannerApi")
    public ApidocResult<Collection<CsapDocModelController>> scannerApi(String className) {
        return ApidocResult.success(apidocDevtools.scannerApi(className).values());
    }

    /**
     * 获取指定类下的所有方法
     *
     * @param className class名称
     * @return 结果
     */
    @GetMapping("getAllMethod")
    public ApidocResult<Collection<CsapDocMethod>> getAllMethod(String className) {
        return ApidocResult.success(apidocDevtools.getAllMethod(className).values());
    }

    @ApiOperation(value = "添加修改一个API请求参数", description = "添加修改同时可用", paramType = ParamType.BODY)
    @PostMapping("addRequestParam")
    public ApidocResult<Boolean> addRequestParam(@RequestBody MethodModel methodModel) {
        return ApidocResult.success(apidocDevtools.addMethodParam(methodModel, true));
    }

    @ApiOperation(value = "添加修改一个API返回参数", description = "添加修改同时可用", paramType = ParamType.BODY)
    @PostMapping("addResponseParam")
    public ApidocResult<Boolean> addResponseParam(@RequestBody MethodModel methodModel) {
        return ApidocResult.success(apidocDevtools.addMethodParam(methodModel, false));
    }

    /**
     * 获取方法字段验证信息
     *
     * @param className  类名
     * @param methodName 方法名
     * @return 结果
     */
    @GetMapping("getMethodValidateFields")
    public ApidocResult<ParamGroupMethodProperty.ParamDataValidate> getMethodValidateFields(@RequestParam String className,
                                                                                            @RequestParam String methodName,
                                                                                            @RequestParam String fieldName) {
        return ApidocResult.success(ApidocOptional.ofNullable(apidocDevtools.getMethodValidateFields(className, methodName, fieldName))
                .orElseGet(ParamGroupMethodProperty.ParamDataValidate::new));
    }

    /**
     * 验证的枚举类型列表
     *
     * @return 结果
     */
    @GetMapping("validatePatternTypes")
    public ApidocResult<ValidatePatternType.PatternMap> validatePatternTypes() {
        ValidatePatternType.PatternMap patternMap = ValidatePatternType.PatternMap.builder()
                .patternList(ValidatePatternType.getAll())
                .patternTypeList(ValidatePatternType.PatternMap.PATTERN_DESCR)
                .build();
        return ApidocResult.success(patternMap);
    }
}
