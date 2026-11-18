# 数据库模式与 Redis 模式(规则集中存储)方案

状态:已审定(2026-11-18),实现中(数据库模式的 Java、Go 实现已完成验收;Java、Go 的 Redis 模式与迁移工具也已实现)。计划版本:Java 1.4.0(2026-12-22);Go 已有 SQL 规则来源,同步调整表结构。

## 1. 为什么要做

现在 Java 的规则来源只有注解、YAML 文件和 SQLite 文件(`paramType = SQL_LITE`)。这几种方式都是"跟着服务走的本地文件",在生产环境有几个问题:

- **多实例不一致**:同一个服务部署多台,每台各有一份 YAML / SQLite,在其中一台的 DevTools 改了规则,其他实例看不到。
- **容器里改了就丢**:SQLite 文件在容器里,重新部署后修改丢失,除非额外挂卷。
- **没法集中管理**:团队版要跨服务汇总、审批、回滚规则,需要一个共享的存储。
- **运维不认 SQLite**:企业生产环境通常只允许用已有的 MySQL / PostgreSQL,不接受单独的 SQLite 文件。

Go 版已经有 SQL 规则来源(SQLite / PostgreSQL / MySQL,带版本号、修改记录、回滚、多实例同步)。Java 补上数据库模式后,两种语言可以**共用同一个库、同一套表**。

## 2. 目标

1. Java 新增数据库模式:规则存在业务已有的关系数据库里,支持 **MySQL 5.7+/8.x、PostgreSQL 12+,以及国产数据库达梦(DM8)、人大金仓(KingbaseES V8/V9)**。SQLite 保留,作为单机和开发环境的选项。
2. **表结构 Java 与 Go 统一**:同一个库里,Java 服务和 Go 服务的规则、修改记录、版本号格式相同。DevTools、团队版读写的是同一套表。
3. **多实例同步**:保存后所有实例在几秒内生效。默认靠轮询全局版本号,可选接入 Redis 等通知实现即时生效。
4. **修改记录与回滚**:每次保存记录修改前后的内容、修改人、时间;DevTools 里可以查看和回滚,Java 与 Go 一致。
5. **迁移工具**:把现有的 YAML 目录或 SQLite 文件一次性导入数据库,也能从数据库导出成 YAML,方便纳入 Git 审查。
6. MCP 配置(1.3.0)也可以存进同一个库。

不在本次范围:Oracle、SQL Server、OceanBase、openGauss / GaussDB 等(方言层留好扩展点,按需求再加;其中兼容 MySQL / PostgreSQL 协议的库可先按对应方言试用)。多人协作、审批、完整审计放在团队版,见第 7 节。

## 3. 设计要点

### 3.1 使用方式(Java)

```java
@EnableApidoc(value = "com.example", paramType = ApiStrategyType.DATABASE)
```

```yaml
csap:
  apidoc:
    database:
      datasource-bean: ""          # 指定 DataSource Bean 名,多数据源时用;空 = 唯一(或 primary)的 DataSource
      dialect: ""                  # 空 = 按 JDBC URL 识别;可选 mysql / postgresql / dm / kingbase / sqlite / h2
      table-prefix: csap_apidoc_   # 表名前缀
      init-schema: true            # 启动时自动建表(没有建表权限时关掉,用提供的 SQL 手工执行)
      poll-interval: 5s            # 多实例同步的轮询间隔,0 表示不轮询
      service: ""                  # 服务名;空时取 spring.application.name,再空为 default
      read-only: false             # true:只读取规则,禁止保存
```

- 默认直接用应用的 `DataSource`,不引入连接池,不额外开连接。
- 只依赖 JDBC,不依赖 MyBatis / JPA,**不带数据库驱动**(由应用自带)。
- 模块 `csap-apidoc-database`,已包含在 `csap-apidoc-boot-starter` 中;Spring Boot 2 和 3 用同一个 jar(沿用 1.2.0 的做法)。
- 建表 SQL 放在 jar 内 `META-INF/csap-apidoc/database/{mysql,postgresql,dm,kingbase,sqlite,h2}.sql`,官网同样提供,DBA 可以手工执行。
- **`service` 默认值**:同一个库里按 `service` 区分各服务的规则。默认取 `spring.application.name`,没配置时为 `default`。多个服务共用一个库时必须各自不同,否则规则会互相覆盖。
- **只读模式**(`read-only: true`):启动时只读取规则,不建表,DevTools 保存返回明确提示。用于生产环境:规则在测试环境或团队版里修改、审批后再进入生产库。

