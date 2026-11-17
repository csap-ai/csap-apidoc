# REST 接口

本页列出 CSAP Apidoc 在应用里注册的全部 HTTP 接口：路径、参数、返回结构和用途。字段名以 `ai.csap.apidoc.model`、`ai.csap.apidoc.response` 包里的类为准。

## 总览

| 方法 | 路径 | 用途 | 注册条件 |
| --- | --- | --- | --- |
| GET | `/csap-api.html` | 文档页面 | 引入 `csap-apidoc-ui`（starter 已包含） |
| GET | `/csap/apidoc` | 全量文档 | 启动类上有 `@EnableApidoc` |
| GET | `/csap/apidoc/{tableName}` | 按 Controller / 方法过滤 | 同上 |
| GET | `/csap/apidoc/parent` | 接口树（页面左侧列表用） | 同上 |
| GET | `/csap/apidoc/method` | 单个接口详情 | 同上 |
| GET | `/csap/openapi/postman` | 导出 Postman Collection | 同上 |
| GET / POST | `/csap/yaml/*` | DevTools：读写 YAML / JSON 文档 | `csap.apidoc.devtool.enabled=true` |
| POST | `/devtools/*` | DevTools：字段查询、隐藏接口 | `csap.apidoc.devtool.enabled=true` |
| GET | `/devtools-ui`、`/devtools-ui/**` | DevTools 页面 | `csap.apidoc.devtool.enabled=true` |
| POST | `/csap/apidoc/devtools/proxy` | 调试用服务端转发 | `csap.apidoc.devtools.proxy.enabled=true` |

前五个文档接口由 `ai.csap.apidoc.web.CsapDocController` 提供，路径前缀 `/csap`。

:::warning
这些接口本身不做任何鉴权，会暴露接口结构；DevTools 接口还能写文件。生产环境请关闭 DevTools，并按需用 Spring Security 等手段限制 `/csap/**`、`/csap-api.html` 的访问。
:::

## 外层包装 ApidocResult

除 `/csap/openapi/postman`、DevTools 页面和转发接口外，接口都返回 `ai.csap.apidoc.core.ApidocResult`：

