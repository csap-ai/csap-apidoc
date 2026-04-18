package ai.csap.apidoc.test;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@ApiModel(value = "授权信息")
public class AuthorizationParam {
    /**
     * 菜单的ID
     */
    @ApiModelProperty(value = "菜单的ID列表", groups = {
            @Group(value = "authorization", request = @Request)
    })
    private List<Long> menus;
    /**
     * 按钮的ID
     */
    @ApiModelProperty(value = "按钮的ID列表", groups = {
            @Group(value = "authorization", request = @Request)
    })
    private List<Long> buttons;
    /**
     * 角色的ID
     */
    @ApiModelProperty(value = "角色ID", groups = {
            @Group(value = "authorization", request = @Request(required = true))
    })
    @NotNull(message = "角色ID未传")
    private int ruleId;
    /**
     * 图片对象
     */
    private List<MultipartFile> files;

}
