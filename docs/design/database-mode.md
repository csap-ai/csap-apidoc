# 数据库模式(规则存数据库)方案

状态:草案,待审定。计划版本:Java 1.4.0(2026-12-22);Go 已有 SQL 规则来源,同步调整表结构。

## 1. 为什么要做

现在 Java 的规则来源只有注解、YAML 文件和 SQLite 文件(`paramType = SQL_LITE`)。这几种方式都是"跟着服务走的本地文件",在生产环境有几个问题:

- **多实例不一致**:同一个服务部署多台,每台各有一份 YAML / SQLite,在其中一台的 DevTools 改了规则,其他实例看不到。
- **容器里改了就丢**:SQLite 文件在容器里,重新部署后修改丢失,除非额外挂卷。
- **没法集中管理**:团队版要跨服务汇总、审批、回滚规则,需要一个共享的存储。
- **运维不认 SQLite**:企业生产环境通常只允许用已有的 MySQL / PostgreSQL,不接受单独的 SQLite 文件。

Go 版已经有 SQL 规则来源(SQLite / PostgreSQL / MySQL,带版本号、修改记录、回滚、多实例同步)。Java 补上数据库模式后,两种语言可以**共用同一个库、同一套表**。

## 2. 目标

1. Java 新增数据库模式:规则存在业务已有的关系数据库里,首批支持 **MySQL 5.7+/8.x、PostgreSQL 12+**。SQLite 保留,作为单机和开发环境的选项。
2. **表结构 Java 与 Go 统一**:同一个库里,Java 服务和 Go 服务的规则、修改记录、版本号格式相同。DevTools、团队版读写的是同一套表。
3. **多实例同步**:保存后所有实例在几秒内生效。默认靠轮询全局版本号,可选接入 Redis 等通知实现即时生效。
4. **修改记录与回滚**:每次保存记录修改前后的内容、修改人、时间;DevTools 里可以查看和回滚,Java 与 Go 一致。
5. **迁移工具**:把现有的 YAML 目录或 SQLite 文件一次性导入数据库,也能从数据库导出成 YAML,方便纳入 Git 审查。
6. MCP 配置(1.3.0)也可以存进同一个库。

不在本次范围:Oracle、SQL Server、达梦、人大金仓等(方言层留好扩展点,按需求再加);跨服务的规则审批(团队版)。

## 3. 设计要点

### 3.1 使用方式(Java)

```java
@EnableApidoc(value = "com.example", paramType = ApiStrategyType.DATABASE)
```

```yaml
csap:
  apidoc:
    database:
      # 不配置时使用应用自己的 DataSource;配置后使用单独的连接
      datasource-bean: ""          # 指定 DataSource Bean 名,多数据源时用
      table-prefix: csap_apidoc_   # 表名前缀
      init-schema: true            # 启动时自动建表(没有权限时关掉,用提供的 SQL 手工执行)
      poll-interval: 5s            # 多实例同步的轮询间隔,0 表示不轮询
      service: order-service       # 服务名,同一个库存多个服务的规则时用来区分
```

- 默认直接用应用的 `DataSource`,不引入连接池,不额外开连接。
- 只依赖 JDBC,不依赖 MyBatis / JPA,不引入数据库驱动(由应用自带)。
- Spring Boot 2 和 3 用同一个 jar(沿用 1.2.0 的做法)。

### 3.2 表结构(Java 与 Go 共用)

在 Go 现有三张表的基础上加 `service` 列,支持一个库存多个服务:

| 表 | 作用 | 主要字段 |
|---|---|---|
| `csap_apidoc_rule` | 每个接口一行规则 | `service`、`rule_key`(类名.方法名 / 分组.接口名)、`content`(规则 JSON,与 YAML 同结构)、`version`、`updated_by`、`updated_at` |
| `csap_apidoc_history` | 每次保存的修改记录 | `service`、`rule_key`、`before`、`after`、`version`、`updated_by`、`updated_at` |
| `csap_apidoc_meta` | 全局版本号 | `service`、`version` |

- 规则内容用 JSON 存一个接口一行,而不是像 Java SQLite 那样存整份文档,这样保存、比对、回滚都按接口进行。
- Go 现有的表结构要升级(加 `service` 列,默认值兼容旧数据),Go 发 v0.2.0 时附迁移说明。
- 建表 SQL 按方言放在 jar 里,并在官网提供,DBA 可以手工执行。

### 3.3 运行时流程

1. 启动时加载本服务的全部规则到内存(规则查找仍走内存,不在请求链路上查库)。
2. DevTools 保存:一个事务内,全局版本号加 1(行锁串行化并发保存)、写规则、写修改记录,然后立即刷新本实例。
3. 其他实例每隔 `poll-interval` 查一次版本号,变了就重新加载;配了通知实现的,收到通知立即加载。
4. 规则来源优先级沿用现有设计:注解 < YAML < 数据库。

### 3.4 迁移

- `csap-apidoc-migrate`(命令行或启动参数):YAML 目录 / SQLite 文件 → 数据库;数据库 → YAML 目录。
- 导入前检查冲突,默认不覆盖已有规则;导入本身记一条修改记录,可以回滚。
- 旧的 `paramType = SQL_LITE` 继续可用,文档里推荐生产环境改用数据库模式。

## 4. 测试

- MySQL 8、PostgreSQL 16 用 Testcontainers 跑集成测试,CI 增加对应作业(Go 已有 PostgreSQL 作业,补 MySQL)。
- 多实例:两个应用实例连同一个库,一边保存,另一边在轮询间隔内生效;并发保存时版本号不丢、不重复。
- Java 写、Go 读(以及反过来)同一个库,规则内容和文档输出一致。
- 迁移:YAML / SQLite 导入后,文档输出与导入前逐字节一致。

## 5. 排期

| 时间 | 内容 |
|---|---|
| 11-25 前 | 本方案审定;统一表结构定稿(Java 与 Go 共用) |
| 12-12 – 12-18 | Java 数据库策略、多实例同步、修改记录与回滚、DevTools 接入 |
| 12-18 – 12-20 | 迁移工具;Go 表结构升级(v0.2.0) |
| 12-20 – 12-22 | MySQL / PostgreSQL 集成测试、文档、发布 1.4.0 与 Go v0.2.0 |

团队版 1.0(12-31)的规则审批、修改记录直接建立在这套表上。

## 6. 风险与待定

- 1.4.0 原计划内容较多(Mock、JSON Schema、字段库、版本管理、校验对齐),加入数据库模式后排期很紧。建议数据库模式作为 1.4.0 的首要项,"字段库与模板"如果来不及顺延到 1.5.0。
- 首批只支持 MySQL 和 PostgreSQL;国产数据库(达梦、人大金仓等)是否需要,请确认。
- 是否需要"只读模式"(生产环境只读数据库里的规则,禁止通过 DevTools 修改)——建议作为配置项提供。
