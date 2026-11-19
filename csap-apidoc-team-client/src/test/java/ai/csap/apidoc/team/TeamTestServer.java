package ai.csap.apidoc.team;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

/**
 * 测试用的本地"团队版":记录收到的请求,按 responder 返回状态码和响应体。
 */
public class TeamTestServer implements AutoCloseable {

    /**
     * 收到的一次请求
     */
    public static final class Req {
        public final String method;
        public final String path;
        public final Map<String, List<String>> headers;
        public final String body;
        public final long atNanos = System.nanoTime();

        Req(String method, String path, Map<String, List<String>> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }
    }

    /**
     * 响应:状态码 + 响应体
     */
    public static final class Resp {
        final int status;
        final String body;

        public Resp(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    public final List<Req> requests = new CopyOnWriteArrayList<>();
    private final HttpServer server;
    private volatile Function<Req, Resp> responder = r -> new Resp(200, "{\"heartbeatSeconds\":30}");

    public TeamTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] in = ex.getRequestBody().readAllBytes();
            Req req = new Req(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestHeaders(),
                    new String(in, StandardCharsets.UTF_8));
            requests.add(req);
            Resp resp = responder.apply(req);
            byte[] out = resp.body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(resp.status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(out);
                }
            }
            ex.close();
        });
        server.start();
    }

    public void respond(Function<Req, Resp> responder) {
        this.responder = responder;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public int count(String path) {
        int n = 0;
        for (Req r : requests) {
            if (r.path.equals(path)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
