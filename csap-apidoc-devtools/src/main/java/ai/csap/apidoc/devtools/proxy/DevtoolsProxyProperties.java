package ai.csap.apidoc.devtools.proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the optional devtools HTTP reverse-proxy.
 *
 * <p>All keys live under {@code csap.apidoc.devtools.proxy.*}. The whole feature
 * is gated by {@link #isEnabled()} — when {@code false} (the default), no proxy
 * controller bean is registered and the endpoint returns 404.
 *
 * <p><strong>Security model.</strong> This proxy is a deliberate Server-Side
 * Request Forgery (SSRF) primitive: by design it lets a HTTP client cause the
 * host application to issue an outbound HTTP request to an arbitrary URL. Two
 * mitigations are MANDATORY:
 *
 * <ol>
 *   <li>{@link #isEnabled()} defaults to {@code false}. Operators must opt in.</li>
 *   <li>{@link #getAllowedHosts()} MUST be populated when enabled. If left
 *       empty, every request is denied with HTTP 502 / {@code host_not_allowed}
 *       (fail-closed, intentionally safer than wide-open). A WARN is logged at
 *       startup so the misconfiguration is visible.</li>
 * </ol>
 *
 * <p>Operators are strongly encouraged to additionally lock the proxy endpoint
 * behind Spring Security so only authenticated devtools users can invoke it,
 * and to never expose this endpoint on a public network boundary.
 *
 * @author yangchengfu
 * @since 1.0
 */
@Validated
@ConfigurationProperties(prefix = "csap.apidoc.devtools.proxy")
public class DevtoolsProxyProperties {

    /** Default request/response body cap: 5 MiB. */
    public static final long DEFAULT_MAX_BODY_BYTES = 5L * 1024 * 1024;

    /** Default upstream timeout: 30 seconds. */
    public static final int DEFAULT_TIMEOUT_MS = 30_000;

    /**
     * Whether the proxy controller is registered. {@code false} by default.
     * When {@code false}, {@code POST /csap/apidoc/devtools/proxy} returns 404.
     */
    private boolean enabled = false;

    /**
     * Hostnames the proxy is allowed to target. Required when {@link #isEnabled()}
     * is {@code true}; an empty list with proxy enabled causes every request to
     * be denied. Supports literal hostnames ({@code api.example.com}) and prefix
     * wildcards ({@code *.example.com} matches {@code api.example.com} and
     * {@code nested.api.example.com} but NOT the bare apex {@code example.com}).
     * Matching is case-insensitive and ignores the URL port.
     */
    private List<String> allowedHosts = new ArrayList<>();

    /**
     * Maximum size, in bytes, of either the inbound request body forwarded to
     * upstream OR the upstream response body returned to the caller. Inbound
     * over-cap requests are rejected with HTTP 415 / {@code body_too_large}.
     * Over-cap upstream responses are truncated and returned with header
     * {@code X-Csap-Apidoc-Proxy-Truncated: 1}. Defaults to 5 MiB.
     */
    private long maxBodyBytes = DEFAULT_MAX_BODY_BYTES;

    /**
     * Connect + read timeout for the upstream call, in milliseconds. Defaults
     * to 30_000 (30 s).
     */
    private int timeoutMs = DEFAULT_TIMEOUT_MS;

    /**
     * Header names that must NEVER be forwarded to upstream, regardless of
     * what the caller put in the request. Case-insensitive. Defaults
     * cover the obvious browser-session leaks ({@code Cookie}) and the
     * forwarded-IP family. Operators may extend this list with internal
     * service-mesh headers, tenant headers, etc.
     */
    private List<String> stripHeaders = new ArrayList<>(Arrays.asList(
            "Cookie", "X-Forwarded-For", "X-Real-IP"));

    /**
     * If {@code true}, log the request and response bodies (truncated) at
     * INFO level. {@code false} by default — request bodies typically contain
     * bearer tokens and other secrets that must not leak into log aggregation.
     * Enable temporarily for debugging only.
     */
    private boolean logBodies = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = (allowedHosts != null) ? allowedHosts : new ArrayList<>();
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public List<String> getStripHeaders() {
        return stripHeaders;
    }

    public void setStripHeaders(List<String> stripHeaders) {
        this.stripHeaders = (stripHeaders != null) ? stripHeaders : new ArrayList<>();
    }

    public boolean isLogBodies() {
        return logBodies;
    }

    public void setLogBodies(boolean logBodies) {
        this.logBodies = logBodies;
    }
}
