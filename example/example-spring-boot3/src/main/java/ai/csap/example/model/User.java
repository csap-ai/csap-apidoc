package ai.csap.example.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User Entity
 *
 * @author CSAP Team
 */
@Data
@ApiModel(description = "用户实体")
public class User {

    @ApiModelProperty(value = "用户ID", example = "1001")
    private Long id;

    @ApiModelProperty(value = "用户名", required = true, example = "john_doe")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    private String username;

    @ApiModelProperty(value = "邮箱地址", required = true, example = "john@example.com")
    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @ApiModelProperty(value = "手机号", example = "13800138000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @ApiModelProperty(value = "年龄", example = "25")
    @Min(value = 18, message = "年龄必须大于18岁")
    @Max(value = 120, message = "年龄必须小于120岁")
    private Integer age;

    @ApiModelProperty(value = "账户状态", example = "ACTIVE")
    private UserStatus status;

    @ApiModelProperty(value = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2024-01-01T10:00:00")
    private LocalDateTime updateTime;
}

