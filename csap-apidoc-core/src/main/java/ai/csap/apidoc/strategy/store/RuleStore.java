package ai.csap.apidoc.strategy.store;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import ai.csap.apidoc.model.RuleChange;

/**
 * 规则存储(数据库、Redis 两个实现):每个接口一条规则 JSON,规则键为 {@code 类全名.方法名},
 * 全局版本号每次保存加 1,另有修改记录。{@link RuleStoreParamGroupStrategy} 在它之上做内存缓存、同步和 DevTools 支持。
 *
 * @author ycf
 * @since 1.4.0
 */
public interface RuleStore {

    /**
     * 服务标识
     */
    String service();

    /**
     * 用于日志的存储说明
     */
    String describe();

    /**
     * 首次使用前的准备:建表 / 检查表;只读时不能写任何东西
     *
     * @param createIfMissing 缺少表时是否自动创建
     * @param readOnly        只读模式
     */
    void init(boolean createIfMissing, boolean readOnly) throws Exception;

    /**
     * 读取本服务的全部规则。先读版本号再读规则:两者之间若有修改,记下的版本偏旧,下次检查会再加载一次,不会漏。
     */
    Snapshot load() throws Exception;

    /**
     * 本服务的全局版本号,没有记录时为 0
     */
    long version() throws Exception;

    /**
     * 保存一个接口的规则(原子:版本号加 1、写规则、写修改记录)
     *
     * @param ruleKey   规则键
     * @param rulesJson 规则 JSON,null 表示删除
     * @param updatedBy 修改人
     * @return 新版本号;内容与已有的相同、没有修改时返回 -1
     */
    long save(String ruleKey, String rulesJson, String updatedBy) throws Exception;

    /**
     * 修改记录,最新的在前
     *
     * @param ruleKey 规则键,null 或空表示全部
     * @param limit   条数,小于等于 0 用默认值
     */
    List<RuleChange> history(String ruleKey, int limit) throws Exception;

    /**
     * 回滚到某条修改记录之前的内容(回滚本身也是一次保存)
     *
     * @throws IllegalArgumentException 修改记录不存在
     */
    long rollback(long changeId, String updatedBy) throws Exception;

    /**
     * 订阅"规则有变化"的即时通知(如 Redis 的 Pub/Sub);不支持时返回 null,靠轮询同步。
     *
     * @param onChange 有变化时回调(可能在别的线程)
     * @return 取消订阅的句柄
     */
    default AutoCloseable subscribe(Runnable onChange) throws Exception {
        return null;
    }

    /**
     * 一次加载的结果
     */
    final class Snapshot {
        private final long version;
        private final Map<String, String> rules;

        public Snapshot(long version, Map<String, String> rules) {
            this.version = version;
            this.rules = Collections.unmodifiableMap(rules);
        }

        public long version() {
            return version;
        }

        /**
         * 规则键 → 规则 JSON
         */
        public Map<String, String> rules() {
            return rules;
        }
    }
}
