package ai.csap.apidoc.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ai.csap.validation.factory.IValidateBaseFactory;
import ai.csap.validation.factory.Validate;

/**
 * 接口字段规则的"先构建、再替换"。
 *
 * <p>生成某个接口文档时,字段规则(返回过滤的 {@code FilterClassParam}、请求校验规则)先登记到一个临时键下,
 * 整个接口处理完再一次性替换到正式键({@code 类名.方法名})。这样运行时读取规则的线程
 * 不会读到"已清空、尚未登记完"的中间状态(原来是先 {@code clear()} 再逐条登记)。
 *
 * <p>同时记录每个接口是否配置了返回规则(任一返回字段命中了注解分组、YAML 或 SQLite 中的规则),
 * 供运行时返回字段过滤判断"没有返回规则的接口不过滤"。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class ResponseRuleIndex {

    private static final String STAGING_MARK = "#csap-building#";

    private static final AtomicLong SEQ = new AtomicLong();

    /**
     * 配置了返回规则的接口(含构建中的临时键)
     */
    private static final Set<String> CONFIGURED = ConcurrentHashMap.newKeySet();

    private ResponseRuleIndex() {
    }

    /**
     * 开始构建某个接口的规则,返回本次构建使用的临时键。
     *
     * @param key 正式键(类名.方法名)
     * @return 临时键
     */
    public static String beginBuild(String key) {
        return key + STAGING_MARK + SEQ.incrementAndGet();
    }

    /**
     * 是否是构建中的临时键
     */
    public static boolean isStagingKey(String key) {
        return key != null && key.contains(STAGING_MARK);
    }

    /**
     * 登记:该接口(键可以是临时键)的某个返回字段命中了规则
     */
    public static void markResponseRule(String key) {
        if (key != null) {
            CONFIGURED.add(key);
        }
    }

    /**
     * 构建完成:把临时键下的规则一次性替换到正式键。
     *
     * @param key        正式键
     * @param stagingKey 临时键
     */
    public static void commit(String key, String stagingKey) {
        Validate built = IValidateBaseFactory.VALIDATE_MAP.remove(stagingKey);
        IValidateBaseFactory.VALIDATE_MAP.put(key, built != null ? built : new Validate());
        if (CONFIGURED.remove(stagingKey)) {
            CONFIGURED.add(key);
        } else {
            CONFIGURED.remove(key);
        }
    }

    /**
     * 构建失败:丢弃临时键下的规则,保留原有规则。
     */
    public static void abort(String stagingKey) {
        IValidateBaseFactory.VALIDATE_MAP.remove(stagingKey);
        CONFIGURED.remove(stagingKey);
    }

    /**
     * 该接口是否配置了返回规则
     *
     * @param key 类名.方法名
     */
    public static boolean hasResponseRules(String key) {
        return CONFIGURED.contains(key);
    }
}
