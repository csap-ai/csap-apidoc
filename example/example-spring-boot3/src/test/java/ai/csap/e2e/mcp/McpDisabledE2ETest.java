package ai.csap.e2e.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 默认不开启:/csap/mcp 不存在;DevTools 状态接口显示未开启
 */
@SpringBootTest(classes = McpE2EApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"csap.apidoc.devtool.enabled=true"})
class McpDisabledE2ETest {

    @LocalServerPort
    int port;

    @Test
    void endpointIsNotRegistered() throws Exception {
        McpClient client = new McpClient(port);
        assertEquals(404, client.rpcRaw("initialize", "{\"protocolVersion\":\"2025-06-18\"}").statusCode());
        String status = client.get("/devtools/mcp/status").body();
        assertTrue(status.contains("\"enabled\":false"), status);
    }
}
