package ai.csap.apidoc.team.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.webhook.WebhookEvent;
import ai.csap.apidoc.team.webhook.WebhookService;

/**
 * 每天 00:05(csap.team.audit.checkpoint-cron)发一次当前链头,用于把链头存到团队版之外
 * (群消息、外部系统),之后有人整体重算整条链也能发现。只发给订阅了 audit.checkpoint 的 Webhook。
 */
@Component
public class AuditCheckpoint {

    private static final Logger log = LoggerFactory.getLogger(AuditCheckpoint.class);

    private final AuditChain chain;
    private final Db db;
    private final WebhookService webhooks;

    public AuditCheckpoint(AuditChain chain, Db db, WebhookService webhooks) {
        this.chain = chain;
        this.db = db;
        this.webhooks = webhooks;
    }

    @Scheduled(cron = "${csap.team.audit.checkpoint-cron:0 5 0 * * *}")
    public void scheduled() {
        try {
            fire();
        } catch (RuntimeException e) {
            log.warn("发送审计检查点失败: {}", e.getMessage());
        }
    }

    public void fire() {
        AuditChain.Point head = chain.head();
        long count = db.count("SELECT COUNT(*) FROM csap_team_audit");
        Map<String, Object> cp = new LinkedHashMap<>();
        cp.put("headId", head == null ? 0 : head.id());
        cp.put("headHash", head == null ? AuditChain.ZERO : head.hash());
        cp.put("count", count);
        String text = "CSAP Apidoc 团队版审计链检查点:headId=" + cp.get("headId") + " headHash=" + cp.get("headHash") + " 共 " + count + " 条";
        webhooks.fire(new WebhookEvent("audit.checkpoint", System.currentTimeMillis(), null, null, null, null, null, List.of(), text,
                null, null, null, cp));
    }
}
