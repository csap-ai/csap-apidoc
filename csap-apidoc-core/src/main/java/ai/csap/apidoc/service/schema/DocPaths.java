package ai.csap.apidoc.service.schema;

import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;

/**
 * 接口完整路径与方法(Mock 匹配、JSON Schema 导出共用,规则见 docs/design/1.4.0-mock-schema.md 2.2)。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class DocPaths {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}:]+)(?::[^}]*)?}");

    private DocPaths() {
    }

    /**
     * 完整路径:apiPath[0](没有时取控制器路径)+ paths[0],规整为以 / 开头、不以 / 结尾;
     * {name:正则} 写成 {name}
     *
     * @param method     接口
     * @param controller 所在控制器,可为 null
     * @return 路径
     */
    public static String fullPath(CsapDocMethod method, CsapDocModelController controller) {
        String base = method.getApiPath() != null && method.getApiPath().length > 0 ? method.getApiPath()[0] : null;
        if (base == null && controller != null && controller.getPath() != null && controller.getPath().length > 0) {
            base = controller.getPath()[0];
        }
        String sub = method.getPaths() != null && method.getPaths().length > 0 ? method.getPaths()[0] : null;
        String b = base == null ? "" : base;
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        String joined = b + "/" + strip(sub == null ? "" : sub);
        joined = strip(joined);
        String result = joined.isEmpty() ? "/" : "/" + joined;
        return PATH_VARIABLE.matcher(result).replaceAll("{$1}");
    }

    private static String strip(String s) {
        int from = 0;
        int to = s.length();
        while (from < to && s.charAt(from) == '/') {
            from++;
        }
        while (to > from && s.charAt(to - 1) == '/') {
            to--;
        }
        return s.substring(from, to);
    }

    /**
     * 接口的第一个 HTTP 方法,没有时为 GET
     *
     * @param method 接口
     * @return 方法名
     */
    public static String verb(CsapDocMethod method) {
        return method.getMethods() == null || method.getMethods().isEmpty() || method.getMethods().get(0) == null
                ? RequestMethod.GET.name() : method.getMethods().get(0).name();
    }
}
