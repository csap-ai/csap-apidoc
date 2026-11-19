package ai.csap.apidoc.team.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.auth.Actor;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;

/** 审计保留期清理:全代码里唯一允许删除审计的地方,删除本身记一条 {@code audit.purge}。 */
@Component
public class AuditRetention {

    private static final Logger log = LoggerFactory.getLogger(AuditRetention.class);

    private final Db db;
    private final AuditService audit;
    private final TeamProperties props;

    public AuditRetention(Db db, AuditService audit, TeamProperties props) {
        this.db = db;
        this.audit = audit;
        this.props = props;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void scheduled() {
        try {
            purge(System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.warn("审计保留期清理失败: {}", e.getMessage());
        }
    }

    /** @return 删除的条数 */
    public int purge(long now) {
        int days = props.getAudit().getRetentionDays();
        if (days <= 0) {
            return 0;
        }
        long cutoff = now - days * 86_400_000L;
        int n = db.update("DELETE FROM csap_team_audit WHERE at < ?", cutoff);
        if (n > 0) {
            audit.logAs(Actor.system(), "audit.purge").reason("保留期 " + days + " 天,清理 " + n + " 条").at(now).save();
            log.info("审计保留期清理:删除 {} 条", n);
        }
        return n;
    }
}
