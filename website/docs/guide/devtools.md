# DevTools

本页介绍 CSAP Apidoc 的开发工具（DevTools）：它在开发阶段提供一个网页，用来按接口勾选请求和返回字段、配置校验规则，并把结果写回 YAML 或 SQLite 规则文件（1.4.0 起也可以写入数据库、Redis）。

:::info 适用范围
本页的开启方式和配置是 **Java 版**的。Go 版的后台管理页面与接口和 Java 兼容(页面是同一份前端),但需要用 `devtools.New` 创建并必须配置鉴权,见 [Go 版](/go/)。
:::

## 它解决什么问题

使用 YAML 或 SQLite 存放字段规则（见 [存储策略](/guide/storage-strategies)）时，手写规则文件需要记住字段键的写法（`user.username`、`data.id`……）。DevTools 读取 Controller 方法的参数和返回类型，列出可选字段，你只需要勾选、填写必填和校验规则，保存后由它生成规则条目。

DevTools 只编辑**字段规则**；接口名称、字段说明等仍然写在注解里。注解策略（`paramType = ANNOTATION`）下不能用 DevTools 保存，接口会返回"当前不支持注解形式动态配置"。

## 开启

`csap-apidoc-boot-starter` 已经传递依赖 `ai.csap.apidoc:csap-apidoc-devtools`，不需要单独引入。开启只需要一个配置项：

```yaml
csap:
  apidoc:
    devtool:
      enabled: true
```

前提：

- 项目里有 `@EnableApidoc`（DevTools 的自动配置挂在文档自动配置之下）；
- 要保存规则，`@EnableApidoc` 的 `paramType` 需要是 `ApiStrategyType.YAML` 或 `ApiStrategyType.SQL_LITE`（1.4.0 起还有 `DATABASE`、`REDIS`，见 [数据库模式](/guide/database-mode)、[Redis 模式](/guide/redis-mode)）。

建议只在开发环境的配置文件里开启，例如放在 `application-dev.yml`：

```yaml
# application-dev.yml
csap:
  apidoc:
    devtool:
      enabled: true
```

`csap.apidoc.devtool.enabled` 默认为 `false`。同组的 `csap.apidoc.devtool.cache`（默认 `true`）目前没有被读取。

## 入口

启动后访问：

```
http://localhost:<端口><context-path>/devtools-ui
```

`/devtools-ui` 及其子路径会转发到 jar 内的静态页面 `/csap-api-devtools.html`。开启后，控制台会打印一段包含该地址的启动横幅。

## 能做什么

1.3.0 起左侧多了 **MCP** 页面:勾选哪些接口作为 MCP 工具暴露给 AI、改写工具名和说明、切换鉴权方式、查看调用审计日志,详见 [MCP](/guide/mcp)。MCP 未开启时页面只显示开启方法。

### 浏览接口和字段

页面列出被 `@EnableApidoc` 扫描到的 Controller 和方法。选中方法后，DevTools 根据方法的参数类型和返回类型（包括 `ApidocResult<User>` 这类泛型）列出实体的所有字段，嵌套对象逐层展开。

### 勾选请求字段、返回字段

为方法勾选需要的请求字段和返回字段并保存：

- 请求字段可以设置是否必填和参数位置；
- 返回字段保存为 `include: true`。

保存时以界面提交的内容为准：某个方法参数下的请求规则、以及该方法的整个返回规则，会被这次提交整体替换。

### 配置校验规则

