package ai.csap.validation;

import java.util.*;

/**
 * API和Postman模型类
 */
public class ApiToPostmanConverterModels {

    // Postman集合模型类
    static class PostmanCollection {
        private Info info;
        private List<Item> item;
        private List<Variable> variable;

        public Info getInfo() {
            return info;
        }

        public void setInfo(Info info) {
            this.info = info;
        }

        public List<Item> getItem() {
            if (item == null) item = new ArrayList<>();
            return item;
        }

        public void setItem(List<Item> item) {
            this.item = item;
        }

        public List<Variable> getVariable() {
            if (variable == null) variable = new ArrayList<>();
            return variable;
        }

        public void setVariable(List<Variable> variable) {
            this.variable = variable;
        }
    }

    static class Info {
        private String name;
        private String description;
        private String schema;
        private String _postman_id;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String get_postman_id() {
            return _postman_id;
        }

        public void set_postman_id(String _postman_id) {
            this._postman_id = _postman_id;
        }
    }

    static class Item {
        private String name;
        private String description;
        private List<Item> item;
        private Request request;
        private List<Response> response;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<Item> getItem() {
            if (item == null) item = new ArrayList<>();
            return item;
        }

        public void setItem(List<Item> item) {
            this.item = item;
        }

        public Request getRequest() {
            return request;
        }

        public void setRequest(Request request) {
            this.request = request;
        }

        public List<Response> getResponse() {
            if (response == null) response = new ArrayList<>();
            return response;
        }

        public void setResponse(List<Response> response) {
            this.response = response;
        }
    }

    static class Request {
        private String method;
        private Header header;
        private Body body;
        private Url url;
        private String description;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Header getHeader() {
            return header;
        }

        public void setHeader(Header header) {
            this.header = header;
        }

        public Body getBody() {
            return body;
        }

        public void setBody(Body body) {
            this.body = body;
        }

        public Url getUrl() {
            return url;
        }

        public void setUrl(Url url) {
            this.url = url;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    static class Header {
        private List<HeaderItem> headerItems;

        public List<HeaderItem> getHeaderItems() {
            if (headerItems == null) headerItems = new ArrayList<>();
            return headerItems;
        }

        public void setHeaderItems(List<HeaderItem> headerItems) {
            this.headerItems = headerItems;
        }
    }

    static class HeaderItem {
        private String key;
        private String value;
        private String description;
        private String type;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    static class Body {
        private String mode;
        private String raw;
        private List<FormData> formdata;
        private Options options;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public List<FormData> getFormdata() {
            if (formdata == null) formdata = new ArrayList<>();
            return formdata;
        }

        public void setFormdata(List<FormData> formdata) {
            this.formdata = formdata;
        }

        public Options getOptions() {
            return options;
        }

        public void setOptions(Options options) {
            this.options = options;
        }
    }

    static class FormData {
        private String key;
        private String value;
        private String type;
        private String description;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    static class Options {
        private Raw raw;

        public Raw getRaw() {
            return raw;
        }

        public void setRaw(Raw raw) {
            this.raw = raw;
        }
    }

    static class Raw {
        private String language;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    static class Url {
        private String raw;
        private String protocol;
        private List<String> host;
        private String port;
        private List<String> path;
        private List<QueryParam> query;

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public List<String> getHost() {
            if (host == null) host = new ArrayList<>();
            return host;
        }

        public void setHost(List<String> host) {
            this.host = host;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public List<String> getPath() {
            if (path == null) path = new ArrayList<>();
            return path;
        }

        public void setPath(List<String> path) {
            this.path = path;
        }

        public List<QueryParam> getQuery() {
            if (query == null) query = new ArrayList<>();
            return query;
        }

        public void setQuery(List<QueryParam> query) {
            this.query = query;
        }
    }

    static class QueryParam {
        private String key;
        private String value;
        private String description;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    static class Response {
        private String name;
        private String originalRequest;
        private String status;
        private int code;
        private String _postman_previewlanguage;
        private List<HeaderItem> header;
        private String body;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOriginalRequest() {
            return originalRequest;
        }

        public void setOriginalRequest(String originalRequest) {
            this.originalRequest = originalRequest;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String get_postman_previewlanguage() {
            return _postman_previewlanguage;
        }

        public void set_postman_previewlanguage(String _postman_previewlanguage) {
            this._postman_previewlanguage = _postman_previewlanguage;
        }

        public List<HeaderItem> getHeader() {
            if (header == null) header = new ArrayList<>();
            return header;
        }

        public void setHeader(List<HeaderItem> header) {
            this.header = header;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    static class Variable {
        private String key;
        private String value;
        private String type;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
