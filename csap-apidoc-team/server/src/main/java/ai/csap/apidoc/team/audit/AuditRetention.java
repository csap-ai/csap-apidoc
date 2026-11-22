package ai.csap.apidoc.team.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.auth.Actor;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;

/**
 * 审计保留期清理:全代码里唯一允许删除审计的地方,删除本身记一条 {@code audit.purge}。
 *
 * <p>为了不破坏哈希链,只删"最早的一段":第一条仍在保留期内的记录(按 id)之前的全部记录;
 * 被删的最后一条的 {id, hash} 存入 setting {@code audit.chain.anchor},校验从 anchor 接上。
 */
@Component
public class AuditRetention {

    private static final Logger log = LoggerFactory.getLogger(AuditRetention.class);

    private final Db db;
    private final AuditService audit;
    private final AuditChain chain;
    private final TeamProperties props;

    public AuditRetention(Db db, AuditService audit, AuditChain chain, TeamProperties props) {
        this.db = db;
        this.audit = audit;
        this.chain = chain;
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
        return db.inTx(() -> {
            // 先锁链头,与审计写入串行
            chain.lockHead();
            Long firstKept = db.one("SELECT MIN(id) FROM csap_team_audit WHERE at >= ?", (rs, i) -> {
                long v = rs.getLong(1);
                return rs.wasNull() ? null : v;
            }, cutoff);
            long bound = firstKept == null ? Long.MAX_VALUE : firstKept;
            AuditChain.Point anchor = db.one("SELECT id, hash FROM csap_team_audit WHERE id < ? ORDER BY id DESC",
                    (rs, i) -> new AuditChain.Point(rs.getLong(1), rs.getString(2)), bound);
            if (anchor == null || anchor.hash() == null) {
                return 0;
            }
            int n = db.update("DELETE FROM csap_team_audit WHERE id < ?", bound);
            if (n > 0) {
                chain.setAnchor(anchor);
                audit.logAs(Actor.system(), "audit.purge").at(now)
                        .reason("保留期 " + days + " 天,清理 " + n + " 条;anchor id=" + anchor.id() + " hash=" + anchor.hash()).save();
                log.info("审计保留期清理:删除 {} 条", n);
            }
            return n;
        });
    }
}
