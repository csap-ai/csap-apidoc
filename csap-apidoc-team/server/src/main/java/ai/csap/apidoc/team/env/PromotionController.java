package ai.csap.apidoc.team.env;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.change.ChangeService;
import ai.csap.apidoc.team.change.Who;
import ai.csap.apidoc.team.common.ApiException;

/** 一键推广:在两个环境之间生成 kind=PROMOTE 的修改单。 */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final ChangeService changes;
    private final Environments environments;
    private final Access access;

    public PromotionController(ChangeService changes, Environments environments, Access access) {
        this.changes = changes;
        this.environments = environments;
        this.access = access;
    }

    @PostMapping
    public Map<String, Object> promote(@RequestBody Map<String, Object> body) {
        if (!(body.get("projectId") instanceof Number projectId)) {
            throw ApiException.validation("projectId 必填");
        }
        String from = body.get("from") instanceof String s ? s : null;
        String to = body.get("to") instanceof String s ? s : null;
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            throw ApiException.validation("from 和 to 必填");
        }
        List<String[]> selected = null;
        if (body.get("items") != null) {
            if (!(body.get("items") instanceof List<?> list)) {
                throw ApiException.validation("items 必须是数组");
            }
            selected = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("service") instanceof String svc) || !(m.get("ruleKey") instanceof String key)) {
                    throw ApiException.validation("items 的每一项需要 service 和 ruleKey");
                }
                selected.add(new String[] {svc, key});
            }
        }
        return changes.promote(Who.console(access.user()), projectId.longValue(), environments.require(from), environments.require(to),
                selected, Boolean.TRUE.equals(body.get("includeDeletes")), body.get("title") instanceof String t ? t : null,
                body.get("reason") instanceof String r ? r : null, Boolean.TRUE.equals(body.get("submit")));
    }
}
