package ai.csap.apidoc.team.audit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.env.Env;
import ai.csap.apidoc.team.env.Environments;

/** 审计查询、导出(CSV / JSON,流式,最多 100,000 条)、MCP 调用审计。 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    static final int EXPORT_MAX = 100_000;
    private static final String[] CSV_COLS = {"id", "time", "actorType", "actorName", "source", "ip", "action", "projectId", "service",
            "ruleKey", "changeId", "reason", "ossHistoryId", "diff", "envKey", "prevHash", "hash"};
    private static final String[] MCP_COLS = {"id", "time", "service", "caller", "remoteAddress", "tool", "target", "arguments",
            "status", "success", "result", "durationMs"};

    private final AuditService audit;
    private final McpAuditService mcp;
    private final Access access;
    private final Db db;
    private final AuditChain chain;
    private final Environments environments;

    public AuditController(AuditService audit, McpAuditService mcp, Access access, Db db, AuditChain chain, Environments environments) {
        this.audit = audit;
        this.mcp = mcp;
        this.access = access;
        this.db = db;
        this.chain = chain;
        this.environments = environments;
    }

    /** 可见项目:查看需要开发及以上,导出需要维护者及以上;管理员为 null(不限)。 */
    private Set<Long> projects(SessionUser u, Role min) {
        Set<Long> p = access.projectsWith(u, min);
        if (p != null && p.isEmpty()) {
            throw ApiException.forbidden("没有权限查看审计");
        }
        return p;
    }

    private AuditService.Filter filter(SessionUser u, Role min, Long from, Long to, String actor, Long projectId, String env,
                                       String service, String ruleKey, String action, String source) {
        AuditService.Filter f = new AuditService.Filter();
        f.from = from;
        f.to = to;
        f.actor = actor;
        f.projectId = projectId;
        f.env = env == null || env.isBlank() ? null : environments.require(env).key();
        f.service = service;
        f.ruleKey = ruleKey;
        f.action = action;
        f.source = source;
        f.visibleProjects = projects(u, min);
        if (f.visibleProjects != null && projectId != null && !f.visibleProjects.contains(projectId)) {
            throw ApiException.forbidden("没有权限查看该项目的审计");
        }
        return f;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Long from, @RequestParam(required = false) Long to,
                                    @RequestParam(required = false) String actor, @RequestParam(required = false) Long projectId,
                                    @RequestParam(required = false) String service, @RequestParam(required = false) String ruleKey,
                                    @RequestParam(required = false) String action, @RequestParam(required = false) String source,
                                    @RequestParam(required = false) String env,
                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        SessionUser u = access.user();
        return audit.list(filter(u, Role.DEVELOPER, from, to, actor, projectId, env, service, ruleKey, action, source), Math.max(1, page),
                Math.max(1, Math.min(size, 200)));
    }

    @GetMapping("/{id:[0-9]+}")
    public Map<String, Object> get(@org.springframework.web.bind.annotation.PathVariable long id) {
        SessionUser u = access.user();
        Set<Long> visible = projects(u, Role.DEVELOPER);
        Map<String, Object> m = audit.get(id);
        if (m == null || (visible != null && (m.get("projectId") == null || !visible.contains((Long) m.get("projectId"))))) {
            throw ApiException.notFound("审计记录不存在或无权查看");
        }
        return m;
    }

    /** 校验审计哈希链(仅管理员);每次校验记一条 audit.verify。 */
    @GetMapping("/verify")
    public Map<String, Object> verify(@RequestParam(required = false) Long fromId, @RequestParam(required = false) Long toId) {
        access.admin();
        AuditChain.Result r = chain.verify(fromId, toId);
        audit.log("audit.verify").reason((r.ok() ? "OK" : "FAILED " + r.problem() + " at id=" + r.firstBrokenId()) + ";checked=" + r.checked()
                + (fromId == null ? "" : ";fromId=" + fromId) + (toId == null ? "" : ";toId=" + toId)).save();
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("ok", r.ok());
        m.put("checked", r.checked());
        m.put("headId", r.headId());
        m.put("headHash", r.headHash());
        m.put("firstBrokenId", r.firstBrokenId());
        m.put("problem", r.problem());
        return m;
    }

    private Set<String> visibleServices(Set<Long> projects) {
        if (projects == null) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (Long id : projects) {
            out.addAll(db.query("SELECT service FROM csap_team_project_service WHERE project_id = ?", (rs, i) -> rs.getString(1), id));
        }
        return out;
    }

    private McpAuditService.Filter mcpFilter(SessionUser u, Role min, Long from, Long to, String service, String caller, String tool) {
        McpAuditService.Filter f = new McpAuditService.Filter();
        f.from = from;
        f.to = to;
        f.service = service;
        f.caller = caller;
        f.tool = tool;
        f.visibleServices = visibleServices(projects(u, min));
        return f;
    }

    @GetMapping("/mcp")
    public Map<String, Object> mcpList(@RequestParam(required = false) Long from, @RequestParam(required = false) Long to,
                                       @RequestParam(required = false) String service, @RequestParam(required = false) String caller,
                                       @RequestParam(required = false) String tool, @RequestParam(required = false) String env,
                                       @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        SessionUser u = access.user();
        return mcp.list(environments.require(env), mcpFilter(u, Role.DEVELOPER, from, to, service, caller, tool), Math.max(1, page), Math.max(1, Math.min(size, 200)));
    }

    /**
     * 导出。默认导出审计;{@code kind=mcp} 导出 MCP 调用(筛选用 from/to/service/caller/tool)。
     * 导出本身记一条 {@code audit.export}。
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam(defaultValue = "csv") String format,
                                                        @RequestParam(required = false) String kind,
                                                        @RequestParam(required = false) Long from, @RequestParam(required = false) Long to,
                                                        @RequestParam(required = false) String actor,
                                                        @RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String service,
                                                        @RequestParam(required = false) String ruleKey,
                                                        @RequestParam(required = false) String action,
                                                        @RequestParam(required = false) String source,
                                                        @RequestParam(required = false) String caller,
                                                        @RequestParam(required = false) String tool,
                                                        @RequestParam(required = false) String env) {
        if (!"csv".equals(format) && !"json".equals(format)) {
            throw ApiException.validation("format 只能是 csv 或 json");
        }
        SessionUser u = access.user();
        boolean isMcp = "mcp".equals(kind);
        AuditService.Filter af = isMcp ? null : filter(u, Role.MAINTAINER, from, to, actor, projectId, env, service, ruleKey, action, source);
        Env mcpEnv = isMcp ? environments.require(env) : null;
        McpAuditService.Filter mf = isMcp ? mcpFilter(u, Role.MAINTAINER, from, to, service, caller, tool) : null;
        audit.log("audit.export").reason((isMcp ? "MCP 调用" : "审计") + " 格式=" + format).save();
        boolean csv = "csv".equals(format);
        String name = "csap-team-" + (isMcp ? "mcp-audit-" : "audit-") + System.currentTimeMillis() + (csv ? ".csv" : ".json");
        AuditChain.Point anchor = isMcp ? null : chain.anchor();
        StreamingResponseBody body = out -> write(out, csv, isMcp, af, mf, mcpEnv, anchor);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(csv ? MediaType.parseMediaType("text/csv;charset=UTF-8") : MediaType.APPLICATION_JSON)
                .body(body);
    }

    private void write(OutputStream out, boolean csv, boolean isMcp, AuditService.Filter af, McpAuditService.Filter mf, Env mcpEnv,
                       AuditChain.Point anchor) throws IOException {
        java.io.Writer w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
        String[] cols = isMcp ? MCP_COLS : CSV_COLS;
        boolean[] first = {true};
        IOException[] failure = {null};
        if (csv) {
            w.write('﻿');
            w.write(String.join(",", cols));
            w.write("\r\n");
        } else if (isMcp) {
            w.write("[");
        } else {
            // 审计 JSON 导出:{"anchor":{id,hash}|null,"items":[...]},便于离线校验哈希链
            w.write("{\"anchor\":" + (anchor == null ? "null" : "{\"id\":" + anchor.id() + ",\"hash\":\"" + anchor.hash() + "\"}")
                    + ",\"items\":[");
        }
        java.util.function.Consumer<Map<String, Object>> sink = raw -> {
            Map<String, Object> row = raw;
            if (!isMcp) {
                // 导出里环境列叫 envKey
                row = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> en : raw.entrySet()) {
                    row.put("env".equals(en.getKey()) ? "envKey" : en.getKey(), en.getValue());
                }
            }
            try {
                if (csv) {
                    List<String> cells = new ArrayList<>();
                    for (String c : cols) {
                        cells.add(cell(c, row));
                    }
                    w.write(String.join(",", cells));
                    w.write("\r\n");
                } else {
                    w.write(first[0] ? "\n" : ",\n");
                    w.write(Json.write(row));
                    first[0] = false;
                }
            } catch (IOException e) {
                failure[0] = e;
                throw new IllegalStateException(e);
            }
        };
        try {
            if (isMcp) {
                mcp.stream(mcpEnv, mf, EXPORT_MAX, sink);
            } else {
                audit.stream(af, EXPORT_MAX, sink);
            }
        } catch (IllegalStateException e) {
            if (failure[0] != null) {
                throw failure[0];
            }
            throw e;
        }
        if (!csv) {
            w.write(isMcp ? "\n]" : "\n]}");
        }
        w.flush();
    }

    public static String cell(String col, Map<String, Object> row) {
        Object v = "time".equals(col) ? time(row.get("at")) : row.get(col);
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (!(v instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String time(Object at) {
        if (!(at instanceof Number n)) {
            return "";
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(n.longValue()).atZone(ZoneId.systemDefault()));
    }
}
