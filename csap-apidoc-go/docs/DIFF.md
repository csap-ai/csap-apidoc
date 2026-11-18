# csap-apidoc-diff:破坏性变更检测

`cmd/csap-apidoc-diff` 比较两份 `/csap/apidoc` 文档(Java 和 Go 服务输出同一协议),列出新增 / 删除 / 修改的接口与字段,按规则判定破坏性变更,有破坏性变更时退出码为 1,适合放进 CI。规则表、忽略清单语法和 GitHub Actions 示例见仓库 [docs/design/1.4.0-api-diff.md](../../docs/design/1.4.0-api-diff.md)。

```bash
# 安装(单个静态二进制,不依赖 cgo)
CGO_ENABLED=0 go install github.com/csap-ai/csap-apidoc/csap-apidoc-go/cmd/csap-apidoc-diff@latest

# 存基线(提交进 Git;快照里没有随机 key 和时间戳以外的易变内容)
csap-apidoc-diff snapshot -o api-baseline.json http://localhost:8080/csap/apidoc

# 对比:旧、新可以是 URL、JSON 文件或快照
csap-apidoc-diff api-baseline.json http://localhost:8080/csap/apidoc
csap-apidoc-diff -format markdown -o report.md -ignore .apidoc-ignore old.json new.json
```

选项:`-format text|json|markdown`、`-o 文件`、`-fail-on breaking|warning|never`、`-min-level info|warning|breaking`、`-ignore 文件`(可重复)、`-H "Name: Value"`(请求 URL 时的请求头,可重复)。退出码:0 通过,1 有破坏性变更,2 参数或读取错误。

破坏性变更包括:删除接口、HTTP 方法或路径变化、删除返回字段、请求字段由可选变必填(含新增必填字段和必填请求头)、字段类型变化、枚举取值减少等;完整规则见设计文档。

库用法:`apidiff.Load` / `apidiff.Parse` 读文档,`apidiff.NewReport(old, new, ignoreRules)` 得到报告,`Report.Text` / `Markdown` / `JSON` 输出。
