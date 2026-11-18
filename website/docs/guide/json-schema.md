# JSON Schema 导入导出（1.4.0 计划）

:::warning 1.4.0 计划，以 1.4.0 实现为准
JSON Schema 导出和导入计划随 **Java 1.4.0（2026-12-22）** 发布，Go 同步实现，**当前已发布的版本没有**。本页按已审定的约定编写（设计见仓库 `docs/design/1.4.0-mock-schema.md`），具体行为以 1.4.0 实现为准。
:::

## 它解决什么问题

接口的请求和返回结构已经在文档里，但校验库、代码生成器、网关、测试框架更习惯用 JSON Schema。导出把文档结构变成 JSON Schema（2020-12）；导入把 Schema 里的说明、必填、示例、校验转成字段规则，避免两边各维护一份。

## 导出

| 端点 | 作用 |
|------|------|
| `GET /csap/schema.json` | 全部接口的合集（`$defs` 里是各接口的请求、返回 Schema）；加 `?download=true` 作为文件下载 |
| `GET /csap/schema/method?key=<接口 key>` | 单个接口的请求、返回 Schema |

- 与 `/csap/openapi.json` 一样始终可用，返回裸 JSON（不套外层包装），可直接保存；
- 隐藏的接口不导出，过时（`DEPRECATED`）的接口导出并标 `deprecated`；
- 请求按位置分区：`path`、`query`、`header`、`form`、`body`；
- 校验规则会映射成标准关键字：`NotEmpty` → `minLength` / `minItems`，`Length` / `Size`，`Min` / `Max` → `minimum` / `maximum`，`Pattern` → `pattern`，`Enum` → `enum`；规则本身还会无损地保留在扩展字段 `x-csap-validate` 里；
- 正则原样导出，不翻译方言（Java 正则、Go RE2、ECMA-262 常见写法兼容，个别写法有差异）。

## 导入

`POST /csap/schema/import`，请求体里给接口 key、`kind`（`request` / `response`）、Schema，以及 `apply`：

- **默认只预览**（`apply` 缺省 `false`）：返回会生成的规则、可直接粘贴进规则文件的 YAML、匹配数量，以及 `unmatched`（Schema 里有、文档里没有的字段）和 `skipped`（不支持的关键字）；
- `apply: true` 才写入，走 DevTools 保存的同一条路径（有修改记录、版本号，受只读模式约束）；
- 导入端点**默认关闭**，需要 `csap.apidoc.schema.import-enabled=true`，因为导入的校验会在运行时生效；
- 边界：只更新**文档里已经存在**的字段的说明、必填、示例、校验，不创建接口和字段，不改字段类型；
- 必填只会写成 `true`，不会因为 Schema 没列出就改成 `false`；
- 不提供"导入整个合集"的入口，批量请逐个接口预览确认，或用[迁移工具](/guide/database-mode#迁移)走 Git 审查。

## 相关页面

- [Mock 服务](/guide/mock)
- [导出](/guide/export)
- [字段库与模板](/guide/field-library)
