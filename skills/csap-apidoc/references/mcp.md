# 内嵌 MCP 端点(1.3.0 起)

## 开启

```yaml
csap:
  apidoc:
    mcp:
      enabled: true                 # 默认 false;关闭时 /csap/mcp 返回 404
      auth:
        mode: token                 # token(默认)| passthrough
        tokens:
          - name: my-ide            # 名称出现在审计日志里
            value: ${CSAP_MCP_TOKEN:}   # 令牌值放环境变量,不要写进仓库
```

- token 模式下没有配置任何令牌时,所有请求都是 401(fail-closed)。
- passthrough 模式不校验 MCP 端点本身,把 `Authorization`、`Cookie`(可配置)转发给目标接口,由接口自己的鉴权把关。
- 用了 Spring Security:放行 `/csap/mcp` 并对它关闭 CSRF。
- 开发时也打开 `csap.apidoc.devtool.enabled: true`,在 `/devtools-ui/mcp` 勾选暴露的接口、改写工具名和说明、看审计日志。

## 客户端配置

Claude Code:

```bash
claude mcp add --transport http csap-apidoc http://localhost:8080/csap/mcp \
  --header "Authorization: Bearer $CSAP_MCP_TOKEN"
```

Cursor(`.cursor/mcp.json`):

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

## 工具长什么样

| 字段 | 来源 |
|---|---|
| 名称 | 默认 `{Controller 简名去掉 Controller}_{方法名}`,如 `Product_getProduct`;可在 DevTools 改写(字母、数字、`_`、`-`,最长 64) |
| 说明 | 接口标题 + 描述 + 最后一行 `HTTP GET /path` |
| 参数 | 路径变量、查询参数、请求头为顶层属性;`@RequestBody` 放在 `body`;不带注解的对象参数按字段展开为查询参数 |

调用在服务内部以本机 HTTP 请求转发到接口,经过所有 Filter(包括 Spring Security),返回字段过滤(`csap.apidoc.runtime.filter.enabled`)同样生效。

## 排错

| 现象 | 原因 |
|---|---|
| 404 | 没开 `csap.apidoc.mcp.enabled`,或 `csap.apidoc.mcp.path` 改过 |
| 401 | 令牌不对 / 没配令牌 / 请求头不是 `Authorization: Bearer ...` |
| 403 | 请求带了 `Origin` 且不在 `csap.apidoc.mcp.allowed-origins` 里(浏览器发起的请求) |
| 工具列表缺接口 | 写操作默认不暴露;接口在文档里隐藏;参数含文件上传(不支持);`expose-get-by-default: false` |
| `Unknown tool` | 工具未暴露或已改名,先 `tools/list` |
| 调用超时 / 连接失败 | 回环地址不对(HTTPS、只监听特定网卡、有前置网关),配置 `csap.apidoc.mcp.base-url` |
| POST 被拦截 403 | Spring Security 的 CSRF,对 `/csap/mcp` 关闭 |

协议:支持 MCP `2026-07-28`(无状态)以及 `2025-11-25`、`2025-06-18`、`2025-03-26`(initialize 握手),只返回 JSON,不开 SSE 流。
