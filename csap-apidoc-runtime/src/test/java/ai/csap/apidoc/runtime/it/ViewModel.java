package ai.csap.apidoc.runtime.it;

import com.fasterxml.jackson.annotation.JsonView;

/**
 * {@code @JsonView} 与返回规则同时生效
 */
public class ViewModel {

    public interface Summary {
    }

    @JsonView(Summary.class)
    private String a = "a";

    @JsonView(Summary.class)
    private String b = "b";

    private String c = "c";

    public String getA() {
        return a;
    }

    public String getB() {
        return b;
    }

    public String getC() {
        return c;
    }
}
