package ai.csap.apidoc.team.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;

/** 权限判断(契约第 3 节)。管理员在所有项目里等同维护者。 */
@Component
public class Access {

    private final Db db;

    public Access(Db db) {
        this.db = db;
    }

    public SessionUser user() {
        SessionUser u = RequestContext.user();
        if (u == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return u;
    }

    public SessionUser admin() {
        SessionUser u = user();
        if (!u.isAdmin()) {
            throw ApiException.forbidden("需要管理员权限");
        }
        return u;
    }

    /** 用户在项目里的实际角色,管理员为 MAINTAINER,无关系为 null。 */
    public Role roleIn(SessionUser u, long projectId) {
        if (u.isAdmin()) {
            return Role.MAINTAINER;
        }
        return memberRole(u.id(), projectId);
    }

    public Role memberRole(long userId, long projectId) {
        String r = db.one("SELECT role FROM csap_team_member WHERE project_id = ? AND user_id = ?",
                (rs, i) -> rs.getString(1), projectId, userId);
        return Role.parse(r);
    }

    public SessionUser requireRole(long projectId, Role min) {
        SessionUser u = user();
        Role r = roleIn(u, projectId);
        if (r == null || !r.atLeast(min)) {
            throw ApiException.forbidden("没有权限执行此操作");
        }
        return u;
    }

    /** 用户至少有 min 角色的项目 id;管理员返回 null(表示全部)。 */
    public Set<Long> projectsWith(SessionUser u, Role min) {
        if (u.isAdmin()) {
            return null;
        }
        List<String[]> rows = db.query("SELECT project_id, role FROM csap_team_member WHERE user_id = ?",
                (rs, i) -> new String[] {String.valueOf(rs.getLong(1)), rs.getString(2)}, u.id());
        Set<Long> out = new HashSet<>();
        for (String[] r : rows) {
            Role role = Role.parse(r[1]);
            if (role != null && role.atLeast(min)) {
                out.add(Long.parseLong(r[0]));
            }
        }
        return out;
    }

    public Long projectOfService(String service) {
        return db.one("SELECT project_id FROM csap_team_project_service WHERE service = ?", (rs, i) -> {
            long v = rs.getLong(1);
            return rs.wasNull() ? null : v;
        }, service);
    }

    /** 能否查看某服务(管理员全部;其他人需是服务所在项目成员)。 */
    public boolean canViewService(SessionUser u, String service) {
        if (u.isAdmin()) {
            return true;
        }
        Long pid = projectOfService(service);
        return pid != null && memberRole(u.id(), pid) != null;
    }

    public void requireViewService(String service) {
        SessionUser u = user();
        if (!canViewService(u, service)) {
            throw ApiException.notFound("服务不存在或无权查看");
        }
    }
}
