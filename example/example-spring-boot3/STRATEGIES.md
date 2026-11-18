# 参数存储策略

`@EnableApidoc` 通过 `paramType` 选择接口参数文档的存储方式(`ApiStrategyType`):

| 取值 | 说明 |
|---|---|
| `ANNOTATION`(默认) | 只读注解,不生成外部文件 |
| `YAML` | 参数配置存成 YAML 文件,适合纳入版本管理 |
| `JSON` | 同上,格式为 JSON |
| `SQL_LITE` | 存成 SQLite 数据库文件,配合 DevTools 可视化维护 |

一个应用实例同一时间只能使用一种策略。各策略的详细说明以官网 https://apidoc.csap.ai 为准,本页只说明示例项目里的用法。

## 示例项目当前使用的策略

两个示例的启动类都固定使用 `YAML`:

```java
@SpringBootApplication
@EnableApidoc(value = "ai.csap.example", paramType = ApiStrategyType.YAML)
public class ExampleApplication { }
```

(两个示例都用 `value` 按包扫描;只想文档化部分 Controller 时可改用 `apiPackageClasses = {...}` 直接列出类。)

`application-sqlite.yml`、`application-yaml.yml` 只是带不同日志与应用名的 profile 样例(端口与默认相同),**不会**切换策略;策略只由启动类上的 `@EnableApidoc` 决定。要试其他策略,请直接修改 `@EnableApidoc`。

## ANNOTATION 策略

```java
@EnableApidoc("ai.csap.example")
```

参数文档完全来自代码里的注解,不生成外部文件,适合以代码为准的场景。

## YAML 策略

```java
@EnableApidoc(
    value = "ai.csap.example",
    paramType = ApiStrategyType.YAML,
    path = "csap-api-doc"      // 可省略,默认值即 csap-api-doc
)
```

- 文件写在 classpath 根目录下的 `path` 目录中(开发时即 `target/classes/csap-api-doc/`)
- 在 DevTools 中编辑后会写回这些文件;需要保留修改时,把它们放进 `src/main/resources/<path>/` 并提交到 Git
- 示例测试资源见 `src/test/resources/csap-api-doc/method-field/`

## SQL_LITE 策略

```java
@EnableApidoc(
    value = "ai.csap.example",
    paramType = ApiStrategyType.SQL_LITE,
    path = "csap-data",
    fileName = "apidoc-db"
)
```

- 数据库文件为 `<path>/<fileName>.db`(位于 classpath 根目录下),首次使用时自动建表
- 适合通过 DevTools 在线调整参数、由非开发人员维护文档的场景
- 二进制文件不便做 diff 与代码评审

## DATABASE 策略(1.4.0)

字段规则存进**应用自己的关系数据库**(MySQL 5.7+/8、PostgreSQL 12+、达梦 DM8、人大金仓),用应用已有的 `DataSource`,
只依赖 JDBC,不自带驱动;多实例通过轮询版本号自动同步,保存会留下修改记录,可回滚。

最小用法(示例项目默认不启用,需要自己加依赖并改 `@EnableApidoc`):

1. 加依赖:`spring-boot-starter-jdbc` 和你的数据库驱动(试用可用 `com.h2database:h2`)
2. `@EnableApidoc(value = "ai.csap.example", paramType = ApiStrategyType.DATABASE)`
3. 配置数据源(其余 `csap.apidoc.database.*` 都有默认值),可直接参考 `src/main/resources/application-database.yml`

首次使用时自动建表(表前缀默认 `csap_apidoc_`,`init-schema: false` 可关闭,建表 SQL 在 jar 的
`META-INF/csap-apidoc/database/{mysql,postgresql,dm,kingbase,sqlite,h2}.sql`)。
`service` 默认取 `spring.application.name`,同一套表里靠它区分不同服务;生产环境可开 `read-only: true`,只读不写。

## 策略对比

| 特性 | ANNOTATION | YAML | SQL_LITE | DATABASE |
|---------|-----------|------|----------|----------|
| 外部文件 | 无 | YAML 文件 | `.db` 文件 | 应用自己的数据库 |
| 可视化配置 | 否 | DevTools / 手工编辑 | DevTools | DevTools |
| 版本管理 | 随代码 | 文本,易 diff | 二进制,不便 | 修改记录 + 回滚 |
| 参数是否改代码 | 是 | 否 | 否 | 否 |

## 切换策略

1. 修改 `@EnableApidoc` 的 `paramType`(以及 `path` / `fileName`)
2. 重启应用

旧策略里的数据不会自动迁移。

## 在注解参数中引用配置

`@EnableApidoc` 的字符串属性支持 `${...}` 占位符:

```java
@EnableApidoc(
    value = "ai.csap.example",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${apidoc.path:csap-data}",
    fileName = "${apidoc.filename:apidoc-db}"
)
```

```yaml
apidoc:
  path: csap-data
  filename: example-db
```

## 常见问题

**能同时用多种策略吗?** 不能,同一实例只有一个 `paramType`。

**不同环境用不同路径?** 可以,用 `${...}` 占位符让 `path` / `fileName` 随环境配置变化。生产环境建议关闭 DevTools(`csap.apidoc.devtool.enabled: false`)。
