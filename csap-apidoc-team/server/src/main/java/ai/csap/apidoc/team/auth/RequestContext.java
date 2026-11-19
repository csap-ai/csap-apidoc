package ai.csap.apidoc.team.auth;

/** 当前请求的身份,线程内有效,由 {@link AuthFilter} 设置和清理。 */
public final class RequestContext {

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    private static final class Holder {
        SessionUser user;
        TokenPrincipal token;
        Actor actor;
        String ip;
    }

    private static Holder h() {
        Holder h = CURRENT.get();
        if (h == null) {
            h = new Holder();
            CURRENT.set(h);
        }
        return h;
    }

    public static void setIp(String ip) { h().ip = ip; }
    public static String ip() { Holder h = CURRENT.get(); return h == null ? null : h.ip; }

    public static void setUser(SessionUser u) { h().user = u; }
    public static SessionUser user() { Holder h = CURRENT.get(); return h == null ? null : h.user; }

    public static void setToken(TokenPrincipal t) { h().token = t; }
    public static TokenPrincipal token() { Holder h = CURRENT.get(); return h == null ? null : h.token; }

    /** 覆盖默认操作者(例如登录失败时尚无会话用户)。 */
    public static void setActor(Actor a) { h().actor = a; }

    /** 当前操作者;没有请求上下文时(后台任务)为系统。 */
    public static Actor actor() {
        Holder h = CURRENT.get();
        if (h == null) {
            return Actor.system();
        }
        if (h.actor != null) {
            return h.actor;
        }
        if (h.user != null) {
            return Actor.user(h.user.id(), h.user.username(), "CONSOLE", h.ip);
        }
        if (h.token != null) {
            return new Actor(Actor.TOKEN, h.token.id(), h.token.name(), "OPEN_API", h.ip);
        }
        return new Actor(Actor.SYSTEM, null, "anonymous", "CONSOLE", h.ip);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
