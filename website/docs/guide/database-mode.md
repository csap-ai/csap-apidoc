# 数据库模式

:::warning 1.4.0 计划中，实现进行中
数据库模式计划随 **Java 1.4.0（2026-12-22）** 和 **Go v0.2.0** 发布，当前已发布的版本（Java 1.3.0、Go v0.1.1）**没有**这个功能。本页按已审定的方案编写，细节以发布时的代码为准。设计文档见 [database-mode.md](https://github.com/csap-ai/csap-apidoc/blob/main/docs/design/database-mode.md)。
:::

数据库模式把字段规则存进业务**已有的关系数据库**，而不是 classpath 里的 YAML 文件或单机的 SQLite 文件。支持 MySQL / MariaDB、PostgreSQL、达梦（DM8）、人大金仓（KingbaseES），SQLite 保留用于单机和开发环境。**Java 与 Go 使用同一套表**，同一个库里两种语言的服务可以各存各的规则，DevTools 和后续的团队版读写的也是这套表。

规则的内容、字段键、`validate` 写法与 [存储策略](/guide/storage-strategies) 中的 YAML 完全相同，只是换了存放的位置。

## 什么时候选它

| 场景 | 建议 |
|------|------|
| 同一个服务部署多台，希望在一台上改规则、其他台几秒内生效 | 数据库模式 |
| 容器部署，重新发布后不能丢掉 DevTools 里改过的规则 | 数据库模式 |
| 企业生产环境只允许使用已有的 MySQL / PostgreSQL / 达梦 / 人大金仓 | 数据库模式 |
| 希望规则集中管理、有修改记录、能回滚 | 数据库模式 |
| 规则很少改，希望随代码评审、版本库管理 | YAML |
| 单机、开发环境，不想依赖外部数据库 | SQLite 或 YAML |

各模式的完整对比见 [存储策略 - 如何选择](/guide/storage-strategies#如何选择)。

## 快速开始（Java）

1. 应用里已有 `DataSource`（JDBC 驱动由应用自己引入，框架不带驱动）。`csap-apidoc-boot-starter` 已经包含数据库模式的模块 `csap-apidoc-database`，不需要额外依赖；Spring Boot 2 和 3 使用同一个 jar。
2. 开启：

```java
@SpringBootApplication
@EnableApidoc(apiPackages = "com.example.controller", paramType = ApiStrategyType.DATABASE)
public class Application { /* ... */ }
```

3. 配置（都有默认值，通常什么都不用写）：

```yaml
csap:
  apidoc:
    database:
      datasource-bean: ""          # 指定 DataSource Bean 名，多数据源时用；空 = 应用的主 DataSource
      dialect: ""                  # 空 = 按 JDBC URL 识别
      table-prefix: csap_apidoc_   # 表名前缀
      init-schema: true            # 启动时自动建表
      poll-interval: 5s            # 多实例同步的轮询间隔，0 = 不轮询
      service: ""                  # 服务名；空时取 spring.application.name，再空为 default
      read-only: false             # true = 只读，不允许保存
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `datasource-bean` | 空 | 要使用的 `DataSource` Bean 名。空时使用应用的主 `DataSource`，不新建连接池 |
| `dialect` | 空 | 空时按 JDBC URL 识别：`mysql` / `mariadb`、`postgresql`、`dm`、`kingbase8`、`sqlite`、`h2`。识别不了或需要覆盖时显式指定 |
| `table-prefix` | `csap_apidoc_` | 三张表的名字前缀 |
| `init-schema` | `true` | 启动时自动建表（表已存在则跳过）。账号没有建表权限时设为 `false`，由 DBA 手工执行 [建表 SQL](#建表-sql) |
| `poll-interval` | `5s` | 多实例同步：每隔这个时间查一次全局版本号，变了就重新加载。`0` 表示不轮询 |
| `service` | 空 | 服务名。空时用 `spring.application.name`，仍为空则是 `default`。同一个库存放多个服务的规则时，**每个服务必须不同**，否则规则互相覆盖 |
| `read-only` | `false` | 见 [只读模式](#只读模式) |

规则查找始终走内存：启动时把本服务的全部规则加载进来，请求链路上不查库。

## 各数据库的注意事项

### MySQL / MariaDB

- 版本 **5.7.7 及以上**。表的主键是 `(service, rule_key)` 的复合主键，在 `utf8mb4` 下索引长度会超过 767 字节，需要 InnoDB 的大前缀索引（5.7.7 起默认支持；更早的版本建表会报 `Specified key was too long`）。
- 规则内容用 `LONGTEXT`，不用 `JSON` 类型，这样 5.7 和各类 MySQL 兼容库都能用。
- 库字符集建议 `utf8mb4`。

### PostgreSQL

- 版本 12 及以上。规则内容用 `TEXT`。

### 达梦（DM8）

- JDBC URL 以 `jdbc:dm:` 开头时自动识别。规则内容用 `CLOB`，自增主键用 `IDENTITY`，保存时用 `SELECT ... FOR UPDATE` 行锁串行化并发保存。
- **驱动需要自行获取**：达梦的 JDBC 驱动由厂商提供，请从达梦官网或厂商的 Maven 仓库获取与你数据库版本匹配的驱动并加入应用依赖。框架不带驱动。
- 达梦默认把空字符串当作 NULL，这也是建表语句里不使用字符串默认值（`DEFAULT ''`）的原因。

### 人大金仓（KingbaseES V8 / V9）

- JDBC URL 以 `jdbc:kingbase8:` 开头时自动识别，默认按 **PostgreSQL 兼容模式**处理。
- **驱动需要自行获取**：从人大金仓官网或厂商仓库下载与数据库版本匹配的 JDBC 驱动。
- **兼容模式的选择**：库在初始化时可以选择 PostgreSQL、Oracle、MySQL 等兼容模式。库是 PostgreSQL 兼容模式时不用额外配置；初始化为 MySQL 或 Oracle 兼容模式的库，请用 `csap.apidoc.database.dialect` 显式指定与之匹配的方言，并先在测试库上确认建表和保存都正常。

### SQLite / H2

SQLite 保留，用于单机和开发环境。多个实例不要共用同一个 SQLite 文件。H2 提供了建表 SQL，主要用于测试。

:::tip 其他数据库
Oracle、SQL Server、OceanBase、openGauss / GaussDB 等不在 1.4.0 范围内。兼容 MySQL 或 PostgreSQL 协议的库可以先显式指定对应的 `dialect` 试用，但没有经过官方测试。
:::

## 建表 SQL

默认 `init-schema: true`，框架启动时自动建表。如果应用的数据库账号没有建表权限，或者公司要求 DDL 由 DBA 执行：

1. 设置 `csap.apidoc.database.init-schema=false`；
2. 由 DBA 按方言执行下面的 SQL（表名前缀 `csap_apidoc_` 按需要替换）。

同样的文件在 jar 里：`META-INF/csap-apidoc/database/{mysql,postgresql,dm,kingbase,sqlite,h2}.sql`。**以 jar 里的文件为准**，下面是同一表结构的参考写法。

三张表：

| 表 | 作用 | 列 |
|----|------|----|
| `csap_apidoc_rule` | 每个接口一行规则 | `service`、`rule_key`、`rules`、`version`、`updated_by`、`updated_at`；主键 `(service, rule_key)` |
| `csap_apidoc_history` | 每次保存的修改记录 | `id`（自增）、`service`、`rule_key`、`before_rules`、`after_rules`、`version`、`updated_by`、`updated_at`；索引 `(service, rule_key, id)` |
| `csap_apidoc_meta` | 全局版本号 | `service`（主键）、`version` |

- `rule_key`：Java 是 `类全名.方法名`，Go 是 `分组.接口名`。
- `rules`：规则 JSON，与 YAML 规则同一结构，一个接口一行。规则被清空时删除这一行（修改记录里仍保留）。
- 时间列都是毫秒时间戳。
- 大文本列：MySQL `LONGTEXT`，PostgreSQL / 人大金仓 `TEXT`，达梦 `CLOB`。

MySQL：

```sql
CREATE TABLE IF NOT EXISTS csap_apidoc_rule (
  service    VARCHAR(128) NOT NULL,
  rule_key   VARCHAR(255) NOT NULL,
  rules      LONGTEXT     NOT NULL,
  version    BIGINT       NOT NULL,
  updated_by VARCHAR(128),
  updated_at BIGINT       NOT NULL,
  PRIMARY KEY (service, rule_key)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS csap_apidoc_history (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  service      VARCHAR(128) NOT NULL,
  rule_key     VARCHAR(255) NOT NULL,
  before_rules LONGTEXT,
  after_rules  LONGTEXT,
  version      BIGINT       NOT NULL,
  updated_by   VARCHAR(128),
  updated_at   BIGINT       NOT NULL,
  KEY idx_csap_apidoc_history (service, rule_key, id)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS csap_apidoc_meta (
  service VARCHAR(128) NOT NULL PRIMARY KEY,
  version BIGINT       NOT NULL
) DEFAULT CHARSET = utf8mb4;
```

PostgreSQL / 人大金仓（PostgreSQL 兼容模式）：

```sql
CREATE TABLE IF NOT EXISTS csap_apidoc_rule (
  service    VARCHAR(128) NOT NULL,
  rule_key   VARCHAR(255) NOT NULL,
  rules      TEXT         NOT NULL,
  version    BIGINT       NOT NULL,
  updated_by VARCHAR(128),
  updated_at BIGINT       NOT NULL,
  PRIMARY KEY (service, rule_key)
);

CREATE TABLE IF NOT EXISTS csap_apidoc_history (
  id           BIGSERIAL    PRIMARY KEY,
  service      VARCHAR(128) NOT NULL,
  rule_key     VARCHAR(255) NOT NULL,
  before_rules TEXT,
  after_rules  TEXT,
  version      BIGINT       NOT NULL,
  updated_by   VARCHAR(128),
  updated_at   BIGINT       NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_csap_apidoc_history ON csap_apidoc_history (service, rule_key, id);

CREATE TABLE IF NOT EXISTS csap_apidoc_meta (
  service VARCHAR(128) PRIMARY KEY,
  version BIGINT       NOT NULL
);
```

达梦（DM8）：

```sql
CREATE TABLE csap_apidoc_rule (
  service    VARCHAR(128) NOT NULL,
  rule_key   VARCHAR(255) NOT NULL,
  rules      CLOB         NOT NULL,
  version    BIGINT       NOT NULL,
  updated_by VARCHAR(128),
  updated_at BIGINT       NOT NULL,
  PRIMARY KEY (service, rule_key)
);

CREATE TABLE csap_apidoc_history (
  id           BIGINT IDENTITY(1,1) PRIMARY KEY,
  service      VARCHAR(128) NOT NULL,
  rule_key     VARCHAR(255) NOT NULL,
  before_rules CLOB,
  after_rules  CLOB,
  version      BIGINT       NOT NULL,
  updated_by   VARCHAR(128),
  updated_at   BIGINT       NOT NULL
);
CREATE INDEX idx_csap_apidoc_history ON csap_apidoc_history (service, rule_key, id);

CREATE TABLE csap_apidoc_meta (
  service VARCHAR(128) PRIMARY KEY,
  version BIGINT       NOT NULL
);
```

:::info
上面的列长度和约束是参考写法，发布时会与 jar 内的文件核对。改了表名前缀时，同步修改 `table-prefix`。
:::

## 多实例同步

- 全局版本号存在 `{prefix}meta` 表里，按 `service` 区分。
- DevTools 保存时，在一个事务里完成：版本号加 1（行锁，保证并发保存串行）、写规则、写修改记录，然后立即刷新当前实例。
- 其他实例每隔 `poll-interval`（默认 5 秒）查一次版本号，变了就重新加载规则。所以其他实例在**几秒内**生效，不需要重启。
- `poll-interval: 0` 关闭轮询，此时其他实例要重启才会读到新规则。
- 可选的即时通知（例如 Redis）见 [Redis 模式](#redis-模式规划中)，不是默认行为。

## 修改记录与回滚

每次保存都会往 `{prefix}history` 写一条：接口（`rule_key`）、修改前后的内容（`before_rules` / `after_rules`）、版本号、修改人、时间。DevTools 里可以查看某个接口的修改记录并回滚到之前的内容。Java 与 Go 的记录格式一致。

`history` 表只增不改，需要完整、长期的审计追溯时，由 [团队版](/roadmap) 在这套表的基础上提供（成员、审批、防篡改记录等）。

## 只读模式

`csap.apidoc.database.read-only=true` 时：

- 只读取库里的规则，不建表，不写入；
- DevTools 保存时返回明确的提示，而不是静默失败。

适合生产环境：规则在测试环境（或之后团队版的审批流）里修改，生产环境只读取。多实例的轮询同步仍然有效，在别处改了规则，只读的实例也会在几秒内加载到。

## 从 YAML / SQLite 迁移

:::info 计划中
YAML 目录 / SQLite 文件与数据库之间互相导入导出的迁移工具（`csap-apidoc-migrate`）**尚未完成**，计划在 1.4.0 发布前提供。完成后会在这里补充用法。导入前会检查冲突、默认不覆盖已有规则，导入本身记一条修改记录、可以回滚。
:::

在工具提供之前，可以：

- 保留原来的 YAML / SQLite，先在测试环境用数据库模式试用；
- 用 DevTools 在数据库模式下重新勾选需要的规则（规则内容与 YAML 同一结构）。

原来的 `paramType = SQL_LITE` 继续可用，只是生产环境、多实例推荐改用数据库模式。

## Go 版

Go 版已有 SQL 规则来源 `strategy/sql`（v0.1.x）。v0.2.0 计划：

- 改用上面这套表（增加 `service` 列，列名 `rules` / `before_rules` / `after_rules` 沿用 Go 现有列名），`Options.Service` 指定服务名；
- 新增达梦、人大金仓方言；
- 提供 v0.1.x 旧表的迁移。

细节见 [Go 版](/go/#字段规则yaml与-java-兼容)。Java 与 Go 的服务可以共用同一个库，规则键不同（Java 是 `类全名.方法名`，Go 是 `分组.接口名`），互不影响。

## Redis 模式（规划中）

1.4.0 计划同时提供 Redis 模式：规则存进 Redis，多实例订阅变更后即时同步，Java 与 Go 使用同一种数据结构。**目前还没有实现**，用法和配置项以发布时的文档为准。已确定的取舍：修改记录只保留最近若干条，需要完整、长期的审计追溯时请用数据库模式；Redis 需要开启持久化，否则重启会丢规则。

## 相关页面

- [存储策略与返回过滤](/guide/storage-strategies)：各种规则来源和字段如何进入文档
- [DevTools](/guide/devtools)：保存规则、打包运行时能否保存
- [配置参考](/guide/configuration)
- [Go 版](/go/)
