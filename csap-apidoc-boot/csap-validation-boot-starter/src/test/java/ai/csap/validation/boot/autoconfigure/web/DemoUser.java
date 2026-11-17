package ai.csap.validation.boot.autoconfigure.web;

import ai.csap.apidoc.annotation.ApiModel;

/**
 * 请求体:Spring Boot 2 风格的 javax.validation 注解 + Spring Boot 3 风格的 jakarta.validation 注解.
 */
@ApiModel(description = "用户")
public class DemoUser {
    @javax.validation.constraints.NotNull(message = "用户名不能为空")
    @javax.validation.constraints.Size(min = 3, max = 20, message = "长度3-20")
    private String username;

    @jakarta.validation.constraints.Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;

    @javax.validation.constraints.Min(value = 18, message = "未满18岁")
    private Integer age;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
