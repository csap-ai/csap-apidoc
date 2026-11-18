package ai.csap.apidoc.mock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.service.schema.DocPaths;

/**
 * 请求 → 文档接口的匹配(设计文档 2.2):{name} 匹配一个非空段;字面段多的优先,占位符少的优先,key 小的优先。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class MockMatcher {

    private final List<Entry> entries = new ArrayList<>();

    /** 匹配结果 */
    public static final class Match {
        private final CsapDocMethod method;
        private final Set<String> allowedMethods;
        private final boolean pathFound;

        Match(CsapDocMethod method, Set<String> allowedMethods, boolean pathFound) {
            this.method = method;
            this.allowedMethods = allowedMethods;
            this.pathFound = pathFound;
        }

        /** 命中的接口;没有命中为 null */
        public CsapDocMethod getMethod() {
            return method;
        }

        /** 路径存在但方法不对时,该路径支持的方法(用于 Allow 头) */
        public Set<String> getAllowedMethods() {
            return allowedMethods;
        }

        public boolean isPathFound() {
            return pathFound;
        }
    }

    private static final class Entry {
        final CsapDocMethod method;
        final String[] segments;
        final Set<String> verbs = new HashSet<>();
        final int literals;
        final int placeholders;

        Entry(CsapDocMethod method, String path) {
            this.method = method;
            this.segments = split(path);
            int l = 0;
            for (String s : segments) {
                if (!isPlaceholder(s)) {
                    l++;
                }
            }
            this.literals = l;
            this.placeholders = segments.length - l;
            if (method.getMethods() != null) {
                method.getMethods().forEach(m -> {
                    if (m != null) {
                        verbs.add(m.name());
                    }
                });
            }
        }

        boolean matchesPath(String[] req) {
            if (req.length != segments.length) {
                return false;
            }
            for (int i = 0; i < req.length; i++) {
                if (!isPlaceholder(segments[i]) && !segments[i].equals(req[i])) {
                    return false;
                }
            }
            return true;
        }

        boolean matchesVerb(String verb) {
            return verbs.isEmpty() || verbs.contains(verb);
        }
    }

    public MockMatcher(CsapDocResponse doc) {
        Set<String> seen = new HashSet<>();
        if (doc != null && doc.getApiList() != null) {
            for (CsapDocModelController c : new ArrayList<>(doc.getApiList())) {
                if (c == null || Boolean.TRUE.equals(c.getHidden()) || c.getMethodList() == null) {
                    continue;
                }
                for (CsapDocMethod m : c.getMethodList()) {
                    if (m == null || m.getKey() == null || Boolean.TRUE.equals(m.getHidden())
                            || !seen.add(m.getKey())) {
                        continue;
                    }
                    entries.add(new Entry(m, DocPaths.fullPath(m, c)));
                }
            }
        }
        entries.sort(Comparator.<Entry>comparingInt(e -> -e.literals).thenComparingInt(e -> e.placeholders)
                .thenComparing(e -> e.method.getKey()));
    }

    /**
     * 匹配
     *
     * @param verb HTTP 方法(大写)
     * @param path 去掉 /csap/mock 前缀后的路径
     * @return 结果
     */
    public Match match(String verb, String path) {
        String[] req = split(path);
        Set<String> allowed = new TreeSet<>();
        boolean found = false;
        for (Entry e : entries) {
            if (!e.matchesPath(req)) {
                continue;
            }
            found = true;
            if (e.matchesVerb(verb)) {
                return new Match(e.method, allowed, true);
            }
            allowed.addAll(e.verbs);
        }
        return new Match(null, allowed, found);
    }

    static String[] split(String path) {
        List<String> out = new ArrayList<>();
        if (path != null) {
            for (String s : path.split("/")) {
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out.toArray(new String[0]);
    }

    private static boolean isPlaceholder(String s) {
        return s.length() > 2 && s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}';
    }
}
