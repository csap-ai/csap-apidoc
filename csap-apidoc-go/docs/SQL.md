# SQL 规则来源:表结构与升级

`strategy/sql` 的表结构从 v0.2.0 起与 Java 1.4.0 的数据库模式相同:Go 和 Java 服务可以指向同一个库,用 `service` 列区分各自的规则。

## 表结构

表名前缀默认 `csap_apidoc_`(`Options.TablePrefix`)。时间一律是毫秒时间戳。

| 表 | 列 |
|---|---|
| `{prefix}rule` | `service` VARCHAR(128)、`rule_key` VARCHAR(512)、`rules`(大文本,规则 JSON)、`version` BIGINT、`updated_by` VARCHAR(128)、`updated_at` BIGINT;主键 `(service, rule_key)` |
| `{prefix}history` | `id`(自增主键)、`service`、`rule_key`、`before_rules`、`after_rules`(可空,空表示修改前没有规则 / 这次是删除)、`version`、`updated_by`、`updated_at`;索引 `{prefix}history_key (service, rule_key, id)` |
| `{prefix}meta` | `service` VARCHAR(128) 主键、`version` BIGINT:该服务的版本号,每次保存加 1 |

| 方言 | 大文本 | 自增主键 | 说明 |
|---|---|---|---|
| `SQLite` | `TEXT` | `INTEGER PRIMARY KEY AUTOINCREMENT` | |
| `Postgres` | `TEXT` | `BIGSERIAL PRIMARY KEY` | 占位符 `$n` |
| `MySQL` | `LONGTEXT` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | 表选项 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` |
| `DM`(达梦 DM8) | `CLOB` | `BIGINT IDENTITY(1, 1) PRIMARY KEY` | |
| `Kingbase`(人大金仓) | `TEXT` | `BIGSERIAL PRIMARY KEY` | 按 PostgreSQL 兼容模式支持;MySQL 兼容模式的库可用 `MySQL` 方言试用;Oracle 兼容模式未验证,暂不支持 |

没有用 `DEFAULT ''`:达梦和 Oracle 兼容模式会把空串当成 NULL。`updated_by` 为空时写 `unknown`。

规则键:Go 是 `分组.接口名`,Java 是 `类全名.方法名`,所以一个库里的 Go 服务和 Java 服务各用各的 `service` 名即可,规则 JSON 结构两边一样:

```json
{"request": {"<参数名>.<字段>": {"required": true, "include": null, "paramType": "", "description": "",
              "validate": [{"type": "NotEmpty", "pattern": "", "code": "", "message": "", "descr": "", "level": 1}]}},
 "response": {"data.rows.id": {"include": true}}}
