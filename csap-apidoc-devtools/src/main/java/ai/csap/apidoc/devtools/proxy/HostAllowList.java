package ai.csap.apidoc.devtools.proxy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure helper that decides whether a given URL's host is on the configured
 * allowlist. Stateless and immutable; safe for concurrent use.
 *
 * <p>Match rules:
 * <ul>
 *   <li>Empty / null configured list → every host denied (fail-closed).</li>
 *   <li>Literal match — {@code "api.example.com"} matches {@code api.example.com}
 *       only.</li>
 *   <li>Prefix wildcard — {@code "*.example.com"} matches {@code api.example.com}
 *       and {@code nested.api.example.com}, but NOT the bare apex
 *       {@code example.com}.</li>
 *   <li>Matching is case-insensitive.</li>
 *   <li>The URL's port is ignored for matching purposes.</li>
 * </ul>
 *
 * <p>Regex patterns are intentionally NOT supported — keeping the surface
 * simple makes the rules auditable.
 *
 * @author yangchengfu
 * @since 1.0
 */
public final class HostAllowList {

    private final List<String> literals;
    private final List<String> wildcardSuffixes;

    /**
     * Build an allowlist matcher.
     *
     * @param entries configured entries; may be empty or {@code null}
     */
    public HostAllowList(List<String> entries) {
        List<String> normLiterals = new ArrayList<>();
        List<String> normWildcards = new ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null) {
                    continue;
                }
                String e = raw.trim().toLowerCase(Locale.ROOT);
                if (e.isEmpty()) {
                    continue;
                }
                if (e.startsWith("*.")) {
                    // store the suffix WITHOUT the leading "*", so we match
                    // ".example.com" and require at least one label before it.
                    normWildcards.add(e.substring(1));
                } else {
                    normLiterals.add(e);
                }
            }
        }
        this.literals = Collections.unmodifiableList(normLiterals);
        this.wildcardSuffixes = Collections.unmodifiableList(normWildcards);
    }

    /**
     * @param url the absolute URL to evaluate; {@code null} or non-absolute
     *            URLs return {@code false}
     * @return {@code true} iff the URL's host is on the allowlist
     */
    public boolean isAllowed(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return isHostAllowed(uri.getHost());
    }

    /**
     * @param host the bare hostname (no port, no scheme)
     * @return {@code true} iff the host is on the allowlist
     */
    public boolean isHostAllowed(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String literal : literals) {
            if (literal.equals(h)) {
                return true;
            }
        }
        for (String suffix : wildcardSuffixes) {
            // suffix starts with '.', e.g. ".example.com"
            // require a non-empty label before it: rule out exact apex match.
            if (h.length() > suffix.length() && h.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return literals.isEmpty() && wildcardSuffixes.isEmpty();
    }
}
