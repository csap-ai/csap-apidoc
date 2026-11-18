package ai.csap.apidoc.strategy.store;

import java.util.function.Supplier;

/**
 * 保存规则时记录的修改人。默认是 DevTools;由其他入口(如 JSON Schema 导入)保存时,
 * 用 {@link #runAs(String, Supplier)} 临时指定,数据库 / Redis 模式的修改记录里就是这个名字。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class RuleActor {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RuleActor() {
    }

    /**
     * 当前线程指定的修改人,没有时返回默认值
     *
     * @param defaultActor 默认修改人
     * @return 修改人
     */
    public static String current(String defaultActor) {
        String c = CURRENT.get();
        return c == null || c.isEmpty() ? defaultActor : c;
    }

    /**
     * 以指定修改人执行
     *
     * @param actor  修改人
     * @param action 动作
     * @param <T>    返回类型
     * @return 动作的返回值
     */
    public static <T> T runAs(String actor, Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set(actor);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
