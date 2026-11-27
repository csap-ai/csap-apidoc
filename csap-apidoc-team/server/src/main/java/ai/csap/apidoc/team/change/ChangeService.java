package ai.csap.apidoc.team.change;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.RequestContext;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.common.UnifiedDiff;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.env.Env;
import ai.csap.apidoc.team.env.EnvDiff;
import ai.csap.apidoc.team.env.Environments;
import ai.csap.apidoc.team.rule.RulePublisher;
import ai.csap.apidoc.team.rule.RuleReader;
import ai.csap.apidoc.team.service.ServiceService;
import ai.csap.apidoc.team.user.UserService;
import ai.csap.apidoc.team.webhook.WebhookEvent;
import ai.csap.apidoc.team.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** 修改单状态机(契约第 4 节)。 */
@Service
public class ChangeService {

    private static final Logger log = LoggerFactory.getLogger(ChangeService.class);

    private final Db db;
    private final RuleReader reader;
    private final RulePublisher publisher;
    private final AuditService audit;
    private final WebhookService webhooks;
    private final Access access;
    private final UserService users;
    private final ServiceService services;
    private final Environments environments;
    private final EnvDiff envDiff;

    public ChangeService(Db db, RuleReader reader, RulePublisher publisher, AuditService audit, WebhookService webhooks,
                         Access access, UserService users, ServiceService services, Environments environments, EnvDiff envDiff) {
        this.environments = environments;
        this.envDiff = envDiff;
        this.db = db;
        this.reader = reader;
        this.publisher = publisher;
        this.audit = audit;
        this.webhooks = webhooks;
        this.access = access;
        this.users = users;
        this.services = services;
    }

    // ---- 数据 ----

    record Change(long id, long projectId, String title, String reason, long authorId, String status, long createdAt, long updatedAt,
                  Long submittedAt, Long decidedBy, Long decidedAt, String decisionNote, Long publishedAt, String kind,
                  String envKey, String sourceEnv) {
    }

    record Item(long id, long changeId, String service, String ruleKey, long baseVersion, String before, String after,
                Long publishedVersion, Long publishedHistoryId) {
    }

    /** 创建 / 编辑时的一条输入。baseVersion 为 null 表示取当前版本。 */
    public record ItemInput(String service, String ruleKey, Long baseVersion, String afterRules) {
    }

    private static final String CHANGE_COLS = "id, project_id, title, reason, author_id, status, created_at, updated_at, submitted_at,"
            + " decided_by, decided_at, decision_note, published_at, kind, env_key, source_env";

