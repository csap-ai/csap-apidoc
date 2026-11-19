package ai.csap.apidoc.team.webhook;

import java.util.List;
import java.util.Map;

/** 一次要推送的事件。 */
public record WebhookEvent(String event, long at, Long projectId, String projectName, Long changeId, String changeTitle,
                           String author, List<Map<String, String>> items, String text) {

    public static final List<String> ALL = List.of("change.submitted", "change.approved", "change.rejected",
            "change.published", "change.conflict", "rule.changed_outside", "service.offline");

    public static Map<String, String> item(String service, String ruleKey) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("service", service);
        m.put("ruleKey", ruleKey);
        return m;
    }
}
