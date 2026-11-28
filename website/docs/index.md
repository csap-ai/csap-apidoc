---
pageType: home

hero:
  name: CSAP Apidoc
  text: 多语言的运行时 API 文档框架
  tagline: 启动即生成文档 · 按接口控制显示字段 · 页内在线调试
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/quick-start
    - theme: alt
      text: 介绍
      link: /guide/overview
    - theme: alt
      text: GitHub
      link: https://github.com/csap-ai/csap-apidoc

features:
  - title: 运行时生成文档
    details: 启动时从代码生成文档(Java 扫描注解,Go 读取路由和结构体标签),在内存里生成,打开 /csap-api.html 即可查看。
  - title: 字段规则
    details: 用注解(Java)、结构体标签(Go)或 YAML 文件描述每个接口显示哪些请求参数和返回字段,同一个模型在不同接口里可以显示不同字段。YAML 规则文件两种语言通用。
  - title: 在线调试
    details: 文档页内直接发请求,支持环境切换、全局请求头、鉴权、请求历史与重放、命名快照导入导出(1.1.1 起)。
  - title: 多格式导出
    details: 在文档页导出 OpenAPI、Postman、Markdown,也可以复制为 TypeScript 类型。
  - title: 可视化管理
    details: DevTools 后台勾选要显示的字段、配置校验规则并写回 YAML,开发阶段免去手改文件。
  - title: 网关聚合
    details: 在一个页面里切换查看多个服务的文档。
---

## 一分钟接入

### Java(Spring Boot 2.x / 3.x)

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.5.1</version>
</dependency>
```

在启动类上加 `@EnableApidoc` 指定要扫描的包,启动后访问 `http://localhost:8080/csap-api.html`。完整步骤见 [快速开始](/guide/quick-start)。

### Go(gin、net/http)

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.3.1
```

创建 `apidoc.New(...)`,用 `ginadapter.Mount` 或 `nethttpadapter.Mount` 挂载,注册路由时用 `apidoc.Op` 声明接口,启动后访问 `/csap-api.html`。完整步骤见 [Go 版](/go/)。

## 适用范围

- **多语言**:Java 版已发布(Spring Boot 2.x / 3.x,见 [安装](/guide/installation));Go 版 v0.3.0 已发布,见 [Go 版](/go/);Rust 等更多语言陆续支持。各语言共用同一个文档页面和同一种规则文件。
- 已发布到 [Maven Central](https://central.sonatype.com/namespace/ai.csap.apidoc),基于 [Apache-2.0](https://github.com/csap-ai/csap-apidoc/blob/main/LICENSE) 协议开源。
