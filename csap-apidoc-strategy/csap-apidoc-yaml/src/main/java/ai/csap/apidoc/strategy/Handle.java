package ai.csap.apidoc.strategy;

import static ai.csap.apidoc.strategy.ApidocStrategy.API_PATH;
import static ai.csap.apidoc.strategy.ApidocStrategy.DEFAULT_PREFIX_NAME;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.handle.IStandardHandle;

import cn.hutool.core.util.StrUtil;
import lombok.SneakyThrows;

/**
 * 数据处理
 *
 * @param <H> 参数类型
 * @param <R> 参数类型
 * @Author ycf
 * @Date 2021/11/3 9:12 下午
 * @Version 1.0
 */
public interface Handle<H, R> extends IStandardHandle {
    Logger LOG = LoggerFactory.getLogger(Handle.class);
    YAMLMapper YAML_MAPPER = new YAMLMapper();
    /**
     * JSON 策略使用的 JSON 读写器,配置与 YAML_MAPPER 保持一致
     */
    ObjectMapper JSON_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 是否为 JSON 文件策略
     *
     * @param strategyType 策略类型
     * @return true 表示 .json 文件
     */
    static boolean isJson(ApidocStrategyName strategyType) {
        return strategyType != null && ApiStrategyType.JSON.getSuffix().equals(strategyType.getSuffix());
    }

    /**
     * 判断内容是否为 JSON:去掉 BOM 和空白后以 { 或 [ 开头。
     * 1.1.1 及以前 JSON 策略写出的 .json 文件内容其实是 YAML,据此区分新旧格式。
     *
     * @param content 文件内容
     * @return true 表示 JSON
     */
    static boolean looksLikeJson(byte[] content) {
        int i = 0;
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        for (; i < content.length; i++) {
            char c = (char) content[i];
            if (!Character.isWhitespace(c)) {
                return c == '{' || c == '[';
            }
        }
        return false;
    }


    /**
     * 转换YAML
     *
     * @param type         泛型类型
     * @param strategyType 策略类型
     * @param path         路径
     * @return 数据
     */
    default ApidocOptional<List<R>> convertYaml(TypeReference<R> type, ApidocStrategyName strategyType, String path) {
        return convertYaml(type, strategyType, path, FilePrefixStrategyType.DEFAULT.getName());
    }