### 3.2 表结构(Java 与 Go 共用)

在 Go 现有三张表的基础上加 `service` 列,支持一个库存多个服务。列名**沿用 Go 现有的 `rules` / `before_rules` / `after_rules`**(原稿写的 `content` / `before` / `after` 作废),这样 Go 已有数据的迁移只需加 `service` 列,不用改列名。

| 表 | 作用 | 字段 |
|---|---|---|
| `{prefix}rule` | 每个接口一行规则 | `service`、`rule_key`、`rules`(规则 JSON)、`version`、`updated_by`、`updated_at`;主键 `(service, rule_key)` |
| `{prefix}history` | 每次保存的修改记录 | `id`(自增)、`service`、`rule_key`、`before_rules`、`after_rules`、`version`、`updated_by`、`updated_at`;索引 `(service, rule_key, id)` |
| `{prefix}meta` | 全局版本号 | `service`(主键)、`version` |

`{prefix}` 默认 `csap_apidoc_`。

- **规则键**:Java 为 `类全名.方法名`,Go 为 `分组.接口名`。
- **规则内容**:`rules` 是 JSON,与 YAML 规则同一结构,一个接口一行,而不是像 Java SQLite 那样存整份文档,这样保存、比对、回滚都按接口进行。规则被清空时删除 `rule` 表中的这一行(`history` 里仍有记录)。
- **时间**:`updated_at` 为毫秒时间戳。
- **大文本类型**:MySQL `LONGTEXT`、PostgreSQL / 人大金仓 `TEXT`、达梦 `CLOB`。不使用 JSON 类型,以兼容 MySQL 5.7 和各类兼容库。
- **不使用 `DEFAULT ''`**:部分数据库的 Oracle 兼容模式会把空字符串当作 NULL,`DEFAULT ''` 在各方言上行为不一致,所以表定义里不写字符串默认值,列值都由程序显式写入;`updated_by` 没有修改人时写 `unknown`,`before_rules` / `after_rules` 可空(空表示修改前没有规则 / 这次是删除)。`rule_key` 为 VARCHAR(512),`service` 为 VARCHAR(128)。
- Go 现有的表升级(加 `service` 列,旧数据 `service` 填默认值)随 v0.2.0 提供迁移,见第 3.4 节。
- 建表 SQL 按方言放在 jar 里,并在官网提供。

### 3.2.1 方言

建表语句、自增主键、占位符、行锁写法、JSON / 大文本字段类型按方言区分:

| 方言 | 版本 | 说明 |
|---|---|---|
| MySQL / MariaDB | 5.7+ / 8.x | `rules` 用 `LONGTEXT`(不依赖 JSON 类型,兼容 5.7 与各类 MySQL 兼容库) |
| PostgreSQL | 12+ | `TEXT` |
| 达梦 DM8 | 8.x | `CLOB`;自增用 `IDENTITY`;`SELECT ... FOR UPDATE` 行锁 |
| 人大金仓 KingbaseES | V8 / V9 | 默认按 PostgreSQL 兼容模式;MySQL 兼容模式的库用 `dialect: mysql` 试用;Oracle 兼容模式未验证,暂不支持 |
| SQLite | 3.x | 单机、开发环境 |
| H2 | 2.x | 测试环境(有建表 SQL,主要用于测试) |

默认根据 JDBC URL 自动识别方言(`jdbc:mysql:` / `jdbc:mariadb:`、`jdbc:postgresql:`、`jdbc:dm:`、`jdbc:kingbase8:`、`jdbc:sqlite:`、`jdbc:h2:`),也可以用 `csap.apidoc.database.dialect` 显式指定。Go 版同样支持 MySQL、PostgreSQL、SQLite、达梦、人大金仓(Go 用 `database/sql`,驱动由使用方 import)。

### 3.3 运行时流程

1. 启动时加载本服务的全部规则到内存(规则查找仍走内存,不在请求链路上查库)。
2. DevTools 保存:一个事务内,全局版本号加 1(行锁串行化并发保存)、写规则、写修改记录,然后立即刷新本实例。
3. 其他实例每隔 `poll-interval` 查一次版本号,变了就重新加载;配了通知实现的,收到通知立即加载。
4. 规则来源优先级沿用现有设计:注解 < YAML < 数据库。

### 3.4 迁移

