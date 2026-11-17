package ai.csap.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 内置正则的正例/反例。PatternValidationStrategy 用 {@code Pattern.matcher(v).matches()} 整串匹配,这里保持一致。
 */
class ValidatePatternTypeTest {

    private static boolean matches(ValidatePatternType type, String value) {
        return Pattern.compile(type.getPattern()).matcher(value).matches();
    }

    static Stream<Arguments> valid() {
        return Stream.of(
                Arguments.of(ValidatePatternType.PATTERN_12, "-1"),
                Arguments.of(ValidatePatternType.PATTERN_12, "-120"),
                Arguments.of(ValidatePatternType.PATTERN_14, "0"),
                Arguments.of(ValidatePatternType.PATTERN_14, "-35"),
                Arguments.of(ValidatePatternType.PATTERN_17, "1.5"),
                Arguments.of(ValidatePatternType.PATTERN_17, "0.01"),
                Arguments.of(ValidatePatternType.PATTERN_21, "abc123"),
                Arguments.of(ValidatePatternType.PATTERN_27, "user_name1"),
                Arguments.of(ValidatePatternType.PATTERN_30, "hello world"),
                Arguments.of(ValidatePatternType.PATTERN_31, "abc-123"),
                Arguments.of(ValidatePatternType.PATTERN_33, "example.com"),
                Arguments.of(ValidatePatternType.PATTERN_33, "www.csap.ai."),
                Arguments.of(ValidatePatternType.PATTERN_34, "http://example.com"),
                Arguments.of(ValidatePatternType.PATTERN_34, "https://www.example.com/a/b-c.html?x=1&y=2"),
                Arguments.of(ValidatePatternType.PATTERN_35, "13812345678"),
                Arguments.of(ValidatePatternType.PATTERN_35, "19912345678"),
                Arguments.of(ValidatePatternType.PATTERN_36, "12345678"),
                Arguments.of(ValidatePatternType.PATTERN_36, "010-12345678"),
                Arguments.of(ValidatePatternType.PATTERN_36, "(0755)1234567"),
                Arguments.of(ValidatePatternType.PATTERN_41, "09"),
                Arguments.of(ValidatePatternType.PATTERN_41, "31"),
                Arguments.of(ValidatePatternType.PATTERN_42, "spring-beans.xml"),
                Arguments.of(ValidatePatternType.PATTERN_42, "pom.XML")
        );
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of(ValidatePatternType.PATTERN_12, "-0"),
                Arguments.of(ValidatePatternType.PATTERN_12, "1"),
                Arguments.of(ValidatePatternType.PATTERN_12, "-1\"\""),
                Arguments.of(ValidatePatternType.PATTERN_14, "5"),
                Arguments.of(ValidatePatternType.PATTERN_14, "-0"),
                Arguments.of(ValidatePatternType.PATTERN_14, "00"),
                Arguments.of(ValidatePatternType.PATTERN_17, "0.0"),
                Arguments.of(ValidatePatternType.PATTERN_17, "-1.5"),
                Arguments.of(ValidatePatternType.PATTERN_17, "abc"),
                Arguments.of(ValidatePatternType.PATTERN_21, "abc 123"),
                Arguments.of(ValidatePatternType.PATTERN_21, "中文"),
                Arguments.of(ValidatePatternType.PATTERN_27, "user-name"),
                Arguments.of(ValidatePatternType.PATTERN_30, "a&b"),
                Arguments.of(ValidatePatternType.PATTERN_30, "say \"hi\""),
                Arguments.of(ValidatePatternType.PATTERN_31, "a~b"),
                Arguments.of(ValidatePatternType.PATTERN_31, "\""),
                Arguments.of(ValidatePatternType.PATTERN_33, "localhost"),
                Arguments.of(ValidatePatternType.PATTERN_33, "example.com "),
                Arguments.of(ValidatePatternType.PATTERN_34, "ftp://example.com"),
                Arguments.of(ValidatePatternType.PATTERN_34, "https://"),
                Arguments.of(ValidatePatternType.PATTERN_35, "12345678901"),
                Arguments.of(ValidatePatternType.PATTERN_35, "1381234567"),
                Arguments.of(ValidatePatternType.PATTERN_36, "010-123"),
                Arguments.of(ValidatePatternType.PATTERN_36, "(0755-1234567"),
                Arguments.of(ValidatePatternType.PATTERN_41, "32"),
                Arguments.of(ValidatePatternType.PATTERN_41, "00"),
                Arguments.of(ValidatePatternType.PATTERN_42, "pom.txt"),
                Arguments.of(ValidatePatternType.PATTERN_42, "pomxml")
        );
    }

    @ParameterizedTest(name = "{0} 应匹配 [{1}]")
    @MethodSource("valid")
    void validValuesMatch(ValidatePatternType type, String value) {
        assertTrue(matches(type, value), type + " " + type.getPattern());
    }

    @ParameterizedTest(name = "{0} 不应匹配 [{1}]")
    @MethodSource("invalid")
    void invalidValuesDoNotMatch(ValidatePatternType type, String value) {
        assertFalse(matches(type, value), type + " " + type.getPattern());
    }

    @Test
    void correctedPatternsHaveNoStrayWhitespaceOrChineseText() {
        for (ValidatePatternType type : ValidatePatternType.values()) {
            String p = type.getPattern();
            assertEquals(p.trim(), p, type + " 正则首尾不能有空白");
            assertFalse(p.contains(" 或 "), type + " 正则里不能有说明文字");
        }
        assertEquals("不能含有 ^%&',;=?$\" 等字符", ValidatePatternType.PATTERN_30.getDescr());
    }
}
