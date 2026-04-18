package ai.csap.apidoc;

import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件匹配和输出策略类型
 *
 * @Author ycf
 * @Date 2021/11/3 6:05 下午
 * @Version 1.0
 */
@AllArgsConstructor
@Getter
public enum FilePrefixStrategyType {
    //默认类型(表示-每个method,controller,model等都会独立生成一个文件,具体名称以类名为主)
    DEFAULT("*"),
    //固定类型(api-controller.yaml,api-method.yaml)
    FIXED("api");
    /**
     * 配合和输出类型
     */
    private final String name;

    /**
     * 是否是默认的文件名称
     *
     * @param name 参数名称
     * @return 结果
     */
    public static boolean findName(String name) {
        return Stream.of(values()).anyMatch(i -> i.getName().equals(name));
    }
}