```

## 使用

```go
s := sqlstrategy.New(sqlstrategy.Options{
    DB:      db,
    Dialect: sqlstrategy.MySQL, // SQLite / Postgres / MySQL / DM / Kingbase
    Service: "order-service",   // 服务名,默认 "default";所有读写都按它过滤
})
if err := s.EnsureSchema(ctx); err != nil { ... } // 建表;发现旧表返回 sqlstrategy.ErrLegacySchema
```

- `EnsureSchema` 逐表探测(`SELECT service FROM <表> WHERE 1 = 0`),不存在才建,已存在不动;发现 v0.1.x 的旧表(没有 `service` 列)时返回 `ErrLegacySchema`,**不会自动改旧表**。
- `Schema()` 返回建表语句,想自己执行(或交给 DBA)时用。
- 同一个库里各服务的规则、版本号、修改记录、回滚互不影响;回滚别的服务的修改记录会返回 `strategy.ErrNoChange`。
- `Notifier`(如 Redis Pub/Sub)接口保持 v0.1.x 的签名,消息不带服务名;多个服务共用一个通知频道时,收到通知的实例多查一次本服务的版本号(版本没变就不重新加载),结果正确。想避免多余的查询,让通知实现同时实现 `ServiceNotifier`(`PublishService` / `SubscribeService`,消息带服务名),策略会优先使用它并忽略其他服务的消息。

## 从 v0.1.x 升级到 v0.2.0

v0.1.x 的表没有 `service` 列,`meta` 是 `id = 1` 的单行。升级会:

1. `rule`:加 `service`,主键改为 `(service, rule_key)`,空的 `updated_by` 改成 `unknown`;
2. `history`:加 `service`,索引改成 `(service, rule_key, id)`;
3. `meta`:由单行改成每个服务一行,旧版本号保留。

旧数据全部归到你在 `Options.Service` 里指定的服务名(没填就是 `default`)。**迁移前先停掉所有旧版本实例并备份数据库**。

### 方式一:`Migrate`

```go
s := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: sqlstrategy.MySQL, Service: "order-service"})
if err := s.Migrate(ctx); err != nil { ... }
```

`Migrate` 逐表检查,只处理旧结构的表,可以重复执行;最后调用 `EnsureSchema` 补齐缺的表。SQLite、PostgreSQL、金仓的 DDL 在事务里,失败整体回滚;MySQL 和达梦的 DDL 会隐式提交,中途失败要按报错手工处理,所以更要先备份。

### 方式二:手工执行 SQL

`s.MigrationSQL()` 返回下面这组语句(`default` 换成你的服务名,前缀 `csap_apidoc_` 换成你的前缀)。三张表要么都是旧结构、要么就只执行对应表的那一段,已经升级的表不要重复执行。重建表用"建新表、拷贝、删旧表、改名"的办法,各方言通用。

#### MySQL

```sql
CREATE TABLE csap_apidoc_rule_v2 (
	service VARCHAR(128) NOT NULL,
	rule_key VARCHAR(512) NOT NULL,
	rules LONGTEXT NOT NULL,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL,
	updated_at BIGINT NOT NULL,
	PRIMARY KEY (service, rule_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO csap_apidoc_rule_v2 (service, rule_key, rules, version, updated_by, updated_at) SELECT 'default', rule_key, rules, version, CASE WHEN updated_by IS NULL OR updated_by = '' THEN 'unknown' ELSE updated_by END, updated_at FROM csap_apidoc_rule;
DROP TABLE csap_apidoc_rule;
ALTER TABLE csap_apidoc_rule_v2 RENAME TO csap_apidoc_rule;
DROP INDEX csap_apidoc_history_key ON csap_apidoc_history;
ALTER TABLE csap_apidoc_history ADD COLUMN service VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);
CREATE TABLE csap_apidoc_meta_v2 (
	service VARCHAR(128) NOT NULL PRIMARY KEY,
	version BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO csap_apidoc_meta_v2 (service, version) SELECT 'default', version FROM csap_apidoc_meta WHERE id = 1;
DROP TABLE csap_apidoc_meta;
ALTER TABLE csap_apidoc_meta_v2 RENAME TO csap_apidoc_meta;
```

#### PostgreSQL / 人大金仓

```sql
CREATE TABLE csap_apidoc_rule_v2 (
	service VARCHAR(128) NOT NULL,
	rule_key VARCHAR(512) NOT NULL,
	rules TEXT NOT NULL,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL,
	updated_at BIGINT NOT NULL,
	PRIMARY KEY (service, rule_key)
);
INSERT INTO csap_apidoc_rule_v2 (service, rule_key, rules, version, updated_by, updated_at) SELECT 'default', rule_key, rules, version, CASE WHEN updated_by IS NULL OR updated_by = '' THEN 'unknown' ELSE updated_by END, updated_at FROM csap_apidoc_rule;
DROP TABLE csap_apidoc_rule;
ALTER TABLE csap_apidoc_rule_v2 RENAME TO csap_apidoc_rule;
DROP INDEX csap_apidoc_history_key;
ALTER TABLE csap_apidoc_history ADD COLUMN service VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);
CREATE TABLE csap_apidoc_meta_v2 (
	service VARCHAR(128) NOT NULL PRIMARY KEY,
	version BIGINT NOT NULL
);
INSERT INTO csap_apidoc_meta_v2 (service, version) SELECT 'default', version FROM csap_apidoc_meta WHERE id = 1;
DROP TABLE csap_apidoc_meta;
ALTER TABLE csap_apidoc_meta_v2 RENAME TO csap_apidoc_meta;
```

#### 达梦 DM8

```sql
CREATE TABLE csap_apidoc_rule_v2 (
	service VARCHAR(128) NOT NULL,
	rule_key VARCHAR(512) NOT NULL,
	rules CLOB NOT NULL,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL,
	updated_at BIGINT NOT NULL,
	PRIMARY KEY (service, rule_key)
);
INSERT INTO csap_apidoc_rule_v2 (service, rule_key, rules, version, updated_by, updated_at) SELECT 'default', rule_key, rules, version, CASE WHEN updated_by IS NULL OR updated_by = '' THEN 'unknown' ELSE updated_by END, updated_at FROM csap_apidoc_rule;
DROP TABLE csap_apidoc_rule;
ALTER TABLE csap_apidoc_rule_v2 RENAME TO csap_apidoc_rule;
DROP INDEX csap_apidoc_history_key;
ALTER TABLE csap_apidoc_history ADD COLUMN service VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);
CREATE TABLE csap_apidoc_meta_v2 (
	service VARCHAR(128) NOT NULL PRIMARY KEY,
	version BIGINT NOT NULL
);
INSERT INTO csap_apidoc_meta_v2 (service, version) SELECT 'default', version FROM csap_apidoc_meta WHERE id = 1;
DROP TABLE csap_apidoc_meta;
ALTER TABLE csap_apidoc_meta_v2 RENAME TO csap_apidoc_meta;
```

#### SQLite

```sql
CREATE TABLE csap_apidoc_rule_v2 (
	service VARCHAR(128) NOT NULL,
	rule_key VARCHAR(512) NOT NULL,
	rules TEXT NOT NULL,
	version BIGINT NOT NULL,
	updated_by VARCHAR(128) NOT NULL,
	updated_at BIGINT NOT NULL,
	PRIMARY KEY (service, rule_key)
);
INSERT INTO csap_apidoc_rule_v2 (service, rule_key, rules, version, updated_by, updated_at) SELECT 'default', rule_key, rules, version, CASE WHEN updated_by IS NULL OR updated_by = '' THEN 'unknown' ELSE updated_by END, updated_at FROM csap_apidoc_rule;
DROP TABLE csap_apidoc_rule;
ALTER TABLE csap_apidoc_rule_v2 RENAME TO csap_apidoc_rule;
DROP INDEX csap_apidoc_history_key;
ALTER TABLE csap_apidoc_history ADD COLUMN service VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX csap_apidoc_history_key ON csap_apidoc_history (service, rule_key, id);
CREATE TABLE csap_apidoc_meta_v2 (
	service VARCHAR(128) NOT NULL PRIMARY KEY,
	version BIGINT NOT NULL
);
INSERT INTO csap_apidoc_meta_v2 (service, version) SELECT 'default', version FROM csap_apidoc_meta WHERE id = 1;
DROP TABLE csap_apidoc_meta;
ALTER TABLE csap_apidoc_meta_v2 RENAME TO csap_apidoc_meta;
```


## 测试

`integration/` 模块里的测试(SQLite 默认运行,设置 `CSAP_TEST_PG_DSN` / `CSAP_TEST_MYSQL_DSN` 加测 PostgreSQL / MySQL)覆盖:建表幂等、两个服务共用一个库互不干扰、旧表迁移(`Migrate` 与手工 SQL 两种方式)、带服务名的通知、以及与 Java 约定的表结构和规则 JSON 一致(`interop_test.go`,含读取 Java 风格写入的行)。达梦、金仓目前只有语句层面的单元测试,没有连真实数据库验证。
