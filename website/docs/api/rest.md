# REST 接口

本页列出 CSAP Apidoc 在应用里注册的全部 HTTP 接口：路径、参数、返回结构和用途。字段名以 `ai.csap.apidoc.model`、`ai.csap.apidoc.response` 包里的类为准。

:::info 适用范围
本页的路径、参数和返回结构对 Java 版和 Go 版基本一致(Go 版同样提供 `/csap/apidoc*`、`/csap/openapi.json`、`/csap/openapi/postman`、`/csap/yaml/*`、`/devtools/*`),字段名以 Java 的 `ai.csap.apidoc.model` 类为准。DevTools 转发代理(`/csap/apidoc/devtools/proxy`)和网关聚合 `resources` 目前只有 **Java 版**。Go 版见 [Go 版](/go/)。
:::

## 总览

| 方法 | 路径 | 用途 | 注册条件 |
| --- | --- | --- | --- |
| GET | `/csap-api.html` | 文档页面 | 引入 `csap-apidoc-ui`（starter 已包含） |
| GET | `/csap/apidoc` | 全量文档 | 启动类上有 `@EnableApidoc` |
| GET | `/csap/apidoc/{tableName}` | 按 Controller / 方法过滤 | 同上 |
| GET | `/csap/apidoc/parent` | 接口树（页面左侧列表用） | 同上 |
| GET | `/csap/apidoc/method` | 单个接口详情 | 同上 |
| GET | `/csap/openapi/postman` | 导出 Postman Collection | 同上 |
| GET | `/csap/openapi.json` | 导出 OpenAPI 3.0（1.2.0 起） | 同上 |
| GET / POST | `/csap/yaml/*` | DevTools：读写 YAML / JSON 文档 | `csap.apidoc.devtool.enabled=true` |
| GET | `/csap/schema.json`、`/csap/schema/method` | 导出 JSON Schema（1.4.0 起） | 始终开启 |
| POST | `/csap/schema/import` | 导入 JSON Schema（1.4.0 起） | `csap.apidoc.schema.import-enabled=true` |
| ANY | `/csap/mock/**`、GET `/csap/mock-status` | Mock 服务（1.4.0 起） | `csap.apidoc.mock.enabled=true` |
| GET / POST / DELETE | `/csap/yaml/fieldTemplates`、POST `/csap/yaml/setStatus`、`/devtools/rules/*` | DevTools：字段模板、接口状态、规则记录（1.4.0 起） | `csap.apidoc.devtool.enabled=true` |
| POST | `/devtools/*` | DevTools：字段查询 | `csap.apidoc.devtool.enabled=true` |
| GET | `/devtools-ui`、`/devtools-ui/**` | DevTools 页面 | `csap.apidoc.devtool.enabled=true` |
| POST | `/csap/apidoc/devtools/proxy` | 调试用服务端转发 | `csap.apidoc.devtools.proxy.enabled=true` |

前六个文档接口由 `ai.csap.apidoc.web.CsapDocController` 提供，路径前缀 `/csap`。

:::warning
这些接口本身不做任何鉴权，会暴露接口结构；DevTools 接口还能写文件。生产环境请关闭 DevTools，并按需用 Spring Security 等手段限制 `/csap/**`、`/csap-api.html` 的访问。
:::

## 外层包装 ApidocResult

除 `/csap/openapi/postman`、`/csap/openapi.json`、DevTools 页面和转发接口外，接口都返回 `ai.csap.apidoc.core.ApidocResult`：

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

:::tip flush 参数
1.1.1 起，`flush=true` 会重新全量扫描，完成后替换缓存再返回；应用启动时的扫描尚未完成时，请求会等待扫描结束，拿到完整文档。1.1.0 中缓存存在时 `flush` 不起作用。重新扫描不会加载新编译的代码，代码改动后仍需重启应用。
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

:::tip 1.1.0 的缓存问题
1.1.0 中带 `key` 调用时，过滤直接作用在缓存的 Controller 对象上，调用一次之后，全量文档里这个 Controller 只剩下 `key` 对应的接口。1.1.1 改为在副本上过滤，缓存不受影响。还在用 1.1.0 时，请不要在共享环境里带 `key` 调用这个接口。
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

## GET /csap/openapi.json

1.2.0 起提供。把全量文档转换成 OpenAPI 3.0（`openapi: 3.0.3`），**直接返回 JSON，没有 `ApidocResult` 包装**，`Content-Type` 为 `application/json`。

