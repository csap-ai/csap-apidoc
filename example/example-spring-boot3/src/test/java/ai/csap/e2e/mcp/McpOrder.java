package ai.csap.e2e.mcp;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

@ApiModel("订单")
public class McpOrder {
    @ApiModelProperty("ID")
    private Long id;
    @ApiModelProperty("订单号")
    private String no;
    @ApiModelProperty("内部字段")
    private String secret = "internal";
    @ApiModelProperty("租户")
    private String tenant;
    @ApiModelProperty("语言")
    private String lang;
    @ApiModelProperty("支付密码")
    private String password;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
