package ai.csap.apidoc.devtools.proxy;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Wraps a {@link RestTemplate} configured for the devtools proxy:
 * <ul>
 *   <li>Connect + read timeouts driven by {@link DevtoolsProxyProperties#getTimeoutMs()}.</li>
 *   <li>4xx / 5xx responses are NOT translated into exceptions — the proxy
 *       must forward upstream status verbatim.</li>
 *   <li>Body is requested as {@code byte[]} so we never touch character
 *       encoding for the upstream payload.</li>
 * </ul>
 *
 * <p>One {@link RestTemplate} instance is shared across requests; Spring's
 * {@code RestTemplate} is documented as thread-safe once configured.
 *
 * @author yangchengfu
 * @since 1.0
 */
public class UpstreamClient {

    private final RestTemplate restTemplate;

    public UpstreamClient(DevtoolsProxyProperties props) {
        this.restTemplate = build(props.getTimeoutMs());
    }

    /** Test seam — inject an externally configured RestTemplate. */
    public UpstreamClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Issue an upstream call and return its response without throwing on
     * non-2xx status.
     */
    public ResponseEntity<byte[]> exchange(URI uri, HttpMethod method, HttpHeaders headers, byte[] body) {
        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(uri, method, entity, byte[].class);
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    private static RestTemplate build(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestTemplate rt = new RestTemplate(factory);
        rt.setErrorHandler(new PassThroughErrorHandler());
        return rt;
    }

    /**
     * Treats every HTTP response — including 4xx / 5xx — as non-erroneous so
     * that the controller can forward the upstream status code verbatim.
     */
    private static final class PassThroughErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse response) {
            return false;
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
            // never invoked because hasError() returns false; declared for the
            // interface contract only.
        }
    }
}
