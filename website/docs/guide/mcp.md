# MCP(让 AI 调用你的接口)

1.3.0 起,`csap-apidoc-boot-starter` 带有内嵌的 MCP 端点(模块 `csap-apidoc-mcp`):服务自身提供 `/csap/mcp`,文档里的每个接口自动成为一个 [MCP](https://modelcontextprotocol.io) 工具,Claude Code、Cursor 等 AI 客户端连上后就能直接查询、调用你的接口,不用另写一层。**默认关闭。**

- 工具名、说明、参数 JSON Schema 都来自文档;
- 默认只暴露 GET,写操作需要在 DevTools 里逐个打开;
- 调用在服务内部转发到接口,经过所有 Filter(包括 Spring Security),返回字段按规则过滤;
- 鉴权:MCP 专用令牌,或透传调用方的请求头;
- 每次调用记审计日志,参数中的密码、令牌等自动打码。

Spring Boot 2 和 3 使用同一个 jar,不需要额外依赖。

## 开启

```yaml
csap:
  apidoc:
    mcp:
      enabled: true
      auth:
        tokens:
          - name: my-ide                    # 名称会出现在审计日志里
            value: ${CSAP_MCP_TOKEN:}       # 令牌值请放在环境变量里
```

启动日志出现 `csap-apidoc MCP: 端点已开启 POST /csap/mcp(鉴权方式 token)` 即可。未开启时 `/csap/mcp` 返回 404。

:::warning
默认鉴权方式是 `token`。没有配置任何令牌时,所有请求都会被拒绝(401),启动时会打 WARN。
:::

开发时建议同时开启 DevTools(`csap.apidoc.devtool.enabled: true`),在 `/devtools-ui/mcp` 管理暴露范围、鉴权方式和查看审计日志。

## 在 AI 客户端里连接

### Claude Code

```bash
export CSAP_MCP_TOKEN=...   # 与服务端配置的令牌一致
claude mcp add --transport http csap-apidoc http://localhost:8080/csap/mcp \
  --header "Authorization: Bearer $CSAP_MCP_TOKEN"
```

然后在对话里直接说"查一下 ID 为 1 的商品",Claude 会调用 `Product_getProduct` 之类的工具。`/mcp` 命令可以查看连接状态和工具列表。

### Cursor

在项目的 `.cursor/mcp.json`(或全局 `~/.cursor/mcp.json`)中:

```json
{
  "mcpServers": {
    "csap-apidoc": {
      "url": "http://localhost:8080/csap/mcp",
      "headers": { "Authorization": "Bearer ${env:CSAP_MCP_TOKEN}" }
    }
  }
}
```

### 其他客户端

任何支持 Streamable HTTP 传输的 MCP 客户端都可以连接,地址是 `http(s)://<服务地址>/csap/mcp`,请求头带 `Authorization: Bearer <令牌>`。只支持 stdio 的客户端可以用 `mcp-remote` 一类的桥接工具。

DevTools MCP 页面的"接入"标签会按当前服务地址生成上面的配置。

## 工具是怎么生成的

| 字段 | 来源 |
|---|---|
| 名称 | `{Controller 简名去掉 Controller}_{方法名}`,如 `ProductController.getProduct` → `Product_getProduct`。只含字母、数字、`_`、`-`,最长 64,重名时加 `_2`。可在 DevTools 改写 |
| 标题 | `@ApiOperation` 的 value |
| 说明 | 标题 + 描述 + 最后一行 `HTTP GET /api/products/getProduct/{id}`。可在 DevTools 改写,让 AI 更好理解 |
| 参数(inputSchema) | 见下表;类型、必填、说明、示例、校验规则都来自文档 |
| 提示(annotations) | GET 为 `readOnlyHint`;DELETE 为 `destructiveHint` |

参数的位置以 Spring 的方法参数注解为准:

| 方法参数 | 工具参数 |
|---|---|
| `@PathVariable` | 顶层属性,必填 |
| `@RequestParam`、不带注解的简单类型 | 顶层属性 |
| `@RequestHeader` | 顶层属性(名称就是请求头名) |
| `@RequestBody` | `body` 属性,对象字段按文档生成 |
| 不带注解的对象(查询对象) | 字段展开为顶层属性(嵌套字段用 `a.b`) |
| `HttpServletRequest`、`Principal`、`BindingResult` 等 | 忽略 |
| `MultipartFile` 等文件上传 | 不支持,接口不会成为工具 |

文档中隐藏的接口不会成为工具。

## 调用与返回

`tools/call` 在服务内部以一次本机 HTTP 请求(`http://127.0.0.1:{端口}{context-path}`)调用对应接口:

- 请求经过所有 Servlet Filter,Spring Security、租户、链路追踪等和真实调用一致;
- 开启了 [返回字段过滤](/guide/runtime-filter) 时,结果已按规则过滤,和文档显示的字段一致;
- 返回接口的原始响应体(文本),超过 `max-result-chars` 截断;
- 状态码不是 2xx 时 `isError: true`,文本以 `HTTP 状态码` 开头,AI 可以据此修正参数重试。

服务使用 HTTPS、只监听特定网卡或者端口不能直连时,用 `csap.apidoc.mcp.base-url` 指定回环地址。

## 暴露范围

- 默认只暴露 GET 接口(`expose-get-by-default: true`),POST / PUT / PATCH / DELETE 都不暴露。
- 在 DevTools 的 MCP 页面逐个勾选需要开放的写操作,保存时会再次确认。
- 也可以取消勾选某个 GET 接口,不让 AI 看到。

设置保存在规则文件里,和字段规则一样可以提交到代码仓库、做版本管理:

| 字段规则策略(`@EnableApidoc(paramType)`) | MCP 设置保存位置 |
|---|---|
| `YAML`、`ANNOTATION`、`JSON` | `src/main/resources/{path}/mcp.yaml`(`path` 默认 `csap-api-doc`) |
| `SQL_LITE` | 同一个 SQLite 库的 `mcp_setting`、`mcp_tool` 表 |

1.4.0 的数据库模式计划也支持把 MCP 设置存进同一个库(规划中,具体表和配置以发布时为准)。

```yaml
# csap-api-doc/mcp.yaml
auth:
  mode: token
  forwardHeaders: [Authorization, Cookie]
tools:
  com.example.ProductController.getProduct:
    name: get_product
    description: 按商品 ID 查询商品详情
  com.example.ProductController.createProduct:
    enabled: true
  com.example.ProductController.listProducts:
    enabled: false
```

和默认值相同的项不写入文件。打成 jar 运行时 classpath 里的规则文件是只读的,DevTools 的修改只在内存生效(页面会提示);生产环境需要在线修改并持久化时,用 `csap.apidoc.mcp.config-file` 指定一个可写的文件。

## 鉴权

| 方式 | MCP 端点本身 | 转发给接口的请求头 |
|---|---|---|
| `token`(默认) | 需要 `Authorization: Bearer <令牌>`,令牌在 `csap.apidoc.mcp.auth.tokens` 配置 | `forward-headers` 中除 `Authorization` 以外的头,加上 `upstream-headers` |
| `passthrough` | 不校验 | `forward-headers`(默认 `Authorization`、`Cookie`)原样转发,加上 `upstream-headers` |

- 令牌只在配置文件 / 环境变量里,不进规则文件,DevTools 也不显示令牌值,只显示名称。
- 比较令牌时使用常量时间比较;审计日志只记令牌名称。
- 接口本身需要登录(例如 Spring Security)而 MCP 用 token 模式时,用 `upstream-headers` 给回环调用配置一个服务账号凭据,或者改用 passthrough 模式,让 AI 客户端带上用户自己的凭据。
- 鉴权方式和转发头可以在 DevTools 里切换,保存到规则文件,优先于配置文件。

### 和 Spring Security 一起使用

`/csap/mcp` 是 POST 接口,Spring Security 默认的 CSRF 保护会拦截它。放行并关闭 CSRF:

```java
http.authorizeHttpRequests(a -> a.requestMatchers("/csap/mcp").permitAll() /* token 模式由 MCP 自己校验 */)
    .csrf(c -> c.ignoringRequestMatchers("/csap/mcp"));
```

passthrough 模式下 `/csap/mcp` 本身也可以放行,接口的访问控制照常由 Spring Security 对回环请求执行。

## 审计日志

每次工具调用和每次鉴权失败记一条:时间、调用方(令牌名称;passthrough 模式为来源 IP)、工具、实际请求(方法和路径)、参数摘要、状态码、结果摘要、耗时。

- 参数和结果按 JSON 处理,字段名包含 `password`、`pwd`、`secret`、`token`、`authorization`、`apikey`、`credential`、`cookie`、`session`、`privatekey` 等的值替换为 `***`;用 `audit.mask-fields` 追加字段名。摘要截断到 `audit.max-summary-chars`。请求头从不记录。
- 默认只保存在内存(最近 `audit.capacity` 条);`audit.persist: true` 时同时写入 SQLite(`audit.sqlite-file`)。
- 在 DevTools 的 MCP 页面"审计日志"标签查看,或调用 `GET /devtools/mcp/audit?limit=&tool=&caller=`。

## 配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `csap.apidoc.mcp.enabled` | `false` | 总开关 |
| `csap.apidoc.mcp.path` | `/csap/mcp` | 端点路径 |
| `csap.apidoc.mcp.base-url` | 空 | 回环调用的基础地址,默认 `http://127.0.0.1:{端口}{context-path}` |
| `csap.apidoc.mcp.call-timeout` | `30s` | 单次工具调用超时 |
| `csap.apidoc.mcp.max-result-chars` | `50000` | 工具结果最大长度 |
| `csap.apidoc.mcp.expose-get-by-default` | `true` | 未单独设置的 GET 接口是否暴露 |
| `csap.apidoc.mcp.allowed-origins` | 空 | 允许的浏览器来源;带 `Origin` 头且不在列表中的请求返回 403 |
| `csap.apidoc.mcp.instructions` | 空 | 发给 AI 的服务说明,默认用文档标题和描述 |
| `csap.apidoc.mcp.store` | `auto` | 设置存储:`auto`(跟随字段规则策略)、`yaml`、`sqlite`、`memory` |
| `csap.apidoc.mcp.config-file` | 空 | 设置文件的显式路径(`.yaml` 或 `.db`) |
| `csap.apidoc.mcp.auth.mode` | `token` | `token` 或 `passthrough`(规则文件中的设置优先) |
| `csap.apidoc.mcp.auth.tokens[n].name` / `.value` | 空 | 令牌名称和值 |
| `csap.apidoc.mcp.auth.forward-headers` | `[Authorization, Cookie]` | 转发给接口的请求头 |
| `csap.apidoc.mcp.auth.upstream-headers` | 空 | 每次回环调用附加的请求头 |
| `csap.apidoc.mcp.audit.enabled` | `true` | 是否记录审计 |
| `csap.apidoc.mcp.audit.capacity` | `500` | 内存保留条数 |
| `csap.apidoc.mcp.audit.persist` | `false` | 是否写入 SQLite |
| `csap.apidoc.mcp.audit.sqlite-file` | `csap-mcp-audit.db` | 审计 SQLite 文件(相对工作目录) |
| `csap.apidoc.mcp.audit.max-rows` | `10000` | SQLite 最多保留条数 |
| `csap.apidoc.mcp.audit.max-summary-chars` | `512` | 参数 / 结果摘要最大长度 |
| `csap.apidoc.mcp.audit.mask-fields` | 空 | 追加的打码字段名 |

## 协议

- 传输:MCP Streamable HTTP,只接受 POST,响应为 `application/json`(不开 SSE 流);GET / DELETE 返回 405。
- 版本:`2026-07-28`(无状态,请求 `_meta` 带版本,校验 `MCP-Protocol-Version`、`Mcp-Method`、`Mcp-Name` 头,支持 `server/discover`),以及 `2025-11-25`、`2025-06-18`、`2025-03-26`(`initialize` 握手,客户端请求的版本支持就原样返回,否则返回 `2025-11-25`)。
- 方法:`initialize`、`ping`、`server/discover`、`tools/list`、`tools/call`。不发会话 ID(无状态)。

## 安全建议

1. **只在需要的环境开启。** 默认关闭;生产环境开启前确认暴露范围。
2. **写操作逐个打开**,打开前想清楚 AI 误调用的后果;优先暴露幂等、可回滚的接口。
3. **生产环境用 token 模式**,或 passthrough + 接口自身鉴权;令牌放在环境变量或密钥管理里,定期轮换。
4. **不要把端点直接暴露在公网**;放在内网或网关之后。
5. **定期看审计日志**,开启 `audit.persist` 保留历史。
6. 返回数据里有敏感字段时,开启 [返回字段过滤](/guide/runtime-filter) 并配置返回规则,AI 只能看到规则允许的字段。
7. 浏览器里的页面不应直接调用 MCP 端点;确需跨域时才配置 `allowed-origins`。

## 常见问题

| 现象 | 原因 |
|---|---|
| 404 | 没开 `csap.apidoc.mcp.enabled`,或改了 `path` |
| 401 | 令牌不对、没配置令牌,或请求头不是 `Authorization: Bearer ...` |
| 403 | 请求带 `Origin` 且不在 `allowed-origins` 中;或 Spring Security 的 CSRF 拦截 |
| 工具列表里缺接口 | 写操作默认不暴露;接口在文档中隐藏;接口有文件上传参数 |
| 调用超时、连接被拒绝 | 回环地址不通(HTTPS、只监听特定网卡、前置网关),配置 `base-url` |
| 工具结果里少了字段 | 返回字段过滤生效,检查该接口的返回规则 |
| 客户端报响应不是 JSON-RPC | 应用有对所有返回值生效的全局 `ResponseBodyAdvice`(统一包装 `{code, data}`),请在它的 `supports` 里排除 `/csap/mcp` 或 `ai.csap.apidoc.mcp` 包 |

后续计划(不在 1.3.0):独立的 `csap-apidoc-mcp` 聚合服务(多服务、stdio)、Go 版 MCP。见 [路线图](https://github.com/csap-ai/csap-apidoc/blob/main/docs/ROADMAP.md)。
