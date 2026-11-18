# Mock 服务

:::tip 1.4.0 新增
Mock 服务从 **Java 1.4.0、Go v0.2.0** 起提供。设计与黄金样本见仓库 `docs/design/1.4.0-mock-schema.md`。Mock 默认关闭，不要在生产环境开启。
:::

## 它解决什么问题

后端接口还没做好时，前端和测试无法联调；想复现某个边界数据，也得等后端真的处于那个状态。Mock 服务根据文档里已有的接口信息（字段、类型、示例、校验规则），生成模拟响应，让文档页面和调用方在没有真实后端的情况下继续工作。

## 适用场景

- 前端先于后端开发，按文档约定的响应结构联调；
- 测试需要稳定复现某种响应，数据每次一样；
- 对接方在接口上线前先看到返回数据长什么样。

## 开启

Mock 默认关闭，也**不要在生产环境开启**（启用时启动日志会打一行 WARN）：

```yaml
csap:
  apidoc:
    mock:
      enabled: true      # 默认 false；关闭时 /csap/mock/** 直接 404
      seed: csap         # 随机种子，默认 csap（结果可复现）；random = 每次请求都不同
      array-size: 2      # 数组默认长度，0~10
      max-depth: 6       # 对象嵌套深度上限，1~20
      heuristics: true   # 字段名启发式，默认开启
      delay-ms: 0        # 每次响应前固定等待毫秒数，调试 loading 状态用
```

## 怎么调用

Mock 的地址是 `/csap/mock` 加上原接口的路径，HTTP 方法不变：

```bash
# 真实接口：POST /api/users/42
curl -X POST http://localhost:8080/csap/mock/api/users/42
```

- 用路径前缀，而不是请求头切换：关闭时前缀直接 404，永远不会悄悄替换真实接口；
- 返回的是文档里**返回结构本身**（含统一包装），文档里没有的接口或隐藏接口返回 404，路径对但方法不对返回 405；
- 响应头 `X-CSAP-Mock: 1`、`X-CSAP-Mock-Operation`（接口 key）、`X-CSAP-Mock-Seed`（本次种子）；
- 单次请求可以换种子：请求头 `X-CSAP-Mock-Seed` 或查询参数 `_csap_mock_seed`。

## 数据怎么生成

- **可复现**：同一个种子、同一个接口，每次生成的数据一样（按字段路径派生，与生成顺序无关）；Java 与 Go 的结果一致；
- **取值优先级**：规则里的 `mock` → 字段示例 `example` → 默认值 → 枚举 → 数值约束（`Min` / `Max`）→ 按字段名的启发式 → 类型默认值；
- **字段名启发式**：默认开启，按字段名生成更像样的数据（`email`、手机号、`name`、各类 id、`url`、页码、`age` 等）；可以用 `csap.apidoc.mock.heuristics=false` 关闭；
- 字符串长度、数组长度会按 `Length` / `Size` / `NotEmpty` 校验收紧；正则（`Pattern`）不参与生成，需要符合正则的值请用 `example` 或 `mock` 指定。

### 在规则里指定 Mock 值

字段规则可以加 `mock`，与 `example` 并列（YAML、数据库、Redis 里同一结构）：

```yaml
demo.UserController:
  detail:
    response:
      data.level:
        mock: "@oneOf(1,2,3)"   # 表达式
      data.token:
        mock: "@uuid"
      data.vip:
        mock: true              # 字面值原样输出，数字、布尔、数组、对象都可以
```

表达式有 `@int(lo,hi)`、`@oneOf(a,b,c)`、`@uuid`；其他以 `@` 开头的字符串原样输出，`@@` 开头表示转义。`mock` 只在 Mock 响应里使用。

## 在文档页里使用

Mock 开启后，文档页的"在线调试"环境里会多一个预设环境"Mock"，选中后请求发往 Mock，响应上显示"Mock"标记；没开启时不显示。对应的探测接口是 `GET /csap/mock-status`。

## Go

Go 版用 `apidoc.Options.Mock`：

```go
doc := apidoc.New(apidoc.Options{
    Mock: apidoc.MockOptions{
        Enabled: true,
        // Seed: "csap",             // "random" 每次请求用新种子
        // ArraySize: mock.Size(2),  // 数组默认长度 0~10
        // MaxDepth: 6,              // 嵌套深度上限 1~20
        // DelayMs: 0,
        // DisableHeuristics: false, // 关闭字段名启发式
    },
})
```

访问控制沿用 `Options.DocAuth`；规则里的 `mock` 键、取值规则、Java 与 Go 生成的数据一致。详见仓库 `csap-apidoc-go/docs/MOCK.md`。

## 相关页面

- [在线调试](/guide/testing)
- [JSON Schema 导入导出](/guide/json-schema)
- [字段库与模板](/guide/field-library)：`example`、模板
