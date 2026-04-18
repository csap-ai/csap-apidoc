package ai.csap.apidoc.strategy;

import static ai.csap.apidoc.DbUtilsCrudUtil.count;
import static ai.csap.apidoc.DbUtilsCrudUtil.getConnection;
import static ai.csap.apidoc.DbUtilsCrudUtil.queryList;
import static ai.csap.apidoc.DbUtilsCrudUtil.save;
import static ai.csap.apidoc.DbUtilsCrudUtil.updateByWhere;
import static ai.csap.apidoc.util.IValidate.DOT;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import ai.csap.apidoc.util.ApidocUtils;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.handle.IStandardHandle;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * SQLite 参数分组模式
 *
 * @Author ycf
 * @Date 2025/9/9 23:41
 * @Version 1.0
 */
@Slf4j
public class SQLiteParamGroupStrategy implements ParamGroupStrategy, SQLiteHandle {

    /**
     * 字段验证工厂
     */
    private final IValidateFactory validateFactory;
    /**
     * 配置处理
     */
    private static final Map<String, Map<String, Map<String, ParamGroupMethodProperty>>> HANDLE_MAP = new ConcurrentHashMap<>(16);
    /**
     * 是否创建过数据库文件
     */
    private static final Map<String, File> CREATE_FILE_MAP = new ConcurrentHashMap<>(16);

    public SQLiteParamGroupStrategy(IValidateFactory validateFactory) {
        this.validateFactory = validateFactory;
    }

    /**
     * @param strategyModel 策略对象
     * @return 文件结果
     */
    @SneakyThrows
    private ApidocOptional<File> filterFile(StrategyModel strategyModel, boolean isDevtools) {
        if (CREATE_FILE_MAP.containsKey(strategyModel.getId())) {
            return ApidocOptional.of(CREATE_FILE_MAP.get(strategyModel.getId()));
        }

        Resource[] resources = RESOURCE_PATTERN.getResources(String.format("classpath*:%s/%s", strategyModel.getPath(), strategyModel.getFileName() + getSuffix()));
        if (isDevtools && ArrayUtil.isEmpty(resources)) {
            String path = URLDecoder.decode(pathNameFilter(RESOURCE_PATTERN.getResources("/")[0].getURL()
                                                                                                .getPath()), StandardCharsets.UTF_8);
            String path2 = "classpath*:sqlite-apidoc-example/apidoc-example.db";
            Resource[] resources2 = RESOURCE_PATTERN.getResources(path2);
            File file = new File(path + "/" + strategyModel.getPath() + "/" + strategyModel.getFileName() + getSuffix());
            if (file.exists()) {
                return ApidocOptional.of(file);
            }
            File copy = FileUtil.copyFile(resources2[0].getInputStream(), file);
            log.info("filterFile copy file copy:{} to :{}", resources2[0].getURI(), copy.getPath());
            return ApidocOptional.of(copy);
        }
        if (ArrayUtil.isNotEmpty(resources)) {
            Resource resource = resources[0];
            if (isLocal() && "file".equals(resource.getURL().getProtocol())) {
                return ApidocOptional.ofNullable(new File(pathNameFilter(resource.getFile().getPath())));
            }
            File dbFile = copyResourceToFile(resource, strategyModel.getPath(), strategyModel.getFileName());
            if (Objects.nonNull(dbFile)) {
                CREATE_FILE_MAP.put(strategyModel.getId(), dbFile);
                return ApidocOptional.of(dbFile);
            }
        }
        return ApidocOptional.empty();
    }

