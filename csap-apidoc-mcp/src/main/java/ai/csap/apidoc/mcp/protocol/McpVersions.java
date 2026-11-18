package ai.csap.apidoc.mcp.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 支持的 MCP 协议版本。
 * <ul>
 *   <li>modern(无握手,每个请求在 _meta 里带版本):2026-07-28</li>
 *   <li>legacy(initialize 握手):2025-11-25、2025-06-18、2025-03-26</li>
 * </ul>
 *
 * @author ycf
 * @since 1.3.0
 */
public final class McpVersions {

    public static final String V2026_07_28 = "2026-07-28";
    public static final String V2025_11_25 = "2025-11-25";
    public static final String V2025_06_18 = "2025-06-18";
    public static final String V2025_03_26 = "2025-03-26";

    public static final List<String> MODERN = Collections.singletonList(V2026_07_28);
    public static final List<String> LEGACY = Collections.unmodifiableList(
            Arrays.asList(V2025_11_25, V2025_06_18, V2025_03_26));
    public static final List<String> ALL;

    static {
        List<String> all = new ArrayList<>(MODERN);
        all.addAll(LEGACY);
        ALL = Collections.unmodifiableList(all);
    }

    private McpVersions() {
    }

    /**
     * legacy initialize 协商:客户端请求的版本支持就原样返回,否则返回最新的 legacy 版本
     *
     * @param requested 客户端请求的版本
     * @return 协商结果
     */
    public static String negotiateLegacy(String requested) {
        return requested != null && LEGACY.contains(requested) ? requested : LEGACY.get(0);
    }
}
