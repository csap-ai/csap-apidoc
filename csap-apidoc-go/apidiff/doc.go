// Package apidiff 比较两份 /csap/apidoc 文档,判定破坏性变更。Java 与 Go 服务输出同一协议,两种语言的文档都能比。
// 规则表见仓库 docs/design/1.4.0-api-diff.md。命令行工具是 cmd/csap-apidoc-diff。
package apidiff
