package ai.csap.example.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户请求 - 继承泛型父类并指定泛型参数为 User
 *
 * 这个类用于测试继承泛型父类的场景：
 * - 父类 BaseRequest<T> 中有泛型字段 T data 和 List<T> dataList
 * - 子类继承时指定 T = User
 * - 期望 getFieldsFromMethod 能正确解析 data 字段为 User 类型
 *
 * @author CSAP Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "用户请求")
public class UserRequest extends BaseRequest<User> {

    @ApiModelProperty(value = "额外备注", example = "测试备注")
    private String remark;

    @ApiModelProperty(value = "是否批量操作", example = "false")
    private Boolean batch;
}

