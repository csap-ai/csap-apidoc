package ai.csap.apidoc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import ai.csap.apidoc.annotation.ParamType;
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
public interface GlobalCsapRequestParamProvider extends Supplier<Map<String, List<CsapDocModel>>> {
    /**
     * 获取全局请求参数
     *
     * @return 默认文档
     */
    @Override
    default Map<String, List<CsapDocModel>> get() {
        return GlobalCsapRequestParamProvider.defaultParam();
    }

    /**
     * 默认分页参数
     *
     * @return 默认但参数列表
     */
    static Map<String, List<CsapDocModel>> defaultParam() {
        return MapUtil.<String, List<CsapDocModel>>builder().put("Page", Lists.newArrayList(CsapDocModel.builder()
                .name("com.csap.mybatisplus.page.Page")
                .value("分页参数")
                .description("分页参数")
                .modelType(ModelType.OBJECT)
                .global(true)
                .parameters(
                        Arrays.asList(CsapDocParameter.builder()
                                        .dataType(Boolean.class.getSimpleName())
                                        .defaultValue("true")
                                        .description("是否查询总条数")
                                        .example("true")
                                        .force(true)
                                        .name("isSearchCount")
                                        .paramType(ParamType.QUERY)
                                        .position(0)
                                        .required(false)
                                        .value("是否查询总条数")
                                        .build(),
                                CsapDocParameter.builder()
                                        .dataType(Long.class.getSimpleName())
                                        .defaultValue("20")
                                        .description("每页显示条数")
                                        .example("20")
                                        .force(true)
                                        .length(11)
                                        .name("pageSize")
                                        .paramType(ParamType.QUERY)
                                        .position(0)
                                        .required(false)
                                        .value("每页显示条数")
                                        .build(),
                                CsapDocParameter.builder()
                                        .dataType(Long.class.getSimpleName())
                                        .defaultValue("1")
                                        .description("当前页数")
                                        .example("1")
                                        .length(11)
                                        .force(true)
                                        .name("currentPage")
                                        .paramType(ParamType.QUERY)
                                        .position(0)
                                        .required(false)
                                        .value("当前页数")
                                        .build(),
                                CsapDocParameter.builder()
                                        .dataType("String[]")
                                        .defaultValue("create_time")
                                        .description("排序字段 ")
                                        .example("GET:?column=create_time&column=id POST:asc:['create_time','id']")
                                        .name("column")
                                        .force(true)
                                        .paramType(ParamType.QUERY)
                                        .position(0)
                                        .required(false)
                                        .value("排序字段")
                                        .build(),
                                CsapDocParameter.builder()
                                        .dataType("Boolean[]")
                                        .defaultValue(Boolean.TRUE.toString())
                                        .description("是否正序排列 接收数组形式参数,该参数和cloum排序字段一一对应")
                                        .example("GET:?asc=true&asc=true POST:asc:[true,false]")
                                        .name("asc")
                                        .force(true)
                                        .paramType(ParamType.QUERY)
                                        .position(0)
                                        .required(false)
                                        .value("是否正序排列")
                                        .build()))
                .build())).build();
    }
}