    @Override
    public String getName() {
        return ApiStrategyType.SQL_LITE.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.SQL_LITE.getSuffix();
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate requestBasicParams(StrategyModel strategyModel,
                                                                         String keyName, CsapDocMethod docMethod,
                                                                         Method method, String paramName,
                                                                         Parameter parameter) {
        ParamGroupMethodProperty.ParamDataValidate dataValidate = requestGroup(strategyModel,
                keyName + DOT + paramName, docMethod, method, parameter.getType(),
                parameter.getAnnotations());
        if (Objects.nonNull(dataValidate) && Objects.nonNull(ApidocUtils.getParameterAnnotation(parameter, PathVariable.class))) {
            dataValidate.setParamType(ParamType.PATH);
        }
        return dataValidate;
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramRequestGroup(StrategyModel strategyModel, String keyName,
                                                                        CsapDocMethod docMethod, Method method,
                                                                        Field field) {
        return requestGroup(strategyModel, keyName, docMethod, method, field.getType(), field.getAnnotations());
    }

    /**
     * 通用的请求参数处理
     *
     * @param keyName     字段拼接名称
     * @param method      方法
     * @param type        字段类型
     * @param annotations 字段注解
     * @return 方法验证属性
     */
    private ParamGroupMethodProperty.ParamDataValidate requestGroup(StrategyModel strategyModel,
                                                                    String keyName, CsapDocMethod docMethod,
                                                                    Method method, Class<?> type,
                                                                    Annotation[] annotations) {
        return ApidocOptional.ofNullable(paramDataValidate(strategyModel, keyName, docMethod, true))
                             .isNotNullCondition(i -> Objects.nonNull(validateFactory),
                                     i -> i.getValidate().addAll(validateFactory
                                             .getAllFieldConstraintValidator(type, annotations)))
                             .isNotNullCondition(i -> CollectionUtil.isNotEmpty(i.getValidate()),
                                     i -> i.getValidate().sort(Comparator.comparingInt(
                                             Validate.ConstraintValidatorField::getLevel)))
                             .get();
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramResponseGroup(StrategyModel strategyModel,
                                                                         String keyName, CsapDocMethod docMethod,
                                                                         Method method, Field field) {
        return paramDataValidate(strategyModel, keyName, docMethod, false);
    }

    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> getHandle(StrategyModel strategyModel,
                                                                        CsapDocMethod method) {
        return handle(strategyModel, null, null, null);
    }

    /**
     * 将查询结果转换为按类和方法分组的参数映射
     *
     * @param mapList 查询得到的API方法列表
     * @return 分组后的参数映射
     */
    private Map<String, Map<String, ParamGroupMethodProperty>> convertToHandleMap(List<Map<String, Object>> mapList) {
        // 初始化结果Map，使用ConcurrentHashMap保证线程安全
        Map<String, Map<String, ParamGroupMethodProperty>> handleMap = new ConcurrentHashMap<>(16);
        if (CollectionUtil.isEmpty(mapList)) {
            return handleMap;
        }
        for (Map<String, Object> methodMap : mapList) {
            // 获取一级分组键：类名
            String className = (String) methodMap.get("class_name");
            // 获取二级分组键：方法名
            String methodName = (String) methodMap.get("name");
            // 获取请求和响应的JSON字符串
            String requestJson = (String) methodMap.get("request");
            String responseJson = (String) methodMap.get("response");
            // 创建方法属性对象并设置参数
            ParamGroupMethodProperty methodProperty = new ParamGroupMethodProperty();
            methodProperty.setRequest(parseJsonToParams(requestJson));
            methodProperty.setResponse(parseJsonToParams(responseJson));
            // 按类名和方法名层级存入结果Map
            handleMap.computeIfAbsent(className, k -> new HashMap<>(16))
                     .put(methodName, methodProperty);
        }

        return handleMap;
    }

    /**
     * 将JSON字符串解析为参数映射
     *
     * @param json JSON字符串
     * @return 参数映射对象
     */
    private Map<String, ParamGroupMethodProperty.ParamDataValidate> parseJsonToParams(String json) {
        try {
            // 使用FastJSON解析JSON为Map<String, ParamDataValidate>
            return JSON.parseObject(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            // 解析失败时返回空Map
            return Maps.newHashMap();
        }
    }

    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> handle(StrategyModel strategyModel,
                                                                     ApidocStrategyName strategyType, String path,
                                                                     String prefixName) {
        if (!HANDLE_MAP.containsKey(strategyModel.getId())) {
            filterFile(strategyModel, false)
                    .isNullCondition(i -> log.isDebugEnabled(), () -> log.debug("handle get file is null {} {}", strategyModel.getPath(), strategyModel.getFileName()))
                    .isNotNull(i -> {
                        try (Connection connection = getConnection(i.getPath())) {
                            List<Map<String, Object>> mapList = queryList(connection, API_METHOD, Sets.newHashSet("class_name", "simple_name", "name", "request", "response"),
                                    MapUtil.<String, Object>builder().build())
                                    .orElse(null);
                            HANDLE_MAP.computeIfAbsent(strategyModel.getId(), s -> new HashMap<>(16))
                                      .putAll(convertToHandleMap(mapList));
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    });
        }
        return HANDLE_MAP.get(strategyModel.getId());
    }

    private String objToString(Object o) {
        return Objects.isNull(o) ? null : JSON.toJSONString(o);
    }

    @Override
    public void writeAndMerge(StrategyModel strategyModel,
                              Map<String, Map<String, ParamGroupMethodProperty>> dataMap,
                              ApidocStrategyName strategyType, String className, String methodName,
                              Boolean request) {
        filterFile(strategyModel, true)
                .isNullCondition(i -> log.isDebugEnabled(), () -> log.debug("writeAndMerge get file is null {} {}", strategyModel.getPath(), strategyModel.getFileName()))
                .isNotNull(i -> {
                    try (Connection connection = getConnection(i.getPath())) {
                        ParamGroupMethodProperty property = dataMap.get(className).get(methodName);
                        Integer c = count(connection, API_METHOD, MapUtil
                                .<String, Object>builder("class_name", className).put("name", methodName).build())
                                .orElse(0);
                        if (c > 0) {
                            updateByWhere(connection, API_METHOD, MapUtil.<String, Object>builder()
                                                                         .put(request ? "request" : "response", objToString(request ? property.getRequest() : property.getResponse()))
                                                                         .put("update_time", DateUtil.formatLocalDateTime(LocalDateTime.now()))
                                                                         .build(),
                                    MapUtil.<String, Object>builder("class_name", className).put("name", methodName)
                                           .build())
                                    .when(res -> log.isDebugEnabled(), res -> log.debug("writeAndMerge update result is {}", res))
                                    .orElse(0);
                        } else {
                            save(connection, API_METHOD, MapUtil.<String, Object>builder()
                                                                .put(request ? "request" : "response", objToString(request ? property.getRequest() : property.getResponse()))
                                                                .put("class_name", className)
                                                                .put("name", methodName)
                                                                .put("simple_name", IStandardHandle.splitName(className))
                                                                .put("create_time", DateUtil.formatLocalDateTime(LocalDateTime.now()))
                                                                .put("update_time", DateUtil.formatLocalDateTime(LocalDateTime.now()))
                                                                .build())
                                    .when(res -> log.isDebugEnabled(), res -> log.debug("writeAndMerge save result is {}", res))
                                    .orElse(0);
                        }
                        HANDLE_MAP.computeIfAbsent(strategyModel.getId(), s -> new HashMap<>(16)).putAll(dataMap);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
    }

    @Override
    public String fileName() {
        return SQLiteHandle.DEFAULT_DB_NAME;
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }
}