- `csap-apidoc-migrate`(命令行或启动参数):YAML 目录 / SQLite 文件 → 数据库;数据库 → YAML 目录。**迁移工具尚未完成**,完成前文档里一律写"计划中"。
- Go v0.1.x 的旧表(没有 `service` 列)升级到新表结构:Go v0.2.0 提供迁移函数和说明。
- 导入前检查冲突,默认不覆盖已有规则;导入本身记一条修改记录,可以回滚。
- 旧的 `paramType = SQL_LITE` 继续可用,文档里推荐生产环境改用数据库模式。

### 3.5 Redis 模式

没有关系数据库、或希望规则跟随已有 Redis 时,可以把规则存进 Redis。用法:

```java
@EnableApidoc(value = "com.example", paramType = ApiStrategyType.REDIS)
```

```yaml
csap:
  apidoc:
    redis:
      key-prefix: "csap:apidoc:"   # 键前缀
      service: order-service       # 服务名,同一个 Redis 存多个服务时区分
      history-limit: 200           # 每个接口保留的修改记录条数
      poll-interval: 5s            # 版本号轮询兜底间隔
      subscribe: true              # 是否订阅变更频道即时同步
      read-only: false             # true:只读,禁止保存和回滚
```

数据约定(键名、修改记录格式、保存用的 Lua 脚本、同步与回滚流程)**以 [redis-mode.md](redis-mode.md) 为准**,Java 与 Go 必须一致,改动前先改那份文档。要点:

- **连接**:Java 使用应用已有的 `RedisConnectionFactory`(Spring Data Redis,可选依赖,Boot 2 / 3 通用),不另建连接;Go 由使用方传入实现了简单接口的客户端(主模块不引入 Redis 依赖,go-redis 的适配示例写在文档里)。
- **键**(服务名用 hash tag 包起来,同一服务的键落在 Redis Cluster 的同一个槽):
  - `{prefix}{{service}}:rules`:Hash,字段是规则键(类名.方法名 / 分组.接口名),值是规则 JSON(与数据库模式、YAML 同一结构);
  - `{prefix}{{service}}:version`:全局版本号;
  - `{prefix}{{service}}:history:{规则键}`:List,保存修改前后内容、修改人、时间,保留 `history-limit` 条;
  - `{prefix}{{service}}:changes`:Hash,修改记录 id → 规则键的索引,用于按 id 回滚;
  - 频道 `{prefix}{{service}}:changed`:保存后发布新版本号。
- 保存用一段 Lua 脚本原子完成"版本号加 1、写规则、写修改记录、更新索引、发布通知"。
- **多实例同步**:订阅变更频道即时生效,同时按 `poll-interval` 检查版本号兜底(订阅断开时也不会漏)。
- **修改记录与回滚**:支持,但只保留最近 `history-limit` 条;需要完整、长期的审计追溯时请用数据库模式(团队版的审计基于数据库)。
- **只读模式**:与数据库模式相同,不能保存、不能回滚。
- **持久化提醒**:Redis 需要开启 AOF 或 RDB 持久化,否则重启会丢规则;文档里写明,并提供"导出成 YAML"作为备份。
- 迁移工具同样支持 YAML / SQLite / 数据库 ↔ Redis。

### 3.6 各模式在打包运行(`java -jar`、容器)时能否保存

| 模式 | 打包运行时 DevTools 能否保存 | 适合 |
|---|---|---|
| 注解 / YAML / JSON(规则文件在 jar 里) | 不能(jar 内文件不可写),1.3.0 起返回明确提示 | 开发环境在源码目录运行 |
| SQLite | 以 1.3.0 实测结论为准(见官网存储策略页) | 单机、开发 |
| 数据库(MySQL / PostgreSQL / 达梦 / 人大金仓) | 能(1.4.0 起) | 生产、多实例、团队版 |
| Redis | 能(1.4.0 起) | 生产、多实例、已有 Redis 的项目 |

## 4. 测试

- MySQL 8、PostgreSQL 16 用 Testcontainers 跑集成测试,CI 增加对应作业(Go 已有 PostgreSQL 作业,补 MySQL)。
- 达梦 DM8、人大金仓用官方提供的容器镜像跑同一套集成测试;镜像无法在 CI 公开拉取时,在本地 / 自建 Runner 上跑,发布前必须全部通过。
- 多实例:两个应用实例连同一个库,一边保存,另一边在轮询间隔内生效;并发保存时版本号不丢、不重复。
- Java 写、Go 读(以及反过来)同一个库,规则内容和文档输出一致。
- 迁移:YAML / SQLite 导入后,文档输出与导入前逐字节一致。
- Redis:Testcontainers 跑 Redis 7;多实例订阅同步、订阅断开后靠版本号兜底;并发保存不丢版本;Java 写、Go 读一致。

