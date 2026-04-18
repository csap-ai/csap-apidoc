package ai.csap.apidoc.test;

import java.util.Arrays;
import java.util.List;

import ai.csap.apidoc.test.model.TestBean;
import ai.csap.apidoc.test.model.TestModel;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2019年-12月-28日 18:55:00
 **/
public class TestService<M> {
    public boolean add(M model) {
        return true;
    }

    public boolean delete(M model) {
        return true;
    }

    public boolean update(M model) {
        return true;
    }

    public List<TestModel<TestBean>> query(M model) {
        return Arrays.asList(new TestModel());
    }
}
