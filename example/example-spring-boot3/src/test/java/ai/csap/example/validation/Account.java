package ai.csap.example.validation;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * jakarta.validation 注解(Spring Boot 3)
 */
@Data
@ApiModel(description = "账号")
public class Account {
    @ApiModelProperty(value = "用户名", required = true)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    private String username;

    @ApiModelProperty(value = "邮箱", required = true)
    @Email(message = "邮箱格式不正确")
    private String email;
}
