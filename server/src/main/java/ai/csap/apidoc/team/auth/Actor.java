package ai.csap.apidoc.team.auth;

/** 审计里的操作者。 */
public record Actor(String type, Long id, String name, String source, String ip) {

    public static final String USER = "USER";
    public static final String TOKEN = "TOKEN";
    public static final String SERVICE = "SERVICE";
    public static final String SYSTEM = "SYSTEM";

    public static Actor system() {
        return new Actor(SYSTEM, null, "system", "SYSTEM", null);
    }

    public static Actor service(String name) {
        return new Actor(SERVICE, null, name, "SERVICE", null);
    }

    public static Actor user(long id, String username, String source, String ip) {
        return new Actor(USER, id, username, source, ip);
    }
}