## 5. 排期

| 时间 | 内容 |
|---|---|
| 11-18 | 本方案审定;统一表结构定稿(Java 与 Go 共用) |
| 12-12 – 12-18 | Java 数据库策略、多实例同步、修改记录与回滚、DevTools 接入 |
| 12-18 – 12-20 | 迁移工具;Go 表结构升级(v0.2.0) |
| 12-18 – 12-20 | 达梦、人大金仓方言与集成测试 |
| 12-18 – 12-20 | Redis 模式(Java 与 Go)与集成测试 |
| 12-20 – 12-22 | 全部数据库的集成测试、文档、发布 1.4.0 与 Go v0.2.0 |

团队版 1.0(12-31)的规则审批、修改记录直接建立在这套表上。

## 6. 风险与待定

- 1.4.0 原计划内容较多(Mock、JSON Schema、字段库、版本管理、校验对齐),加入数据库模式和 Redis 模式后排期很紧。建议数据库模式作为 1.4.0 的首要项,"字段库与模板"如果来不及顺延到 1.5.0。
- 达梦、人大金仓的驱动不在 Maven Central,测试和示例需要从厂商仓库获取;文档写清楚驱动的获取方式。
- 1.4.0 在达梦、人大金仓真实库上**没有验证**(只有语句层面的测试),文档标为试用;MySQL 8、PostgreSQL 有集成测试。
- 已提供"只读模式"配置(`read-only`,见 3.1):生产环境只读取数据库里的规则,禁止通过 DevTools 修改(修改走测试环境或团队版的审批)。

## 7. 团队版:多人协作与审计追溯(规划)

开源版的数据库模式提供"存储 + 多实例同步 + 修改记录 + 回滚",够单个团队直接用。团队版在同一套表的基础上(增加团队版自己的表),面向多人、多服务、多环境协作:

### 7.1 成员、角色与权限

- 组织 → 项目(一个或多个服务)→ 成员;角色:管理员、维护者(可改规则、可审批)、开发(可提交修改)、只读。
- 细粒度权限(2027):按服务、按接口分组限定谁能看、谁能改。
- 登录:账号密码;SSO(OIDC、LDAP)在 2027。

### 7.2 多人协作

- **修改草稿与冲突检测**:每个人的修改先存为草稿;提交时按规则的版本号做乐观锁检查,别人先改过就提示合并,不会互相覆盖。
- **编辑提示**:有人正在编辑同一个接口的规则时,页面上提示。
- **评论**:可以在某个接口、某次修改上留言讨论。
- **审批流**:修改提交后由维护者审批,通过才发布到环境;可配置哪些服务、哪些环境需要审批(例如生产环境必须审批)。

### 7.3 多环境

- 开发 / 测试 / 生产等环境各有一份规则;修改先在低环境生效,确认后一键"推广"到下一个环境,推广本身也走审批、留记录。
- 环境之间可以对比差异。

### 7.4 审计追溯

- **每一次操作都记录**:谁(成员、令牌或系统)、什么时间、从哪里(DevTools、MCP、开放接口、迁移工具,以及来源 IP)、改了什么(修改前后的完整内容和差异)、为什么(提交说明、关联的审批单)。
- **规则的完整历史**:任意接口的规则可以查看每一个版本、任意两个版本的差异,并回滚到任意版本。
- **防篡改**:审计记录只追加、不可修改;每条记录带上一条的哈希,形成链,可以校验是否被改动。
- **查询与导出**:按人、时间、服务、接口、操作类型筛选;导出 CSV / JSON,满足等保、内控审计的留存要求;保留期可配置。
- MCP 调用审计(1.3.0)和规则修改审计汇总在同一个查询界面。

### 7.5 通知

- 修改提交、审批结果、推广到生产、破坏性变更等事件发 Webhook;企业微信、钉钉、飞书可以直接配置。

### 7.6 排期

| 版本 | 时间 | 内容 |
|---|---|---|
| 团队版 1.0 | 2026-12-31 | 成员与角色、草稿与冲突检测、审批流、审计记录与查询导出、规则完整历史与回滚、Webhook 通知;支持 MySQL / PostgreSQL / 达梦 / 人大金仓 |
| 团队版 1.1 | 2027 Q1 | 多环境与推广、评论、编辑提示、审计防篡改哈希链、SSO(OIDC / LDAP) |
| 团队版 1.2 | 2027 Q2 | 细粒度权限、跨服务发布记录与破坏性变更告警 |
