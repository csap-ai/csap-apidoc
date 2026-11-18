# 获取方式

本页列出获取 CSAP Apidoc 的方式：从 Maven Central 引入依赖、从 GitHub 获取发布版本、从源码构建，以及 Go 版的安装。Java 版当前版本是 **1.2.0**。1.1.0 是第一个发布到 Maven Central 的版本，但它的 jar 实际是 Java 17 字节码，需要 JDK 17 才能运行，请使用 1.1.1 及以上（Java 11 字节码）。

## Maven Central

所有构件都发布在 Maven Central，命名空间为 [`ai.csap.apidoc`](https://central.sonatype.com/namespace/ai.csap.apidoc)。一般项目只需要文档 starter：

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

```groovy
implementation 'ai.csap.apidoc.boot:csap-apidoc-boot-starter:1.2.0'
```

参数校验 starter `csap-validation-boot-starter` 是可选的，需要运行时参数校验（1.2.0 起）、它的校验异常处理器或 `ValidationProperties` 时引入，见 [安装](/guide/installation)。

### 全部构件

| groupId | artifactId | 说明 |
| --- | --- | --- |
| `ai.csap.apidoc.boot` | `csap-apidoc-boot-starter` | 文档 starter，包含下表中除校验 starter 外的运行时模块 |
| `ai.csap.apidoc.boot` | `csap-validation-boot-starter` | 参数校验 starter（可选） |
| `ai.csap.apidoc` | `csap-apidoc-core` | 核心：扫描、解析、文档数据接口 |
| `ai.csap.apidoc` | `csap-apidoc-annotation` | 文档注解 |
| `ai.csap.apidoc` | `csap-apidoc-common` | 公共模型与工具 |
| `ai.csap.apidoc` | `csap-apidoc-ui` | 文档页面 `/csap-api.html` |
| `ai.csap.apidoc` | `csap-apidoc-devtools` | DevTools 可视化工具 |
| `ai.csap.apidoc` | `csap-apidoc-runtime` | 返回字段过滤（1.2.0 起），默认关闭 |
| `ai.csap.apidoc` | `csap-apidoc-yaml` | YAML 存储策略 |
| `ai.csap.apidoc` | `csap-apidoc-sqlite` | SQLite 存储策略 |
| `ai.csap.apidoc` | `csap-apidoc-standard` | 存储策略共用的文件处理 |
| `ai.csap.apidoc` | `csap-validation-core` | 校验核心 |

各模块的关系和使用场景见 [安装](/guide/installation)。

:::tip
发布到 Maven Central 的构件都带有 GPG 签名、源码包和 Javadoc 包。旧文档中的 `com.csap.framework.boot:csap-framework-apidoc-boot-starter` 等坐标从未发布过，请使用上表中的坐标。
:::

## GitHub

- 源码仓库：[github.com/csap-ai/csap-apidoc](https://github.com/csap-ai/csap-apidoc)，`main` 是唯一的开发分支。
- 发布页面：[GitHub Releases](https://github.com/csap-ai/csap-apidoc/releases)。每个版本对应一个 `vX.Y.Z` 标签（1.2.0 对应 `v1.2.0`），发布说明列出该版本的主要变化。
- 发布页面不附带 jar 包，jar 请从 Maven Central 获取；需要源码时可以下载该标签的源码压缩包，或用 git 检出该标签。

```bash
git clone https://github.com/csap-ai/csap-apidoc.git
cd csap-apidoc
git checkout v1.2.0
```

## 从源码构建

### 环境

- JDK 11 及以上（CI 使用 JDK 11、17、21 构建；1.1.1 起不论用哪个 JDK 构建，都输出 Java 11 字节码）
- Maven 3
- 只有修改文档页面前端时才需要 Node.js（CI 使用 Node.js 18）

### 构建并安装到本地仓库

在仓库根目录执行（CI 使用同一条命令并加上 `-DskipTests=false`）：

```bash
mvn -B clean install
```

完成后，所有模块以根 `pom.xml` 中的版本（检出 `v1.2.0` 时为 `1.2.0`）安装到本地 Maven 仓库（`~/.m2/repository/ai/csap/apidoc/`），其他项目可以直接引用。

:::tip 关于测试
根 `pom.xml` 中 `skipTests` 默认是 `true`，上面的命令不会运行单元测试。需要运行测试时显式打开：

```bash
mvn -B test -DskipTests=false
```
:::

### 构建文档页面前端

文档页面的源码在 `csap-apidoc-ui` 目录（React + Vite）。CI 中的检查命令：

```bash
cd csap-apidoc-ui
npm ci
npm run build   # 类型检查并构建到 dist/
npm test        # 单元测试
```

`npm run build` 只输出到 `csap-apidoc-ui/dist`。要把新的前端打进 `csap-apidoc-ui` 的 jar，需要把构建产物放到 `csap-apidoc-ui/src/main/resources/static/`（入口文件名为 `csap-api.html`）后再执行 Maven 构建；`csap-apidoc-ui/build.sh` 脚本完成的就是这几步。1.1.1 起，发布流程会按当前源码重新构建这个页面再打包。

### 运行示例工程

示例工程不在根 `pom.xml` 的模块列表中，需要单独启动。它们依赖 `pom.xml` 里 `csap.apidoc.version` 指定版本的构件（从 Maven Central 下载，或先在根目录执行 `mvn clean install`）。

```bash
# Spring Boot 3 示例（需要 JDK 17），端口 8083
cd example/example-spring-boot3
mvn spring-boot:run
```

启动后访问 `http://localhost:8083/csap-api.html`。这个示例默认开启了 DevTools，可以访问 `http://localhost:8083/devtools-ui`。

Spring Boot 2 示例在 `example/example-spring-boot2`（端口 8082），启动方式相同。1.1.0 中它的 `pom.xml` 里 `mainClass` 写错（`ai.csap.apidoc.example.ExampleApplication`），1.1.1 已改为实际启动类 `ai.csap.example.ExampleApplication`。

## Go 版

Go 版是仓库里独立的 Go module（路径 `csap-apidoc-go/`），当前版本 **v0.1.1**，要求 Go 1.22 及以上，不走 Maven Central：

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.1.1
```

它的版本标签带模块目录前缀，格式是 `csap-apidoc-go/vX.Y.Z`（例如 `csap-apidoc-go/v0.1.1`），和 Java 版的 `vX.Y.Z` 标签互不影响。接入方式见 [Go 版](/go/)，API 文档见 [pkg.go.dev](https://pkg.go.dev/github.com/csap-ai/csap-apidoc/csap-apidoc-go)。

## 许可证

CSAP Apidoc 以 [Apache License 2.0](https://github.com/csap-ai/csap-apidoc/blob/main/LICENSE) 开源。
