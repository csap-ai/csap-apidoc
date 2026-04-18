package ai.csap.apidoc;

import java.util.regex.Pattern;

import org.testng.annotations.Test;

/**
 * @Author ycf
 * @Date 2021/9/29 5:52 下午
 * @Version 1.0
 */
@Test
public class ValidateTest {
    public void notNull() {
        Pattern pattern = Pattern.compile("^[0-9]*$");
        System.out.println(pattern.matcher("-1").matches());
    }
}