```bash
curl -o openapi.json http://localhost:8080/csap/openapi.json
```

生成规则见[导出](/guide/export#服务端导出-openapi)。

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
| POST | `/devtools/hiddenMethod` | Body：`MethodModel` | 目前直接返回 `true`，没有实际作用 |

`write`、`writeSelect`、`writeFieldMethod` 要求 `@EnableApidoc` 的 `type` 是 `YAML` 或 `JSON`，否则返回错误「当前配置不支持yaml或者/json形式的文档」。

`GET /devtools-ui` 和 `/devtools-ui/**` 转发到 DevTools 页面 `/csap-api-devtools.html`。

## 1.4.0 新增接口

下表的"Java / Go"一列说明两种语言是否都有；Go 的开关是 `apidoc.Options` 里的对应选项，路径相同。

| 方法 | 路径 | 默认 | Java / Go | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/csap/schema.json` | 开启 | 都有 | 全部接口的 JSON Schema 合集，裸 JSON（不套 `ApidocResult`）；`?download=true` 作为文件下载 |
| GET | `/csap/schema/method` | 开启 | 都有 | 参数 `key`（接口 key）；单个接口的请求、返回 Schema，裸 JSON；key 不存在返回 404 |
| POST | `/csap/schema/import` | **关闭** | 都有 | Body：`key`、`kind`、`section`、`schema`、`apply`；默认只预览，`apply=true` 才写入。开启：Java `csap.apidoc.schema.import-enabled=true`，Go `Options.Schema.ImportEnabled`；关闭时 404 |
| ANY | `/csap/mock/{原接口路径}` | **关闭** | 都有 | 按文档里的返回结构生成可复现的模拟数据，任意 HTTP 方法；路径不存在 404，方法不对 405。开启：Java `csap.apidoc.mock.enabled=true`，Go `Options.Mock.Enabled`；关闭时 404 |
| GET | `/csap/mock-status` | **关闭**（随 Mock） | 都有 | Mock 开启时返回 `{"enabled":true,"prefix":"/csap/mock","seed":"csap"}`（有 `ApidocResult` 包装），文档页用它探测 |
| GET | `/csap/yaml/fieldTemplates` | 随 DevTools | 都有 | 内置 + 项目字段模板，标注来源 |
| POST | `/csap/yaml/fieldTemplates` | 随 DevTools | 都有 | Body：模板（`name`、`title`、`description`、`example`、`required`、`validate`）；保存项目模板 |
| DELETE | `/csap/yaml/fieldTemplates/{name}` | 随 DevTools | 都有 | 删除项目模板，内置模板只读 |
| POST | `/csap/yaml/setStatus` | 随 DevTools | 都有 | Body：`className`、`methodName`、`status`、`statusNote`；`status` 传 `null` 清除覆盖 |
| GET | `/devtools/rules/status` | 随 DevTools | 都有 | 是否是数据库 / Redis 模式（`supported`）、是否只读（`readOnly`）、当前 `paramType` |
| GET | `/devtools/rules/history` | 随 DevTools | 都有 | 参数 `ruleKey`（可选）、`limit`（默认 100，范围 1~1000）；修改记录，最新的在前 |
| POST | `/devtools/rules/rollback` | 随 DevTools | 都有 | Body：`{"changeId": 记录ID}`；回滚到该条修改之前的内容，回滚本身产生新记录（修改人 `devtools-rollback`）；只读模式返回 `code = 1007` |

- "随 DevTools"：Java 需要 `csap.apidoc.devtool.enabled=true`，Go 需要用 `devtools.New` 创建并配置鉴权，与其他 DevTools 接口一样；
- `/csap/yaml/fieldTemplates`、`setStatus` 在不可写（只读、打包运行不可写、`ANNOTATION` 模式）时返回 `1007`；
- `devtools/rules/*` 只对数据库模式和 Redis 模式有效，其他模式 `status` 返回 `supported: false`；
- 各接口的用法见 [Mock](/guide/mock)、[JSON Schema](/guide/json-schema)、[字段库](/guide/field-library)、[接口状态](/guide/api-status)、[数据库模式](/guide/database-mode#devtools规则记录页)。

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
这个接口可以让服务端访问名单内的任意地址，本身不做调用方鉴权。只在内网或开发环境开启，并用 Spring Security 等保护它。1.1.1 起，文档页面「设置」里的代理地址填以 `/csap/apidoc/devtools/proxy` 结尾的地址时，在线调试会通过这个接口转发；1.1.0 的文档页面不会调用它。
:::
