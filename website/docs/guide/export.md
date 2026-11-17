# 导出

本页说明 CSAP Apidoc 能把接口文档导出成哪些格式、每种格式怎么导出，以及各自的局限。

## 支持的格式

| 格式 | 在哪里生成 | 入口 |
| --- | --- | --- |
| OpenAPI 3.0（JSON） | 浏览器 | 文档页面右上角「导出文档」 |
| Postman Collection v2.1 | 浏览器 | 文档页面右上角「导出文档」 |
| Markdown | 浏览器 | 文档页面右上角「导出文档」 |
| Postman Collection v2.1 | 服务端 | `GET /csap/openapi/postman` |
| 单个接口的 TypeScript 类型 | 浏览器 | 接口标题旁的「复制为 TypeScript」 |

没有 YAML 格式的 OpenAPI 导出，也没有 JSON Schema 导出。

## 从文档页面导出

打开 `/csap-api.html`，点击右上角「导出文档」，选择格式后浏览器会直接下载文件：

| 菜单项 | 文件名 |
| --- | --- |
| OpenAPI 3.0 | `openapi.json` |
| Postman Collection | `postman_collection.json` |
| Markdown | `api-documentation.md` |

导出过程完全在浏览器里完成：页面遍历左侧接口树里的每个接口，逐个请求 `GET /csap/apidoc/method` 取详情，再拼成目标格式。因此：

- 导出的是**当前服务**的全部接口。配置了[网关聚合](/guide/gateway-aggregation)时，先在「切换服务」里选中要导出的服务。
- 左侧搜索框的过滤**不影响**导出范围，始终导出整棵树。
- 接口很多时需要一些时间，期间页面上会显示「生成中」的提示。某个接口取详情失败时，它会被跳过（错误打印在浏览器控制台），其余接口照常导出。

### 服务地址

OpenAPI 的 `servers` 和 Postman 的 `baseUrl` 变量按下面的规则取值：

- 选中了网关聚合里的某个服务：用该服务的文档地址去掉末尾的 `/csap/apidoc/parent`，例如 `/order-api`。
- 没有选择服务（单服务部署）：固定为 `http://localhost:8080`。

单服务部署导出后，记得把 `servers[0].url` 或 Postman 的 `baseUrl` 改成实际地址。

### OpenAPI 3.0

生成 `openapi: 3.0.0` 的 JSON 文档，所有结构都内联在各个接口里。已知局限：

- `info` 固定为标题 `API Documentation`、版本 `1.0.0`，不读取 `csap.apidoc.api-info`；`tags` 为空，`components.schemas` 为空。
- 每个接口只取第一个 HTTP 方法。`operationId` 由方法名加路径生成，例如 `get_users_{id}`。
- GET / DELETE 接口的所有请求参数都被写成 `in: query` 参数，**包括路径参数**；POST / PUT / PATCH 接口的所有请求参数都被合并成一个 `application/json` 的 `requestBody`，不区分 Query、表单和 Body。
- 接口声明的请求头写成 `in: header` 参数。
- 只生成 `200` 响应，结构来自返回参数。
- 类型按 Java 类型名映射：`String`、`Integer`、`Long`、`Double`、`Float`、`Boolean`、`Date`、`DateTime`、`BigDecimal`、`Object`、`Array`、`List`、`Map`，以及 `List<…>` 这类泛型写法；其他类型一律视为 `string`。
- 不生成 `securitySchemes`。

导入 Swagger UI、Apifox 等工具前，建议至少检查路径参数和 `servers`。

### Postman Collection

生成 Postman Collection v2.1：

- 每个 Controller 一个文件夹，接口名就是接口标题。
- URL 写成 `{{baseUrl}}/路径`，集合变量 `baseUrl` 的值见上文。
- 接口声明的请求头会带上；POST / PUT 没有声明 `Content-Type` 时补一个 `application/json`。
- GET / DELETE 的请求参数变成 Query 参数，非必填的默认禁用；POST / PUT / PATCH 的请求参数生成一个原始 JSON Body，值依次取示例值、默认值，都没有时按类型给空字符串、`0`、`false` 或当前时间。
- 有返回参数时附带一个 `200` 的示例响应。

和 OpenAPI 一样，GET / DELETE 的路径参数也会被当成 Query 参数，导入后需要手动调整。

### Markdown

生成一份按接口树分级的 Markdown 文档：开头是标题 `API Documentation`、生成时间和接口总数，然后每个接口一节，包含请求方法、请求路径、请求头表格、按参数类型（Query / PATH / BODY 等）分组的请求参数表格、返回参数表格。BODY 和 FORM_DATA 参数、以及返回参数会附带 JSON 示例。嵌套字段用 `父.子` 的形式展开，枚举值和校验规则追加在描述列里。

文档里的表头、小标题是固定的中文，切换界面语言不影响导出内容。

### 复制为 TypeScript

这不是整份文档的导出。在单个接口的详情页点击标题旁的「复制为 TypeScript」，会根据该接口的请求参数和返回参数生成 TypeScript 类型定义，可以复制到剪贴板。

## 服务端导出 Postman

`GET /csap/openapi/postman` 直接在服务端把当前应用的文档转换成 Postman Collection v2.1 并返回 JSON 文本：

```bash
curl -o postman_collection.json http://localhost:8080/csap/openapi/postman
```

它和页面导出的区别：

- 只包含当前应用自己的接口，不涉及网关聚合。
- 集合名称和描述取自 `csap.apidoc.api-info` 的 `title`、`description`。
- 跳过 `hidden` 为 `true` 的接口。
- `baseUrl` 变量固定为 `http://localhost:8080`。
- 没有示例值的 Query 参数会按参数名填一组内置样例（例如 `id` 填 `1`、`pageSize` 填 `20`），其余为空。
- 请求 Body 只展开第一层字段，嵌套对象不会展开。
- 有返回参数的接口除 `200` 示例外，还会附带固定内容的 `400`、`404`、`429` 示例响应。

返回值直接是 Postman JSON，**没有** `ApidocResult` 外层包装。详见 [REST 接口](/api/rest)。

## 在 Java 代码里生成

服务端转换由 `ai.csap.apidoc.service.standard.PostmanConverterService` 完成。它目前**没有注册为 Spring Bean**（`CsapDocController` 内部直接 `new` 一个），所以不能 `@Autowired` 注入，需要自己创建：

```java
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.standard.PostmanConverterService;

public class PostmanExporter {

    public String export() {
        // 读取已缓存的全量文档，与 GET /csap/apidoc 返回的 data 相同
        CsapDocResponse doc = ApidocContext.cmd(false, false);
        return new PostmanConverterService().convertToPostmanCollection(doc);
    }
}
```

需要 `Map` 形式时用 `convertToPostmanCollectionMap(doc)`。请在应用启动完成之后调用：文档是在 `ApplicationStartedEvent` 之后异步生成的。

:::warning OpenApiConverterService
`csap-apidoc-core` 里还有一个 `OpenApiConverterService`，但目前既没有把它注册为 Bean，也没有对应的 HTTP 接口；它只遍历 `globalApiList`，对普通扫描出来的接口不会生成任何路径；参数的 `required` 为空时还会空指针。不建议使用；需要 OpenAPI 请从文档页面导出。
:::
