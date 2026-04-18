package ai.csap.apidoc.core;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2021年-01月-16日 17:22:00
 **/
interface ApidocChildren<ChildrenR> {
    /**
     * 获取真实返回的类型
     *
     * @return 当前实例
     */
    default ChildrenR children() {
        return (ChildrenR) this;
    }

}
