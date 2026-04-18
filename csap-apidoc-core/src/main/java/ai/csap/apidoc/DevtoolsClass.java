package ai.csap.apidoc;

/**
 * 开发模式class
 *
 * @author yangchengfu
 * @dataTime 2021年-05月-21日 14:04:00
 **/
public interface DevtoolsClass {
    /**
     * 获取class对象
     *
     * @param cl
     * @return
     */
    Class<?> toolsClass(Class<?> cl);

    /**
     * 是否开发模式-返回所有接口使用
     *
     * @return
     */
    default Boolean devtools() {
        return Boolean.FALSE;
    }
}
