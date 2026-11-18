// Package apidoc 是 CSAP Apidoc 的 Go 实现:运行时生成接口文档、按配置控制文档可见字段与实际返回字段,
// 并提供与 Java 版 csap-framework-apidoc 协议兼容的 HTTP 接口(/csap/apidoc*、/csap/yaml/*),
// 可直接复用 csap-apidoc-ui 与 devtools 两套前端。设计见 docs/DESIGN.md。
package apidoc