```json
{
  "code": "0",
  "message": "成功",
  "data": { },
  "language": "ZH",
  "time": 1763337600000,
  "success": true
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | String | `"0"` 表示成功 |
| `message` | String | 成功时为 `成功` |
| `data` | 泛型 | 实际数据，各接口不同 |
| `language` | String | 默认 `ZH` |
| `time` | Long | 服务端生成响应时的毫秒时间戳 |
| `success` | boolean | 由 `isSuccess()` 序列化而来，等价于 `code == "0"` |

接口内部抛出的异常（例如 `/csap/apidoc/method` 找不到接口）**不会**被包装成 `ApidocResult`，按应用自身的错误处理返回，默认是 HTTP 500。

值为 `null` 的字段是否出现在 JSON 里取决于应用的 Jackson 配置（如 `spring.jackson.default-property-inclusion`）。下文的示例省略了大部分字段。

## GET /csap/apidoc

返回全量文档。

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `flush` | query | Boolean | 否 | 默认 `false`，见下面的说明 |

:::warning flush 参数目前不起作用
文档在应用启动后生成一次并缓存。`flush=true` 只在缓存为空时才会被使用，而应用启动完成后缓存总是已经存在，所以带上 `flush=true` 也只会返回缓存。想看到代码改动后的文档，需要重启应用，或者开启 `csap.apidoc.devtool.enabled` 后依赖 `/csap/apidoc/method` 的按类重新扫描（见[架构](/guide/architecture)）。
:::

`data` 的类型是 `ai.csap.apidoc.model.CsapDocResponse`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `apiInfo` | `CsapApiInfo` | 文档基本信息，来自 `csap.apidoc.api-info` |
| `resources` | `List<CsapDocResource>` | 网关聚合的服务列表，来自 `csap.apidoc.resources` |
| `apiList` | `List<CsapDocModelController>` | 所有 Controller 及其接口 |
| `docEnumList` | `List<CsapDocEnum>` | 标注了 `@ApiResponseCode` 的响应码枚举 |
| `enumList` | `Map<String, List<Map>>` | 文件型文档策略读出的枚举 |
| `globalApiList` | `List<CsapDocModelController>` | 全局接口列表 |
| `globalRequestParam` | `List<CsapDocModel>` | 全局请求参数 |
| `groups` | `Set<String>` | 出现过的分组，至少包含 `default` |
| `versions` | `Set<String>` | 出现过的版本，至少包含 `default` |

`apiList` 里每个 Controller（`CsapDocModelController`）的主要字段：

| 字段 | 说明 |
| --- | --- |
| `name` | Controller 全限定类名 |
| `simpleName` | 简单类名 |
| `value` / `description` | `@Api` 的 `value`、`description` |
| `path` | 类上 `@RequestMapping` 的路径数组 |
| `tags`、`group`、`version`、`status`、`protocols`、`position`、`hidden` | 对应 `@Api` 的属性 |
| `methodList` | 接口列表，元素类型 `CsapDocMethod` |

每个接口（`CsapDocMethod`）的主要字段：

| 字段 | 说明 |
| --- | --- |
| `key` | 接口标识，格式为 `Controller全限定名.方法名` |
| `name` | Java 方法名 |
| `value` / `description` | `@ApiOperation` 的 `value`、`description` |
| `methods` | HTTP 方法列表，如 `["GET"]` |
| `paths` | 方法上映射的路径数组 |
| `apiPath` | 所属 Controller 的路径数组 |
| `paramType` | 请求参数类型：`PATH`、`QUERY`、`BODY`、`FORM_DATA`、`HEADER`、`FORM`、`DEFAULT` |
| `paramTypes` | 本接口出现过的所有参数类型 |
| `request` / `response` | 请求、返回参数模型，元素类型 `CsapDocModel` |
| `methodHeaders` | 请求头，元素类型 `CsapDocMethodHeaders`（`key`、`value`、`required`、`description`、`example`、`position`、`hidden`） |
| `paramNames` | 方法参数名 |
| `tags`、`group`、`version`、`status`、`hidden` | 对应注解属性 |
| `globalHeaderHints` | 来自 `@DocGlobalHeader`，元素为 `CsapDocGlobalHeaderHint`（`name`、`description`、`example`、`required`） |
| `authHint` | 来自 `@DocAuth`，类型 `CsapDocAuthHint`（`scheme`、`description`、`in`、`name`），空字段不输出 |

`globalHeaderHints` 和 `authHint` 按「方法 > 类 > 包」的优先级合并，只是给调试界面用的提示，不影响接口的实际鉴权。

参数模型 `CsapDocModel` 的 `parameters` 是字段列表（`CsapDocParameter`），常用字段有 `name`、`value`、`description`、`dataType`、`required`、`example`、`defaultValue`、`paramType`、`modelType`（`OBJECT`、`ARRAY`、`T_OBJECT`、`BASE_DATA`）、`extendDescr`（枚举说明）、`validate`（校验规则，元素含 `type`、`code`、`message`、`pattern`、`descr`、`level`）。嵌套对象放在 `children` 里，它本身又是一个 `CsapDocModel`。

```bash
curl http://localhost:8080/csap/apidoc
```

```json
{
  "code": "0",
  "message": "成功",
  "data": {
    "apiInfo": { "title": "Api Documentation", "version": "1.0" },
    "apiList": [
      {
        "name": "com.example.UserController",
        "simpleName": "UserController",
        "value": "用户管理",
        "path": ["/users"],
        "methodList": [
          {
            "key": "com.example.UserController.getUser",
            "name": "getUser",
            "value": "查询用户",
            "methods": ["GET"],
            "paths": ["/{id}"],
            "apiPath": ["/users"],
            "paramType": "DEFAULT",
            "request": [ ],
            "response": [ ],
            "methodHeaders": [],
            "authHint": { "scheme": "bearer" }
          }
        ]
      }
    ],
    "groups": ["default"],
    "versions": ["default"]
  }
}
```

## GET `/csap/apidoc/{tableName}`

按 Controller 和方法过滤文档，`data` 同样是 `CsapDocResponse`。

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `tableName` | path | String | 是 | Controller 全限定类名，如 `com.example.UserController` |
| `key` | query | String | 否 | 接口 `key`，如 `com.example.UserController.getUser` |

实际行为：

- **带 `key`**：返回的 `apiList` 只包含名称等于 `tableName` 的 Controller，且它的 `methodList` 只包含 `key` 匹配的接口。
- **不带 `key`**：不做任何过滤，返回的就是全量文档，和 `/csap/apidoc` 相同。
- 开启了 `csap.apidoc.devtool.enabled` 时，会先重新扫描 `tableName` 对应的类，再返回。

```bash
curl "http://localhost:8080/csap/apidoc/com.example.UserController?key=com.example.UserController.getUser"
```

:::danger 已知问题：带 key 调用会破坏缓存
带 `key` 调用时，过滤是直接在缓存的 Controller 对象上执行的（`CsapDocController.apiList` 里在 `peek` 中调用 `setMethodList`），外层虽然复制了一份 `CsapDocResponse`，但只是浅拷贝，Controller 对象与缓存共享。结果是：调用一次之后，缓存里这个 Controller 只剩下 `key` 对应的那一个接口，之后 `/csap/apidoc`、`/csap/apidoc/parent` 和文档页面里该 Controller 的其他接口都会消失。

未开启 DevTools 时只能重启应用恢复。开启 `csap.apidoc.devtool.enabled` 时，再次请求该 Controller（例如在页面里点开它的任意接口，触发 `/csap/apidoc/method` 的重新扫描）通常可以恢复，但不要依赖这一点。

在问题修复之前，请不要在共享环境里带 `key` 调用这个接口；需要单个接口的信息时请用 `/csap/apidoc/method`。
:::

## GET /csap/apidoc/parent

返回精简的接口树，文档页面左侧列表和网关聚合都使用它。`data` 类型是 `ai.csap.apidoc.response.CsapDocParentResponse`：

| 字段 | 说明 |
| --- | --- |
| `apiInfo` | 同 `/csap/apidoc` |
| `resources` | 网关聚合的服务列表，元素为 `CsapDocResource`（`name`、`url`、`version`） |
| `apiList` | 接口树，元素为 `CsapDocParentResponseModel` |
| `enumList` | 同 `/csap/apidoc` 的 `enumList` |
| `groups`、`versions` | 同 `/csap/apidoc` |
| `globalApiList`、`globalRequestParam` | 本接口不填充 |

`CsapDocParentResponseModel`：

| 字段 | 说明 |
| --- | --- |
| `title` | Controller 的 `@Api` `value` |
| `key` | Controller 全限定类名 |
| `children` | 接口列表，元素为 `CsapDocMethodModel` |

`CsapDocMethodModel`：

| 字段 | 说明 |
| --- | --- |
| `title` | 接口的 `@ApiOperation` `value` |
| `key` | 接口 `key` |
| `isLeaf` | 恒为 `true` |
| `method` | 第一个 HTTP 方法 |
| `path` | 完整路径：Controller 第一个路径 + 方法第一个路径 |
| `type` | Controller 的 `protocols`：`HTTP`、`HTTPS`、`WS`、`WSS` |

```json
{
  "code": "0",
  "message": "成功",
  "data": {
    "apiInfo": { "title": "Api Documentation", "version": "1.0" },
    "resources": [
      { "name": "订单服务", "url": "/order-api/csap/apidoc/parent", "version": "v1" }
    ],
    "apiList": [
      {
        "title": "用户管理",
        "key": "com.example.UserController",
        "children": [
          {
            "title": "查询用户",
            "key": "com.example.UserController.getUser",
            "isLeaf": true,
            "method": "GET",
            "path": "/users/{id}",
            "type": "HTTP"
          }
        ]
      }
    ],
    "groups": ["default"],
    "versions": ["default"]
  }
}
```

## GET /csap/apidoc/method

返回单个接口的详情，文档页面右侧、在线调试和导出都使用它。

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | query | String | 是 | Controller 全限定类名 |
| `key` | query | String | 是 | 接口 `key` |

开启了 `csap.apidoc.devtool.enabled` 时，每次调用都会重新扫描 `name` 对应的类并更新缓存，所以开发时改了注解，点开接口就能看到新内容。找不到 Controller 或接口时抛出 `NoSuchElementException`，按应用的错误处理返回。

`data` 类型是 `ai.csap.apidoc.response.CsapDocMethodResponse`：

| 字段 | 说明 |
| --- | --- |
| `title` | 接口名称 |
| `description` | 接口描述 |
| `method` | 第一个 HTTP 方法 |
| `methods` | 全部 HTTP 方法 |
| `patch` | 完整路径（字段名就是 `patch`） |
| `paramType` | 请求参数类型 |
| `tags` | 标签 |
| `headers` | 请求头，元素为 `MethodHeadersResponseModel`（`key`、`value`、`required`、`description`、`example`） |
| `request` | 请求参数，元素为 `MethodRequestModel` |
| `response` | 返回参数，元素同样是 `MethodRequestModel` |

`MethodRequestModel` 是把参数模型拍平后的字段树：`key`、`name`、`value`、`description`、`dataType`、`required`、`defaultValue`、`example`、`paramType`（未声明时为 `DEFAULT`）、`modelType`、`validate`、`extendDescr`、`children`。

这个接口不返回 `globalHeaderHints` 和 `authHint`，需要时请从 `/csap/apidoc` 取。

```bash
curl "http://localhost:8080/csap/apidoc/method?name=com.example.UserController&key=com.example.UserController.getUser"
```

```json
{
  "code": "0",
  "message": "成功",
  "data": {
    "title": "查询用户",
    "method": "GET",
    "methods": ["GET"],
    "patch": "/users/{id}",
    "paramType": "DEFAULT",
    "headers": [],
    "request": [
      {
        "name": "id",
        "value": "用户ID",
        "dataType": "Long",
        "required": true,
        "paramType": "PATH",
        "children": []
      }
    ],
    "response": [ ]
  }
}
```

## GET /csap/openapi/postman

把全量文档转换成 Postman Collection v2.1，**直接返回 JSON 字符串，没有 `ApidocResult` 包装**。

```bash
curl -o postman_collection.json http://localhost:8080/csap/openapi/postman
```

生成规则和局限见[导出](/guide/export)。

## DevTools 接口

以下接口仅在 `csap.apidoc.devtool.enabled=true` 时注册，供 DevTools 页面使用，用法见 [DevTools](/guide/devtools)。返回值都是 `ApidocResult`。

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/csap/yaml/write` | Body：`StandardProperties` | 写入文档文件 |
| POST | `/csap/yaml/writeSelect` | Body：类名集合，空集合表示全部 | 把选中的 Controller 写入文档文件 |
| POST | `/csap/yaml/writeFieldMethod` | Body：`Map<类名, Map<方法名, ParamGroupMethodProperty>>` | 只写入字段和校验信息 |
| GET | `/csap/yaml/scannerApi` | `className` | 扫描类下的接口 |
| GET | `/csap/yaml/getAllMethod` | `className` | 类下的所有方法 |
| POST | `/csap/yaml/addRequestParam` | Body：`MethodModel` | 添加 / 修改请求参数 |
| POST | `/csap/yaml/addResponseParam` | Body：`MethodModel` | 添加 / 修改返回参数 |
| GET | `/csap/yaml/getMethodValidateFields` | `className`、`methodName`、`fieldName` | 字段的校验配置 |
| GET | `/csap/yaml/validatePatternTypes` | 无 | 可用的正则校验类型 |
| POST | `/devtools/getFields` | Body：`GetFieldExcled` | 取类的字段，可按方法参数 / 返回值解析泛型 |
| POST | `/devtools/hiddenMethod` | Body：`MethodModel` | 隐藏接口 |

