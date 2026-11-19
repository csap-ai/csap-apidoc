package ai.csap.apidoc.team.rule;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.change.ChangeService;
import ai.csap.apidoc.team.change.Who;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.UnifiedDiff;

/** 规则读取、历史、差异、回滚。 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleReader reader;
    private final ChangeService changes;
    private final Access access;

    public RuleController(RuleReader reader, ChangeService changes, Access access) {
        this.reader = reader;
        this.changes = changes;
        this.access = access;
    }

    @GetMapping("/{service}")
    public Map<String, Object> items(@PathVariable String service) {
        access.requireViewService(service);
        return reader.items(service);
    }

    @GetMapping("/{service}/item")
    public Map<String, Object> item(@PathVariable String service, @RequestParam String key) {
        access.requireViewService(service);
        RuleReader.Current c = reader.current(service, key);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleKey", key);
        m.put("rules", c == null ? null : c.rules());
        m.put("version", c == null ? 0 : c.version());
        return m;
    }

    @GetMapping("/{service}/history")
    public Object history(@PathVariable String service, @RequestParam(required = false) String key,
                          @RequestParam(defaultValue = "50") int limit) {
        access.requireViewService(service);
        return reader.history(service, key, Math.max(1, Math.min(limit, 500)));
    }

    /** 比较 from 与 to 两条历史记录之后的规则内容;to 省略表示与当前规则比较。 */
    @GetMapping("/{service}/diff")
    public Map<String, Object> diff(@PathVariable String service, @RequestParam String key, @RequestParam long from,
                                    @RequestParam(required = false) Long to) {
        access.requireViewService(service);
        RuleReader.HistoryRow f = reader.historyById(service, from);
        if (f == null || !f.ruleKey().equals(key)) {
            throw ApiException.notFound("历史记录不存在: " + from);
        }
        String toRules;
        String toLabel;
        if (to == null) {
            RuleReader.Current c = reader.current(service, key);
            toRules = c == null ? null : c.rules();
            toLabel = "current";
        } else {
            RuleReader.HistoryRow t = reader.historyById(service, to);
            if (t == null || !t.ruleKey().equals(key)) {
                throw ApiException.notFound("历史记录不存在: " + to);
            }
            toRules = t.after();
            toLabel = "history#" + to;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("diff", UnifiedDiff.ofRules(f.after(), toRules, "history#" + from, toLabel));
        return m;
    }

    @PostMapping("/{service}/rollback")
    public Map<String, Object> rollback(@PathVariable String service, @RequestBody Map<String, Object> body) {
        access.requireViewService(service);
        String key = body.get("key") instanceof String s ? s : null;
        if (key == null || key.isBlank() || !(body.get("historyId") instanceof Number id)) {
            throw ApiException.validation("key 和 historyId 必填");
        }
        String reason = body.get("reason") instanceof String r ? r : null;
        return changes.rollback(Who.console(access.user()), service, key, id.longValue(), reason);
    }
}
