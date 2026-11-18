package ai.csap.apidoc.mcp.protocol;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具的提供与执行(协议层不关心工具从哪里来)
 *
 * @author ycf
 * @since 1.3.0
 */
public interface McpToolHandler {

    /**
     * 当前暴露的工具定义(name、title、description、inputSchema、annotations),顺序稳定
     *
     * @return 工具定义
     */
    List<ObjectNode> listTools();

    /**
     * 执行工具
     *
     * @param name      工具名
     * @param arguments 参数(对象,可能为空对象)
     * @param context   请求上下文
     * @return 结果
     * @throws McpException 工具不存在等协议错误
     */
    ToolResult callTool(String name, JsonNode arguments, McpCallContext context);

    /**
     * 工具执行结果(作为一段文本返回)
     */
    final class ToolResult {
        private final String text;
        private final boolean error;

        public ToolResult(String text, boolean error) {
            this.text = text;
            this.error = error;
        }

        public String getText() {
            return text;
        }

        public boolean isError() {
            return error;
        }
    }
}
