package ai.csap.apidoc.team.auth;

/** 当前登录用户(控制台)。 */
public record SessionUser(long id, String username, String displayName, String orgRole) {

    public boolean isAdmin() {
        return "ADMIN".equals(orgRole);
    }
}
