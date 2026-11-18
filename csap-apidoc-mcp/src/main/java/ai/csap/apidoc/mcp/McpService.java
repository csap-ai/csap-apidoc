package ai.csap.apidoc.mcp;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.csap.apidoc.mcp.audit.AuditEntry;
import ai.csap.apidoc.mcp.audit.AuditLog;
import ai.csap.apidoc.mcp.audit.Redactor;
import ai.csap.apidoc.mcp.autoconfigure.McpProperties;
import ai.csap.apidoc.mcp.config.AuthSetting;
import ai.csap.apidoc.mcp.config.McpSettings;
import ai.csap.apidoc.mcp.config.McpSettingsStore;
import ai.csap.apidoc.mcp.config.ToolSetting;
import ai.csap.apidoc.mcp.invoke.LoopbackInvoker;
import ai.csap.apidoc.mcp.protocol.JsonRpc;
import ai.csap.apidoc.mcp.protocol.McpCallContext;
import ai.csap.apidoc.mcp.protocol.McpException;
import ai.csap.apidoc.mcp.protocol.McpToolHandler;
import ai.csap.apidoc.mcp.security.McpAuthenticator;
import ai.csap.apidoc.mcp.tool.HandlerIndex;
import ai.csap.apidoc.mcp.tool.McpTool;
import ai.csap.apidoc.mcp.tool.ToolCatalog;
import ai.csap.apidoc.model.CsapDocResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * MCP 核心服务:工具目录(缓存)、工具调用、鉴权设置、规则文件读写、审计。
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpService implements McpToolHandler {

    private static final Logger LOG = LoggerFactory.getLogger(McpService.class);
    private static final Pattern HEADER_NAME = Pattern.compile("^[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}$");

    private final McpProperties properties;
    private final McpSettingsStore store;
    private final Supplier<CsapDocResponse> docs;
    private final Supplier<HandlerIndex> handlers;
    private final LoopbackInvoker invoker;
    private final McpAuthenticator authenticator;
    private final AuditLog audit;
    private final Redactor redactor;

    private volatile McpSettings settings;
    private final AtomicLong docVersion = new AtomicLong();
    private final AtomicLong settingsVersion = new AtomicLong();
    private final Object catalogLock = new Object();
    private volatile CatalogKey catalogKey;
    private volatile ToolCatalog catalog = ToolCatalog.empty();

    public McpService(McpProperties properties, McpSettingsStore store, Supplier<CsapDocResponse> docs,
                      Supplier<HandlerIndex> handlers, LoopbackInvoker invoker, McpAuthenticator authenticator,
                      AuditLog audit) {
        this.properties = properties;
        this.store = store;
        this.docs = docs;
        this.handlers = handlers;
        this.invoker = invoker;
        this.authenticator = authenticator;
        this.audit = audit;
        this.redactor = new Redactor(properties.getAudit().getMaskFields(), properties.getAudit().getMaxSummaryChars());
        McpSettings loaded;
        try {
            loaded = store.load();
        } catch (RuntimeException e) {
            LOG.warn("csap-apidoc MCP: {},使用默认设置", e.getMessage());
            loaded = McpSettings.empty();
        }
        this.settings = loaded;
    }

    // ------------------------------------------------------------------ catalog

    /**
     * 文档刷新(扫描、DevTools 修改规则)后调用,下次使用时重建工具目录
     */
    public void onDocumentRefreshed() {
        docVersion.incrementAndGet();
    }

    public ToolCatalog catalog() {
        CsapDocResponse doc = docs == null ? null : docs.get();
        CatalogKey key = new CatalogKey(doc, docVersion.get(), settingsVersion.get());
        CatalogKey current = catalogKey;
        if (key.equals(current)) {
            return catalog;
        }
        synchronized (catalogLock) {
            if (key.equals(catalogKey)) {
                return catalog;
            }
            HandlerIndex index = handlers == null ? null : handlers.get();
            ToolCatalog built = index == null ? ToolCatalog.empty()
                    : ToolCatalog.build(doc, index, settings, properties.isExposeGetByDefault());
            catalog = built;
            catalogKey = key;
            return built;
        }
    }

    @Override
    public List<ObjectNode> listTools() {
        List<ObjectNode> list = new ArrayList<>();
        for (McpTool t : catalog().getExposed().values()) {
            list.add(definition(t));
        }
        return list;
    }

    static ObjectNode definition(McpTool t) {
        ObjectNode n = JsonRpc.NODES.objectNode();
        n.put("name", t.getName());
        if (t.getTitle() != null && !t.getTitle().isEmpty()) {
            n.put("title", t.getTitle());
        }
        n.put("description", t.getDescription());
        n.set("inputSchema", t.getInputSchema().deepCopy());
        ObjectNode ann = n.putObject("annotations");
        if (t.getTitle() != null && !t.getTitle().isEmpty()) {
            ann.put("title", t.getTitle());
        }
        String m = t.getHttpMethod();
        ann.put("readOnlyHint", t.isReadOnly());
        if (!t.isReadOnly()) {
            ann.put("destructiveHint", "DELETE".equals(m));
        }
        ann.put("idempotentHint", "GET".equals(m) || "PUT".equals(m) || "DELETE".equals(m) || "HEAD".equals(m));
        ann.put("openWorldHint", false);
        return n;
    }

    // ------------------------------------------------------------------ call

    @Override
    public ToolResult callTool(String name, JsonNode arguments, McpCallContext ctx) {
        McpTool tool = catalog().getExposed().get(name);
        if (tool == null) {
            throw new McpException(JsonRpc.INVALID_PARAMS, "Unknown tool: " + name);
        }
        long start = System.currentTimeMillis();
        AuditEntry entry = new AuditEntry();
        entry.setTime(start);
        entry.setCaller(ctx.getCaller());
        entry.setRemoteAddress(ctx.getRemoteAddress());
        entry.setTool(tool.getName());
        entry.setTarget(tool.getHttpMethod() + " " + tool.getPathPattern());
        entry.setArguments(redactor.summarize(arguments));
        ToolResult result;
        try {
            entry.setTarget(tool.getHttpMethod() + " " + invoker.buildPathAndQuery(tool, arguments));
            LoopbackInvoker.Result r = invoker.invoke(tool, arguments, upstreamHeaders(ctx));
            entry.setStatus(r.getStatus());
            boolean ok = r.getStatus() >= 200 && r.getStatus() < 300;
            String body = r.getBody() == null ? "" : r.getBody();
            String text = truncate(body);
            if (!ok) {
                text = "HTTP " + r.getStatus() + (text.isEmpty() ? "" : "\n" + text);
            } else if (text.isEmpty()) {
                text = "HTTP " + r.getStatus() + " (empty body)";
            }
            entry.setSuccess(ok);
            entry.setResult(redactor.summarizeText(body));
            result = new ToolResult(text, !ok);
        } catch (LoopbackInvoker.InvalidArgumentsException e) {
            entry.setResult(e.getMessage());
            result = new ToolResult(e.getMessage(), true);
        } catch (HttpTimeoutException e) {
            String msg = "调用超时(" + properties.getCallTimeout() + ")";
            entry.setResult(msg);
            result = new ToolResult(msg, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            entry.setResult("interrupted");
            result = new ToolResult("调用被中断", true);
        } catch (Exception e) {
            String msg = "调用失败: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            LOG.warn("csap-apidoc MCP: 工具 {} {}", tool.getName(), msg);
            entry.setResult(redactor.truncate(msg));
            result = new ToolResult(msg, true);
        }
        entry.setDurationMs(System.currentTimeMillis() - start);
        record(entry);
        return result;
    }

    private String truncate(String body) {
        int max = properties.getMaxResultChars();
        if (max <= 0 || body.length() <= max) {
            return body;
        }
        return body.substring(0, max) + "\n…(结果过长已截断,共 " + body.length() + " 字符)";
    }

    /**
     * 转发给目标接口的请求头:forward-headers(token 模式不转发 Authorization)+ upstream-headers
     */
    Map<String, String> upstreamHeaders(McpCallContext ctx) {
        Map<String, String> out = new LinkedHashMap<>();
        McpProperties.AuthMode mode = authMode();
        for (String h : forwardHeaders()) {
            if (mode == McpProperties.AuthMode.TOKEN && "Authorization".equalsIgnoreCase(h)) {
                continue;
            }
            String v = ctx.header(h);
            if (v != null) {
                out.put(h, v);
            }
        }
        out.putAll(properties.getAuth().getUpstreamHeaders());
        return out;
    }

    /**
     * 记录鉴权失败等非工具调用事件
     *
     * @param caller 调用方
     * @param ip     来源
     * @param status HTTP 状态码
     * @param reason 原因
     */
    public void recordRejected(String caller, String ip, int status, String reason) {
        AuditEntry e = new AuditEntry();
        e.setTime(System.currentTimeMillis());
        e.setCaller(caller);
        e.setRemoteAddress(ip);
        e.setTool("-");
        e.setTarget("POST " + properties.getPath());
        e.setStatus(status);
        e.setSuccess(false);
        e.setResult(reason);
        record(e);
    }

    private void record(AuditEntry e) {
        if (audit != null && properties.getAudit().isEnabled()) {
            audit.record(e);
        }
    }

    // ------------------------------------------------------------------ auth

    public McpProperties.AuthMode authMode() {
        AuthSetting a = settings.getAuth();
        if (a != null && a.getMode() != null) {
            McpProperties.AuthMode m = parseMode(a.getMode());
            if (m != null) {
                return m;
            }
        }
        return properties.getAuth().getMode() == null ? McpProperties.AuthMode.TOKEN : properties.getAuth().getMode();
    }

    public List<String> forwardHeaders() {
        AuthSetting a = settings.getAuth();
        if (a != null && a.getForwardHeaders() != null) {
            return a.getForwardHeaders();
        }
        return properties.getAuth().getForwardHeaders();
    }

    static McpProperties.AuthMode parseMode(String s) {
        if (s == null) {
            return null;
        }
        try {
            return McpProperties.AuthMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public McpAuthenticator.Result authenticate(String authorization, String remoteAddress) {
        return authenticator.authenticate(authMode(), authorization, remoteAddress);
    }

    // ------------------------------------------------------------------ admin

    public McpProperties getProperties() {
        return properties;
    }

    public McpSettingsStore getStore() {
        return store;
    }

    public McpAuthenticator getAuthenticator() {
        return authenticator;
    }

    public AuditLog getAudit() {
        return audit;
    }

    public McpSettings currentSettings() {
        return settings.copy();
    }

    /**
     * 一个工具的修改
     */
    public static class ToolUpdate {
        private String methodKey;
        private Boolean enabled;
        private String name;
        private String description;

        public String getMethodKey() {
            return methodKey;
        }

        public void setMethodKey(String methodKey) {
            this.methodKey = methodKey;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * 保存结果
     */
    public static class SaveResult {
        private final boolean persisted;
        private final String location;
        private final List<String> errors;

        public SaveResult(boolean persisted, String location, List<String> errors) {
            this.persisted = persisted;
            this.location = location;
            this.errors = errors;
        }

        public boolean isPersisted() {
            return persisted;
        }

        public String getLocation() {
            return location;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * 批量修改工具设置。与默认值相同的项不写入规则文件,保持文件精简。
     *
     * @param updates 修改
     * @return 结果;有校验错误时不保存
     */
    public synchronized SaveResult updateTools(List<ToolUpdate> updates) {
        ToolCatalog cat = catalog();
        McpSettings next = settings.copy();
        List<String> errors = new ArrayList<>();
        for (ToolUpdate u : updates) {
            if (u == null || u.getMethodKey() == null) {
                continue;
            }
            McpTool t = cat.byMethodKey(u.getMethodKey());
            if (t == null) {
                errors.add("接口不存在: " + u.getMethodKey());
                continue;
            }
            String name = blankToNull(u.getName());
            if (name != null && !ToolCatalog.NAME_PATTERN.matcher(name).matches()) {
                errors.add("工具名只能包含字母、数字、_ 和 -,最长 64: " + name);
                continue;
            }
            if (name != null && name.equals(t.getDefaultName())) {
                name = null;
            }
            String desc = blankToNull(u.getDescription());
            if (desc != null && desc.equals(t.getDefaultDescription())) {
                desc = null;
            }
            Boolean enabled = u.getEnabled();
            if (enabled != null && enabled == t.isExposedByDefault()) {
                enabled = null;
            }
            ToolSetting s = new ToolSetting(enabled, name, desc);
            if (s.isEmpty()) {
                next.getTools().remove(u.getMethodKey());
            } else {
                next.getTools().put(u.getMethodKey(), s);
            }
        }
        // 改写名不能重名(忽略大小写)
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, ToolSetting> e : next.getTools().entrySet()) {
            String n = e.getValue().getName();
            if (n != null && !names.add(n.toLowerCase(Locale.ROOT))) {
                errors.add("工具名重复: " + n);
            }
        }
        if (!errors.isEmpty()) {
            return new SaveResult(false, store.location(), errors);
        }
        return persist(next);
    }

    /**
     * 修改鉴权方式
     *
     * @param mode           token / passthrough
     * @param forwardHeaders 转发的请求头
     * @return 结果
     */
    public synchronized SaveResult updateAuth(String mode, List<String> forwardHeaders) {
        List<String> errors = new ArrayList<>();
        McpProperties.AuthMode m = parseMode(mode);
        if (mode != null && m == null) {
            errors.add("鉴权方式只能是 token 或 passthrough: " + mode);
        }
        List<String> headers = null;
        if (forwardHeaders != null) {
            headers = new ArrayList<>();
            for (String h : forwardHeaders) {
                String v = blankToNull(h);
                if (v == null) {
                    continue;
                }
                if (!HEADER_NAME.matcher(v).matches()) {
                    errors.add("请求头名称不合法: " + v);
                } else if (!headers.contains(v)) {
                    headers.add(v);
                }
            }
        }
        if (!errors.isEmpty()) {
            return new SaveResult(false, store.location(), errors);
        }
        McpSettings next = settings.copy();
        String modeValue = m == null ? null : m.name().toLowerCase(Locale.ROOT);
        next.setAuth(modeValue == null && headers == null ? null : new AuthSetting(modeValue, headers));
        return persist(next);
    }

    private SaveResult persist(McpSettings next) {
        List<String> errors = new ArrayList<>();
        try {
            store.save(next);
        } catch (Exception e) {
            LOG.warn("csap-apidoc MCP: 保存规则文件失败: {}", e.getMessage());
            errors.add("保存失败(仅在内存生效): " + e.getMessage());
        }
        settings = next;
        settingsVersion.incrementAndGet();
        return new SaveResult(errors.isEmpty() && store.isPersistent(), store.location(), errors);
    }

    public List<AuditEntry> queryAudit(int limit, String tool, String caller) {
        return audit == null ? new ArrayList<>() : audit.query(limit, tool, caller);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final class CatalogKey {
        final Object doc;
        final long docVersion;
        final long settingsVersion;

        CatalogKey(Object doc, long docVersion, long settingsVersion) {
            this.doc = doc;
            this.docVersion = docVersion;
            this.settingsVersion = settingsVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CatalogKey)) {
                return false;
            }
            CatalogKey k = (CatalogKey) o;
            return doc == k.doc && docVersion == k.docVersion && settingsVersion == k.settingsVersion;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(doc) * 31 + Long.hashCode(docVersion) * 17 + Long.hashCode(settingsVersion);
        }
    }
}
