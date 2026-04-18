package ai.csap.apidoc.devtools;

import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.ApiPropertys;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;
import ai.csap.apidoc.devtools.model.api.MethodModel;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;

/**
 * 开发工具API接口控制器
 * 提供开发过程中所需的辅助功能接口
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>获取类的字段信息，用于动态生成文档</li>
 *   <li>控制接口方法的显示和隐藏</li>
 *   <li>辅助开发者快速配置和调试API文档</li>
 * </ul>
 *
 * <p>注意：此Controller通常仅在开发环境启用</p>
 *
 * @author yangchengfu
 * @dataTime 2020年-02月-05日 17:22:00
 **/
@RestController
@RequestMapping("devtools")
@Api(value = "开发工具相关接口")
@AllArgsConstructor
public class DevtoolsController {
    private final ApidocDevtools devtoolsService;

    /**
     * 获取指定类的所有字段信息
     * 用于在开发工具中动态展示类的字段结构
     *
     * <p>功能说明：</p>
     * <ul>
     *   <li>只获取当前类声明的字段，不包括父类字段</li>
     *   <li>支持过滤指定的字段</li>
     *   <li>返回字段的完整信息，包括名称、类型、注解等</li>
     *   <li>支持从方法中获取泛型类型信息（参数或返回值）</li>
     * </ul>
     *
     * <p>使用方式：</p>
     * <ul>
     *   <li>方式1：直接传入 className，获取类的字段（无法获取泛型信息）</li>
     *   <li>方式2：传入 controllerClassName、methodName、parameterIndex，从方法参数中获取泛型类型</li>
     *   <li>方式3：传入 controllerClassName、methodName、parameterIndex=-1，从返回值中获取泛型类型</li>
     * </ul>
     *
     * @param getFieldExcled 字段获取参数，包含类名和需要排除的字段列表
     * @return 字段信息列表
     */
    @ApiOperation(value = "获取指定类所有字段", description = "不包括父类，支持从方法中获取泛型类型", group = "APP")
    @ApiPropertys({
            @ApiProperty(value = "类名称", name = "className", required = false, paramType = ParamType.QUERY),
            @ApiProperty(value = "Controller类名", name = "controllerClassName", required = false, paramType = ParamType.QUERY),
            @ApiProperty(value = "方法名", name = "methodName", required = false, paramType = ParamType.QUERY),
            @ApiProperty(value = "参数索引（-1表示返回值）", name = "parameterIndex", required = false, paramType = ParamType.QUERY)
    })
    @PostMapping("getFields")
    public ApidocResult<List<Field>> getFields(@RequestBody GetFieldExcled getFieldExcled) {
        // 如果指定了方法信息，从方法中获取泛型类型
        if (StrUtil.isNotEmpty(getFieldExcled.getControllerClassName()) &&
                StrUtil.isNotEmpty(getFieldExcled.getMethodName())) {
            try {
                return ApidocResult.success(devtoolsService.getFieldsFromMethod(getFieldExcled));
            } catch (Exception e) {
                // 如果从方法获取失败，回退到使用 className
                if (StrUtil.isEmpty(getFieldExcled.getClassName())) {
                    return ApidocResult.success(Collections.emptyList());
                }
            }
        }

        // 传统方式：使用 className 或 aClass
        if (StrUtil.isEmpty(getFieldExcled.getClassName()) && getFieldExcled.getAClass() == null) {
            return ApidocResult.success(Collections.emptyList());
        }
        return ApidocResult.success(devtoolsService.getFields(getFieldExcled));
    }


    /**
     * 隐藏指定的接口方法
     * 用于在开发工具中动态控制接口的显示和隐藏
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>临时隐藏正在开发中的接口</li>
     *   <li>隐藏内部测试接口</li>
     *   <li>动态调整文档的展示内容</li>
     * </ul>
     *
     * @param methodModel 方法模型，包含要隐藏的方法信息
     * @return true表示操作成功，false表示操作失败
     */
    @ApiOperation(value = "隐藏接口", paramType = ParamType.BODY)
    @PostMapping("hiddenMethod")
    public ApidocResult<Boolean> hiddenMethod(@RequestBody MethodModel methodModel) {
        return ApidocResult.success(devtoolsService.hiddenMethod(methodModel));
    }

}
