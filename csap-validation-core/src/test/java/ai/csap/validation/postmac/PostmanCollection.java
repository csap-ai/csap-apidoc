package ai.csap.validation.postmac;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

// 顶层Postman Collection
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostmanCollection {
    private Info info;
    private List<Item> item = new ArrayList<>();
    private Auth auth;
    private List<Event> event = new ArrayList<>();
    private List<Variable> variable = new ArrayList<>();
}

// 基本信息
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Info {
    private String _postman_id;
    private String name;
    private String schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
}

// 单个API项
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Item {
    private String name;
    private List<Event> event = new ArrayList<>();
    private Request request;
    private List<Response> response = new ArrayList<>();
}

// 请求信息
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Request {
    private String method;
    private List<Header> header = new ArrayList<>();
    private Url url;
    private Body body;
    private String description;
}

// URL信息
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Url {
    private String raw;
    private String protocol;
    private List<String> host = new ArrayList<>();
    private List<String> path = new ArrayList<>();
    private List<QueryParam> query = new ArrayList<>();
}

// 查询参数
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class QueryParam {
    private String key;
    private String value;
    private String description;
    private boolean disabled = false;
}

// 请求头
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Header {
    private String key;
    private String value;
    private String description;
}

// 请求体
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Body {
    private String mode; // 如 "raw"
    private String raw; // 原始JSON字符串
    private Map<String, Object> options; // 可选配置
}

// 响应信息
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Response {
    private String name;
    private OriginalRequest originalRequest;
    private String status;
    private int code;
    private String _postman_previewlanguage;
    private List<Header> header = new ArrayList<>();
    private List<Object> cookie = new ArrayList<>();
    private String body;
}

// 原始请求（用于响应示例）
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class OriginalRequest {
    private String method;
    private List<Header> header = new ArrayList<>();
    private Url url;
    private Body body;
}

// 事件（如测试脚本）
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Event {
    private String listen; // 如 "test"
    private Script script;
}

// 脚本内容
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Script {
    private String type = "text/javascript";
    private List<String> exec = new ArrayList<>();
}

// 认证信息
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Auth {
    private String type = "apikey";
    private List<Map<String, String>> apikey = new ArrayList<>();
}

// 环境变量
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class Variable {
    private String key;
    private String value;
}
