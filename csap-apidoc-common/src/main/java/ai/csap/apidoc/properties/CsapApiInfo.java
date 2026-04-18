package ai.csap.apidoc.properties;

import ai.csap.apidoc.annotation.ApiAuthorization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description csap doc文档
 * @dataTime 2019年-12月-26日 21:19:00
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsapApiInfo {
    /**
     * 版本
     */
    private String version;
    /**
     * 标题
     */
    private String title;
    /**
     * 描述
     */
    private String description;
    /**
     * 服务的URL
     */
    private String serviceUrl;

    private String license = "Apache 2.0";

    private String licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0";
    /**
     * 联系人
     */
    private Contact contact;
    /**
     * 认证类型
     */
    private ApiAuthorization authorizationType = ApiAuthorization.OAUTH_2;

    public CsapApiInfo(
            String title,
            String description,
            String version,
            String serviceUrl,
            Contact contact,
            String license,
            String licenseUrl) {
        this.title = title;
        this.description = description;
        this.version = version;
        this.serviceUrl = serviceUrl;
        this.contact = contact;
        this.license = license;
        this.licenseUrl = licenseUrl;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Contact {
        /**
         * 联系人名称
         */
        private String name;
        /**
         * url
         */
        private String url;
        /**
         * 联系邮箱
         */
        private String email;

    }
}
