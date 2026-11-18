# 接口上下线管理（1.4.0 计划）

:::warning 1.4.0 计划，以 1.4.0 实现为准
接口状态管理计划随 **Java 1.4.0（2026-12-22）** 发布，**当前已发布的版本没有**。本页按已审定的约定编写（设计见仓库 `docs/design/1.4.0-api-status.md`），具体行为以 1.4.0 实现为准。
:::

## 它解决什么问题

接口有"开发中、完成、过时"三种状态（`ApiStatus`：`DEFAULT`、`FINISH`、`DEPRECATED`，见 [注解参考](/api/annotations#apistatus)），但目前只是个字段，文档页、导出和 AI 调用都不用它，DevTools 也不能改。这个功能让状态在文档页、OpenAPI 和 [MCP](/guide/mcp) 里可见，并能在 DevTools 里修改。

## 有效状态怎么定

按顺序取第一个有值的：

1. 规则存储里该接口的 `status`（在 DevTools 里改出来的）；
2. 方法上 `@ApiOperation.status`（不是 `DEFAULT` 时）；
3. 类上 `@Api.status`（不是 `DEFAULT` 时，整个 Controller 过时则其下接口都过时）；
4. `DEFAULT`。

注解里的 `DEFAULT` 视为"没标"。要明确标成开发中，只能经规则存储。

## 各处怎么用

- **文档页**：`FINISH` 显示"完成"，`DEPRECATED` 显示"过时"（带备注）；`DEFAULT` 不显示标记（绝大多数接口都没标，全显示是噪音）；可以按状态筛选。
- **OpenAPI / Postman 导出**：过时接口输出 `deprecated: true`，Postman 里名称前加 `[过时]`；非 `DEFAULT` 的状态额外输出 `x-csap-status`。
- **MCP**：**过时接口默认不暴露**，要用必须在 DevTools 的 MCP 页里逐个打开；配置 `csap.apidoc.mcp.expose-deprecated=true` 则按普通接口处理。开发中的接口**不会**默认隐藏。工具说明前会带 `[过时]` / `[完成]` 前缀，AI 因此知道该不该用。
- **DevTools**：接口列表可以改状态并填备注，保存走规则存储的正常保存（修改记录、回滚、多实例同步都生效），受只读模式和打包运行不可写的限制；`ANNOTATION` 模式没有规则存储，不能改。

## 存储

状态存在规则 JSON 里，与 `request`、`response` 并列，多两个可选字段 `status`、`statusNote`（备注，如替代接口、下线时间）。YAML、数据库、Redis 的结构不变（数据库表结构不变），Java 与 Go 共用同一格式。

:::warning 升级注意
旧版本的 Java DevTools 保存某个接口的字段规则时，会按它认识的结构整行重写，**会丢掉该接口的 `status`**。数据库、Redis 模式下多实例升级到 1.4.0 时请**一次全部升级**，不要新旧混跑。
:::

## 相关页面

- [注解参考](/api/annotations#apistatus)
- [MCP](/guide/mcp)
- [版本对比与破坏性变更](/guide/api-diff)：状态变为过时会被报告
