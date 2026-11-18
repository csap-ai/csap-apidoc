package ai.csap.apidoc.strategy;

import java.util.List;

import ai.csap.apidoc.model.RuleChange;

/**
 * 能查看修改记录并回滚的规则存储(目前是数据库模式)。DevTools 通过它提供"修改记录 / 回滚"。
 *
 * @author ycf
 * @since 1.4.0
 */
public interface RuleHistoryProvider {

    /**
     * 是否只读;只读时不能回滚
     */
    boolean isReadOnly();

    /**
     * 修改记录,最新的在前
     *
     * @param ruleKey 规则键(类全名.方法名),null 或空表示全部
     * @param limit   条数,小于等于 0 用默认值
     */
    List<RuleChange> history(String ruleKey, int limit);

    /**
     * 把某条修改记录对应的接口恢复成"修改前"的内容,回滚本身也会留下一条修改记录。
     *
     * @param changeId  修改记录 id
     * @param updatedBy 修改人
     * @return 新版本号;已经是那个内容时返回 -1
     */
    long rollback(long changeId, String updatedBy);
}
