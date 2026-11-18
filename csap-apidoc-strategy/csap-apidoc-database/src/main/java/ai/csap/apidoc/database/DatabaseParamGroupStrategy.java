package ai.csap.apidoc.database;

import java.time.Duration;
import java.util.function.Supplier;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.strategy.store.RuleStore;
import ai.csap.apidoc.strategy.store.RuleStoreParamGroupStrategy;
import ai.csap.validation.factory.IValidateFactory;

/**
 * 数据库参数分组模式({@code paramType = ApiStrategyType.DATABASE}):字段规则存在应用自己的关系数据库里。
 * 内存缓存、保存、轮询同步、修改记录与回滚等公共逻辑见 {@link RuleStoreParamGroupStrategy}。
 *
 * @author ycf
 * @since 1.4.0
 */
public class DatabaseParamGroupStrategy extends RuleStoreParamGroupStrategy {

    /**
     * @param validateFactory 注解校验器工厂,可为 null
     * @param storeSupplier   规则表访问对象;第一次用到数据库时才调用(此时才解析 DataSource)
     * @param initSchema      表不存在时是否自动建表
     * @param readOnly        只读模式
     * @param pollInterval    轮询版本号的间隔,null 或 0 表示不轮询
     */
    public DatabaseParamGroupStrategy(IValidateFactory validateFactory, Supplier<DatabaseRuleStore> storeSupplier,
                                      boolean initSchema, boolean readOnly, Duration pollInterval) {
        super(ApiStrategyType.DATABASE, "数据库模式", "csap.apidoc.database", validateFactory, storeSupplier,
                initSchema, readOnly, pollInterval);
    }

    @Override
    public DatabaseRuleStore store() {
        return (DatabaseRuleStore) super.store();
    }
}
