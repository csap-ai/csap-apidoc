package ai.csap.apidoc.devtools.proxy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the proxy controller's request/response semantics with a mocked
 * upstream, plus the conditional auto-config wiring.
 *
 * @author yangchengfu
 * @since 1.0
 */
class DevtoolsProxyControllerTest {

    private DevtoolsProxyProperties props;
    private RestTemplate restTemplate;
    private MockRestServiceServer upstream;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        props = new DevtoolsProxyProperties();
        props.setEnabled(true);
        props.setAllowedHosts(Arrays.asList("api.example.com", "*.staging.example.com"));
        props.setMaxBodyBytes(1024);
        props.setTimeoutMs(5_000);
        props.setStripHeaders(Arrays.asList("Cookie", "X-Forwarded-For", "X-Real-IP"));
        props.setLogBodies(false);

        restTemplate = new RestTemplate();
        UpstreamClient upstreamClient = new UpstreamClient(restTemplate);
        // The upstream's RestTemplate has the default error handler — that
        // would translate 500 into an exception. The PROXY's UpstreamClient
        // already swaps that out, but MockRestServiceServer uses whatever
        // RestTemplate it's bound to. Re-apply the pass-through error handler
        // here so MockMvc sees the controller's "forward upstream status" path.
        restTemplate.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
                // never invoked — see hasError().
            }
        });
        upstream = MockRestServiceServer.bindTo(restTemplate).build();

        objectMapper = new ObjectMapper();
        HostAllowList allowList = new HostAllowList(props.getAllowedHosts());
        DevtoolsProxyController controller =
                new DevtoolsProxyController(props, allowList, upstreamClient, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void happyPathForwardsResponseAndAddsProxyMarkerHeader() throws Exception {
        upstream.expect(requestTo("https://api.example.com/v1/orders/42?expand=items"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-Id", "demo"))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/v1/orders/42?expand=items\","
                + "\"headers\":{\"X-Tenant-Id\":\"demo\",\"Authorization\":\"Bearer abc\"}"
                + "}";

        MvcResult res = mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(DevtoolsProxyController.HEADER_PROXY_MARKER, "1"))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).isEqualTo("{\"id\":42}");
        upstream.verify();
    }

    @Test
    void hostNotOnAllowlistReturns502JsonEnvelope() throws Exception {
        // Note: no upstream expectation set — the controller must NOT call out.
        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://evil.example.org/exfil\""
                + "}";

        MvcResult res = mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(header().string(DevtoolsProxyController.HEADER_PROXY_MARKER, "1"))
                .andReturn();
        String envelope = res.getResponse().getContentAsString();
        assertThat(envelope).contains("\"error\":\"host_not_allowed\"");
        assertThat(envelope).contains("\"url\":\"https://evil.example.org/exfil\"");
        upstream.verify();
    }

    @Test
    void upstream500IsForwardedVerbatim() throws Exception {
        upstream.expect(requestTo("https://api.example.com/oops"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError().body("{\"err\":\"boom\"}").contentType(MediaType.APPLICATION_JSON));

        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/oops\""
                + "}";

        MvcResult res = mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(DevtoolsProxyController.HEADER_PROXY_MARKER, "1"))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).isEqualTo("{\"err\":\"boom\"}");
        upstream.verify();
    }

    @Test
    void requestBodyOverCapReturns415() throws Exception {
        // No upstream call must happen.
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2048; i++) {
            huge.append('x');
        }
        // Embed inside a JSON string (escape nothing — only ASCII).
        String body = "{"
                + "\"method\":\"POST\","
                + "\"url\":\"https://api.example.com/upload\","
                + "\"body\":\"" + huge + "\""
                + "}";

        MvcResult res = mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).contains("\"error\":\"body_too_large\"");
        upstream.verify();
    }

    @Test
    void setCookieFromUpstreamIsStripped() throws Exception {
        HttpHeaders upstreamHeaders = new HttpHeaders();
        upstreamHeaders.set("Set-Cookie", "session=secret; Path=/");
        upstreamHeaders.set("Connection", "keep-alive");
        upstreamHeaders.set("X-Custom", "kept");
        upstreamHeaders.setContentType(MediaType.APPLICATION_JSON);

        upstream.expect(requestTo("https://api.example.com/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON)
                        .headers(upstreamHeaders));

        String body = "{"
                + "\"method\":\"POST\","
                + "\"url\":\"https://api.example.com/login\","
                + "\"headers\":{\"Content-Type\":\"application/json\"},"
                + "\"body\":\"{}\""
                + "}";

        MvcResult res = mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().doesNotExist("Connection"))
                .andExpect(header().string("X-Custom", "kept"))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).isEqualTo("{\"ok\":true}");
        upstream.verify();
    }

    @Test
    void upstreamBodyOverCapIsTruncatedAndFlagged() throws Exception {
        props.setMaxBodyBytes(8); // 8 bytes only
        // Rebuild controller with the smaller cap — the easiest way is just to
        // keep the same controller; the bean reads props live each call.

        byte[] huge = new byte[64];
        Arrays.fill(huge, (byte) 'A');
        upstream.expect(requestTo("https://api.example.com/big"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(huge, MediaType.APPLICATION_OCTET_STREAM));

        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/big\""
                + "}";

        MvcResult res = mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(DevtoolsProxyController.HEADER_PROXY_TRUNCATED, "1"))
                .andReturn();
        assertThat(res.getResponse().getContentAsByteArray()).hasSize(8);
    }

    @Test
    void wildcardAllowlistEntryAllowsSubdomain() throws Exception {
        upstream.expect(requestTo("https://orders.staging.example.com/x"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://orders.staging.example.com/x\""
                + "}";

        mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        upstream.verify();
    }

    @Test
    void cookieAndForwardedForRequestHeadersAreStripped() throws Exception {
        upstream.expect(requestTo("https://api.example.com/x"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-Id", "demo"))
                .andRespond(req -> {
                    // Must not see Cookie / X-Forwarded-For.
                    if (req.getHeaders().containsKey("Cookie")) {
                        throw new AssertionError("Cookie must be stripped from upstream request");
                    }
                    if (req.getHeaders().containsKey("X-Forwarded-For")) {
                        throw new AssertionError("X-Forwarded-For must be stripped from upstream request");
                    }
                    org.springframework.mock.http.client.MockClientHttpResponse r =
                            new org.springframework.mock.http.client.MockClientHttpResponse(
                                    "{}".getBytes(StandardCharsets.UTF_8),
                                    org.springframework.http.HttpStatus.OK);
                    r.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return r;
                });

        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/x\","
                + "\"headers\":{"
                +   "\"X-Tenant-Id\":\"demo\","
                +   "\"Cookie\":\"session=leak\","
                +   "\"X-Forwarded-For\":\"1.2.3.4\""
                + "}}";

        mockMvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        upstream.verify();
    }

    @Test
    void controllerBeanIsNotRegisteredWhenDisabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        DevtoolsProxyAutoConfiguration.class));

        // Default (no property set) → disabled.
        runner.run(ctx ->
                assertThat(ctx).doesNotHaveBean(DevtoolsProxyController.class));

        // Explicit enabled=false → disabled.
        runner.withPropertyValues("csap.apidoc.devtools.proxy.enabled=false")
                .run(ctx ->
                        assertThat(ctx).doesNotHaveBean(DevtoolsProxyController.class));

        // enabled=true → bean present (allowed-hosts may be empty; the
        // controller still loads, requests just fail-closed).
        runner.withPropertyValues(
                        "csap.apidoc.devtools.proxy.enabled=true",
                        "csap.apidoc.devtools.proxy.allowed-hosts[0]=api.example.com")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(DevtoolsProxyController.class);
                    assertThat(ctx).hasSingleBean(HostAllowList.class);
                    assertThat(ctx).hasSingleBean(UpstreamClient.class);
                    assertThat(ctx).hasSingleBean(DevtoolsProxyProperties.class);
                });
    }

    @Test
    void emptyAllowlistDeniesEvenWhenEnabled() throws Exception {
        // Mirror "enabled but allowed-hosts left blank" — the unit test path
        // (no Spring boot up) — just rebuild allowList with an empty list.
        props.setAllowedHosts(Collections.emptyList());
        HostAllowList emptyAllow = new HostAllowList(props.getAllowedHosts());
        DevtoolsProxyController c = new DevtoolsProxyController(
                props, emptyAllow, new UpstreamClient(restTemplate), objectMapper);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(c).build();

        String body = "{"
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/x\""
                + "}";

        MvcResult res = mvc.perform(post("/csap/apidoc/devtools/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())
                .andReturn();
        assertThat(res.getResponse().getContentAsString())
                .contains("\"error\":\"host_not_allowed\"");
    }
}
