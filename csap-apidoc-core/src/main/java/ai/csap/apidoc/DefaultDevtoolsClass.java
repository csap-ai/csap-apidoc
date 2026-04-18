package ai.csap.apidoc;

/**
 * 开发模式class
 *
 * @author yangchengfu
 * @dataTime 2021年-05月-21日 14:04:00
 **/
public class DefaultDevtoolsClass implements DevtoolsClass {
    /**
     * 获取class对象
     *
     * @param cl
     * @return
     */
    @Override
    public Class<?> toolsClass(Class<?> cl) {
        return cl;
    }
}
