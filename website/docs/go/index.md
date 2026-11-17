---
title: Go 版(开发中)
---

# Go 版(开发中)

CSAP Apidoc 的 Go 实现,作为独立的 Go 模块放在仓库的 `csap-apidoc-go/` 目录,与 Java 版协议兼容。

:::warning 尚未发布
Go 版还没有发布正式版本,代码目前在 [`feat/apidoc-go`](https://github.com/csap-ai/csap-apidoc/tree/feat/apidoc-go/csap-apidoc-go) 分支([PR #13](https://github.com/csap-ai/csap-apidoc/pull/13)),接口在发布前仍可能调整。发布后会提供 `go get` 安装方式和完整文档。
:::

## 与 Java 版的关系

- **同一套协议**:`/csap/apidoc` 等接口的返回格式与 Java 版一致,可以直接使用同一个文档页面(`/csap-api.html`)。
- **同一种规则文件**:YAML 规则与 Java 版的 `method-field.yaml` 格式相同,Java 项目的规则文件可以直接复用。
- **同样的返回过滤语义**:规则里放行的字段才输出;另提供与 Java 完全一致的"按类型过滤"兼容模式,方便从 Java 迁移。

## 已完成

| 阶段 | 内容 |
|---|---|
| M1 | 运行时生成文档、`/csap/apidoc*` 接口、内嵌文档页面、OpenAPI 3.1 导出、gin 适配 |
| M2 | 返回字段过滤(路径白名单 / Java 兼容模式)、请求参数校验(关闭 / 观察 / 拦截三种模式) |
| M3 | YAML 与 SQL(SQLite、PostgreSQL、MySQL)规则来源,改了自动生效,多实例同步 |

## 计划中

- **M4**:后台管理(复用 DevTools 前端)、修改记录与回滚、鉴权;补齐 Java 1.1.0 新增的 `globalHeaderHints`、`authHint`。
- **M5**:标准库 `net/http` 适配、Postman 导出、正式发布。

设计文档:[DESIGN.md](https://github.com/csap-ai/csap-apidoc/blob/feat/apidoc-go/csap-apidoc-go/docs/DESIGN.md)。
