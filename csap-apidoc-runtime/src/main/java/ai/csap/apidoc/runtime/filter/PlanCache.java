package ai.csap.apidoc.runtime.filter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 挂在掩码节点 / 接口规则上的预编译计划缓存,每个引擎一份(同一个接口可能被不同的消息转换器写出)。
 * 规则热更新时掩码、规则整体替换,旧的计划随之被回收。
 *
 * @author ycf
 * @since 1.2.0
 */
final class PlanCache {

    static final int JACKSON = 0;

    static final int FASTJSON = 1;

    static final int FASTJSON2 = 2;

    @SuppressWarnings("unchecked")
    private final ConcurrentMap<Object, Object>[] maps = new ConcurrentMap[3];

    @SuppressWarnings("unchecked")
    <K, T> T get(int engine, K key, Function<? super K, T> factory) {
        ConcurrentMap<Object, Object> map = maps[engine];
        if (map == null) {
            synchronized (this) {
                map = maps[engine];
                if (map == null) {
                    map = new ConcurrentHashMap<>(4);
                    maps[engine] = map;
                }
            }
        }
        Object found = map.get(key);
        if (found == null) {
            found = map.computeIfAbsent(key, k -> factory.apply((K) k));
        }
        return (T) found;
    }
}
