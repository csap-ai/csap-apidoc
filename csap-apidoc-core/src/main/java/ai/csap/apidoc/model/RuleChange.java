package ai.csap.apidoc.model;

/**
 * 一条规则修改记录。
 *
 * @author ycf
 * @since 1.4.0
 */
public class RuleChange {

    private final long id;
    private final String ruleKey;
    private final String before;
    private final String after;
    private final long version;
    private final String updatedBy;
    private final long updatedAt;

    public RuleChange(long id, String ruleKey, String before, String after, long version, String updatedBy, long updatedAt) {
        this.id = id;
        this.ruleKey = ruleKey;
        this.before = before;
        this.after = after;
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    /**
     * 修改前的规则 JSON,新增时为 null
     */
    public String getBefore() {
        return before;
    }

    /**
     * 修改后的规则 JSON,删除时为 null
     */
    public String getAfter() {
        return after;
    }

    public long getVersion() {
        return version;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 毫秒时间戳
     */
    public long getUpdatedAt() {
        return updatedAt;
    }
}
