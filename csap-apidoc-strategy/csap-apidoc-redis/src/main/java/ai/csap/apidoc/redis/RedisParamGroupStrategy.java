package ai.csap.apidoc.redis;

import java.time.Duration;
import java.util.function.Supplier;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.strategy.store.RuleStoreParamGroupStrategy;
import ai.csap.validation.factory.IValidateFactory;

/**
 * Redis 参数分组模式({@code paramType = ApiStrategyType.REDIS}):字段规则存在应用自己的 Redis 里。
 * 内存缓存、保存、同步、修改记录与回滚等公共逻辑见 {@link RuleStoreParamGroupStrategy}。
 *
 * @author ycf
 * @since 1.4.0
 */
public class RedisParamGroupStrategy extends RuleStoreParamGroupStrategy {

    public RedisParamGroupStrategy(IValidateFactory validateFactory, Supplier<RedisRuleStore> storeSupplier,
                                   boolean readOnly, Duration pollInterval) {
        super(ApiStrategyType.REDIS, "Redis 模式", "csap.apidoc.redis", validateFactory, storeSupplier,
                false, readOnly, pollInterval);
    }

    @Override
    public RedisRuleStore store() {
        return (RedisRuleStore) super.store();
    }
}
