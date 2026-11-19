package ai.csap.apidoc.team.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.user.UserService;

/** 项目与成员。 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final Db db;
    private final Access access;
    private final AuditService audit;
    private final UserService users;

    public ProjectController(Db db, Access access, AuditService audit, UserService users) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.users = users;
    }

    private Map<String, Object> toMap(long id, SessionUser viewer) {
        Map<String, Object> m = db.one("SELECT id, name, description, require_approval, created_at, updated_at FROM csap_team_project"
                + " WHERE id = ?", (rs, i) -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", rs.getLong(1));
                    r.put("name", rs.getString(2));
                    r.put("description", rs.getString(3));
                    r.put("requireApproval", rs.getInt(4) == 1);
                    r.put("createdAt", rs.getLong(5));
                    r.put("updatedAt", rs.getLong(6));
                    return r;
                }, id);
        if (m == null) {
            return null;
        }
        m.put("serviceCount", db.count("SELECT COUNT(*) FROM csap_team_project_service WHERE project_id = ?", id));
        m.put("memberCount", db.count("SELECT COUNT(*) FROM csap_team_member WHERE project_id = ?", id));
        Role r = access.roleIn(viewer, id);
        m.put("myRole", r == null ? null : r.name());
        return m;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        SessionUser u = access.user();
        List<Long> ids = db.query("SELECT id FROM csap_team_project ORDER BY id", (rs, i) -> rs.getLong(1));
        Set<Long> visible = access.projectsWith(u, Role.VIEWER);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : ids) {
            if (visible == null || visible.contains(id)) {
                out.add(toMap(id, u));
            }
        }
        return out;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        access.admin();
        String name = name(body.get("name"));
        String desc = desc(body.get("description"));
        boolean req = !body.containsKey("requireApproval") || Boolean.TRUE.equals(body.get("requireApproval"));
        long now = System.currentTimeMillis();
        long id;
        try {
            id = db.insert("INSERT INTO csap_team_project (name, description, require_approval, created_at, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?)", name, desc, req ? 1 : 0, now, now);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiException.conflict("项目名称已存在");
        }
        audit.log("project.create").project(id).reason(name).save();
        return toMap(id, access.user());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        SessionUser u = access.user();
        Map<String, Object> m = toMap(id, u);
        if (m == null || access.roleIn(u, id) == null) {
            throw ApiException.notFound("项目不存在或无权查看");
        }
        return m;
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        exists(id);
        SessionUser u = access.requireRole(id, Role.MAINTAINER);
        StringBuilder changed = new StringBuilder();
        try {
            if (body.containsKey("name")) {
                db.update("UPDATE csap_team_project SET name = ? WHERE id = ?", name(body.get("name")), id);
                changed.append("name ");
            }
            if (body.containsKey("description")) {
                db.update("UPDATE csap_team_project SET description = ? WHERE id = ?", desc(body.get("description")), id);
                changed.append("description ");
            }
            if (body.containsKey("requireApproval")) {
                db.update("UPDATE csap_team_project SET require_approval = ? WHERE id = ?",
                        Boolean.TRUE.equals(body.get("requireApproval")) ? 1 : 0, id);
                changed.append("requireApproval=").append(Boolean.TRUE.equals(body.get("requireApproval")));
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiException.conflict("项目名称已存在");
        }
        db.update("UPDATE csap_team_project SET updated_at = ? WHERE id = ?", System.currentTimeMillis(), id);
        audit.log("project.update").project(id).reason(changed.toString().trim()).save();
        return toMap(id, u);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        access.admin();
        Map<String, Object> p = toMap(id, access.user());
        if (p == null) {
            throw ApiException.notFound("项目不存在");
        }
        if (db.count("SELECT COUNT(*) FROM csap_team_project_service WHERE project_id = ?", id) > 0) {
            throw ApiException.conflict("项目下还有服务,请先移走或删除服务");
        }
        db.inTx(() -> {
            db.update("DELETE FROM csap_team_member WHERE project_id = ?", id);
            db.update("DELETE FROM csap_team_project WHERE id = ?", id);
        });
        audit.log("project.delete").project(id).reason((String) p.get("name")).save();
        return new LinkedHashMap<>();
    }

    // ---- 成员 ----

    @GetMapping("/{id}/members")
    public List<Map<String, Object>> members(@PathVariable long id) {
        exists(id);
        access.requireRole(id, Role.VIEWER);
        return db.query("SELECT u.id, u.username, u.display_name, u.status, m.role, m.created_at FROM csap_team_member m"
                + " JOIN csap_team_user u ON u.id = m.user_id WHERE m.project_id = ? ORDER BY u.id", (rs, i) -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("userId", rs.getLong(1));
                    r.put("username", rs.getString(2));
                    r.put("displayName", rs.getString(3));
                    r.put("status", rs.getString(4));
                    r.put("role", rs.getString(5));
                    r.put("createdAt", rs.getLong(6));
                    return r;
                }, id);
    }

    @PutMapping("/{id}/members/{userId}")
    public Map<String, Object> putMember(@PathVariable long id, @PathVariable long userId, @RequestBody Map<String, Object> body) {
        exists(id);
        SessionUser actor = access.requireRole(id, Role.MAINTAINER);
        Role role = Role.parse(body.get("role") instanceof String s ? s : null);
        if (role == null) {
            throw ApiException.validation("role 只能是 MAINTAINER / DEVELOPER / VIEWER");
        }
        UserService.UserRow target = users.byId(userId);
        if (target == null) {
            throw ApiException.notFound("用户不存在");
        }
        if ("ADMIN".equals(target.orgRole()) && !actor.isAdmin()) {
            throw ApiException.forbidden("维护者不能设置管理员的项目角色");
        }
        long now = System.currentTimeMillis();
        String before = db.one("SELECT role FROM csap_team_member WHERE project_id = ? AND user_id = ?",
                (rs, i) -> rs.getString(1), id, userId);
        if (before == null) {
            try {
                db.update("INSERT INTO csap_team_member (project_id, user_id, role, created_at) VALUES (?, ?, ?, ?)", id, userId,
                        role.name(), now);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                db.update("UPDATE csap_team_member SET role = ? WHERE project_id = ? AND user_id = ?", role.name(), id, userId);
            }
        } else {
            db.update("UPDATE csap_team_member SET role = ? WHERE project_id = ? AND user_id = ?", role.name(), id, userId);
        }
        audit.log("member.update").project(id).before(before).after(role.name()).reason(target.username()).save();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("role", role.name());
        return m;
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Map<String, Object> removeMember(@PathVariable long id, @PathVariable long userId) {
        exists(id);
        SessionUser actor = access.requireRole(id, Role.MAINTAINER);
        UserService.UserRow target = users.byId(userId);
        if (target != null && "ADMIN".equals(target.orgRole()) && !actor.isAdmin()) {
            throw ApiException.forbidden("维护者不能移除管理员");
        }
        String before = db.one("SELECT role FROM csap_team_member WHERE project_id = ? AND user_id = ?",
                (rs, i) -> rs.getString(1), id, userId);
        if (before == null) {
            throw ApiException.notFound("该用户不是项目成员");
        }
        db.update("DELETE FROM csap_team_member WHERE project_id = ? AND user_id = ?", id, userId);
        audit.log("member.remove").project(id).before(before).reason(target == null ? String.valueOf(userId) : target.username()).save();
        return new LinkedHashMap<>();
    }

    private void exists(long id) {
        if (db.count("SELECT COUNT(*) FROM csap_team_project WHERE id = ?", id) == 0) {
            throw ApiException.notFound("项目不存在");
        }
    }

    private static String name(Object v) {
        if (!(v instanceof String s) || s.isBlank() || s.trim().length() > 128) {
            throw ApiException.validation("项目名称不能为空且不超过 128 字");
        }
        return s.trim();
    }

    private static String desc(Object v) {
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s) || s.length() > 1000) {
            throw ApiException.validation("项目描述不超过 1000 字");
        }
        return s;
    }
}
