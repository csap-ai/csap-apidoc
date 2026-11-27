package ai.csap.apidoc.team.auth;

/** 开放接口令牌身份。envKey 仅对注册令牌有意义:绑定的环境,null = 默认环境。 */
public record TokenPrincipal(long id, String name, String scope, Long projectId, long createdBy, String envKey) {
}
