package ai.csap.apidoc.team.env;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import ai.csap.apidoc.strategy.store.RuleJson;
import ai.csap.apidoc.team.rule.RuleReader;

/** 两个环境之间规则的差异(内容用 {@link RuleJson#sameJson} 判断相同)。 */
@Component
public class EnvDiff {

    public static final String DIFFERENT = "DIFFERENT";
    public static final String ONLY_FROM = "ONLY_FROM";
    public static final String ONLY_TO = "ONLY_TO";

    public record Item(String ruleKey, String status, Long fromVersion, Long toVersion, String fromRules, String toRules) {
    }

    public record ServiceDiff(String service, List<Item> items) {
    }

    private final RuleReader reader;

    public EnvDiff(RuleReader reader) {
        this.reader = reader;
    }

    /** 只返回有差异的服务和规则;两个环境都必须可用。 */
    public List<ServiceDiff> diff(Env from, Env to, Collection<String> services) {
        List<ServiceDiff> out = new ArrayList<>();
        for (String service : new TreeSet<>(services)) {
            Map<String, RuleReader.Current> f = reader.allRules(from, service);
            Map<String, RuleReader.Current> t = reader.allRules(to, service);
            List<Item> items = new ArrayList<>();
            for (String key : new TreeSet<>(f.keySet())) {
                RuleReader.Current a = f.get(key);
                RuleReader.Current b = t.get(key);
                if (b == null) {
                    items.add(new Item(key, ONLY_FROM, a.version(), null, a.rules(), null));
                } else if (!RuleJson.sameJson(a.rules(), b.rules())) {
                    items.add(new Item(key, DIFFERENT, a.version(), b.version(), a.rules(), b.rules()));
                }
            }
            for (String key : new TreeSet<>(t.keySet())) {
                if (!f.containsKey(key)) {
                    RuleReader.Current b = t.get(key);
                    items.add(new Item(key, ONLY_TO, null, b.version(), null, b.rules()));
                }
            }
            items.sort(java.util.Comparator.comparing(Item::ruleKey));
            if (!items.isEmpty()) {
                out.add(new ServiceDiff(service, items));
            }
        }
        return out;
    }
}
