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

import com.fasterxml.jackson.core.type.TypeReference;
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
                .map(i -> readValue(type, i))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    /**
     * 读取数据
     *
     * @param type        类型
     * @param inputStream 文件流
     * @param <R1>        结果泛型
     * @return 结果
     */
    @SneakyThrows
    private <R1> R1 readValue(TypeReference<R1> type, InputStream inputStream) {
        return YAML_MAPPER.readValue(inputStream, type);
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
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String resourcePath = Objects.requireNonNull(Objects.requireNonNull(classLoader.getResource("")))
                .getPath() + path;
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
        YAML_MAPPER.writeValue(file, value);
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
