package ai.csap.apidoc.team.webhook;

import java.util.List;
import java.util.Map;

/**
 * 一次要推送的事件。envKey / envName 为 null 表示与环境无关;comment 只在 change.commented 里有;
 * checkpoint 只在 audit.checkpoint 里有。
 */
public record WebhookEvent(String event, long at, Long projectId, String projectName, Long changeId, String changeTitle,
                           String author, List<Map<String, String>> items, String text, String envKey, String envName,
                           Map<String, Object> comment, Map<String, Object> checkpoint) {

    public static final List<String> ALL = List.of("change.submitted", "change.approved", "change.rejected",
            "change.published", "change.conflict", "change.commented", "rule.changed_outside", "service.offline", "audit.checkpoint");

    /** 1.0 兼容构造:与环境无关。 */
    public WebhookEvent(String event, long at, Long projectId, String projectName, Long changeId, String changeTitle, String author,
                        List<Map<String, String>> items, String text) {
        this(event, at, projectId, projectName, changeId, changeTitle, author, items, text, null, null, null, null);
    }

    public WebhookEvent(String event, long at, Long projectId, String projectName, Long changeId, String changeTitle, String author,
                        List<Map<String, String>> items, String text, String envKey, String envName) {
        this(event, at, projectId, projectName, changeId, changeTitle, author, items, text, envKey, envName, null, null);
    }

    public static Map<String, String> item(String service, String ruleKey) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("service", service);
        m.put("ruleKey", ruleKey);
        return m;
    }
}
