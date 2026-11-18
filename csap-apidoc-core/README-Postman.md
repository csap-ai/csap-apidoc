# Postman 导出

`PostmanConverterService`(`ai.csap.apidoc.service.standard`)把 CSAP API 文档转换成 Postman Collection v2.1 JSON,可以直接在 Postman 里导入。

## 最简单的用法:HTTP 接口

引入 `csap-apidoc-boot-starter` 后,应用自带导出接口:

```
GET /csap/openapi/postman
```

返回 Postman Collection JSON。把内容保存为文件,在 Postman 中选择 Import 导入即可。

同一个应用还提供 OpenAPI 3.0 导出:`GET /csap/openapi.json`。

## 在代码中使用

`PostmanConverterService` 由 starter 在 `ApidocAutoConfiguration` 中注册为 Bean,入参是 `CsapDocResponse`:

```java
@Autowired
private PostmanConverterService postmanConverterService;

public String export(CsapDocResponse apiDoc) {
    return postmanConverterService.convertToPostmanCollection(apiDoc);   // JSON 字符串
}
```

另有 `convertToPostmanCollectionMap(CsapDocResponse)`,返回 `Map<String, Object>` 形式的集合。转换失败会抛出 `RuntimeException`。

Postman 模型类(`PostmanCollection`、`PostmanItem`、`PostmanRequest`、`PostmanUrl`、`PostmanHeader`、`PostmanBody`、`PostmanResponse`、`PostmanVariable` 等)是 `PostmanConverterService` 的静态内部类。

## 转换规则

- 集合名称与描述取自 `csap.apidoc.api-info`(`title` / `description`),未配置时为默认值
- 每个 Controller 对应一个 Postman 文件夹,文件夹名取 Controller 的名称(`@Api` 的值),为空时取类简名
- URL 为 Controller 路径加方法路径,自动合并多余的斜杠;集合变量 `baseUrl` 默认值为 `http://localhost:8080`,请在 Postman 中改成实际地址
- 请求头取自接口文档中的请求头配置
- 查询参数取自接口的请求参数:有示例值时使用示例;非必填参数默认置为禁用;没有模型信息时回退到方法的参数名列表
- HTTP 方法取自接口的方法列表,缺省为 GET
- 请求体按接口的请求模型生成

## 测试

```bash
mvn -pl csap-apidoc-core test -Dtest=PostmanConverterServiceUrlTest
```

## 许可证

遵循项目整体许可证(Apache License 2.0)。
