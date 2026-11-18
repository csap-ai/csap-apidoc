package ai.csap.apidoc.mcp.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.mcp.McpService;
import ai.csap.apidoc.mcp.audit.AuditEntry;
import ai.csap.apidoc.mcp.autoconfigure.McpProperties;
import ai.csap.apidoc.mcp.protocol.McpVersions;
import ai.csap.apidoc.mcp.tool.McpTool;

/**
 * DevTools 的 MCP 管理接口(与其他 DevTools 接口同一开关:csap.apidoc.devtool.enabled)。
 * MCP 未开启时只返回状态。令牌值从不返回。
 *
 * @author ycf
 * @since 1.3.0
 */
@ConditionalOnProperty(prefix = "csap.apidoc.devtool", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("devtools/mcp")
public class McpAdminController {

    private final ObjectProvider<McpService> service;
    private final McpProperties properties;

    public McpAdminController(ObjectProvider<McpService> service, McpProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("status")
    public ApidocResult<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        McpService s = service.getIfAvailable();
        m.put("enabled", s != null);
        m.put("path", properties.getPath());
        m.put("protocolVersions", McpVersions.ALL);
        if (s == null) {
            return ApidocResult.success(m);
        }
        m.put("authMode", s.authMode().name().toLowerCase());
        m.put("defaultAuthMode", properties.getAuth().getMode().name().toLowerCase());
        m.put("forwardHeaders", s.forwardHeaders());
        m.put("tokenNames", s.getAuthenticator().tokenNames());
        m.put("upstreamHeaderNames", new ArrayList<>(properties.getAuth().getUpstreamHeaders().keySet()));
        m.put("exposeGetByDefault", properties.isExposeGetByDefault());
        m.put("exposeDeprecated", properties.isExposeDeprecated());
        m.put("storeType", s.getStore().type());
        m.put("storeLocation", s.getStore().location());
        m.put("storePersistent", s.getStore().isPersistent());
        m.put("auditEnabled", properties.getAudit().isEnabled());
        m.put("auditPersistent", s.getAudit() != null && s.getAudit().isPersistent());
        int total = s.catalog().getAll().size();
        m.put("toolCount", total);
        m.put("exposedCount", s.catalog().getExposed().size());
        return ApidocResult.success(m);
    }

    @GetMapping("tools")
    public ApidocResult<List<Map<String, Object>>> tools() {
        McpService s = service.getIfAvailable();
        List<Map<String, Object>> out = new ArrayList<>();
        if (s == null) {
            return ApidocResult.success(out);
        }
        for (McpTool t : s.catalog().getAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("methodKey", t.getMethodKey());
            m.put("className", t.getClassName());
            m.put("methodName", t.getMethodName());
            m.put("title", t.getTitle());
            m.put("httpMethod", t.getHttpMethod());
            m.put("path", t.getPathPattern());
            m.put("name", t.getName());
            m.put("defaultName", t.getDefaultName());
            m.put("description", t.getDescription());
            m.put("defaultDescription", t.getDefaultDescription());
            m.put("exposed", t.isExposed());
            m.put("exposedByDefault", t.isExposedByDefault());
            m.put("enabledSetting", t.getEnabledSetting());
            m.put("readOnly", t.isReadOnly());
            m.put("hidden", t.isHidden());
            m.put("status", t.getStatus());
            m.put("statusNote", t.getStatusNote());
            m.put("unsupportedReason", t.getUnsupportedReason());
            m.put("inputSchema", t.getInputSchema());
            out.add(m);
        }
        return ApidocResult.success(out);
    }

    @PostMapping("tools")
    public ApidocResult<McpService.SaveResult> saveTools(@RequestBody List<McpService.ToolUpdate> updates) {
        McpService s = service.getIfAvailable();
        if (s == null) {
            return ApidocResult.error("400", "MCP 未开启(csap.apidoc.mcp.enabled=false)");
        }
        McpService.SaveResult r = s.updateTools(updates);
        return r.getErrors().isEmpty() || r.getErrors().get(0).startsWith("保存失败") ? ApidocResult.success(r)
                : ApidocResult.error("400", String.join("; ", r.getErrors()), r);
    }

    /**
     * 鉴权设置请求体
     */
    public static class AuthUpdate {
        private String mode;
        private List<String> forwardHeaders;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public List<String> getForwardHeaders() {
            return forwardHeaders;
        }

        public void setForwardHeaders(List<String> forwardHeaders) {
            this.forwardHeaders = forwardHeaders;
        }
    }

    @PostMapping("auth")
    public ApidocResult<McpService.SaveResult> saveAuth(@RequestBody AuthUpdate update) {
        McpService s = service.getIfAvailable();
        if (s == null) {
            return ApidocResult.error("400", "MCP 未开启(csap.apidoc.mcp.enabled=false)");
        }
        McpService.SaveResult r = s.updateAuth(update.getMode(), update.getForwardHeaders());
        return r.getErrors().isEmpty() || r.getErrors().get(0).startsWith("保存失败") ? ApidocResult.success(r)
                : ApidocResult.error("400", String.join("; ", r.getErrors()), r);
    }

    @GetMapping("audit")
    public ApidocResult<List<AuditEntry>> audit(@RequestParam(defaultValue = "100") int limit,
                                                @RequestParam(required = false) String tool,
                                                @RequestParam(required = false) String caller) {
        McpService s = service.getIfAvailable();
        return ApidocResult.success(s == null ? new ArrayList<>() : s.queryAudit(limit, tool, caller));
    }
}
