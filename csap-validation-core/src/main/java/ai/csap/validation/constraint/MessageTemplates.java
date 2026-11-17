package ai.csap.validation.constraint;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ObjectUtils;

/**
 * 约束注解 message 的简单插值.
 *
 * <p>写明文的 message 原样返回;没写 message 时注解的默认值是 {@code {jakarta.validation.constraints.NotNull.message}}
 * 这类模板,这里按 {@code ValidationMessages}(应用自定义)和 Hibernate Validator 自带的消息包(按 JVM 默认 Locale)取文案,
 * 再把 {@code {min}}、{@code {max}} 这类占位符换成注解属性值。EL 表达式 {@code ${...}} 不求值,直接去掉。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class MessageTemplates {
    private static final Pattern PARAMETER = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{[^{}]*}");
    private static final String[] BUNDLES = {"ValidationMessages", "org.hibernate.validator.ValidationMessages"};

    private MessageTemplates() {
    }

    /**
     * 插值
     *
     * @param message    注解的 message
     * @param annotation 注解
     * @return 文案
     */
    public static String interpolate(String message, Annotation annotation) {
        if (message == null || message.indexOf('{') < 0) {
            return message;
        }
        String result = replace(message, annotation, true);
        // 消息包里的文案可能还带 {value} 等属性占位符
        result = replace(result, annotation, false);
        return EXPRESSION.matcher(result).replaceAll("");
    }

    private static String replace(String message, Annotation annotation, boolean bundle) {
        Matcher matcher = PARAMETER.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = bundle ? bundleMessage(key) : attribute(annotation, key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String bundleMessage(String key) {
        if (!key.contains(".")) {
            return null;
        }
        String value = lookup(key);
        if (value == null && key.startsWith(ConstraintFlavor.JAVAX + ".")) {
            value = lookup(ConstraintFlavor.JAKARTA + key.substring(ConstraintFlavor.JAVAX.length()));
        } else if (value == null && key.startsWith(ConstraintFlavor.JAKARTA + ".")) {
            value = lookup(ConstraintFlavor.JAVAX + key.substring(ConstraintFlavor.JAKARTA.length()));
        }
        return value;
    }

    private static String lookup(String key) {
        for (String name : BUNDLES) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(name, Locale.getDefault(),
                        MessageTemplates.class.getClassLoader());
                if (bundle.containsKey(key)) {
                    return bundle.getString(key);
                }
            } catch (MissingResourceException ignored) {
                // 没有这个消息包
            }
        }
        return null;
    }

    private static String attribute(Annotation annotation, String name) {
        Object value = AnnotationUtils.getValue(annotation, name);
        if (value == null) {
            return null;
        }
        return ObjectUtils.isArray(value) ? ObjectUtils.nullSafeToString(value) : String.valueOf(value);
    }
}
