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
import ai.csap.apidoc.team.db.SchemaInitializer;
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

    public HistorySync(Db db, Settings settings, AuditService audit, WebhookService webhooks, TeamProperties props) {
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

    /** @return 记了多少条 rule.changed_outside */
    public synchronized int syncOnce() {
        Long cursor = settings.cursor(SchemaInitializer.HISTORY_CURSOR);
        long last = cursor == null ? 0 : cursor;
        long settleBefore = System.currentTimeMillis() - props.getSync().getSettleMillis();
        int recorded = 0;
        while (true) {
            List<Row> rows = db.page("SELECT id, service, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM "
                    + db.historyTable() + " WHERE id > ? ORDER BY id", (rs, i) -> new Row(rs.getLong(1), rs.getString(2),
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
                if (db.count("SELECT COUNT(*) FROM csap_team_change_item WHERE published_history_id = ?", r.id()) == 0) {
                    record(r);
                    recorded++;
                }
                last = r.id();
                settings.setCursor(SchemaInitializer.HISTORY_CURSOR, last);
            }
            if (stop || rows.size() < BATCH) {
                break;
            }
        }
        return recorded;
    }

    private void record(Row r) {
        Long projectId = db.one("SELECT project_id FROM csap_team_project_service WHERE service = ?", (rs, i) -> {
            long v = rs.getLong(1);
            return rs.wasNull() ? null : v;
        }, r.service());
        audit.logAs(Actor.service(r.updatedBy()), "rule.changed_outside").project(projectId).service(r.service()).rule(r.ruleKey())
                .before(r.before()).after(r.after()).diff(UnifiedDiff.ofRules(r.before(), r.after(), "before", "after"))
                .ossHistory(r.id()).at(r.updatedAt()).reason("服务侧直接修改,版本 " + r.version()).save();
        String pname = projectId == null ? null
                : db.one("SELECT name FROM csap_team_project WHERE id = ?", (rs, i) -> rs.getString(1), projectId);
        webhooks.fire(new WebhookEvent("rule.changed_outside", System.currentTimeMillis(), projectId, pname, null, null, null,
                List.of(WebhookEvent.item(r.service(), r.ruleKey())),
                (pname == null ? "" : "[" + pname + "] ") + "服务 " + r.service() + " 的规则 " + r.ruleKey() + " 被 " + r.updatedBy()
                        + " 在服务侧直接修改"));
    }
}
