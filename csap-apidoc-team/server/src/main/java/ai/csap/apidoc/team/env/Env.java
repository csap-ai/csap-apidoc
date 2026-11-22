package ai.csap.apidoc.team.env;

import com.zaxxer.hikari.HikariDataSource;

import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;

/**
 * 一个环境:展示信息、保护 / 只读标志,以及读写该环境开源表({prefix}rule / history / meta / mcp_audit)的 {@link Db}。
 * 团队版自己的表不在这里,永远在主数据源。数据源地址和密码不对外暴露。
 */
public final class Env {

    private final String key;
    private final String name;
    private final int order;
    private final boolean isProtected;
    private final boolean readOnly;
    private final Db db;
    private final HikariDataSource ownPool;
    private final String secret;
    private volatile boolean up;
    private volatile String error;

    Env(String key, String name, int order, boolean isProtected, boolean readOnly, Db db, HikariDataSource ownPool, String secret) {
        this.key = key;
        this.name = name;
        this.order = order;
        this.isProtected = isProtected;
        this.readOnly = readOnly;
        this.db = db;
        this.ownPool = ownPool;
        this.secret = secret;
        this.error = "尚未连接";
    }

    public String key() { return key; }
    public String name() { return name; }
    public int order() { return order; }
    public boolean isProtected() { return isProtected; }
    public boolean readOnly() { return readOnly; }
    public boolean isUp() { return up; }
    public String error() { return up ? null : error; }
    /** 是否使用独立数据源(与团队版主库分开提交)。 */
    public boolean ownDataSource() { return ownPool != null; }

    /** 开源表访问,不检查状态(仅内部使用)。 */
    public Db rawDb() { return db; }

    /** 开源表访问;环境不可用时抛 {@code ENV_UNAVAILABLE}(503)。 */
    public Db db() {
        if (!up) {
            throw ApiException.envUnavailable(name, error);
        }
        return db;
    }

    /** 发布 / 写入前:只读环境一律拒绝。 */
    public void requireWritable() {
        if (readOnly) {
            throw ApiException.forbidden("环境「" + name + "」只读,不能向它发布");
        }
    }

    public String ruleTable() { return db.ruleTable(); }
    public String historyTable() { return db.historyTable(); }
    public String metaTable() { return db.metaTable(); }
    public String mcpAuditTable() { return db.mcpAuditTable(); }

    void setUp() {
        this.up = true;
        this.error = null;
    }

    void setDown(String message) {
        this.up = false;
        this.error = scrub(message);
    }

    /** 去掉可能带出的数据源密码。 */
    String scrub(String message) {
        String m = message == null ? "不可用" : message;
        if (secret != null && !secret.isEmpty()) {
            m = m.replace(secret, "***");
        }
        return m.length() > 300 ? m.substring(0, 300) : m;
    }

    void close() {
        if (ownPool != null) {
            ownPool.close();
        }
    }
}
