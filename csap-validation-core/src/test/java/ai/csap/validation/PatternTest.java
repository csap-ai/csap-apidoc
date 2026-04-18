package ai.csap.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author ycf
 * @Date 2025/9/1 17:18
 * @Version 1.0
 */
public class PatternTest {

    /**
     * 将任意类型中的泛型参数<T>替换为自定义字符串
     * 例如：List<String> → List<MyType>，Map<Integer, String> → Map<MyType>
     *
     * @param input       原始字符串
     * @param replacement 替换后的内容
     * @return 处理后的字符串
     */
    public static String replaceGenericType(String input, String replacement) {
        // 正则表达式说明：
        // (\w+) 匹配类型名（字母、数字、下划线组成）
        // <.*?> 非贪婪匹配<和>之间的泛型参数
        Pattern pattern = Pattern.compile("(\\w+)<.*?>");

        Matcher matcher = pattern.matcher(input);

        // 替换逻辑：保留类型名，替换<...>为<replacement>
        return matcher.replaceAll("$1<" + replacement + ">");
    }

    public static void main(String[] args) {
        // 测试用例（覆盖多种泛型场景）
        String[] testCases = {
                "List<String>",
                "Map<Integer, String>",
                "Set<Long>",
                "CustomClass<T>",
                "User<Detail>",
                "List<Map<String, Integer>>", // 仅替换最外层泛型
                "Other<Inner<Type>>"         // 仅替换最外层泛型
        };

        String replacement = "MyCustomType";

        for (String test : testCases) {
            String result = replaceGenericType(test, replacement);
            System.out.println(test + " → " + result);
        }
    }
}
