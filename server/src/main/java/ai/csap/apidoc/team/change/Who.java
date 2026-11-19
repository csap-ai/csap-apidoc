package ai.csap.apidoc.team.change;

import ai.csap.apidoc.team.auth.SessionUser;

/** 修改单操作的执行者:控制台用户,或开放接口令牌(以令牌创建者的身份,受令牌绑定的项目限制)。 */
public record Who(SessionUser user, boolean viaToken, Long tokenProject) {

    public static Who console(SessionUser u) {
        return new Who(u, false, null);
    }

    public static Who token(SessionUser creator, Long projectId) {
        return new Who(creator, true, projectId);
    }
}
