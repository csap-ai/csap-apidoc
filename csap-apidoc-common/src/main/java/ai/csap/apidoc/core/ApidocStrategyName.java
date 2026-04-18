package ai.csap.apidoc.core;

/**
 * @Author ycf
 * @Date 2025/9/4 15:44
 * @Version 1.0
 */
public interface ApidocStrategyName {

    /**
     * 策略类型
     *
     * @return 策略
     */
    String getName();

    /**
     * 后缀
     */
    String getSuffix();

    /**
     * 策略类型
     *
     * @return 结果
     */
    ApidocStrategyName strategyType();
}
