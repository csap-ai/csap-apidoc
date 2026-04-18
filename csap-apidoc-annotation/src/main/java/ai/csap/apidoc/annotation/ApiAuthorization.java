package ai.csap.apidoc.annotation;

/**
 * @author yangchengfu
 * @description API 认证类型
 * //    HAWK_AUTHORIZATION;
 * //    DIGEST_AUTH,
 * //    AWS_SIGNATURE,
 * //    NTLM_AUTHORIZATION,
 * //    AKAMAI_EDGEGRID;
 * @dataTime 2019年-12月-27日 17:13:00
 **/
public enum ApiAuthorization {
    //认证类型
    BEARER_TOKEN("Authorization", "Basic YWRtaW46MTIzNDU2"),
    BASIC_AUTH("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOlsib2F1dGgyLXJl"),
    OAUTH_2("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOlsib2F1dGgyLXJl"),
    OTHER("", "");

    private String key;

    private String value;

    ApiAuthorization(String key, String value) {
        this.key = key;
        this.value = value;
    }

    private ApiAuthorization setKey(String key) {
        this.key = key;
        return this;
    }

    private ApiAuthorization setValue(String value) {
        this.value = value;
        return this;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    /**
     * 重新定义
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public static ApiAuthorization resolve(String key, String value) {
        return ApiAuthorization.OTHER.setKey(key).setValue(value);
    }

    @Override
    public String toString() {
        return key + " " + this.value;
    }
}
