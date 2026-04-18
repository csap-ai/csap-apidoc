package ai.csap.apidoc.handle;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.AntPathMatcher;

import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.core.ApidocStrategyName;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.SneakyThrows;

/**
 * 标准处理.
 * <p>Created on 2025/9/13
 *
 * @author ycf
 * @since 1.0
 */
public interface IStandardHandle {
    String REPLACE_NAME = "target/classes";
    String REPLACE_TARGET_NAME = "src/main/resources";
    String PATTERN_LOCAL = "%s/%s%s%s";
    /**
     * classpath 路径
     * path=路径, prefixName=文件名称前缀, 文件名称-fileName(), 文件后缀=strategyType.getSuffix())
     */
    String PATTERN = "classpath*:%s/%s%s%s";
    /**
     * 路径匹配
     */
    AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    /**
     * 资源处理器
     */
    ResourcePatternResolver RESOURCE_PATTERN = new PathMatchingResourcePatternResolver();

    /**
     * 文件名称
     *
     * @return 文件名称
     */
    String fileName();

    /**
     * 当前文档路径
     *
     * @return 文件路径
     */
    default String path() {
        return "";
    }

    /**
     * 是否本地环境
     *
     * @return 是否本地环境
     */
    default boolean isLocal() {
        return "file".equals(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("")).getProtocol());
    }

    /**
     * 获取className名称
     *
     * @param className 类名称
     * @return 类名称
     */
    static String splitName(String className) {
        String[] strings = className.split("\\.");
        return strings.length > 1 ? strings[strings.length - 1] : className;
    }

    /**
     * 获取文件路径
     *
     * @param path 文件路径
     * @return 返回实际文件路径
     */
    default String getFilePath(String path) {
        return StrUtil.isEmpty(path()) ? path : path + File.separator + path();
    }

    /**
     * 祛除classes文件下路径名称,获取本地名称
     *
     * @param pathName 路径名称
     * @return 路径名称
     */
    default String pathNameFilter(String pathName) {
        return pathName.replace(REPLACE_NAME, REPLACE_TARGET_NAME);
    }

    /**
     * 本地路径
     *
     * @param strategyType 策略类型
     * @param path         路径
     * @param prefixName   前缀
     * @return 结果
     */
    @SneakyThrows
    default Stream<InputStream> localPath(ApidocStrategyName strategyType, String path,
                                          String prefixName) {
        String pattern = String.format(PATTERN_LOCAL, path, prefixName, fileName(),
                strategyType.getSuffix());
        return Optional.ofNullable(StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                Thread.currentThread().getContextClassLoader()
                                        .getResources(pattern).asIterator(),
                                Spliterator.ORDERED), false)
                        .map(i -> {
                            File[] f;
                            File file = new File(URLDecoder.decode(pathNameFilter(i.getPath()), StandardCharsets.UTF_8));
                            if (file.isFile()) {
                                f = new File[]{file};
                            } else {
                                f = file.listFiles();
                            }
                            return f;
                        })
                        .filter(ArrayUtil::isNotEmpty)
                        .flatMap(Arrays::stream)
                        .filter(i -> PATH_MATCHER.match(prefixName + fileName() + strategyType.getSuffix(), i.getName()))
                        .map(this::fileInputStream))
                .get();
    }

    @SneakyThrows
    default InputStream fileInputStream(File file) {
        return new FileInputStream(file);
    }

    @SneakyThrows
    default InputStream inputStream(Resource file) {
        return file.getInputStream();
    }


    /**
     * 运行路径
     *
     * @param strategyType 策略类型
     * @param path         路径
     * @param prefixName   前缀
     * @return 结果
     */
    @SneakyThrows
    default Stream<InputStream> runPath(ApidocStrategyName strategyType, String path,
                                        String prefixName) {
        String pattern = String.format(PATTERN, path, prefixName, fileName(),
                strategyType.getSuffix());
        String filePattern = prefixName + fileName() + strategyType.getSuffix();
        return Stream.of(RESOURCE_PATTERN.getResources(pattern))
                .filter(i -> PATH_MATCHER.match(filePattern,
                        Objects.requireNonNull(i.getFilename())))
                .map(this::inputStream);
    }


    /**
     * 获取流数据
     *
     * @param strategyType 策略
     * @param path         路径
     * @param prefixName   前缀名称
     * @return 结果
     */
    @SneakyThrows
    default Stream<InputStream> getStream(ApidocStrategyName strategyType, String path,
                                          String prefixName) {
        boolean local = false;
        if (!FilePrefixStrategyType.findName(prefixName)) {
            //不是默认文件模式，必须是固定文件路径
            String pattern = String.format(PATTERN, path, prefixName, fileName(),
                    strategyType.getSuffix());
            Resource[] resources = RESOURCE_PATTERN.getResources(pattern);
            if (ArrayUtil.isNotEmpty(resources)) {
                local = "file".equals(resources[0].getURI().getScheme());
            } else {
                String path1 = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("")).getPath();
                String s = String.format(PATTERN_LOCAL, path, prefixName, fileName(), strategyType.getSuffix());
                File file = new File(URLDecoder.decode(pathNameFilter(path1 + s), StandardCharsets.UTF_8));
                if (file.exists()) {
                    return Stream.of(file).map(this::fileInputStream);
                }
            }
        }
        return local ? localPath(strategyType, path, prefixName) : runPath(strategyType, path, prefixName);
    }

}
