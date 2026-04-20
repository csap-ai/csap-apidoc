# CSAP Framework API Doc

[![Maven Central](https://img.shields.io/badge/maven--central-v1.0.3-blue)](https://search.maven.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-11%2B-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x%2F3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 一个零侵入的 Spring Boot API 文档框架 —— 注解生成 / 在线调试 / 多格式导出 / 多策略持久化。
>
> A zero-intrusion Spring Boot API documentation framework — annotation-driven generation, in-browser try-it-out, multi-format export, and pluggable persistence strategies.

## 文档站点 / Site Map

!!! tip "想直接动手 / Just want to ship?"
    跳到 [快速开始 5 分钟集成](guides/QUICK_START.md)，或先读 [注解参考手册](guides/ANNOTATION_REFERENCE.md)。

### 🚀 快速开始 / Getting Started

| 文档 | 说明 |
| :-- | :-- |
| [快速开始](guides/QUICK_START.md) | 5 分钟把框架接入到现有 Spring Boot 项目。 |
| [注解参考手册](guides/ANNOTATION_REFERENCE.md) | `@DocApi` / `@DocMethod` / `@DocParam` / `@DocGlobalHeader` / `@DocAuth` 全量字段说明。 |
| [常见问题 (FAQ)](guides/FAQ.md) | 集成失败、扫描不到接口、导出报错等高频问题。 |
| [网关聚合](guides/GATEWAY-AGGREGATION.md) | 在 Spring Cloud Gateway 后聚合多个微服务文档。 |

### 🏗️ 架构与设计 / Architecture

- [系统架构总览](architecture/ARCHITECTURE.md) — 模块分层、数据流、扩展点。

### ✨ 特性 / Features

| 文档 | 说明 |
| :-- | :-- |
| [环境 + 全局头 + 认证 + Try-it-out](features/environment-auth-headers.md) | M1–M7.1 完整路线图，含 annotation / yaml / sqlite 三源覆盖矩阵。 |
| [试运行 v2 CORS 排错](cors.md) | `⚡ 试运行 v2` 在企业代理 / 浏览器 SOP 下的常见报错与修复。 |

### 🔧 贡献者 / Contributors

| 文档 | 说明 |
| :-- | :-- |
| [代码风格总览](development/code-style/CODE_STYLE_README.md) | Checkstyle + Spotless 规约入口。 |
| [代码风格 — 5 分钟上手](development/code-style/CODE_STYLE_QUICKSTART.md) | 配置 IDE、本地校验。 |
| [代码风格 — 完整规范](development/code-style/CODE_STYLE.md) | 详细规则、命名约定、注释要求。 |
| [快速修复指南](development/code-style/QUICK_FIX_GUIDE.md) | Checkstyle 高频违规一栏式修复。 |
| [Checkstyle 报告解读](development/code-style/CHECKSTYLE_REPORT_SUMMARY.md) | 输出报告字段含义。 |

## 模块速览 / Modules at a Glance

| 模块 | 角色 |
| :-- | :-- |
| `csap-apidoc-annotation` | 注解载体（`@Doc*`），零运行时依赖。 |
| `csap-apidoc-core` | 核心模型、扫描器、导出器（OpenAPI / Postman / Markdown / JSON Schema）。 |
| `csap-apidoc-strategy` | 持久化策略：标准输出、YAML、SQLite，统一接口。 |
| `csap-apidoc-devtools` | 开发者工具，浏览器内管理文档与 Try-it-out。 |
| `csap-apidoc-ui` | 前端查看 / 调试 SDK，可独立嵌入业务后台。 |
| `csap-apidoc-boot` | Spring Boot Starter 自动装配。 |
| `csap-validation-core` | 参数校验框架（JSR-303 桥接）。 |
| `example/example-spring-boot{2,3}` | Spring Boot 2.x / 3.x 端到端示例。 |

完整模块说明请直接阅读对应目录下的 `README.md`，或在 GitHub 仓库浏览。

## 反馈与贡献 / Feedback

- 🐛 [Issues](https://github.com/csap-ai/csap-apidoc/issues)
- 💬 [Discussions](https://github.com/csap-ai/csap-apidoc/discussions)
- 📧 support@csap.ai

License: Apache 2.0 — Made with ❤️ by the CSAP team.
