package ai.csap.apidoc.runtime.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

/**
 * 由文档模型编译接口的返回过滤规则。
 *
 * <p>path 模式的掩码直接取自文档的返回字段树({@link CsapDocMethod#getResponse()}):文档里显示的字段
 * 就是放行的字段(规则 include 为 true 的字段、forceRep 字段及其上级),因此实际输出与文档一致。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class EndpointRuleCompiler {

    /**
     * 文档里 Map 类型字段的 dataType,如 {@code Map<String,Team>}、{@code HashMap<String, Team>}
     */
    private static final Pattern MAP_TYPE = Pattern.compile("^(?:[\\w$]+\\.)*[\\w$]*Map\\s*<");

    /**
     * 防御异常数据的最大深度
     */
    private static final int MAX_DEPTH = 64;

    private EndpointRuleCompiler() {
    }

    /**
     * 由文档的返回字段树编译 path 模式的掩码。
     *
     * @param method 文档方法
     * @return 掩码;返回值是基础类型或没有返回模型时为 null(不过滤)
     */
    public static FieldMask pathMask(CsapDocMethod method) {
        List<CsapDocModel> response = method.getResponse();
        if (response == null || response.isEmpty() || response.get(0) == null) {
            return null;
        }
        CsapDocModel root = response.get(0);
        if (root.getModelType() == ModelType.BASE_DATA) {
            return null;
        }
        List<String> include = new ArrayList<>();
        Set<String> mapPaths = new HashSet<>();
        collect(root.getParameters(), "", include, mapPaths, 0);
        return FieldMask.compile(include, mapPaths);
    }

    private static void collect(List<CsapDocParameter> parameters, String prefix, List<String> include,
                                Set<String> mapPaths, int depth) {
        if (parameters == null || depth > MAX_DEPTH) {
            return;
        }
        for (CsapDocParameter p : parameters) {
            if (p == null || p.getName() == null || p.getName().isEmpty()) {
                continue;
            }
            String path = prefix + p.getName();
            if (isMap(p)) {
                mapPaths.add(path);
            }
            CsapDocModel children = p.getChildren();
            List<CsapDocParameter> sub = children == null ? Collections.emptyList() : children.getParameters();
            if (sub == null || sub.isEmpty()) {
                // 没有下级规则:整个值原样输出
                include.add(path);
            } else {
                collect(sub, path + ".", include, mapPaths, depth + 1);
            }
        }
    }

    static boolean isMap(CsapDocParameter p) {
        String dataType = p.getDataType();
        return dataType != null && MAP_TYPE.matcher(dataType.trim()).find();
    }
}