`write`、`writeSelect`、`writeFieldMethod` 要求 `@EnableApidoc` 的 `type` 是 `YAML` 或 `JSON`，否则返回错误「当前配置不支持yaml或者/json形式的文档」。

`GET /devtools-ui` 和 `/devtools-ui/**` 转发到 DevTools 页面 `/csap-api-devtools.html`。

## POST /csap/apidoc/devtools/proxy

可选的服务端转发接口，用来绕开浏览器跨域限制。默认不注册，需要设置 `csap.apidoc.devtools.proxy.enabled=true`（注意前缀是 `devtools`，和上面的 `devtool` 不同），并配置允许转发的主机名单 `csap.apidoc.devtools.proxy.allowed-hosts`。

请求体（`Content-Type: application/json`）：

```json
{
  "method": "GET",
  "url": "http://localhost:8080/users/1",
  "headers": { "Authorization": "Bearer xxx" },
  "body": null
}
```

成功时原样返回上游的状态码、响应头（去掉 `Set-Cookie` 等逐跳头）和响应体，并加上 `X-Csap-Apidoc-Proxy` 响应头；响应体超过上限被截断时还会带 `X-Csap-Apidoc-Proxy-Truncated`。失败时返回 JSON：

```json
{ "error": "host_not_allowed", "message": "Host is not on csap.apidoc.devtools.proxy.allowed-hosts", "url": "http://…" }
```

`error` 的取值有 `invalid_request`、`invalid_method`、`invalid_url`、`host_not_allowed`、`upstream_unreachable`（HTTP 502）和 `body_too_large`（HTTP 415）。

:::warning
这个接口可以让服务端访问名单内的任意地址，本身不做调用方鉴权。只在内网或开发环境开启，并用 Spring Security 等保护它。1.1.0 的文档页面不会调用这个接口。
:::
