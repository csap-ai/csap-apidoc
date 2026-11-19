package ai.csap.apidoc.team.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 团队版服务注册配置,默认关闭。
 *
 * <pre>
 * csap:
 *   apidoc:
 *     team:
 *       enabled: true
 *       url: http://team.example.com:8090
 *       token: ${CSAP_TEAM_TOKEN}
 * </pre>
 *
 * @author ycf
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = TeamProperties.PREFIX)
public class TeamProperties {

    public static final String PREFIX = "csap.apidoc.team";

    /**
     * 是否向团队版注册本服务。
     */
    private boolean enabled = false;

    /**
     * 团队版地址,例如 http://team.example.com:8090;为空时不注册。
     */
    private String url = "";

    /**
     * 团队版控制台创建的 REGISTER 令牌,建议用环境变量传入。
     */
    private String token = "";

    /**
     * 服务名。为空时依次取 csap.apidoc.database.service、spring.application.name,都没有时为 default。
     */
    private String service = "";

    /**
     * 团队版能访问到的本服务地址(含 context-path,不带末尾 /)。
     * 为空时推断为 http://{本机首个非回环 IPv4}:{server.port}{server.servlet.context-path}。
     */
    private String baseUrl = "";

    /**
     * 实例标识,同一服务内唯一;为空时用 baseUrl 的 host:port。
     */
    private String instanceId = "";

    /**
     * 文档接口路径。
     */
    private String docPath = "/csap/apidoc";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDocPath() {
        return docPath;
    }

    public void setDocPath(String docPath) {
        this.docPath = docPath;
    }

    /**
     * 不输出令牌。
     */
    @Override
    public String toString() {
        String masked = token == null || token.isEmpty() ? "" : "***";
        return "TeamProperties{enabled=" + enabled + ", url=" + url + ", token=" + masked +
                ", service=" + service + ", baseUrl=" + baseUrl + ", instanceId=" + instanceId +
                ", docPath=" + docPath + '}';
    }
}