    /**
     * 读取yaml，获取实际的对象
     *
     * @param strategyType 策略类型
     * @param type         返回的类型
     * @param path         路径
     * @param prefixName   前缀名称
     * @return 结果
     */
    default ApidocOptional<List<R>> convertYaml(TypeReference<R> type, ApidocStrategyName strategyType, String path, String prefixName) {
        if (StrUtil.isEmpty(path)) {
            path = API_PATH;
        }
        path = getFilePath(path);
        if (LOG.isDebugEnabled()) {
            LOG.debug("path is :{}", path);
        }
        return ApidocOptional.ofNullable(getStream(strategyType, path, prefixName)
                .map(i -> readValue(type, strategyType, i))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    /**
     * 读取数据。JSON 策略的文件内容是 JSON 时按 JSON 解析,否则(旧版本写出的 YAML 内容)按 YAML 解析。
     *
     * @param type         类型
     * @param strategyType 策略类型
     * @param inputStream  文件流
     * @param <R1>         结果泛型
     * @return 结果
     */
    @SneakyThrows
    private <R1> R1 readValue(TypeReference<R1> type, ApidocStrategyName strategyType, InputStream inputStream) {
        try (InputStream in = inputStream) {
            byte[] content = in.readAllBytes();
            if (content.length == 0) {
                return null;
            }
            if (isJson(strategyType) && looksLikeJson(content)) {
                return JSON_MAPPER.readValue(content, type);
            }
            return YAML_MAPPER.readValue(content, type);
        }
    }

    /**
     * 写入文件并且合并
     *
     * @param value        数据
     * @param strategyType 策略
     * @param path         路径
     * @param prefixName   前缀名称
     */
    void writeAndMerge(R value, ApidocStrategyName strategyType, String path, String prefixName);

    /**
     * 写入文件并且合并当前文件
     *
     * @param value        数据
     * @param strategyType 策略类型
     * @param path         路径
     */
    void writeAndMerge(R value, ApidocStrategyName strategyType, String path);

    /**
     * 写入文件并且合并当前文件
     *
     * @param value        数据
     * @param strategyType 策略类型
     */
    void writeAndMerge(R value, ApidocStrategyName strategyType);

    /**
     * 写入aoidoc文档
     *
     * @param value 写入的数据
     */
    default void write(R value) {
        write(value, null, API_PATH, null);
    }

    /**
     * 写入aoidoc文档
     *
     * @param value 写入的数据
     * @param path  路径
     */
    default void write(R value, String path) {
        write(value, null, path, null);
    }

    /**
     * 写入aoidoc文档
     *
     * @param value        写入的数据
     * @param strategyType 策略
     * @param path         路径
     */
    default void write(R value, String path, ApidocStrategyName strategyType) {
        write(value, strategyType, path, null);
    }

    /**
     * 写入aoidoc文档
     *
     * @param value        写入的数据
     * @param strategyType 策略
     */
    default void write(R value, ApidocStrategyName strategyType) {
        write(value, strategyType, API_PATH, null);
    }

    /**
     * 写入apidoc文档
     *
     * @param value        需要写入的数据
     * @param strategyType 文档策略
     * @param path         文档路径 example csap-api-doc
     * @param prefixName   文档前缀名称 {prefixName}-controller.yaml
     */
    @SneakyThrows
    default void write(R value, ApidocStrategyName strategyType, String path, String prefixName) {
        if (ObjectUtils.isEmpty(value)) {
            return;
        }
        if (StrUtil.isEmpty(prefixName)) {
            prefixName = DEFAULT_PREFIX_NAME;
        }
        if (StrUtil.isEmpty(path)) {
            path = API_PATH;
        }
        path = getFilePath(path);
        if (strategyType == null) {
            strategyType = ApiStrategyType.YAML;
        }
        // 打包运行(jar)时 classpath 不可写:返回说明清楚的错误(DevTools 显示),而不是 NPE
        String resourcePath = writableClasspathRoot(strategyType) + path;
        StringBuilder pathName = new StringBuilder(URLDecoder.decode(resourcePath, StandardCharsets.UTF_8));
        if (LOG.isDebugEnabled()) {
            LOG.debug("write doc path is:{},strategyType:{},prefixName:{}", pathNameFilter(pathName.toString()), strategyType.getSuffix(), prefixName);
        }
        File filePath = new File(pathNameFilter(pathName.toString()));
        if (!filePath.exists()) {
            filePath.mkdirs();
        }
        pathName.append(File.separator).append(prefixName).append(fileName()).append(strategyType.getSuffix());
        String pathName2 = pathNameFilter(pathName.toString());
        File file = new File(pathName2);
        if (isJson(strategyType)) {
            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, value);
        } else {
            YAML_MAPPER.writeValue(file, value);
        }
        LOG.info("write doc is finished {}", pathName2);
    }

    /**
     * 模糊匹配
     *
     * @return 匹配规则
     */
    default String pattern() {
        return "*";
    }

    /**
     * 处理数据
     *
     * @param standardProperties 返回的信息
     * @param strategyType       策略
     * @param path               路径
     * @return 当前对象
     */
    Handle<H, R> handle(StandardProperties standardProperties, ApidocStrategyName strategyType, String path);

    /**
     * 处理数据
     *
     * @param strategyType 策略
     * @param path         路径
     * @param prefixName   前缀名称
     * @return 当前对象
     */
    R handle(ApidocStrategyName strategyType, String path, String prefixName);

    /**
     * 处理数据
     *
     * @param strategyType 策略
     * @param path         路径
     * @return 当前对象
     */
    R handle(ApidocStrategyName strategyType, String path);

    /**
     * 处理数据
     *
     * @param strategyType 策略
     * @return 当前对象
     */
    R handle(ApidocStrategyName strategyType);

    /**
     * 文档处理
     *
     * @param standardProperties yaml初始化对象
     * @param strategyType       策略类型
     * @return 返回当前对象
     */
    default Handle<H, R> handle(StandardProperties standardProperties, ApidocStrategyName strategyType) {
        return handle(standardProperties, strategyType, API_PATH);
    }
}
