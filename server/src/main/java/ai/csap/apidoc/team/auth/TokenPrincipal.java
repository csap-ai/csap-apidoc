package ai.csap.apidoc.team.auth;

/** 开放接口令牌身份。 */
public record TokenPrincipal(long id, String name, String scope, Long projectId, long createdBy) {
}
