package ai.csap.apidoc.runtime.filter;

/**
 * 返回字段过滤模式。
 *
 * @author ycf
 * @since 1.2.0
 */
public enum FilterMode {
    /**
     * 不过滤
     */
    OFF,
    /**
     * 路径白名单(默认):接口配置了返回规则后,只输出放行的路径、force 字段及其上级节点。
     * 与文档显示的返回字段一致。
     */
    PATH,
    /**
     * 兼容 csap-framework:按"类型"挂白名单 / 黑名单,同一类型多处出现时规则合并;
     * 先查黑名单,白名单为空时输出黑名单之外的全部字段。
     */
    CSAP
}