对请求字段，可以添加 `NotNull`、`NotEmpty`、`Pattern` 三类规则，填写 `code`、`message`、`level`，`Pattern` 可以从 [内置正则](/guide/validation#内置正则) 中选取。已保存的规则通过 `/csap/yaml/getMethodValidateFields` 读回。

注意：1.4.0 起，未勾选必填的字段，其校验规则在请求传了该字段时也会执行；1.3.0 及以前只执行必填字段的规则。见 [参数校验](/guide/validation#必填与非必填字段的规则)。

### 写回文件

保存后：

- YAML 策略：写入 `src/main/resources/<path>/method-field/` 下的 YAML 文件（文件名规则见 [存储策略 - YAML 策略](/guide/storage-strategies#yaml-策略)）；
- SQLite 策略：写入 `<path>/<fileName>.db` 的 `api_method` 表。1.1.1 起数据库文件不存在时会在第一次写入时自动创建。以打包后的 jar 运行时写到 jar 外的文件，见下文。

同时会更新内存中的规则缓存。开启 DevTools 时，按 Controller 或单个接口查询文档的接口（`/csap/apidoc/{className}`、`/csap/apidoc/method`）会重新解析对应的 Controller，因此保存后重新打开该接口的文档即可看到变化，不需要重启。

YAML 文件写回源码目录依赖于"classpath 根目录是 `target/classes`"这一约定，请在 IDE 或 `mvn spring-boot:run` 中使用 DevTools。写出的文件建议和代码一起提交。

### 打包运行时能否在 DevTools 保存

用 `java -jar` 运行打包后的应用时，classpath 在 jar 里，不能写入。下表是用 Spring Boot 3 示例打出 fat jar 后实测的结果（1.3.0）：

| 策略 | 打包运行时保存 | 写到哪里 | 重启后 |
|------|----------------|----------|--------|
| `paramType = YAML` | 不能 | — | — |
| `type = JSON`（导出文档 `/csap/yaml/write`、`writeSelect`） | 不能 | — | — |
| `paramType = SQL_LITE` | 能 | jar 外：启动目录下的 `<path>/<fileName>.db`，默认 `./csap-api-doc/apidoc.db` | 仍在，启动时读回 |
| `paramType = DATABASE`（1.4.0 起） | 能 | 应用自己的数据库 | 仍在，各实例几秒内同步 |
| `paramType = REDIS`（1.4.0 起） | 能 | Redis | 仍在（需开启持久化），各实例即时同步 |

不能保存时，接口返回 HTTP 200 和失败结果（`code` 为 `1007`），DevTools 页面直接显示下面的说明，同时打一条 WARN 日志（1.2.0 及以前是 HTTP 500 + `NullPointerException`）：

```text
当前以打包后的 jar 运行(classpath 根目录不可用),YAML 规则文件位于 jar 内,DevTools 无法保存。可以:1) 在源码目录运行(IDE 或 mvn spring-boot:run),规则写回 src/main/resources 下的规则文件;2) 改用 SQLite 策略 @EnableApidoc(paramType = ApiStrategyType.SQL_LITE),打包运行时规则保存在 jar 外、启动目录下的 SQLite 文件(默认 ./csap-api-doc/apidoc.db),重启后仍在;3) 1.4.0 起可使用数据库模式 @EnableApidoc(paramType = ApiStrategyType.DATABASE),规则存进应用自己的关系数据库(MySQL / PostgreSQL / 达梦 / 人大金仓),多实例自动同步;也可使用 Redis 模式 @EnableApidoc(paramType = ApiStrategyType.REDIS)。 | Running from a packaged jar ...（同样内容的英文）
```

推荐做法：

- 平时在 IDE 或 `mvn spring-boot:run` 中用 DevTools 维护规则，YAML 文件写回源码目录，随代码提交、打包；
- 必须在打包后的环境里调整规则时，用 SQLite 策略。启动目录要可写（不可写时同样返回 `1007`，说明里带上文件路径）；多个实例各有一份文件，互不同步；
- 需要多实例共享、集中维护规则的，用 [数据库模式](/guide/database-mode) 或 [Redis 模式](/guide/redis-mode)（1.4.0 起）。

数据库模式下 DevTools 侧栏有「规则记录」页（`/devtools-ui/rules`），可以查看修改记录、并排对比修改前后并回滚，见 [数据库模式 - 修改记录与回滚](/guide/database-mode#修改记录与回滚)。设置 `csap.apidoc.database.read-only=true` 后只读、不允许保存（生产环境用）。

## 后端接口

页面通过以下接口工作（路径相对于应用的 context-path）。只有 `csap.apidoc.devtool.enabled=true` 时它们才会注册。

| 方法 | 路径 | 作用 |
|------|------|------|
| GET | `/devtools-ui`、`/devtools-ui/**` | 转发到 DevTools 页面 |
| POST | `/devtools/getFields` | 获取某个类，或某个 Controller 方法参数 / 返回值的字段结构 |
| POST | `/devtools/hiddenMethod` | 目前直接返回 `true`，没有实际作用 |
| GET | `/csap/yaml/scannerApi` | 扫描 Controller 及其接口 |
| GET | `/csap/yaml/getAllMethod` | 获取某个 Controller 的全部方法 |
| POST | `/csap/yaml/addRequestParam` | 保存某个方法的请求字段规则 |
| POST | `/csap/yaml/addResponseParam` | 保存某个方法的返回字段规则 |
| GET | `/csap/yaml/getMethodValidateFields` | 读取某个请求字段已保存的规则 |
| GET | `/csap/yaml/validatePatternTypes` | 内置正则列表 |
| POST | `/csap/yaml/write`、`/csap/yaml/writeSelect`、`/csap/yaml/writeFieldMethod` | 导出完整文档。只在 `@EnableApidoc(type = YAML / JSON)` 时可用（1.1.1 起会注册对应策略）；`type` 为默认的 `ANNOTATION` 时返回“当前配置不支持”的错误 |

## 调试代理（可选）

`csap-apidoc-devtools` 还带了一个可选的反向代理接口 `POST /csap/apidoc/devtools/proxy`，供独立部署的文档 UI 绕开浏览器跨域限制发起调试请求。它与 `csap.apidoc.devtool.enabled` 无关，由单独的配置开启：

```yaml
csap:
  apidoc:
    devtools:
      proxy:
        enabled: true
        allowed-hosts:
          - localhost
          - 127.0.0.1
```

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `csap.apidoc.devtools.proxy.enabled` | `false` | 是否注册代理接口 |
| `csap.apidoc.devtools.proxy.allowed-hosts` | 空 | 允许转发的目标主机；为空时拒绝所有请求 |
| `csap.apidoc.devtools.proxy.max-body-bytes` | `5242880` | 请求体上限（字节） |
| `csap.apidoc.devtools.proxy.timeout-ms` | `30000` | 超时（毫秒） |
| `csap.apidoc.devtools.proxy.strip-headers` | `Cookie`、`X-Forwarded-For`、`X-Real-IP` | 转发前去掉的请求头 |
| `csap.apidoc.devtools.proxy.log-bodies` | `false` | 是否记录请求和响应体 |

注意配置前缀是 `csap.apidoc.devtools.proxy`（`devtools`），与 DevTools 开关 `csap.apidoc.devtool.enabled`（`devtool`）不同。

:::tip Spring Boot 3
1.1.0 中代理的自动配置类 `ai.csap.apidoc.devtools.proxy.DevtoolsProxyAutoConfiguration` 只登记在 `META-INF/spring.factories` 中，Spring Boot 3 项目需要手动 `@Import(DevtoolsProxyAutoConfiguration.class)`。1.1.1 起它也登记在 `AutoConfiguration.imports` 中，Spring Boot 2 和 3 都不需要手动导入。
:::

文档页面「设置」里的代理地址填 `/csap/apidoc/devtools/proxy` 时，1.1.1 起在线调试会按这个代理的协议转发请求，见 [跨域与代理](/guide/cors)。

## 安全注意事项

:::warning 不要在生产环境开启
DevTools 是开发工具，没有任何鉴权：

- `/devtools/getFields` 可以按类名读取 classpath 上任意类的字段结构；
- `/csap/yaml/addRequestParam`、`/csap/yaml/addResponseParam` 会在服务器上写文件或数据库；
- 调试代理是一个 SSRF 入口，可以让服务器向 `allowed-hosts` 中的主机发起任意请求。

请只在本机或受信任的开发环境开启，生产环境保持 `csap.apidoc.devtool.enabled` 和 `csap.apidoc.devtools.proxy.enabled` 为默认的 `false`。如果项目接入了 Spring Security，不要把 `/devtools/**`、`/csap/yaml/**`、`/csap/apidoc/devtools/**` 加入免登录白名单。
:::

另外，由于 DevTools 的 jar 总是随 starter 一起打包，静态页面 `/csap-api-devtools.html` 即使在关闭 DevTools 时也能被访问到；此时页面依赖的后端接口不存在，无法实际使用。

## 相关页面

- [存储策略](/guide/storage-strategies)：YAML / SQLite 规则文件格式
- [参数校验](/guide/validation)：校验规则如何执行
- [注解使用指南](/guide/annotations)
