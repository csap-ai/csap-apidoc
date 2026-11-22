package ai.csap.apidoc.team.sync;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Actor;
import ai.csap.apidoc.team.common.UnifiedDiff;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.db.Settings;
import ai.csap.apidoc.team.env.Env;
import ai.csap.apidoc.team.env.Environments;
import ai.csap.apidoc.team.webhook.WebhookEvent;
import ai.csap.apidoc.team.webhook.WebhookService;

/**
 * 服务侧直接修改(契约 6.2):每 10 秒读 {prefix}history 中 id > 游标的记录;
 * 不是团队版发布的(没有对应的 published_history_id)就记 {@code rule.changed_outside} 审计并发事件。
 */
@Component
public class HistorySync {

    private static final Logger log = LoggerFactory.getLogger(HistorySync.class);
    private static final int BATCH = 500;

    private final Db db;
    private final Settings settings;
    private final AuditService audit;
    private final WebhookService webhooks;
    private final TeamProperties props;
    private final Environments environments;

    public HistorySync(Db db, Settings settings, AuditService audit, WebhookService webhooks, TeamProperties props,
                       Environments environments) {
        this.environments = environments;
        this.db = db;
        this.settings = settings;
        this.audit = audit;
        this.webhooks = webhooks;
        this.props = props;
    }

    @Scheduled(initialDelayString = "3", fixedDelayString = "${csap.team.sync.history-seconds:10}", timeUnit = TimeUnit.SECONDS)
    public void tick() {
        try {
            syncOnce();
        } catch (RuntimeException e) {
            log.warn("同步规则历史失败: {}", e.getMessage());
        }
    }

    private record Row(long id, String service, String ruleKey, String before, String after, long version, String updatedBy,
                       long updatedAt) {
    }

    /** 对每个 UP 的环境分别同步;@return 记了多少条 rule.changed_outside */
    public synchronized int syncOnce() {
        int recorded = 0;
        for (Env env : environments.all()) {
            if (!env.isUp()) {
                continue;
            }
            try {
                recorded += syncEnv(env);
            } catch (RuntimeException e) {
                log.warn("同步环境 {} 的规则历史失败: {}", env.key(), e.getMessage());
            }
        }
        return recorded;
    }

    private int syncEnv(Env env) {
        String cursorName = Environments.HISTORY_CURSOR_PREFIX + env.key();
        Db odb = env.db();
        Long cursor = settings.cursor(cursorName);
        if (cursor == null) {
            environments.ensureCursor(env);
            cursor = settings.cursor(cursorName);
        }
        long last = cursor == null ? 0 : cursor;
        long settleBefore = System.currentTimeMillis() - props.getSync().getSettleMillis();
        int recorded = 0;
        while (true) {
            List<Row> rows = odb.page("SELECT id, service, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM "
                    + env.historyTable() + " WHERE id > ? ORDER BY id", (rs, i) -> new Row(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7), rs.getLong(8)),
                    0, BATCH, last);
            if (rows.isEmpty()) {
                break;
            }
            boolean stop = false;
            for (Row r : rows) {
                if (r.updatedAt() > settleBefore) {
                    stop = true;
                    break;
                }
                // 旧版 Go 服务内容相同也会写 before==after 的 history:只推进游标,不记审计、不发事件
                if (!ai.csap.apidoc.strategy.store.RuleJson.sameJson(r.before(), r.after())
                        && db.count("SELECT COUNT(*) FROM csap_team_change_item i JOIN csap_team_change c ON c.id = i.change_id"
                                + " WHERE i.published_history_id = ? AND c.env_key = ?", r.id(), env.key()) == 0) {
                    record(env, r);
                    recorded++;
                }
                last = r.id();
                settings.setCursor(cursorName, last);
            }
            if (stop || rows.size() < BATCH) {
                break;
            }
        }
        return recorded;
    }

    private void record(Env env, Row r) {
        Long projectId = db.one("SELECT project_id FROM csap_team_project_service WHERE service = ?", (rs, i) -> {
            long v = rs.getLong(1);
            return rs.wasNull() ? null : v;
        }, r.service());
        audit.logAs(Actor.service(r.updatedBy()), "rule.changed_outside").project(projectId).env(env.key()).service(r.service()).rule(r.ruleKey())
                .before(r.before()).after(r.after()).diff(UnifiedDiff.ofRules(r.before(), r.after(), "before", "after"))
                .ossHistory(r.id()).at(r.updatedAt()).reason("服务侧直接修改,版本 " + r.version()).save();
        String pname = projectId == null ? null
                : db.one("SELECT name FROM csap_team_project WHERE id = ?", (rs, i) -> rs.getString(1), projectId);
        webhooks.fire(new WebhookEvent("rule.changed_outside", System.currentTimeMillis(), projectId, pname, null, null, null,
                List.of(WebhookEvent.item(r.service(), r.ruleKey())),
                (pname == null ? "" : "[" + pname + (environments.multiple() ? " / " + env.name() : "") + "] ") + "服务 " + r.service()
                        + " 的规则 " + r.ruleKey() + " 被 " + r.updatedBy() + " 在服务侧直接修改", env.key(), env.name()));
    }
}
