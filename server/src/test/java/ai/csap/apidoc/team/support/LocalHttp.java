package ai.csap.apidoc.team.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

/** 测试用本地 HTTP 服务:记录请求,按函数返回响应。 */
public class LocalHttp implements AutoCloseable {

    public record Req(String method, String path, String query, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }
    }

    public record Res(int status, String body) {
    }

    private final HttpServer server;
    public final List<Req> requests = new CopyOnWriteArrayList<>();
    public volatile Function<Req, Res> handler = r -> new Res(200, "{}");

    public LocalHttp() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", ex -> {
            byte[] in = ex.getRequestBody().readAllBytes();
            Req req = new Req(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getRawQuery(),
                    Map.copyOf(ex.getRequestHeaders()), new String(in, StandardCharsets.UTF_8));
            requests.add(req);
            Res res = handler.apply(req);
            byte[] out = res.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(res.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                ex.getResponseBody().write(out);
            }
            ex.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** 等待满足条件的请求出现,最多 timeoutMs。 */
    public List<Req> await(java.util.function.Predicate<Req> p, long count, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            List<Req> m = requests.stream().filter(p).toList();
            if (m.size() >= count) {
                return m;
            }
            Thread.sleep(50);
        }
        return requests.stream().filter(p).toList();
    }
}
