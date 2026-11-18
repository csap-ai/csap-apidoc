# 版本对比与破坏性变更检测（1.4.0 计划）

:::warning 1.4.0 计划，以 1.4.0 实现为准
命令行工具 `csap-apidoc-diff` 随 **Go v0.2.0** 实现（设计见仓库 `docs/design/1.4.0-api-diff.md`、`csap-apidoc-go/docs/DIFF.md`），计划随 1.4.0 发布，**当前已发布的版本没有**。工具读取 `/csap/apidoc` 的统一协议，Java 和 Go 服务的文档都能比较。具体行为以发布版为准。
:::

## 它解决什么问题

接口改动后，调用方是否会被破坏（删字段、改类型、必填变化等），往往到联调甚至上线后才发现。这个工具比较两份文档，列出差异并按规则判定破坏性变更，放进 CI，在合并前拦截。

## 适用场景

- CI 里比较当前分支与基线，发现破坏性变更时让流水线失败；
- 发布前生成"这次改了什么"的差异清单（Markdown 报告）；
- 多服务场景下提前通知调用方（团队版在此基础上做跨服务汇总）。

## 用法

单个静态二进制，不依赖 cgo：

```bash
CGO_ENABLED=0 go install github.com/csap-ai/csap-apidoc/csap-apidoc-go/cmd/csap-apidoc-diff@latest

# 存基线（提交进 Git）
csap-apidoc-diff snapshot -o api-baseline.json http://localhost:8080/csap/apidoc

# 对比：旧、新可以是 URL、JSON 文件或快照
csap-apidoc-diff api-baseline.json http://localhost:8080/csap/apidoc
csap-apidoc-diff -format markdown -o report.md -ignore .apidoc-ignore old.json new.json
```

| 选项 | 说明 |
|------|------|
| `-format text\|json\|markdown` | 报告格式 |
| `-o 文件` | 输出到文件 |
| `-fail-on breaking\|warning\|never` | 什么级别算失败，默认 `breaking` |
| `-min-level info\|warning\|breaking` | 报告里列出的最低级别 |
| `-ignore 文件` | 忽略清单，可重复 |
| `-H "Name: Value"` | 请求 URL 时附带的请求头（如 Authorization），报告里不显示查询串 |

退出码：`0` 通过，`1` 有破坏性变更，`2` 参数或读取错误。

## 判定规则（摘要）

- **破坏性（breaking）**：删除接口、HTTP 方法或路径不再可用、删除返回字段、请求字段由可选变必填（含新增必填字段和必填请求头）、字段类型变化、参数位置变化、枚举取值减少；
- **需要留意（warning）**：接口变为过时、删除请求字段、校验规则变严、长度上限变小、返回字段不再保证存在；
- **兼容（info）**：新增接口、字段、枚举值，说明变化，接口改名（方法和路径不变）。

完整规则表和忽略清单语法见仓库 `docs/design/1.4.0-api-diff.md`。

## 在 CI 里使用

仓库里提交基线文件，CI 里启动服务、对比、失败时把 Markdown 报告写进作业摘要。GitHub Actions 的完整示例见仓库 `docs/design/1.4.0-api-diff.md` 第 5 节。确认变更合理后，重新生成基线并提交。

## 局限

- 只看文档里有的信息：字段语义变化（含义变了但类型没变）、校验规则的细节行为看不出来；
- 校验规则按文本集合比较，不做语义推断；
- 只比较**同一个服务**的两个版本，不要拿 Java 和 Go 的文档互相比较（类型写法不同）。

## 相关页面

- [导出](/guide/export)
- [接口上下线管理](/guide/api-status)
- [路线图](/roadmap)
