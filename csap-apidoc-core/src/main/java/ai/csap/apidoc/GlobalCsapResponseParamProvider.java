package ai.csap.apidoc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

import cn.hutool.core.map.MapUtil;

/**
 * @author yangchengfu
 * @description 全局数据接口
 * <p>
 * key：匹配方法
 * value 参数
 * <p/>
 * @dataTime 2020年-03月-01日 17:41:00
 **/
public interface GlobalCsapResponseParamProvider extends Supplier<Map<String, List<CsapDocModel>>> {
    /**
     * 获取全局请求参数
     *
     * @return 默认文档
     */
    @Override
    default Map<String, List<CsapDocModel>> get() {
        return GlobalCsapResponseParamProvider.defaultParam();
    }

    /**
     * 默认分页参数
     *
     * @return 默认但参数列表
     */
    static Map<String, List<CsapDocModel>> defaultParam() {
        return MapUtil.<String, List<CsapDocModel>>builder(ApidocResult.class.getSimpleName(), Lists.newArrayList(CsapDocModel.builder()
                .name(ApidocResult.class.getName())
                .value("返回参数")
                .description("返回参数")
                .modelType(ModelType.OBJECT)
                .global(true)
                .parameters(Arrays.asList(CsapDocParameter.builder()
                                .dataType(String.class.getSimpleName())
                                .defaultValue("0")
                                .description("返回编码")
                                .example("true")
                                .force(true)
                                .name("code")
                                .position(0)
                                .required(true)
                                .value("返回编码")
                                .build(),
                        CsapDocParameter.builder()
                                .dataType(String.class.getSimpleName())
                                .defaultValue("成功")
                                .description("返回的描述")
                                .example("成功")
                                .force(true)
                                .name("message")
                                .position(0)
                                .required(true)
                                .value("返回的描述")
                                .build(),
                        CsapDocParameter.builder()
                                .dataType(Object.class.getSimpleName())
                                .defaultValue("1")
                                .description("返回的数据")
                                .example("1")
                                .force(true)
                                .name("data")
                                .position(0)
                                .required(true)
                                .value("返回的数据")
                                .build(),
                        CsapDocParameter.builder()
                                .dataType(String.class.getSimpleName())
                                .defaultValue("zh")
                                .description("语言")
                                .example("zh")
                                .name("language")
                                .force(true)
                                .position(0)
                                .required(true)
                                .value("语言")
                                .build(),
                        CsapDocParameter.builder()
                                .dataType(Long.class.getSimpleName())
                                .defaultValue("1540483321460")
                                .description("时间")
                                .example("1540483321460")
                                .name("time")
                                .force(true)
                                .position(0)
                                .required(true)
                                .value("时间")
                                .build()))
                .build())).build();
    }
}
