# 数据库模式

:::tip 1.4.0 新增
数据库模式从 **Java 1.4.0、Go v0.2.0** 起提供。设计文档见 [database-mode.md](https://github.com/csap-ai/csap-apidoc/blob/main/docs/design/database-mode.md)。达梦、人大金仓在 1.4.0 **没有在真实数据库上验证**，属于试用；MySQL、PostgreSQL 有集成测试。
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
| `datasource-bean` | 空 | 要使用的 `DataSource` Bean 名。空时使用容器中唯一（或标记为 primary）的 `DataSource`，不新建连接池；有多个又没有 primary 时必须指定 |
| `dialect` | 空 | 空时按 JDBC URL 识别（`jdbc:mysql:` / `jdbc:mariadb:`、`jdbc:postgresql:`、`jdbc:dm:`、`jdbc:kingbase8:`、`jdbc:sqlite:`、`jdbc:h2:`），识别不了再看数据库产品名。可选值：`mysql`、`postgresql`、`dm`、`kingbase`、`sqlite`、`h2`（不区分大小写，也接受 `mariadb`、`postgres`、`pg`、`dm8`、`kingbase8` 等写法） |
| `table-prefix` | `csap_apidoc_` | 三张表的名字前缀，只能包含字母、数字和下划线 |
| `init-schema` | `true` | 表不存在时启动自动建表（已存在不动）。账号没有建表权限时设为 `false`，由 DBA 手工执行 [建表 SQL](#建表-sql)，此时表不存在会在启动时报错并指出对应的 SQL 文件 |
| `poll-interval` | `5s` | 多实例同步：每隔这个时间查一次全局版本号，变了就重新加载。`0` 表示不轮询 |
| `service` | 空 | 服务名。空时用 `spring.application.name`，仍为空则是 `default`。同一个库存放多个服务的规则时，**每个服务必须不同**，否则规则互相覆盖 |
| `read-only` | `false` | 见 [只读模式](#只读模式) |

规则查找始终走内存：启动时把本服务的全部规则加载进来，请求链路上不查库。

## 各数据库的注意事项

### MySQL / MariaDB

- 版本 **5.7.7 及以上**。`rule` 表的主键是 `(service VARCHAR(128), rule_key VARCHAR(512))` 的复合主键，在 `utf8mb4` 下约 2.5KB，超过旧版本 InnoDB 767 字节的索引长度限制，需要 5.7.7 起默认的大索引前缀（上限 3072 字节）；更早的版本建表会失败。MariaDB 按 MySQL 方言处理。
- 规则内容用 `LONGTEXT`，不用 `JSON` 类型，这样 5.7 和各类 MySQL 兼容库都能用。
- 库字符集建议 `utf8mb4`。

### PostgreSQL

- 版本 12 及以上。规则内容用 `TEXT`。

### 达梦（DM8）

:::warning 1.4.0 未在真实库上验证
达梦和人大金仓的方言、建表 SQL 在 1.4.0 只有语句层面的测试，**没有连真实的达梦、金仓数据库验证**，属于试用。上线前请先在测试库上跑通建表、保存、多实例同步。MySQL 和 PostgreSQL 有集成测试。
:::

- JDBC URL 以 `jdbc:dm:` 开头时自动识别。规则内容用 `CLOB`，自增主键用 `IDENTITY`。
- **驱动需要自行获取**：达梦的 JDBC 驱动不在 Maven Central，请从达梦官网或厂商提供的 Maven 仓库获取与你数据库版本匹配的驱动并加入应用依赖。框架不带驱动。

### 人大金仓（KingbaseES V8 / V9）

- JDBC URL 以 `jdbc:kingbase8:` 开头时自动识别，默认按 **PostgreSQL 兼容模式**处理。
- **驱动需要自行获取**：人大金仓的 JDBC 驱动不在 Maven Central，请从金仓官网或厂商提供的 Maven 仓库获取与数据库版本匹配的驱动。
- **兼容模式的选择**：金仓库在初始化时可以选择 PostgreSQL、Oracle、MySQL 等兼容模式。库是 PostgreSQL 兼容模式时不用额外配置；初始化为 MySQL 兼容模式的库，请设置 `csap.apidoc.database.dialect=mysql` 试用，先在测试库上确认建表和保存正常；Oracle 兼容模式没有验证，暂不支持。

### SQLite / H2

SQLite 保留，用于单机和开发环境。多个实例不要共用同一个 SQLite 文件。H2 提供了建表 SQL，主要用于测试。

:::tip 其他数据库
Oracle、SQL Server、OceanBase、openGauss / GaussDB 等不在 1.4.0 范围内。兼容 MySQL 或 PostgreSQL 协议的库可以先显式指定对应的 `dialect` 试用，但没有经过官方测试。
:::

## 建表 SQL

默认 `init-schema: true`，表不存在时框架启动时自动建表。如果应用的数据库账号没有建表权限，或者公司要求 DDL 由 DBA 执行：

1. 设置 `csap.apidoc.database.init-schema=false`；
2. 由 DBA 按方言执行下面的 SQL（表名前缀 `csap_apidoc_` 按需要替换，同时修改 `table-prefix`）。

下面的内容与 jar 里的 `META-INF/csap-apidoc/database/{mysql,postgresql,dm,kingbase,sqlite,h2}.sql` 一致，Go 版 v0.2.0 使用同样的表结构。

三张表：

| 表 | 作用 | 列 |
|----|------|----|
| `csap_apidoc_rule` | 每个接口一行规则 | `service`、`rule_key`、`rules`、`version`、`updated_by`、`updated_at`；主键 `(service, rule_key)` |
| `csap_apidoc_history` | 每次保存的修改记录 | `id`（自增）、`service`、`rule_key`、`before_rules`、`after_rules`、`version`、`updated_by`、`updated_at`；索引 `(service, rule_key, id)` |
| `csap_apidoc_meta` | 全局版本号 | `service`（主键）、`version` |

- `rule_key`：Java 是 `类全名.方法名`，Go 是 `分组.接口名`。
- `rules`：规则 JSON，与 YAML 规则同一结构，一个接口一行。规则被清空时删除这一行（修改记录里仍保留）。
- `before_rules` / `after_rules` 可空：空表示修改前没有规则 / 这次是删除。`updated_by` 没有修改人时写 `unknown`。
- 时间列都是毫秒时间戳。
- 大文本列：MySQL `LONGTEXT`，PostgreSQL / 人大金仓 `TEXT`，达梦 `CLOB`。
- 表里不使用 `DEFAULT ''`：部分数据库的 Oracle 兼容模式会把空字符串当作 NULL。

### MySQL

```sql
CREATE TABLE csap_apidoc_rule (
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    rules LONGTEXT NOT NULL,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (service, rule_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE csap_apidoc_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    before_rules LONGTEXT,
    after_rules LONGTEXT,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);

CREATE TABLE csap_apidoc_meta (
    service VARCHAR(128) NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### PostgreSQL

```sql
CREATE TABLE csap_apidoc_rule (
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    rules TEXT NOT NULL,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (service, rule_key)
);

CREATE TABLE csap_apidoc_history (
    id BIGSERIAL PRIMARY KEY,
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    before_rules TEXT,
    after_rules TEXT,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);

CREATE TABLE csap_apidoc_meta (
    service VARCHAR(128) NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL
);
```

### 达梦（DM8）

```sql
CREATE TABLE csap_apidoc_rule (
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    rules CLOB NOT NULL,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (service, rule_key)
);

CREATE TABLE csap_apidoc_history (
    id BIGINT IDENTITY(1, 1) PRIMARY KEY,
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    before_rules CLOB,
    after_rules CLOB,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);

CREATE TABLE csap_apidoc_meta (
    service VARCHAR(128) NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL
);
```

### 人大金仓（KingbaseES）

```sql
CREATE TABLE csap_apidoc_rule (
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    rules TEXT NOT NULL,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (service, rule_key)
);

CREATE TABLE csap_apidoc_history (
    id BIGSERIAL PRIMARY KEY,
    service VARCHAR(128) NOT NULL,
    rule_key VARCHAR(512) NOT NULL,
    before_rules TEXT,
    after_rules TEXT,
    version BIGINT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);

CREATE TABLE csap_apidoc_meta (
    service VARCHAR(128) NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL
);
```

SQLite 和 H2 的建表 SQL 见 jar 内同名文件（`sqlite.sql`、`h2.sql`）。

## 从 Go v0.1.x 的旧表升级

Go v0.1.x 的 SQL 规则来源建的表没有 `service` 列，`meta` 表是 `id = 1` 的单行。Java 数据库模式启动时发现这种旧表（`rule` 表存在但没有 `service` 列）会直接报错并提示升级，不会改动旧表；用 Go v0.2.0 升级：

1. 停掉所有旧版本实例，**备份数据库**。
2. 升级到 Go v0.2.0，用 `Options.Service` 指定服务名（没填就是 `default`），旧数据全部归到这个服务名下：

```go
s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: sqlstrategy.MySQL, Service: "order-service"})
if err := s.Migrate(ctx); err != nil { /* ... */ }
```

3. 或者用 `s.MigrationSQL()` 取出迁移 SQL，由 DBA 手工执行。各方言的 SQL 见 [SQL.md](https://github.com/csap-ai/csap-apidoc/blob/main/csap-apidoc-go/docs/SQL.md)。

`Migrate` 可以重复执行；SQLite、PostgreSQL、人大金仓的 DDL 在事务里，失败整体回滚，MySQL 和达梦的 DDL 会隐式提交，中途失败要按报错手工处理，所以更要先备份。

也可以不迁移：给 Java 换一个新的 `table-prefix`，让它用全新的表。

## 多实例同步

- 全局版本号存在 `{prefix}meta` 表里，按 `service` 区分。
- DevTools 保存时，在一个事务里完成：先更新全局版本号（取得行锁，保证并发保存串行）、写规则、写修改记录，然后立即刷新当前实例。
- 其他实例每隔 `poll-interval`（默认 5 秒）查一次版本号，变了就重新加载规则，不需要重启。轮询失败只打 WARN，继续使用内存中的规则。
- 发现版本变化后，本实例重新加载规则，并重新生成受影响类的文档和字段规则，运行时返回字段过滤、请求校验随之更新；DevTools 关闭的生产实例会整体重新扫描一次文档。回滚同样会触发其他实例的同步。
- `poll-interval: 0` 关闭轮询，此时其他实例要重启才会读到新规则。
- 可选的即时通知（例如 Redis）见 [Redis 模式](/guide/redis-mode)，不是默认行为。

## 修改记录与回滚

每次保存都会往 `{prefix}history` 写一条：接口（`rule_key`）、修改前后的内容（`before_rules` / `after_rules`）、版本号、修改人、时间。Java 与 Go 的记录格式一致。

### DevTools「规则记录」页

DevTools 的侧栏有「规则记录」页（`/devtools-ui/rules`），页面语言跟随浏览器，可切换中 / 英：

- 列表显示时间、规则键、操作（新增 / 修改 / 删除）、版本、修改人，可按规则键筛选；
- 点开一条可以并排查看修改前后的差异；
- 回滚需要确认。回滚本身会生成一个新版本，修改人记为 `devtools-rollback`，其他实例随后按 `poll-interval` 同步；
- 在 DevTools 里保存规则时，修改人记为 `devtools`；
- 只读模式下不能回滚（接口返回 `code = 1007`）；没有使用数据库模式时，页面显示如何切换到数据库模式的说明。

页面对应的接口（相对于应用的 context-path，只有开启 DevTools 时注册）：

| 接口 | 作用 |
|------|------|
| `GET devtools/rules/status` | 当前是否是数据库模式、是否只读 |
| `GET devtools/rules/history` | 修改记录，可按规则键筛选 |
| `POST devtools/rules/rollback` | 回滚，请求体 `{"changeId": 记录ID}` |

`history` 表只增不改，需要完整、长期的审计追溯时，由 [团队版](/roadmap) 在这套表的基础上提供（成员、审批、防篡改记录等）。

## 只读模式

`csap.apidoc.database.read-only=true` 时：

- 只读取库里的规则，不建表、不写入；表必须已经存在（缺表时启动报错，提示执行建表 SQL）；
- DevTools 保存和回滚都被拒绝，返回明确的中英文提示（说明处于只读状态、请在有写权限的环境修改、本实例会按 `poll-interval` 自动同步），而不是静默失败。

适合生产环境：规则在测试环境（或之后团队版的审批流）里修改，生产环境只读取。轮询同步仍然有效，在别处改了规则，只读的实例也会按 `poll-interval` 加载到。

## 迁移

迁移工具在以下存储之间搬运规则，规则内容是同一种 JSON 结构，不做转换：

| 方向 | 用途 |
|------|------|
| YAML 目录 / SQLite 文件 → 数据库 / Redis | 从 YAML 或 SQLite 切换到数据库模式或 Redis 模式 |
| 数据库 / Redis → YAML 文件 | 导出做备份，纳入 Git 审查 |

行为：

- **默认不覆盖**：目标里已有且内容相同的规则算"已一致"，内容不同的算"冲突"并跳过，在报告里列出；设置 `overwrite=true` 才覆盖。
- **可以回滚**：导入按一次保存写入，修改人是 `migrate`，在修改记录里能看到、能回滚；内容相同的规则不写，不产生记录。
- **其他实例同步**：导入完成后，当前实例立即重新加载，其他实例按轮询或订阅同步。
- 空规则（没有任何字段）会被忽略；
- **不搬运字段库的项目模板**（保留键 `@templates`），项目模板需要另行迁移。

### Java

模块 `csap-apidoc-migrate`，在应用启动时用启动参数运行，设置了 `csap.apidoc.migrate.from` 才会执行，执行完打印报告：

| 参数 | 说明 |
|------|------|
| `csap.apidoc.migrate.from` | 来源：`yaml`、`sql_lite`（也可写 `sqlite`）、`database`、`redis` |
| `csap.apidoc.migrate.to` | 导入的目标：`database` 或 `redis` |
| `csap.apidoc.migrate.export-to` | 导出成 YAML 的目录（来源只能是 `database` 或 `redis`）；文件为 `{目录}/method-field/api-method-field.yaml` |
| `csap.apidoc.migrate.overwrite` | 默认 `false`；`true` 时覆盖目标里内容不同的规则 |
| `csap.apidoc.migrate.dry-run` | 默认 `false`；`true` 只打印报告，不写入 |

`to` 和 `export-to` 至少设置一个；来源和目标不能相同；目标处于只读模式时不能导入。

```bash
# 先演练：YAML 目录导入数据库，只看报告
java -jar app.jar --csap.apidoc.migrate.from=yaml --csap.apidoc.migrate.to=database --csap.apidoc.migrate.dry-run=true
# 确认后正式导入
java -jar app.jar --csap.apidoc.migrate.from=yaml --csap.apidoc.migrate.to=database
# 数据库导出成 YAML 备份
java -jar app.jar --csap.apidoc.migrate.from=database --csap.apidoc.migrate.export-to=./exported-rules
```

报告列出：新增、覆盖、已一致、冲突（已跳过）、空规则（已忽略）的数量和规则键。例如数据库导入 Redis，第一次"新增 1"，再运行一次变成"已一致 1"。

来源是 YAML 或 SQLite 时，需要在**原来的应用**里运行（它要按 `@EnableApidoc` 的 `path`、`fileName` 找到规则文件），同时配置好目标存储（例如 `csap.apidoc.database.*` 或 Redis 连接）。也可以在代码里用 `RuleMigrator` 编程调用。

### Go

`strategy.Import(ctx, src, dst, ImportOptions{Overwrite, DryRun, By})` 把任意规则来源（YAML 等）导入 SQL 或 Redis 存储，返回 `Created` / `Updated` / `Unchanged` / `Conflicts` 四个列表，修改人默认 `migrate`。规则为空的接口跳过；某条写入失败不会中断，最后一并返回错误。详见仓库 [IMPORT.md](https://github.com/csap-ai/csap-apidoc/blob/main/csap-apidoc-go/docs/IMPORT.md)。

原来的 `paramType = SQL_LITE` 继续可用，只是生产环境、多实例推荐改用数据库模式。Go 旧表的升级见 [从 Go v0.1.x 的旧表升级](#从-go-v01x-的旧表升级)。

## Go 版

Go v0.2.0 的 SQL 规则来源改用上面这套表，Java 与 Go 的服务可以指向同一个库，各用各的 `service` 名；规则键不同（Java 是 `类全名.方法名`，Go 是 `分组.接口名`），规则 JSON 结构相同。API、方言和升级步骤见 [Go 版](/go/#sql-规则来源v020)。

## Redis 模式

没有关系数据库、或希望规则跟随已有 Redis 时，见 [Redis 模式](/guide/redis-mode)。

## 相关页面

- [Redis 模式](/guide/redis-mode)
- [存储策略与返回过滤](/guide/storage-strategies)：各种规则来源和字段如何进入文档
- [DevTools](/guide/devtools)：保存规则、打包运行时能否保存
- [配置参考](/guide/configuration)
- [Go 版](/go/)
