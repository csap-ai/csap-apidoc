package ai.csap.apidoc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.strategy.ParamGroupStrategy;

/**
 * 接口的有效状态:规则存储里的覆盖 > 方法注解(不是 DEFAULT 时)> 类注解(不是 DEFAULT 时)> DEFAULT。
 * 注解里的 DEFAULT 无法和“没标”区分,视为没标。备注来自规则存储。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class ApiStatusResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ApiStatusResolver.class);

    private ApiStatusResolver() {
    }

    /**
     * 计算并写入 docMethod 的有效状态和备注。调用时 docMethod.status 还是方法注解上的值。
     *
     * @param docMethod        接口(须已有 className、name)
     * @param controllerStatus 类注解上的状态,可为 null
     * @param strategyModel    规则来源,可为 null(只按注解计算)
     */
    public static void apply(CsapDocMethod docMethod, ApiStatus controllerStatus, StrategyModel strategyModel) {
        ApiStatus status = null;
        String note = null;
        if (strategyModel != null) {
            try {
                ParamGroupStrategy strategy = ApidocContext.paramStrategy(strategyModel.getParamType());
                ParamGroupMethodProperty rule = strategy == null ? null : strategy.yamlMethodProperty(strategyModel, docMethod);
                if (rule != null) {
                    status = rule.getStatus();
                    note = rule.getStatusNote();
                }
            } catch (RuntimeException e) {
                LOG.debug("read status rule failed for {}.{}: {}", docMethod.getClassName(), docMethod.getName(), e.getMessage());
            }
        }
        if (status == null && docMethod.getStatus() != null && docMethod.getStatus() != ApiStatus.DEFAULT) {
            status = docMethod.getStatus();
        }
        if (status == null && controllerStatus != null && controllerStatus != ApiStatus.DEFAULT) {
            status = controllerStatus;
        }
        docMethod.setStatus(status == null ? ApiStatus.DEFAULT : status);
        docMethod.setStatusNote(note == null || note.isEmpty() ? null : note);
    }
}
