package ai.csap.apidoc.team.change;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.user.UserService;

/**
 * 修改单评论(契约 5.1):能看修改单的人都能看;管理员、维护者、开发能发;只能改、删自己的,
 * 管理员和该项目维护者可以删任何评论;删除是软删除(列表里仍返回,body 为 null)。
 */
@Service
public class CommentService {

    static final int MAX_PER_CHANGE = 500;
    static final int MAX_BODY = 4000;

    private final Db db;
    private final Access access;
    private final AuditService audit;
    private final ChangeService changes;
    private final UserService users;

    public CommentService(Db db, Access access, AuditService audit, ChangeService changes, UserService users) {
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.changes = changes;
        this.users = users;
    }

    private record Row(long id, long changeId, Long itemId, long authorId, String body, boolean deleted, long createdAt, long updatedAt) {
    }

    private static final String COLS = "id, change_id, item_id, author_id, body, deleted, created_at, updated_at";

    private static Row map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long item = rs.getLong(3);
        Long itemId = rs.wasNull() ? null : item;
        return new Row(rs.getLong(1), rs.getLong(2), itemId, rs.getLong(4), rs.getString(5), rs.getInt(6) == 1, rs.getLong(7), rs.getLong(8));
    }

    private Row load(long id) {
        Row r = db.one("SELECT " + COLS + " FROM csap_team_comment WHERE id = ?", CommentService::map, id);
        if (r == null) {
            throw ApiException.notFound("评论不存在");
        }
        return r;
    }

    private Map<String, Object> json(Row r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("changeId", r.changeId());
        m.put("itemId", r.itemId());
        Map<String, Object> author = new LinkedHashMap<>();
        UserService.UserRow u = users.byId(r.authorId());
        author.put("id", r.authorId());
        author.put("displayName", u == null ? "(已删除用户)" : u.displayName());
        m.put("author", author);
        m.put("body", r.deleted() ? null : r.body());
        m.put("deleted", r.deleted());
        m.put("createdAt", r.createdAt());
        m.put("updatedAt", r.updatedAt());
        return m;
    }

    private static String body(Object v) {
        String b = v instanceof String s ? s.trim() : null;
        if (b == null || b.isEmpty() || b.length() > MAX_BODY) {
            throw ApiException.validation("评论内容去掉首尾空白后需要 1–" + MAX_BODY + " 字");
        }
        return b;
    }

    public List<Map<String, Object>> list(SessionUser u, long changeId) {
        changes.info(Who.console(u), changeId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row r : db.query("SELECT " + COLS + " FROM csap_team_comment WHERE change_id = ? ORDER BY id", CommentService::map, changeId)) {
            out.add(json(r));
        }
        return out;
    }

    public Map<String, Object> create(SessionUser u, long changeId, Object bodyRaw, Object itemRaw) {
        ChangeService.Info info = changes.info(Who.console(u), changeId);
        Role role = access.roleIn(u, info.projectId());
        if (role == null || !role.atLeast(Role.DEVELOPER)) {
            throw ApiException.forbidden("只读成员不能发表评论");
        }
        String body = body(bodyRaw);
        Long itemId = null;
        if (itemRaw != null) {
            if (!(itemRaw instanceof Number n)) {
                throw ApiException.validation("itemId 无效");
            }
            itemId = n.longValue();
            if (db.count("SELECT COUNT(*) FROM csap_team_change_item WHERE id = ? AND change_id = ?", itemId, changeId) == 0) {
                throw ApiException.validation("itemId 不属于该修改单");
            }
        }
        if (db.count("SELECT COUNT(*) FROM csap_team_comment WHERE change_id = ?", changeId) >= MAX_PER_CHANGE) {
            throw ApiException.validation("一张修改单最多 " + MAX_PER_CHANGE + " 条评论");
        }
        long now = System.currentTimeMillis();
        final Long item = itemId;
        long id = db.inTx(() -> {
            long cid = db.insert("INSERT INTO csap_team_comment (change_id, item_id, author_id, body, deleted, created_at, updated_at)"
                    + " VALUES (?, ?, ?, ?, 0, ?, ?)", changeId, item, u.id(), body, now, now);
            audit.log("comment.create").project(info.projectId()).env(info.envKey()).change(changeId).after(body).save();
            return cid;
        });
        changes.fireCommented(changeId, id, u.displayName(), body);
        return json(load(id));
    }

    public Map<String, Object> update(SessionUser u, long id, Object bodyRaw) {
        Row r = load(id);
        ChangeService.Info info = changes.info(Who.console(u), r.changeId());
        if (r.authorId() != u.id()) {
            throw ApiException.forbidden("只能修改自己的评论");
        }
        if (r.deleted()) {
            throw ApiException.notFound("评论已删除");
        }
        String body = body(bodyRaw);
        db.inTx(() -> {
            db.update("UPDATE csap_team_comment SET body = ?, updated_at = ? WHERE id = ? AND deleted = 0", body, System.currentTimeMillis(), id);
            audit.log("comment.update").project(info.projectId()).env(info.envKey()).change(r.changeId()).before(r.body()).after(body).save();
        });
        return json(load(id));
    }

    public Map<String, Object> delete(SessionUser u, long id) {
        Row r = load(id);
        ChangeService.Info info = changes.info(Who.console(u), r.changeId());
        Role role = access.roleIn(u, info.projectId());
        boolean allowed = r.authorId() == u.id() || u.isAdmin() || (role != null && role.atLeast(Role.MAINTAINER));
        if (!allowed) {
            throw ApiException.forbidden("只能删除自己的评论");
        }
        if (!r.deleted()) {
            db.inTx(() -> {
                db.update("UPDATE csap_team_comment SET body = NULL, deleted = 1, updated_at = ? WHERE id = ?", System.currentTimeMillis(), id);
                audit.log("comment.delete").project(info.projectId()).env(info.envKey()).change(r.changeId()).before(r.body()).save();
            });
        }
        return json(load(id));
    }
}
