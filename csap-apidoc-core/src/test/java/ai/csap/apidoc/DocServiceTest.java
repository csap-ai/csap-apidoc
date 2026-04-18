package ai.csap.apidoc;

import java.util.Arrays;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.testng.annotations.Test;

import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.test.model.TestBean;
import ai.csap.apidoc.test.model.TestListModel;
import ai.csap.apidoc.test.model.TestModel;
import ai.csap.apidoc.util.ReflectionKit;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.factory.ValidateFilter;
import ai.csap.validation.properties.ValidationProperties;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2019年-12月-28日 18:48:00
 **/
public class DocServiceTest implements ValidateFilter {

    /**
     * 测试文档
     */
    @Test
    public void testScannerPackage() {
        CsapDocConfig docConfig = new CsapDocConfig();
        docConfig.setApiPackages(Arrays.asList("ai.csap.apidoc.test"));
        DefaultValidateFactoryImpl validateFactory = new DefaultValidateFactoryImpl(ConstraintHelper.forAllBuiltinConstraints(), new ValidationProperties());

    }

    @Test
    public void aa() {
        String appendKeyName = "testBean";
        String name = "name2";
        TestModel<TestBean> testBeanTestModel = new TestModel<>();
        TestBean testBean = TestBean.builder()
                .name("张三")
                .build();
        testBean.setName2("1244");
        testBeanTestModel.setTestBean(testBean);
        testBeanTestModel.setListModels(Arrays.asList(TestListModel.builder()
                .password("123456")
                .build()));
        testBeanTestModel.setName("李四");
        System.out.println(ReflectionKit.getValidateValue(name, Arrays.asList(appendKeyName), testBeanTestModel));
    }

    @Override
    public ConstraintHelper getConstraintHelper() {
        return null;
    }
}
