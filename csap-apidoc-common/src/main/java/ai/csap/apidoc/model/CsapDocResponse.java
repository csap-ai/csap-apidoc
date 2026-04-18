package ai.csap.apidoc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.properties.CsapApiInfo;

import cn.hutool.core.collection.CollectionUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author yangchengfu
 * @description 文档接口返回参数
 * @dataTime 2019年-12月-29日 15:31:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(value = "文档返回信息")
@Accessors(chain = true)
public class CsapDocResponse {
    /**
     * api的基本信息，用户自定义
     */
    private CsapApiInfo apiInfo;
    /**
     * 文档资源
     */
    private List<CsapDocResource> resources;
    /**
     * 当前项目所有的api
     */
    private List<CsapDocModelController> apiList = Collections.synchronizedList(new ArrayList<>());
    /**
     * 所有枚举
     */
    private List<CsapDocEnum> docEnumList = new ArrayList<>();
    /**
     * 枚举列表
     */
    private Map<String, List<Map<String, Object>>> enumList;
    /**
     * 全局的api
     */
    private List<CsapDocModelController> globalApiList;

    /**
     * 全局请求参数
     */
    private List<CsapDocModel> globalRequestParam;

    /**
     * 所有 API分组
     */
    private Set<String> groups = ConcurrentHashMap.newKeySet();

    /**
     * 所有 API版本
     */
    private Set<String> versions = ConcurrentHashMap.newKeySet();

    /**
     * 获取目标 controller
     *
     * @param apiLists               api列表
     * @param csapDocModelController 操作的controller
     * @return 结果
     */
    private CsapDocModelController findController(List<CsapDocModelController> apiLists,
                                                   CsapDocModelController csapDocModelController) {
        return IntStream.range(0, apiLists.size())
                .filter(i -> apiLists.get(i) != null &&
                        apiLists.get(i).getName().equals(csapDocModelController.getName()))
                .mapToObj(apiLists::get)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取目标 controller
     *
     * @param methods api列表
     * @param method  操作的controller
     * @return 结果
     */
    private CsapDocMethod findMethodList(List<CsapDocMethod> methods, CsapDocMethod method) {
        if (Objects.isNull(method)) {
            return null;
        }
        return IntStream.range(0, methods.size())
                .filter(i -> methods.get(i) != null &&
                        methods.get(i).getKey().equals(method.getKey()))
                .mapToObj(methods::get)
                .findFirst()
                .orElse(null);
    }

    /**
     * 刷新API列表
     *
     * @param apiLists api列表
     * @return 结果
     */
    public CsapDocResponse flushApiList(List<CsapDocModelController> apiLists, StrategyModel strategyModel) {
        if (CollectionUtil.isEmpty(apiList)) {
            apiList.addAll(apiLists);
            return this;
        }
        if (strategyModel.getFlush()) {
            apiList.addAll(apiLists);
            return this;
        }
        CsapDocModelController controller;
        for (CsapDocModelController csapDocModelController : apiList) {
            controller = findController(apiLists, csapDocModelController);
            if (controller != null) {
                csapDocModelController.setMethodList(controller.getMethodList());
            }
        }
        return this;
    }

    /**
     * 添加枚举列表
     *
     * @param docEnumLists 列表
     * @return 结果
     */
    public CsapDocResponse addEnumList(List<CsapDocEnum> docEnumLists) {
        if (CollectionUtil.isNotEmpty(docEnumLists)) {
            docEnumList.addAll(docEnumLists);
        }
        return this;
    }

    public void sortApi() {
        if (CollectionUtil.isNotEmpty(apiList)) {
            apiList = apiList.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(CsapDocModelController::getPosition)
                            .thenComparing(CsapDocModelController::getName))
                    .collect(Collectors.toList());
        }

    }
}
