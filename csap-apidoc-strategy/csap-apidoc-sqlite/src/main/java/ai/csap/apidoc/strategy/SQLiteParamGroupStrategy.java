package ai.csap.apidoc.strategy;

import static ai.csap.apidoc.DbUtilsCrudUtil.count;
import static ai.csap.apidoc.DbUtilsCrudUtil.getConnection;
import static ai.csap.apidoc.DbUtilsCrudUtil.queryList;
import static ai.csap.apidoc.DbUtilsCrudUtil.save;
import static ai.csap.apidoc.DbUtilsCrudUtil.updateByWhere;

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
import ai.csap.apidoc.SQLiteSchema;
import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.handle.IStandardHandle;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.util.RuleStorageNotWritableException;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
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
        if (!isLocal()) {
            // 打包运行(jar):classpath 不可写,规则保存在 jar 外、启动目录下的 SQLite 文件
            File external = externalFile(strategyModel);
            if (external.isFile()) {
                CREATE_FILE_MAP.put(strategyModel.getId(), external);
                return ApidocOptional.of(external);
            }
            if (isDevtools) {
                return ApidocOptional.of(createExternalFile(strategyModel, external));
            }
        }
        if (CREATE_FILE_MAP.containsKey(strategyModel.getId())) {
            return ApidocOptional.of(CREATE_FILE_MAP.get(strategyModel.getId()));
        }

        Resource[] resources = RESOURCE_PATTERN.getResources(String.format("classpath*:%s/%s", strategyModel.getPath(), strategyModel.getFileName() + getSuffix()));
        if (isDevtools && ArrayUtil.isEmpty(resources)) {
            String path = URLDecoder.decode(pathNameFilter(RESOURCE_PATTERN.getResources("/")[0].getURL()
                                                                                                .getPath()), StandardCharsets.UTF_8);
            File file = new File(path + "/" + strategyModel.getPath() + "/" + strategyModel.getFileName() + getSuffix());
            if (file.exists()) {
                return ApidocOptional.of(file);
            }
            // 首次使用:直接用 DDL 建库(不再依赖已删除的二进制模板 sqlite-apidoc-example/apidoc-example.db)
            try (Connection connection = getConnection(file.getPath())) {
                SQLiteSchema.ensureSchema(connection);
            }
            log.info("filterFile create sqlite db with schema: {}", file.getPath());
            return ApidocOptional.of(file);
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

    /**
     * 打包运行时 SQLite 规则文件的位置:启动目录(user.dir)下的 {path}/{fileName}.db,默认 ./csap-api-doc/apidoc.db
     *
     * @param strategyModel 策略对象
     * @return 文件
     */
    static File externalFile(StrategyModel strategyModel) {
        String name = strategyModel.getFileName() + ApiStrategyType.SQL_LITE.getSuffix();
        String path = strategyModel.getPath();
        File base = new File(System.getProperty("user.dir"));
        return (path == null || path.trim().isEmpty() ? new File(base, name) : new File(new File(base, path), name)).getAbsoluteFile();
    }

    /**
     * DevTools 首次保存时在 jar 外建库:jar 内有模板就复制模板,否则用 DDL 建库
     *
     * @param strategyModel 策略对象
     * @param external      目标文件
     * @return 目标文件
     */
    private File createExternalFile(StrategyModel strategyModel, File external) {
        try {
            Resource[] resources = RESOURCE_PATTERN.getResources(String.format("classpath*:%s/%s", strategyModel.getPath(), strategyModel.getFileName() + getSuffix()));
            File parent = external.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("无法创建目录 " + parent);
            }
            if (ArrayUtil.isNotEmpty(resources)) {
                copyResourceToFile(resources[0], external);
            }
            try (Connection connection = getConnection(external.getPath())) {
                SQLiteSchema.ensureSchema(connection);
            }
            log.info("csap-apidoc: 以 jar 运行,SQLite 规则保存在 jar 外的文件 {}", external.getPath());
            CREATE_FILE_MAP.put(strategyModel.getId(), external);
            return external;
        } catch (Exception e) {
            throw notWritable(external, e);
        }
    }

    /**
     * SQLite 规则文件无法写入时的明确错误(同时打一条 WARN)
     */
    private static RuleStorageNotWritableException notWritable(File file, Exception cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String reason = root.getClass().getSimpleName() + ": " + root.getMessage();
        RuleStorageNotWritableException e = new RuleStorageNotWritableException(
                "SQLite 规则文件 " + file.getPath() + " 无法写入(" + reason + ")。"
                        + "以 jar 运行时 SQLite 规则文件放在启动目录(user.dir)下,请确认该目录可写。"
                        + " | Cannot write the SQLite rule file " + file.getPath() + " (" + reason + "). "
                        + "When running from a jar the SQLite rule file is kept under the working directory (user.dir); "
                        + "make sure it is writable.", cause);
        log.warn("DevTools 保存规则失败: {}", e.getMessage());
        return e;
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
                basicParamRuleKey(strategyModel, docMethod, keyName, paramName), docMethod, method, parameter.getType(),
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
            // 1.4.0 的接口状态:DEFAULT(列默认值)视为没有覆盖
            ApiStatus status = ApiStatus.resolve((String) methodMap.get("status"));
            String statusNote = (String) methodMap.get("status_note");
            // 创建方法属性对象并设置参数
            ParamGroupMethodProperty methodProperty = new ParamGroupMethodProperty();
            methodProperty.setRequest(parseJsonToParams(requestJson));
            methodProperty.setResponse(parseJsonToParams(responseJson));
            methodProperty.setStatus(status == null || status == ApiStatus.DEFAULT ? null : status);
            methodProperty.setStatusNote(statusNote == null || statusNote.isEmpty() ? null : statusNote);
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
                            SQLiteSchema.ensureSchema(connection);
                            List<Map<String, Object>> mapList = queryList(connection, API_METHOD, Sets.newHashSet("class_name", "simple_name", "name", "request", "response", "status", "status_note"),
                                    MapUtil.<String, Object>builder().build())
                                    .orElse(null);
                            HANDLE_MAP.computeIfAbsent(strategyModel.getId(), s -> new HashMap<>(16))
                                      .putAll(convertToHandleMap(mapList));
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    });
        }
        // 还没有规则文件(首次使用、或以 jar 运行且 jar 外尚无文件)时返回空规则,而不是 null
        Map<String, Map<String, ParamGroupMethodProperty>> result = HANDLE_MAP.get(strategyModel.getId());
        return result == null ? new HashMap<>(16) : result;
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
                        SQLiteSchema.ensureSchema(connection);
                        ParamGroupMethodProperty property = dataMap.get(className).get(methodName);
                        Integer c = count(connection, API_METHOD, MapUtil
                                .<String, Object>builder("class_name", className).put("name", methodName).build())
                                .orElse(0);
                        if (c > 0) {
                            updateByWhere(connection, API_METHOD, MapUtil.<String, Object>builder()
                                                                         .put(request ? "request" : "response", objToString(request ? property.getRequest() : property.getResponse()))
                                                                         .put("status", property.getStatus() == null ? "DEFAULT" : property.getStatus().name())
                                                                         .put("status_note", property.getStatusNote() == null ? "" : property.getStatusNote())
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
                                                                .put("status", property.getStatus() == null ? "DEFAULT" : property.getStatus().name())
                                                                .put("status_note", property.getStatusNote() == null ? "" : property.getStatusNote())
                                                                .put("simple_name", IStandardHandle.splitName(className))
                                                                .put("create_time", DateUtil.formatLocalDateTime(LocalDateTime.now()))
                                                                .put("update_time", DateUtil.formatLocalDateTime(LocalDateTime.now()))
                                                                .build())
                                    .when(res -> log.isDebugEnabled(), res -> log.debug("writeAndMerge save result is {}", res))
                                    .orElse(0);
                        }
                        HANDLE_MAP.computeIfAbsent(strategyModel.getId(), s -> new HashMap<>(16)).putAll(dataMap);
                    } catch (Exception e) {
                        // 以前只记日志、DevTools 仍显示保存成功;现在返回明确错误
                        throw notWritable(i, e);
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
