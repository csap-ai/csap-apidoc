package ai.csap.apidoc.team.change;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.Json;

@RestController
@RequestMapping("/api/v1/changes")
public class ChangeController {

    private final ChangeService changes;
    private final Access access;

    public ChangeController(ChangeService changes, Access access) {
        this.changes = changes;
        this.access = access;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Long projectId, @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Boolean mine, @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return changes.list(access.user(), projectId, status, Boolean.TRUE.equals(mine), Math.max(1, page),
                Math.max(1, Math.min(size, 200)));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return changes.create(Who.console(access.user()), longOf(body.get("projectId"), "projectId"), str(body, "title"),
                str(body, "reason"), parseItems(body.get("items"), true), "EDIT");
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return changes.get(Who.console(access.user()), id);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return changes.update(Who.console(access.user()), id, str(body, "title"), str(body, "reason"),
                body.containsKey("items") ? parseItems(body.get("items"), true) : null);
    }

    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable long id) {
        return changes.submit(Who.console(access.user()), id);
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return changes.approve(Who.console(access.user()), id, body == null ? null : str(body, "note"));
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return changes.reject(Who.console(access.user()), id, body == null ? null : str(body, "note"));
    }

    @PostMapping("/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable long id) {
        return changes.withdraw(Who.console(access.user()), id);
    }

    @PostMapping("/{id}/rebase")
    public Map<String, Object> rebase(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        List<ChangeService.ItemInput> edits = body == null || !body.containsKey("items") ? List.of() : parseItems(body.get("items"), false);
        return changes.rebase(Who.console(access.user()), id, edits);
    }

    // ---- 解析 ----

    static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }

    static long longOf(Object v, String name) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        throw ApiException.validation(name + " 必填");
    }

    public static List<ChangeService.ItemInput> parseItemsPublic(Object raw) {
        return parseItems(raw, true);
    }

    /**
     * 解析 items。afterRules 可以是 JSON 字符串或 JSON 对象,null 表示删除。
     *
     * @param requireAfter true 时 afterRules 必须出现(防止漏传导致误删);rebase 时没出现表示保持原内容,会被跳过
     */
    @SuppressWarnings("unchecked")
    static List<ChangeService.ItemInput> parseItems(Object raw, boolean requireAfter) {
        if (!(raw instanceof List<?> list)) {
            throw ApiException.validation("items 必须是数组");
        }
        List<ChangeService.ItemInput> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) {
                throw ApiException.validation("items 的每一项必须是对象");
            }
            Map<String, Object> m = (Map<String, Object>) map;
            if (!m.containsKey("afterRules")) {
                if (requireAfter) {
                    throw ApiException.validation("afterRules 必填(null 表示删除该规则)");
                }
                continue;
            }
            Object after = m.get("afterRules");
            String afterText;
            if (after == null) {
                afterText = null;
            } else if (after instanceof String s) {
                afterText = s;
            } else {
                afterText = Json.write(after);
            }
            Long base = m.get("baseVersion") instanceof Number n ? n.longValue() : null;
            out.add(new ChangeService.ItemInput(str(m, "service"), str(m, "ruleKey"), base, afterText));
        }
        return out;
    }
}