    private static Long nl(java.sql.ResultSet rs, int col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Change mapChange(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Change(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
                rs.getLong(7), rs.getLong(8), nl(rs, 9), nl(rs, 10), nl(rs, 11), rs.getString(12), nl(rs, 13), rs.getString(14),
                rs.getString(15), rs.getString(16));
    }

    private Change load(long id) {
        Change c = db.one("SELECT " + CHANGE_COLS + " FROM csap_team_change WHERE id = ?", ChangeService::mapChange, id);
        if (c == null) {
            throw ApiException.notFound("修改单不存在");
        }
        return c;
    }

    private List<Item> items(long changeId) {
        return db.query("SELECT id, change_id, service, rule_key, base_version, before_rules, after_rules, published_version,"
                + " published_history_id FROM csap_team_change_item WHERE change_id = ? ORDER BY id", (rs, i) ->
                new Item(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
                        rs.getString(7), nl(rs, 8), nl(rs, 9)), changeId);
    }

    private record Project(long id, String name, boolean requireApproval) {
    }

    private Project project(long id) {
        Project p = db.one("SELECT id, name, require_approval FROM csap_team_project WHERE id = ?",
                (rs, i) -> new Project(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1), id);
        if (p == null) {
            throw ApiException.validation("项目不存在");
        }
        return p;
    }

    private Map<String, Object> person(Long userId) {
        if (userId == null) {
            return null;
        }
        UserService.UserRow u = users.byId(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", userId);
        m.put("displayName", u == null ? "(已删除用户)" : u.displayName());
        return m;
    }

    private String displayName(long userId) {
        UserService.UserRow u = users.byId(userId);
        return u == null ? "未知用户" : u.displayName();
    }

    // ---- 环境 ----

    private String keyOf(Change c) {
        return c.envKey() == null ? environments.defaultEnv().key() : c.envKey();
    }

    /** 修改单的目标环境;配置里已不存在返回 null(只读展示时容忍)。 */
    private Env envOrNull(Change c) {
        return environments.find(keyOf(c));
    }

    /** 对修改单执行需要读写环境库的操作:环境必须仍在配置里。 */
    private Env envOf(Change c) {
        Env e = envOrNull(c);
        if (e == null) {
            throw ApiException.validation("修改单的目标环境 " + keyOf(c) + " 已不在配置中");
        }
        return e;
    }

    private String tag(Project p, Env env) {
        return "[" + p.name() + (environments.multiple() && env != null ? " / " + env.name() : "") + "] ";
    }

    private boolean needsApproval(Project p, Env env) {
        return p.requireApproval() || (env != null && env.isProtected());
    }

    // ---- JSON ----

    private static boolean isOpen(String status) {
        return "DRAFT".equals(status) || "PENDING".equals(status) || "CONFLICT".equals(status);
    }

    private long commentCount(long changeId) {
        return db.count("SELECT COUNT(*) FROM csap_team_comment WHERE change_id = ? AND deleted = 0", changeId);
    }

    /** 同一环境、同一服务、同一规则的未完成修改单(DRAFT / PENDING / CONFLICT),exclude 为被排除的修改单 id。 */
    private List<Map<String, Object>> openRows(String envKey, String service, String ruleKey, Long exclude, boolean withAuthor) {
        List<Object> args = new ArrayList<>(List.of(service, ruleKey, envKey));
        String sql = "SELECT DISTINCT c.id, c.title, c.status, c.author_id, c.updated_at FROM csap_team_change_item i JOIN csap_team_change c"
                + " ON c.id = i.change_id WHERE i.service = ? AND i.rule_key = ? AND c.env_key = ?"
                + " AND c.status IN ('DRAFT', 'PENDING', 'CONFLICT')";
        if (exclude != null) {
            sql += " AND c.id <> ?";
            args.add(exclude);
        }
        sql += " ORDER BY c.id";
        return db.query(sql, (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            if (withAuthor) {
                m.put("id", rs.getLong(1));
                m.put("title", rs.getString(2));
                m.put("status", rs.getString(3));
                m.put("author", person(rs.getLong(4)));
                m.put("updatedAt", rs.getLong(5));
            } else {
                m.put("changeId", rs.getLong(1));
                m.put("title", rs.getString(2));
                m.put("status", rs.getString(3));
            }
            return m;
        }, args.toArray());
    }

    /** 规则编辑器用:这条规则上有哪些未完成的修改单。 */
    public List<Map<String, Object>> openChanges(Env env, String service, String ruleKey) {
        return openRows(env.key(), service, ruleKey, null, true);
    }

    private Map<String, Object> toJson(Change c, boolean full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("projectId", c.projectId());
        Project p = db.one("SELECT id, name, require_approval FROM csap_team_project WHERE id = ?",
                (rs, i) -> new Project(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1), c.projectId());
        m.put("projectName", p == null ? null : p.name());
        m.put("title", c.title());
        m.put("reason", c.reason());
        m.put("kind", c.kind());
        m.put("status", c.status());
        Env env = envOrNull(c);
        m.put("env", keyOf(c));
        m.put("envName", env == null ? keyOf(c) : env.name());
        m.put("sourceEnv", c.sourceEnv());
        m.put("requireApproval", (p != null && p.requireApproval()) || (env != null && env.isProtected()));
        m.put("author", person(c.authorId()));
        m.put("createdAt", c.createdAt());
        m.put("updatedAt", c.updatedAt());
        m.put("submittedAt", c.submittedAt());
        m.put("decidedBy", person(c.decidedBy()));
        m.put("decidedAt", c.decidedAt());
        m.put("decisionNote", c.decisionNote());
        m.put("publishedAt", c.publishedAt());
        m.put("commentCount", commentCount(c.id()));
        List<Item> items = items(c.id());
        m.put("itemCount", items.size());
        boolean open = isOpen(c.status());
        boolean readable = env != null && env.isUp();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Item it : items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", it.id());
            im.put("service", it.service());
            im.put("ruleKey", it.ruleKey());
            im.put("baseVersion", it.baseVersion());
            if (full) {
                Long cur = readable ? reader.currentVersion(env, it.service(), it.ruleKey()) : null;
                im.put("beforeRules", it.before());
                im.put("afterRules", it.after());
                im.put("publishedVersion", it.publishedVersion());
                im.put("publishedHistoryId", it.publishedHistoryId());
                im.put("currentVersion", cur);
                im.put("conflict", open && cur != null && cur != it.baseVersion());
                im.put("diff", UnifiedDiff.ofRules(it.before(), it.after(), "before", "after"));
                im.put("overlaps", openRows(keyOf(c), it.service(), it.ruleKey(), c.id(), false));
            }
            out.add(im);
        }
        m.put("items", out);
        return m;
    }

    public Map<String, Object> get(Who who, long id) {
        Change c = load(id);
        checkView(who, c);
        return toJson(c, true);
    }

    private void checkView(Who who, Change c) {
        if (who.viaToken()) {
            if (who.tokenProject() != null && who.tokenProject() != c.projectId()) {
                throw ApiException.forbidden("令牌无权访问该项目");
            }
            return;
        }
        if (access.roleIn(who.user(), c.projectId()) == null) {
            throw ApiException.notFound("修改单不存在或无权查看");
        }
    }

    public Map<String, Object> list(SessionUser u, Long projectId, String status, boolean mine, String env, int page, int size) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        Set<Long> visible = access.projectsWith(u, Role.VIEWER);
        if (visible != null) {
            if (visible.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("items", List.of());
                empty.put("total", 0);
                return empty;
            }
            w.append(" AND project_id IN (");
            boolean first = true;
            for (Long id : visible) {
                w.append(first ? "?" : ", ?");
                args.add(id);
                first = false;
            }
            w.append(')');
        }
        if (projectId != null) { w.append(" AND project_id = ?"); args.add(projectId); }
        if (status != null && !status.isBlank()) { w.append(" AND status = ?"); args.add(status); }
        if (mine) { w.append(" AND author_id = ?"); args.add(u.id()); }
        if (env != null && !env.isBlank()) { w.append(" AND env_key = ?"); args.add(env); }
        long total = db.count("SELECT COUNT(*) FROM csap_team_change" + w, args.toArray());
        List<Change> rows = db.page("SELECT " + CHANGE_COLS + " FROM csap_team_change" + w + " ORDER BY id DESC",
                ChangeService::mapChange, (page - 1) * size, size, args.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Change c : rows) {
            items.add(toJson(c, false));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", total);
        return out;
    }

    // ---- 创建 / 编辑 ----

    private void checkWrite(Who who, long projectId) {
        if (who.viaToken()) {
            if (who.tokenProject() != null && who.tokenProject() != projectId) {
                throw ApiException.forbidden("令牌无权访问该项目");
            }
            return;
        }
        Role r = access.roleIn(who.user(), projectId);
        if (r == null || !r.atLeast(Role.DEVELOPER)) {
            throw ApiException.forbidden("没有权限在该项目创建修改单");
        }
    }

    private List<Item> prepareItems(Env env, long projectId, List<ItemInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw ApiException.validation("修改单至少包含一条规则");
        }
        if (inputs.size() > 500) {
            throw ApiException.validation("一张修改单最多 500 条规则");
        }
        List<Item> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ItemInput in : inputs) {
            if (in.service() == null || in.service().isBlank() || in.ruleKey() == null || in.ruleKey().isBlank()
                    || in.ruleKey().length() > 512) {
                throw ApiException.validation("每条规则都需要 service 和 ruleKey(不超过 512 字)");
            }
            if (!seen.add(in.service() + "\u0000" + in.ruleKey())) {
                throw ApiException.validation("同一张修改单里规则重复: " + in.service() + " / " + in.ruleKey());
            }
            ServiceService.Row s = services.get(in.service());
            if (s == null || s.projectId() == null || s.projectId() != projectId) {
                throw ApiException.validation("服务 " + in.service() + " 不属于该项目");
            }
            if (in.afterRules() != null && !Json.isValid(in.afterRules())) {
                throw ApiException.validation("规则内容不是合法的 JSON: " + in.ruleKey());
            }
            RuleReader.Current cur = reader.current(env, in.service(), in.ruleKey());
            long base = in.baseVersion() != null ? in.baseVersion() : (cur == null ? 0 : cur.version());
            if (base < 0) {
                throw ApiException.validation("baseVersion 无效");
            }
            out.add(new Item(0, 0, in.service(), in.ruleKey(), base, cur == null ? null : cur.rules(), in.afterRules(), null, null));
        }
        return out;
    }

    private void insertItems(long changeId, List<Item> items) {
        for (Item it : items) {
            db.update("INSERT INTO csap_team_change_item (change_id, service, rule_key, base_version, before_rules, after_rules)"
                    + " VALUES (?, ?, ?, ?, ?, ?)", changeId, it.service(), it.ruleKey(), it.baseVersion(), it.before(), it.after());
        }
    }

    private static String title(String t) {
        if (t == null || t.isBlank() || t.trim().length() > 256) {
            throw ApiException.validation("标题不能为空且不超过 256 字");
        }
        return t.trim();
    }

    public Map<String, Object> create(Who who, long projectId, Env env, String title, String reason, List<ItemInput> inputs,
                                      String kind) {
        project(projectId);
        checkWrite(who, projectId);
        env.requireWritable();
        env.db();
        String t = title(title);
        List<Item> items = prepareItems(env, projectId, inputs);
        return createChange(who, projectId, env, null, t, reason, items, kind, t);
    }

    private Map<String, Object> createChange(Who who, long projectId, Env env, String sourceEnv, String title, String reason,
                                             List<Item> items, String kind, String auditReason) {
        long now = System.currentTimeMillis();
        long id = db.inTx(() -> {
            long cid = db.insert("INSERT INTO csap_team_change (project_id, title, reason, author_id, status, created_at, updated_at,"
                    + " kind, env_key, source_env) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)", projectId, title, reason,
                    who.user().id(), now, now, kind, env.key(), sourceEnv);
            insertItems(cid, items);
            audit.log("change.create").project(projectId).env(env.key()).change(cid).reason(auditReason).save();
            return cid;
        });
        return toJson(load(id), true);
    }

    /**
     * 推广(契约 3.3):把来源环境的规则内容推到目标环境,生成 kind=PROMOTE 的修改单(env=to,sourceEnv=from)。
     * items 为 null = 项目所有服务的差异;submit=true 时创建后立刻提交。
     */
    public Map<String, Object> promote(Who who, long projectId, Env from, Env to, List<String[]> selected, boolean includeDeletes,
                                       String title, String reason, boolean submit) {
        project(projectId);
        checkWrite(who, projectId);
        if (from.key().equals(to.key())) {
            throw ApiException.validation("from 和 to 不能是同一个环境");
        }
        to.requireWritable();
        from.db();
        to.db();
        List<String> projectServices = db.query("SELECT service FROM csap_team_project_service WHERE project_id = ?",
                (rs, i) -> rs.getString(1), projectId);
        java.util.Set<String> only = null;
        if (selected != null) {
            only = new java.util.HashSet<>();
            for (String[] pair : selected) {
                if (pair[0] == null || pair[1] == null || !projectServices.contains(pair[0])) {
                    throw ApiException.validation("服务 " + pair[0] + " 不属于该项目");
                }
                only.add(pair[0] + "\u0000" + pair[1]);
            }
        }
        List<Item> items = new ArrayList<>();
        for (EnvDiff.ServiceDiff sd : envDiff.diff(from, to, projectServices)) {
            for (EnvDiff.Item d : sd.items()) {
                if (only != null && !only.contains(sd.service() + "\u0000" + d.ruleKey())) {
                    continue;
                }
                if (EnvDiff.ONLY_TO.equals(d.status()) && !includeDeletes) {
                    continue;
                }
                // base_version / before_rules = 目标环境当前内容;after_rules = 来源环境当前内容(ONLY_TO 为 null)
                items.add(new Item(0, 0, sd.service(), d.ruleKey(), d.toVersion() == null ? 0 : d.toVersion(), d.toRules(), d.fromRules(),
                        null, null));
            }
        }
        if (items.isEmpty()) {
            throw ApiException.validation("两个环境没有差异");
        }
        if (items.size() > 500) {
            throw ApiException.validation("一张修改单最多 500 条规则,请分批推广");
        }
        String t = title == null || title.isBlank() ? "推广 " + from.key() + " → " + to.key() : title(title);
        Map<String, Object> created = createChange(who, projectId, to, from.key(), t, reason, items, "PROMOTE",
                t + "(promote " + from.key() + "→" + to.key() + ")");
        if (submit) {
            return submit(who, ((Number) created.get("id")).longValue());
        }
        return created;
    }

    public Map<String, Object> update(Who who, long id, String title, String reason, List<ItemInput> inputs) {
        Change c = load(id);
        authorOnly(who, c);
        requireStatus(c, "DRAFT");
        Env env = envOf(c);
        env.requireWritable();
        String t = title != null ? title(title) : c.title();
        String r = reason != null ? reason : c.reason();
        List<Item> items = inputs == null ? null : prepareItems(env, c.projectId(), inputs);
        db.inTx(() -> {
            db.update("UPDATE csap_team_change SET title = ?, reason = ?, updated_at = ? WHERE id = ? AND status = 'DRAFT'", t, r,
                    System.currentTimeMillis(), id);
            if (items != null) {
                db.update("DELETE FROM csap_team_change_item WHERE change_id = ?", id);
                insertItems(id, items);
            }
            audit.log("change.update").project(c.projectId()).env(c.envKey()).change(id).reason(t).save();
        });
        return toJson(load(id), true);
    }

    private void authorOnly(Who who, Change c) {
        if (who.user().id() != c.authorId()) {
            throw ApiException.forbidden("只有作者可以操作这张修改单");
        }
        checkWriteScope(who, c);
    }

    private void checkWriteScope(Who who, Change c) {
        if (who.viaToken() && who.tokenProject() != null && who.tokenProject() != c.projectId()) {
            throw ApiException.forbidden("令牌无权访问该项目");
        }
    }

    private static void requireStatus(Change c, String... allowed) {
        for (String s : allowed) {
            if (s.equals(c.status())) {
                return;
            }
        }
        throw ApiException.validation("修改单当前状态是 " + c.status() + ",不能执行此操作");
    }

    /** 状态转移,带旧状态条件;并发修改时失败。 */
    private void transition(Change c, String from, String to) {
        int n = db.update("UPDATE csap_team_change SET status = ?, updated_at = ? WHERE id = ? AND status = ?", to,
                System.currentTimeMillis(), c.id(), from);
        if (n == 0) {
            throw ApiException.conflict("修改单状态已变化,请刷新后重试");
        }
    }

    // ---- 提交 / 审批 ----

    private List<RulePublisher.Pub> pubs(List<Item> items) {
        List<RulePublisher.Pub> out = new ArrayList<>();
        for (Item it : items) {
            out.add(new RulePublisher.Pub(it.id(), it.service(), it.ruleKey(), it.baseVersion(), it.after()));
        }
        return out;
    }

    /** 事务里收集、事务后触发的 webhook 事件。 */
    private final class Pending {
        final List<WebhookEvent> events = new ArrayList<>();
        List<RulePublisher.Conflict> conflicts = List.of();
        /** 目标环境的规则写入已经提交(环境用独立数据源时,与主库事务不是同一个) */
        boolean envWritten;
    }

    /** 主库事务;目标环境已提交而主库失败时,日志里打 ERROR(下一轮历史同步会记 rule.changed_outside)。 */
    private void mainTx(Change c, Env env, Pending pending, Runnable body) {
        try {
            db.inTx(body);
        } catch (RuntimeException e) {
            if (pending.envWritten && env.ownDataSource()) {
                log.error("修改单 #{} 已写入环境 {} 的规则,但团队版主库更新失败,状态与审计未记录;"
                        + "下一轮历史同步会记一条 rule.changed_outside: {}", c.id(), env.key(), e.toString());
            }
            throw e;
        }
    }

    public Map<String, Object> submit(Who who, long id) {
        Change c = load(id);
        authorOnly(who, c);
        requireStatus(c, "DRAFT");
        Env env = envOf(c);
        env.requireWritable();
        env.db();
        Project p = project(c.projectId());
        boolean approval = needsApproval(p, env);
        List<Item> items = items(id);
        Pending pending = new Pending();
        List<RulePublisher.Conflict> pre = publisher.check(env, pubs(items));
        publisher.ensureMeta(env, items.stream().map(Item::service).toList());
        mainTx(c, env, pending, () -> {
            if (!pre.isEmpty()) {
                transition(c, "DRAFT", "CONFLICT");
                recordConflict(c, p, env, pre, pending);
                return;
            }
            if (approval) {
                transition(c, "DRAFT", "PENDING");
                db.update("UPDATE csap_team_change SET submitted_at = ? WHERE id = ?", System.currentTimeMillis(), id);
                audit.log("change.submit").project(c.projectId()).env(env.key()).change(id).reason(c.title()).save();
                pending.events.add(event("change.submitted", c, p, env, who.user().displayName(), items.size(),
                        tag(p, env) + displayName(c.authorId()) + " 提交了修改单「" + c.title() + "」,等待审批(" + items.size() + " 条规则)"));
            } else {
                transition(c, "DRAFT", "PUBLISHED");
                db.update("UPDATE csap_team_change SET submitted_at = ? WHERE id = ?", System.currentTimeMillis(), id);
                audit.log("change.submit").project(c.projectId()).env(env.key()).change(id).reason(c.title() + "(项目无需审批,直接发布)").save();
                pending.events.add(event("change.submitted", c, p, env, who.user().displayName(), items.size(),
                        tag(p, env) + displayName(c.authorId()) + " 提交了修改单「" + c.title() + "」(项目无需审批)"));
                if (publishInTx(c, p, env, items, who, pending)) {
                    pending.events.add(publishedEvent(c, p, env, items.size()));
                }
            }
        });
        return finish(c, pending);
    }

    public Map<String, Object> approve(Who who, long id, String note) {
        Change c = load(id);
        checkDecide(who, c);
        requireStatus(c, "PENDING");
        Env env = envOf(c);
        env.requireWritable();
        env.db();
        Project p = project(c.projectId());
        List<Item> items = items(id);
        Pending pending = new Pending();
        publisher.ensureMeta(env, items.stream().map(Item::service).toList());
        mainTx(c, env, pending, () -> {
            transition(c, "PENDING", "PUBLISHED");
            if (!publishInTx(c, p, env, items, who, pending)) {
                return;
            }
            db.update("UPDATE csap_team_change SET decided_by = ?, decided_at = ?, decision_note = ? WHERE id = ?", who.user().id(),
                    System.currentTimeMillis(), limit(note, 1000), id);
            audit.log("change.approve").project(c.projectId()).env(env.key()).change(id)
                    .reason(c.title() + (note == null ? "" : ":" + note)).save();
            pending.events.add(event("change.approved", c, p, env, who.user().displayName(), items.size(),
                    tag(p, env) + who.user().displayName() + " 通过了修改单「" + c.title() + "」"));
            pending.events.add(publishedEvent(c, p, env, items.size()));
        });
        return finish(c, pending);
    }

    public Map<String, Object> reject(Who who, long id, String note) {
        Change c = load(id);
        checkDecide(who, c);
        if (note == null || note.isBlank()) {
            throw ApiException.validation("驳回必须填写原因(note)");
        }
        requireStatus(c, "PENDING");
        Project p = project(c.projectId());
        Env env = envOrNull(c);
        Pending pending = new Pending();
        db.inTx(() -> {
            transition(c, "PENDING", "REJECTED");
            db.update("UPDATE csap_team_change SET decided_by = ?, decided_at = ?, decision_note = ? WHERE id = ?", who.user().id(),
                    System.currentTimeMillis(), limit(note, 1000), id);
            audit.log("change.reject").project(c.projectId()).env(keyOf(c)).change(id).reason(c.title() + ":" + note).save();
            pending.events.add(event("change.rejected", c, p, env, who.user().displayName(), items(id).size(),
                    tag(p, env) + who.user().displayName() + " 驳回了修改单「" + c.title() + "」:" + note));
        });
        return finish(c, pending);
    }

    public Map<String, Object> withdraw(Who who, long id) {
        Change c = load(id);
        checkWriteScope(who, c);
        Role r = who.viaToken() ? null : access.roleIn(who.user(), c.projectId());
        boolean allowed = who.user().id() == c.authorId() || (r != null && r.atLeast(Role.MAINTAINER));
        if (!allowed) {
            throw ApiException.forbidden("只有作者或维护者可以撤回");
        }
        requireStatus(c, "DRAFT", "PENDING");
        db.inTx(() -> {
            transition(c, c.status(), "WITHDRAWN");
            audit.log("change.withdraw").project(c.projectId()).env(keyOf(c)).change(id).reason(c.title()).save();
        });
        return toJson(load(id), true);
    }

    public Map<String, Object> rebase(Who who, long id, List<ItemInput> edits) {
        Change c = load(id);
        authorOnly(who, c);
        requireStatus(c, "CONFLICT");
        Env env = envOf(c);
        env.requireWritable();
        env.db();
        Map<String, ItemInput> byKey = new HashMap<>();
        if (edits != null) {
            for (ItemInput e : edits) {
                if (e.afterRules() != null && !Json.isValid(e.afterRules())) {
                    throw ApiException.validation("规则内容不是合法的 JSON: " + e.ruleKey());
                }
                byKey.put(e.service() + "\u0000" + e.ruleKey(), e);
            }
        }
        // 先在目标环境读(可能是独立数据源),再在主库事务里更新
        List<Object[]> updates = new ArrayList<>();
        for (Item it : items(id)) {
            RuleReader.Current cur = reader.current(env, it.service(), it.ruleKey());
            ItemInput edit = byKey.get(it.service() + "\u0000" + it.ruleKey());
            String after = edit != null ? edit.afterRules() : it.after();
            updates.add(new Object[] {cur == null ? 0 : cur.version(), cur == null ? null : cur.rules(), after, it.id()});
        }
        db.inTx(() -> {
            for (Object[] u : updates) {
                db.update("UPDATE csap_team_change_item SET base_version = ?, before_rules = ?, after_rules = ? WHERE id = ?", u);
            }
            transition(c, "CONFLICT", "DRAFT");
            audit.log("change.rebase").project(c.projectId()).env(env.key()).change(id).reason(c.title()).save();
        });
        return toJson(load(id), true);
    }

    /** 审批权限:管理员(可审批自己的);维护者不能审批自己提交的。 */
    private void checkDecide(Who who, Change c) {
        if (who.viaToken()) {
            throw ApiException.forbidden("开放接口令牌不能审批修改单");
        }
        if (who.user().isAdmin()) {
            return;
        }
        Role r = access.roleIn(who.user(), c.projectId());
        if (r == null || !r.atLeast(Role.MAINTAINER)) {
            throw ApiException.forbidden("只有维护者或管理员可以审批");
        }
        if (c.authorId() == who.user().id()) {
            throw ApiException.forbidden("不能审批自己提交的修改单");
        }
    }

    // ---- 发布 ----

    /**
     * 在已开启的主库事务里发布(状态已先转成 PUBLISHED);规则写入在目标环境的事务里完成并先提交
     * (环境与主库共用数据源时并入同一事务)。冲突时把状态改成 CONFLICT 并返回 false。
     */
    private boolean publishInTx(Change c, Project p, Env env, List<Item> items, Who who, Pending pending) {
        RulePublisher.Result res = publisher.publish(env, pubs(items), who.user().username());
        if (!res.conflicts().isEmpty()) {
            transition(c, "PUBLISHED", "CONFLICT");
            recordConflict(c, p, env, res.conflicts(), pending);
            return false;
        }
        for (RulePublisher.Done d : res.done()) {
            if (d.publishedVersion() != null) {
                pending.envWritten = true;
            }
        }
        for (RulePublisher.Done d : res.done()) {
            db.update("UPDATE csap_team_change_item SET published_version = ?, published_history_id = ? WHERE id = ?",
                    d.publishedVersion(), d.historyId(), d.itemId());
            if (d.publishedVersion() != null) {
                audit.log("change.publish").project(c.projectId()).env(env.key()).service(d.service()).rule(d.ruleKey()).change(c.id())
                        .before(d.before()).after(d.after()).diff(UnifiedDiff.ofRules(d.before(), d.after(), "before", "after"))
                        .ossHistory(d.historyId()).reason(c.title()).save();
            }
        }
        db.update("UPDATE csap_team_change SET published_at = ? WHERE id = ?", System.currentTimeMillis(), c.id());
        if (res.done().stream().noneMatch(d -> d.publishedVersion() != null)) {
            audit.log("change.publish").project(c.projectId()).env(env.key()).change(c.id())
                    .reason(c.title() + "(内容与当前一致,无需写入)").save();
        }
        return true;
    }

    private WebhookEvent publishedEvent(Change c, Project p, Env env, int count) {
        return event("change.published", c, p, env, displayName(c.authorId()), count,
                tag(p, env) + displayName(c.authorId()) + " 的修改单「" + c.title() + "」已发布(" + count + " 条规则)");
    }

    private void recordConflict(Change c, Project p, Env env, List<RulePublisher.Conflict> conflicts, Pending pending) {
        pending.conflicts = conflicts;
        audit.log("change.conflict").project(c.projectId()).env(env.key()).change(c.id())
                .reason(c.title() + ":" + conflicts.size() + " 条规则版本已被修改").save();
        pending.events.add(event("change.conflict", c, p, env, displayName(c.authorId()), conflicts.size(),
                tag(p, env) + displayName(c.authorId()) + " 的修改单「" + c.title() + "」发布冲突:规则已被他人修改"));
    }

    private WebhookEvent event(String type, Change c, Project p, Env env, String actor, int count, String text) {
        List<Map<String, String>> items = new ArrayList<>();
        for (Item it : items(c.id())) {
            items.add(WebhookEvent.item(it.service(), it.ruleKey()));
        }
        return new WebhookEvent(type, System.currentTimeMillis(), p.id(), p.name(), c.id(), c.title(), displayName(c.authorId()),
                items, text, keyOf(c), env == null ? keyOf(c) : env.name());
    }

    /** 事务提交后:触发 webhook,冲突时抛 409。 */
    private Map<String, Object> finish(Change c, Pending pending) {
        pending.events.forEach(webhooks::fire);
        if (!pending.conflicts.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (RulePublisher.Conflict k : pending.conflicts) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("service", k.service());
                m.put("ruleKey", k.ruleKey());
                m.put("baseVersion", k.baseVersion());
                m.put("currentVersion", k.currentVersion());
                m.put("currentRules", k.currentRules());
                list.add(m);
            }
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("changeId", c.id());
            extra.put("items", list);
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "规则已被他人修改,请先同步最新内容(rebase)", extra);
        }
        return toJson(load(c.id()), true);
    }

    private static String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // ---- 回滚 ----

    public Map<String, Object> rollback(Who who, Env env, String service, String key, long historyId, String reason) {
        ServiceService.Row s = services.get(service);
        if (s == null || s.projectId() == null) {
            throw ApiException.validation("服务尚未分配到项目,不能回滚");
        }
        env.requireWritable();
        RuleReader.HistoryRow h = reader.historyById(env, service, historyId);
        if (h == null || !h.ruleKey().equals(key)) {
            throw ApiException.notFound("历史记录不存在");
        }
        RuleReader.Current cur = reader.current(env, service, key);
        List<ItemInput> in = List.of(new ItemInput(service, key, cur == null ? 0 : cur.version(), h.after()));
        String title = "回滚 " + key + " 到历史 #" + historyId;
        return create(who, s.projectId(), env, title, reason, in, "ROLLBACK");
    }

    // ---- 评论支撑 ----

    /** 评论用:检查可见性并返回修改单的基本信息。 */
    public record Info(long id, long projectId, String title, String status, String envKey, long authorId) {
    }

    public Info info(Who who, long id) {
        Change c = load(id);
        checkView(who, c);
        return new Info(c.id(), c.projectId(), c.title(), c.status(), keyOf(c), c.authorId());
    }

    /** change.commented:创建评论后触发。 */
    public void fireCommented(long changeId, long commentId, String author, String body) {
        Change c = load(changeId);
        Project p = project(c.projectId());
        Env env = envOrNull(c);
        String text = tag(p, env) + author + " 评论了修改单「" + c.title() + "」";
        String cut = body == null || body.length() <= 500 ? body : body.substring(0, 500);
        Map<String, Object> comment = new LinkedHashMap<>();
        comment.put("id", commentId);
        comment.put("author", author);
        comment.put("body", cut);
        List<Map<String, String>> items = new ArrayList<>();
        for (Item it : items(c.id())) {
            items.add(WebhookEvent.item(it.service(), it.ruleKey()));
        }
        webhooks.fire(new WebhookEvent("change.commented", System.currentTimeMillis(), p.id(), p.name(), c.id(), c.title(),
                displayName(c.authorId()), items, text, keyOf(c), env == null ? keyOf(c) : env.name(), comment, null));
    }

    /** 当前请求身份的操作者名,仅供日志之外的调用方使用。 */
    public static String actorName() {
        return RequestContext.actor().name();
    }
}
