# Mock 服务与 JSON Schema

约定见仓库 [docs/design/1.4.0-mock-schema.md](../../docs/design/1.4.0-mock-schema.md)(Java 与 Go 一致,黄金样本在 `docs/design/1.4.0-mock-golden/`,单测直接读那个目录)。

## Mock

按文档里的返回结构生成**可复现**的假数据,默认关闭:

```go
doc := apidoc.New(apidoc.Options{
    Mock: apidoc.MockOptions{
        Enabled: true,
        // Seed: "csap",            // 默认 "csap",结果可复现;"random" 每次请求用新种子
        // ArraySize: mock.Size(2), // 数组默认长度 0~10,默认 2
        // MaxDepth: 6,             // 对象嵌套深度上限 1~20
        // DelayMs: 0,              // 每次响应前等待的毫秒数
        // DisableHeuristics: false,// 关闭字段名启发式(email、phone、id、name 等)
    },
})
```

- 入口是 `/csap/mock` + 原接口路径:`POST /api/users/{id}` 的 Mock 是 `POST /csap/mock/api/users/42`。任意 HTTP 方法。关闭时路由不注册,一律 404;访问控制沿用 `Options.DocAuth`。启用时打一行 WARN 日志。
- 不校验请求、不调用业务代码。接口匹配:占位符 `{x}` 各匹配一个非空段;多个都匹配时字面段多的优先、占位符少的优先、key 小的优先;路径对、方法不对 → 405 + `Allow`;不存在 → 404,body 为 `{"code":"MOCK_NOT_FOUND",...}`。隐藏的接口 404,DEPRECATED 照常。
- 响应:200,头 `X-CSAP-Mock: 1`、`X-CSAP-Mock-Operation`、`X-CSAP-Mock-Seed`;body 就是文档里的返回结构(含统一包装)。没有返回模型 → 200 + 空 body。单次请求可用请求头 `X-CSAP-Mock-Seed` 或查询参数 `_csap_mock_seed` 换种子(请求头优先)。
- 取值优先级:规则里的 `mock` → `example` → `defaultValue` → 枚举(`Enum` 校验或 `extendDescr`)→ `Min`/`Max` → 字段名启发式 → 类型默认值。`Length` 和 `Size` 校验会补齐(用 x)或截断启发式、类型默认值得到的字符串;它们和 `NotEmpty` 也影响数组长度。`Pattern` 不参与生成,需要符合正则的值请写 `example` 或 `mock`。
- 随机数由 `splitmix64(fnv1a64(种子 + "\n" + 路径))` 派生,与生成顺序无关,Java 与 Go 输出一致。
- `/csap/mock-status`(GET,开启时才有):`{"enabled":true,"prefix":"/csap/mock","seed":"csap"}`,文档页用它探测。

在规则里指定字段的 Mock 值(YAML / 数据库 / Redis 同一结构):

```yaml
demo.UserController:
  detail:
    response:
      data.level: {mock: "@oneOf(1,2,3)"}   # 表达式 @int(lo,hi) / @oneOf(a,b,c) / @uuid
      data.vip:   {mock: true}                # 字面值(数字、布尔、字符串、数组、对象)原样输出
```

字符串以 `@@` 开头会去掉一个 `@` 当普通字符串;其他 `@xxx` 原样输出。`mock` 只用于 Mock 响应,字段上的 `mock` 属性也会出现在文档 JSON 里。

## JSON Schema

始终开启(只读文档导出),返回**裸 JSON**(不套外层包装),隐藏接口不导出:

- `GET /csap/schema.json[?download=true]`:全部接口的合集(`x-csap-operations` + `$defs`)。
- `GET /csap/schema/method?key=<接口 key>`:单个接口的 `{operationId, method, path, summary, deprecated, request, response}`;key 不存在 404。

请求 Schema 按位置分区(`path`、`query`、`header`、`form`、`body`);字段说明、必填、示例、默认值映射成标准关键字,校验规则映射成 `minLength` / `minimum` / `pattern` / `enum` 等,并在 `x-csap-validate` 里无损保留原规则;`x-csap-key` 记规则键,导入时用。

### 导入(默认关闭)

把(导出再被人修改的,或外部提供的)Schema 里的说明、必填、示例、校验转成字段规则。**不创建接口和字段、不改类型**,文档里没有的字段进 `unmatched`。

```go
apidoc.Options{Schema: apidoc.SchemaOptions{
    ImportEnabled: true, // POST /csap/schema/import,默认关闭
    Store: yamlStore,    // apply=true 时写入的规则存储;为 nil 时只能预览
    // ReadOnly: true,   // apply=true 返回 1007 错误,预览仍可用
}}
```

`POST /csap/schema/import {"key","kind":"request|response","section":"body","schema":{…},"apply":false}`:`apply` 缺省 false 只预览,返回 `rules`、`yaml`(可粘贴进规则文件)、`matched`、`unmatched`、`skipped`;`apply: true` 才写入,修改人 `schema-import`,走规则存储的正常保存路径(版本号、修改记录、多实例同步),并刷新文档。`required` 只会写 true;同一字段整体替换 Schema 提供了的项,没提供的保持原样。没有批量导入整个合集的端点。

## 与 Java 的差异

- Go 的 Mock 选项是 `apidoc.MockOptions`(Java 是 `csap.apidoc.mock.*`),语义相同。
- `DisableHeuristics` 对应"字段名启发式开关"(默认开启)。
