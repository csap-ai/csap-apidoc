package ai.csap.apidoc.devtools.proxy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP reverse-proxy that lets the standalone {@code csap-apidoc-ui} make
 * try-it-out requests against the host application's APIs without hitting
 * a browser CORS wall.
 *
 * <p>Disabled by default — bean is only registered when
 * {@code csap.apidoc.devtools.proxy.enabled=true} (see
 * {@link DevtoolsProxyAutoConfiguration}). Strict allowlist enforced.
 *
 * <p>See {@link DevtoolsProxyProperties} for the full security model and
 * {@code csap-apidoc-devtools/PROXY.md} for end-user documentation.
 *
 * @author yangchengfu
 * @since 1.0
 */
@RestController
@RequestMapping("/csap/apidoc/devtools")
public class DevtoolsProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(DevtoolsProxyController.class);

    /** Header signalling the response was produced by this proxy. */
    public static final String HEADER_PROXY_MARKER = "X-Csap-Apidoc-Proxy";

    /** Header signalling the upstream response body was truncated. */
    public static final String HEADER_PROXY_TRUNCATED = "X-Csap-Apidoc-Proxy-Truncated";

    /**
     * Hop-by-hop or content-framing headers that we never copy from upstream
     * back to the caller; case-insensitive.
     */
    private static final Set<String> RESPONSE_HEADER_BLOCKLIST;
    static {
        Set<String> s = new HashSet<>();
        for (String name : Arrays.asList(
                "set-cookie",
                "transfer-encoding",
                "content-length",
                "connection",
                "keep-alive",
                "upgrade")) {
            s.add(name);
        }
        RESPONSE_HEADER_BLOCKLIST = Collections.unmodifiableSet(s);
    }

    private final DevtoolsProxyProperties props;
    private final HostAllowList allowList;
    private final UpstreamClient upstream;
    private final ObjectMapper objectMapper;

    public DevtoolsProxyController(DevtoolsProxyProperties props,
                                   HostAllowList allowList,
                                   UpstreamClient upstream,
                                   ObjectMapper objectMapper) {
        this.props = props;
        this.allowList = allowList;
        this.upstream = upstream;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/proxy",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> proxy(@RequestBody ProxyRequest body) {
        long started = System.nanoTime();

        if (body == null || body.url == null || body.url.isEmpty()) {
            return errorEnvelope(HttpStatus.BAD_GATEWAY, "invalid_request",
                    "Missing required field: url", null);
        }

        String methodName = (body.method == null || body.method.isEmpty())
                ? "GET" : body.method.toUpperCase(Locale.ROOT);
        HttpMethod method = HttpMethod.resolve(methodName);
        if (method == null) {
            return errorEnvelope(HttpStatus.BAD_GATEWAY, "invalid_method",
                    "Unsupported HTTP method: " + body.method, body.url);
        }

        URI uri;
        try {
            uri = URI.create(body.url);
        } catch (IllegalArgumentException ex) {
            return errorEnvelope(HttpStatus.BAD_GATEWAY, "invalid_url",
                    "URL could not be parsed: " + ex.getMessage(), body.url);
        }
        if (uri.getHost() == null || uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("http")
                        || uri.getScheme().equalsIgnoreCase("https"))) {
            return errorEnvelope(HttpStatus.BAD_GATEWAY, "invalid_url",
                    "URL must be absolute http(s) with a hostname", body.url);
        }

        if (!allowList.isAllowed(body.url)) {
            LOG.info("[devtools-proxy] DENY host={} method={} url={} (not on allowlist)",
                    uri.getHost(), method, body.url);
            return errorEnvelope(HttpStatus.BAD_GATEWAY, "host_not_allowed",
                    "Host is not on csap.apidoc.devtools.proxy.allowed-hosts", body.url);
        }

        // request body size cap (treat string length as UTF-8 byte length).
        byte[] requestBytes = (body.body == null) ? null : body.body.getBytes(StandardCharsets.UTF_8);
        if (requestBytes != null && requestBytes.length > props.getMaxBodyBytes()) {
            LOG.info("[devtools-proxy] REJECT body_too_large size={} cap={} url={}",
                    requestBytes.length, props.getMaxBodyBytes(), body.url);
            return errorEnvelope(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "body_too_large",
                    "Request body of " + requestBytes.length
                            + " bytes exceeds csap.apidoc.devtools.proxy.max-body-bytes ("
                            + props.getMaxBodyBytes() + ")",
                    body.url);
        }
        // GET / HEAD must not carry a body even if one was supplied.
        if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
            requestBytes = null;
        }

        HttpHeaders forwardHeaders = buildForwardHeaders(body.headers);

        if (LOG.isDebugEnabled()) {
            LOG.debug("[devtools-proxy] forward headers (after strip)={}", forwardHeaders);
        }
        if (props.isLogBodies() && requestBytes != null) {
            LOG.info("[devtools-proxy] request body ({} bytes): {}",
                    requestBytes.length, truncateForLog(body.body));
        }

        ResponseEntity<byte[]> upstreamResponse;
        try {
            upstreamResponse = upstream.exchange(uri, method, forwardHeaders, requestBytes);
        } catch (Exception ex) {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            LOG.warn("[devtools-proxy] FAIL host={} method={} url={} after {} ms: {}",
                    uri.getHost(), method, body.url, ms, ex.toString());
            return errorEnvelope(HttpStatus.BAD_GATEWAY, "upstream_unreachable",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage(), body.url);
        }

        long ms = (System.nanoTime() - started) / 1_000_000L;
        int upstreamStatus = upstreamResponse.getStatusCodeValue();
        byte[] upstreamBody = upstreamResponse.getBody();
        boolean truncated = false;
        if (upstreamBody != null && upstreamBody.length > props.getMaxBodyBytes()) {
            int cap = (int) Math.min((long) Integer.MAX_VALUE, props.getMaxBodyBytes());
            byte[] capped = new byte[cap];
            System.arraycopy(upstreamBody, 0, capped, 0, cap);
            upstreamBody = capped;
            truncated = true;
            LOG.warn("[devtools-proxy] upstream body truncated to {} bytes (cap) url={}",
                    cap, body.url);
        }

        HttpHeaders responseHeaders = filterResponseHeaders(upstreamResponse.getHeaders());
        responseHeaders.set(HEADER_PROXY_MARKER, "1");
        if (truncated) {
            responseHeaders.set(HEADER_PROXY_TRUNCATED, "1");
        }

        LOG.info("[devtools-proxy] OK host={} method={} url={} status={} bytes={} {}ms",
                uri.getHost(), method, body.url, upstreamStatus,
                (upstreamBody == null ? 0 : upstreamBody.length), ms);
        if (props.isLogBodies() && upstreamBody != null) {
            LOG.info("[devtools-proxy] response body ({} bytes): {}",
                    upstreamBody.length,
                    truncateForLog(new String(upstreamBody, StandardCharsets.UTF_8)));
        }

        return ResponseEntity
                .status(upstreamStatus)
                .headers(responseHeaders)
                .body(upstreamBody);
    }

    private HttpHeaders buildForwardHeaders(Map<String, String> requested) {
        HttpHeaders out = new HttpHeaders();
        if (requested == null || requested.isEmpty()) {
            return out;
        }
        Set<String> stripped = new HashSet<>();
        for (String name : props.getStripHeaders()) {
            if (name != null) {
                stripped.add(name.toLowerCase(Locale.ROOT));
            }
        }
        for (Map.Entry<String, String> e : requested.entrySet()) {
            String name = e.getKey();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (stripped.contains(lower)) {
                continue;
            }
            // Host is special — RestTemplate / HttpURLConnection set it
            // from the URI. Forwarding it would either be ignored or break
            // the request.
            if ("host".equals(lower)) {
                continue;
            }
            out.add(name, e.getValue());
        }
        return out;
    }

    private static HttpHeaders filterResponseHeaders(HttpHeaders src) {
        HttpHeaders out = new HttpHeaders();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            String name = e.getKey();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (RESPONSE_HEADER_BLOCKLIST.contains(lower)) {
                continue;
            }
            if (lower.startsWith("proxy-")) {
                continue;
            }
            for (String v : e.getValue()) {
                out.add(name, v);
            }
        }
        return out;
    }

    private ResponseEntity<byte[]> errorEnvelope(HttpStatus status, String code,
                                                 String message, String url) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("error", code);
        envelope.put("message", message);
        if (url != null) {
            envelope.put("url", url);
        }
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException ex) {
            // Should never happen for a tiny LinkedHashMap of strings, but be
            // defensive.
            payload = ("{\"error\":\"" + code + "\",\"message\":\"serialization_failure\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_PROXY_MARKER, "1");
        return ResponseEntity.status(status).headers(headers).body(payload);
    }

    private static String truncateForLog(String s) {
        if (s == null) {
            return "<null>";
        }
        int max = 1024;
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…[" + (s.length() - max) + " more chars]";
    }

    /** Inbound request envelope. */
    public static class ProxyRequest {
        public String method;
        public String url;
        public Map<String, String> headers;
        public String body;
    }
}
