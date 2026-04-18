package ai.csap.apidoc;

import java.util.Arrays;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import org.springframework.util.AntPathMatcher;
import org.testng.annotations.Test;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2020年-04月-05日 21:54:00
 **/
public class Labma {
    @Test
    public void x() {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        List<String> list = Arrays.asList();
        System.out.println(list.stream().anyMatch(i -> antPathMatcher.match(i, "/user/bn")));
        String string = "http://qx-user";
        String string2 = "http://qx-user/user";
        String string1[] = string.split("/");
        System.out.println(string1);
        System.out.println(StrUtil.toUnderlineCase("toUnderlineCase"));
        System.out.println(StrUtil.toCamelCase("updated_at"));
    }


}
