# CSAP Apidoc 团队版 1.1 设计与契约

状态:契约冻结(2026-11-21,主会话制定)。实现会话按本文开发;要改契约先找主会话,不要各自改。
本文只写 1.1 **新增和改变**的部分;没提到的一律沿用 [team-1.0.md](team-1.0.md)(错误码、分页、鉴权、`X-CSAP-Team: 1`、审计只追加、表名 `csap_team_` 前缀、不改开源表结构、界面不出现下游业务名、不写成"只支持 Java"等)。

版本:开发期间 `1.1.0-SNAPSHOT`,发布 `1.1.0`。依赖的开源 `csap-apidoc-database` 仍为 1.5.0(**1.1 不需要开源侧任何改动**,服务注册协议不变)。

1.1 三块内容:

| 编号 | 内容 | 章节 |
|---|---|---|
| A | 多环境(开发 / 测试 / 生产)、一键推广、环境差异对比 | §1–§4 |
| B | 修改单评论、编辑中提示 | §5 |
| C | 审计哈希链(防篡改)与校验 | §6 |

## 0. 兼容原则

- **不配置环境 = 1.0 的行为**:没有 `csap.team.environments` 时,自动有一个环境 `key=default`、`name=默认`,用主数据源和 `csap.team.rule-table-prefix`。所有接口的 `env` 参数可省略,省略即**排序第一的环境**(下称"默认环境")。1.0 的前端、开放接口调用方不改也能用。
- 1.0 的库启动 1.1 时自动升级到 schema 版本 `2`(§7),不需要手工执行 SQL(`create-tables=false` 时除外,见 §7)。
- 1.0 的测试在 1.1 上全部继续通过(只允许因为响应**新增字段**而调整断言)。

## 1. 环境定义(A)

### 1.1 配置

环境**只在配置文件里定义**,不在控制台增删(数据库连接和密码不进团队版的表、不出现在界面和接口里)。

```yaml
csap:
  team:
    environments:
      - key: dev                 # [a-z0-9-]{1,32},唯一;写进各表的 env_key,定了以后不要改
        name: 开发               # 显示名,必填
        datasource:              # 省略 = 用主数据源(spring.datasource)
          url: jdbc:mysql://dev-db:3306/app
          username: app
          password: ${DEV_DB_PASSWORD}
          driver-class-name: ""  # 可省略,按 url 识别
        rule-table-prefix: csap_apidoc_   # 省略 = csap.team.rule-table-prefix
        protected: false         # true:发布到这个环境一律要审批(不看项目的 require_approval)
        read-only: false         # true:不能向这个环境发布(建修改单、推广、回滚都拒绝),只能看
      - key: test
        name: 测试
      - key: prod
        name: 生产
        protected: true
        datasource: { url: jdbc:mysql://prod-db:3306/app, username: app, password: ${PROD_DB_PASSWORD} }
```

- 顺序即展示顺序和推广的默认方向(dev → test → prod),第一个是默认环境。
- 校验(启动失败并给出中文说明):`key` 格式、重复 `key`、`name` 为空、前缀格式(`[A-Za-z0-9_]`)、同一数据源 + 同一前缀被两个环境共用。
- 团队版自己的表**只在主数据源**。各环境的数据源只用来读写该环境的开源表(`{prefix}rule/history/meta/mcp_audit`)。
- 各环境的开源表缺失:`create-tables=true` 时按 1.0 同样方式在该环境的库建开源三张表;为 false 时该环境状态 `DOWN`(不影响其它环境和启动),错误信息给出原因。
- 环境数据源连接池:每个独立数据源一个小连接池(最大 5),启动时连不上不阻止启动,状态 `DOWN`,之后每 60 秒重试。
- 日志、接口、错误信息里不得出现数据源密码。

### 1.2 环境接口

- `GET /api/v1/envs`(任何已登录用户)→ `[{"key","name","order","protected","readOnly","status":"UP|DOWN","error":string|null,"default":bool}]`。`order` 从 0 开始。不返回数据源地址。

## 2. 服务与文档按环境(A)

- 服务名(`service`)跨环境是同一个服务,**项目归属不分环境**(`csap_team_project_service` 不变,仍是"服务 → 项目")。
- 按环境区分的是:实例、地址、在线状态、文档缓存、规则、历史、MCP 审计。新表 `csap_team_service_env`、`csap_team_env_instance` 见 §7。
- **注册**:注册令牌新增可选绑定环境(`csap_team_token.env_key`)。`scope=REGISTER` 的令牌注册上来的服务落到令牌绑定的环境;令牌未绑定环境 → 默认环境。服务注册协议(开源 `team-integration.md`)**不变**,请求体里出现 `environment` 字段时忽略。
- **手工添加服务**:`POST /services {"service","baseUrl","docPath"?,"projectId"?,"env"?}`,`env` 省略 = 默认环境。同一服务在另一个环境再加一次 = 给该环境加地址。
- `GET /services?projectId=&unassigned=&env=`:每项新增 `"envs":[{"env","baseUrl","docPath","source","status","instances","lastSeenAt","lastFetchedAt"}]`(列出该服务出现过的所有环境);原有顶层字段 `baseUrl/docPath/source/status/instances/lastSeenAt/lastFetchedAt` 取 `env` 参数所指环境(省略 = 默认环境;该服务在这个环境没有记录时这些字段为 null、`status=OFFLINE`)。
- `PATCH /services/{service}?env=` 改该环境的 `baseUrl`/`docPath`;`projectId` 不分环境。`DELETE /services/{service}?env=`:带 `env` 只删该环境的地址和实例;不带 `env` 删整个服务(1.0 行为)。
- 文档中心:`GET /docs/{service}?env=`、`GET /docs/search?q=&projectId=&env=` 按环境取缓存(每个环境、每个服务分别拉取)。搜索结果每项新增 `"env"`。
- `service.offline` 事件按 (环境, 服务) 判定,10 分钟去重也按 (环境, 服务)。

## 3. 规则、修改单按环境(A)

### 3.1 规则接口

`GET /rules/{service}`、`/rules/{service}/item`、`/history`、`/diff`、`POST /rules/{service}/rollback` 都增加查询参数 `env`(省略 = 默认环境),读写该环境的开源表。开放接口 `GET /api/open/v1/rules/{service}?env=` 同样。

### 3.2 修改单

- 一张修改单**只针对一个环境**:`csap_team_change.env_key`。创建 body 增加 `"env"`(省略 = 默认环境),开放接口 `POST /api/open/v1/changes` 同样。
- 修改单 JSON 增加 `"env"`、`"envName"`、`"sourceEnv"`(仅推广单,否则 null)、`"requireApproval"`(本单是否需要审批,bool)。列表 `GET /changes` 增加筛选 `env=`。
- 是否需要审批 = `项目.require_approval == 1` **或** `环境.protected == true`。
- 目标环境 `read-only`:创建、编辑、提交、通过、rebase、回滚、推广都返回 `FORBIDDEN`,message 说明"环境只读"。
- 目标环境 `DOWN`:需要读写该环境开源表的操作(包括 §2、§3 的读接口)返回新增错误码 **`ENV_UNAVAILABLE`(HTTP 503)**,message 带环境名。
- 发布的并发安全、写入格式与 1.0 §2.3 完全相同,只是作用在目标环境的库上,在**目标环境数据源的事务**里完成。修改单状态、审计等团队版表的写入在主数据源;两个库不做分布式事务,顺序为:先在目标环境提交规则写入,成功后再在主库更新修改单状态和审计。若主库更新失败,下一轮 `HistorySync`(§3.4)会因为 `published_history_id` 没对上而记一条 `rule.changed_outside`——可接受,但实现要在日志里打 ERROR。
- `kind` 新增 `PROMOTE`(推广单)。

### 3.3 推广与环境差异

**差异对比**

- `GET /envs/diff?from=&to=&projectId=&service=`(`from`、`to` 必填且不同;`projectId` 与 `service` 至少一个;权限同"看规则")→

```json
{"from":"dev","to":"test","services":[
  {"service":"order-service","items":[
    {"ruleKey":"...","status":"DIFFERENT|ONLY_FROM|ONLY_TO","fromVersion":3,"toVersion":2}
  ]}
]}
```

  只列出不同的规则(内容用 `RuleJson.sameJson` 判断相同即不列);`ONLY_FROM` 表示目标环境没有这条,`ONLY_TO` 表示来源环境没有。
- `GET /envs/diff/item?from=&to=&service=&key=` → `{"from","to","service","ruleKey","fromRules","toRules","fromVersion","toVersion","diff"}`,`diff` 为 `to → from` 的统一差异(即"推广后目标会怎么变")。

**推广**

- `POST /promotions {"projectId","from","to","items":[{"service","ruleKey"}]?,"includeDeletes":false,"title","reason","submit":false}` → 新建的修改单(`kind=PROMOTE`,`env=to`,`sourceEnv=from`)。
  - `items` 省略 = 该项目所有服务在两个环境之间 `DIFFERENT` 和 `ONLY_FROM` 的规则;`includeDeletes=true` 时再加上 `ONLY_TO`(推广后在目标环境删除)。
  - 每项 `after_rules` = 来源环境当前内容(`ONLY_TO` 为 null),`base_version`/`before_rules` = 目标环境当前内容。
  - 一条都没有时返回 `VALIDATION`,message "两个环境没有差异"。
  - `submit=true` 时创建后立刻提交(= "一键推广"):目标环境需要审批 → `PENDING`;否则直接发布(或 `CONFLICT`)。
- 权限:能在**目标环境所在项目**建修改单的人(管理员、维护者、开发)。之后的审批、冲突、rebase、撤回与普通修改单完全相同。
- 推广不限制方向(允许 prod → test 拉回),但前端默认按环境顺序给出"推广到下一个环境"。
- 审计:创建推广单记 `change.create`,`reason` 里带 `promote dev→test`;其余动作同 1.0。

### 3.4 服务侧修改与 MCP 审计按环境

- `HistorySync` 对**每个 UP 的环境**分别读 `{prefix}history`,游标名 `oss.history.{env}`。1.0 的游标 `oss.history` 在升级时改名为默认环境的游标(§7)。新环境首次出现时游标从当前最大 id 开始(同 1.0)。
- `rule.changed_outside` 审计与事件带环境。
- `GET /audit/mcp?env=` 读该环境的 `{prefix}mcp_audit`(省略 = 默认环境);导出同样。

### 3.5 审计、Webhook 带环境

- `csap_team_audit.env_key`(可空:与环境无关的动作,如登录、用户管理,为 null)。审计查询增加筛选 `env=`,审计项增加 `"env"`。
- Webhook 增加可选订阅环境 `environments`(数组;空 / 省略 = 全部环境),存在 `csap_team_webhook.env_keys`(逗号分隔,null = 全部)。Webhook 项增加 `"environments":[...]`。
- 所有事件的 GENERIC 请求体增加 `"env":{"key","name"}`(与环境无关的事件为 null);`text` 里带环境名,例如 `[订单 / 生产] 张三 的修改单「...」已发布(2 条规则)`。企业微信、钉钉、飞书同样在正文里带环境名。

## 4. 权限补充(A)

1.1 的角色仍按项目,不分环境(按环境的细粒度权限是以后的事)。环境只通过 `protected`、`read-only` 影响行为。

## 5. 评论与编辑中提示(B)

### 5.1 修改单评论

- 表 `csap_team_comment`(§7)。评论挂在修改单上,可选指向其中一条 `item`。
- 权限:能看该修改单的人都能看评论;管理员、维护者、开发能发评论(只读不能);只能改、删自己的评论,管理员和该项目维护者可以删任何评论。
- 删除是软删除(`deleted=1`),列表里仍返回该条,`body` 为 null、`deleted=true`,便于保持对话顺序。
- 接口:
  - `GET /changes/{id}/comments` → `[{"id","changeId","itemId","author":{"id","displayName"},"body","deleted","createdAt","updatedAt"}]`,按 `id` 升序,不分页(单张修改单评论最多 500 条,超过后发评论返回 `VALIDATION`)。
  - `POST /changes/{id}/comments {"body","itemId"?}` → 评论。`body` 去掉首尾空白后 1–4000 字,否则 `VALIDATION`;`itemId` 必须属于该修改单。
  - `PATCH /comments/{id} {"body"}`、`DELETE /comments/{id}`。
- 修改单任何状态都能评论(包括已发布,用于事后讨论)。
- 修改单 JSON 增加 `"commentCount"`(未删除的条数)。
- 审计:`comment.create`、`comment.update`、`comment.delete`,`change_id` 填修改单,`after_value` 为评论内容(删除时 `before_value`)。
- Webhook 新事件 `change.commented`:GENERIC 请求体在 `change` 之外加 `"comment":{"id","author","body"}`(`body` 截断到 500 字)。只有创建评论触发,修改、删除不触发。

### 5.2 编辑中提示

两部分:

**① 有人正在编辑(内存,不入库)**

- `PUT /presence {"env","service","ruleKey"}`:前端打开规则编辑器后每 30 秒调用一次;90 秒没续上视为离开。`DELETE /presence {"env","service","ruleKey"}`:关闭编辑器时调用(失败也无妨)。
- `GET /presence?env=&service=` → `[{"ruleKey","users":[{"id","displayName","since"}]}]`,不含当前用户自己。
- 权限:能看该服务规则的人。只读用户不能 `PUT`(`FORBIDDEN`)。
- 单实例部署,存在内存即可;不写审计;每个用户最多同时 20 条,超出时挤掉最早的。

**② 有未完成的修改单涉及这条规则(查库)**

- `GET /rules/{service}/item?key=&env=` 的响应增加 `"openChanges":[{"id","title","status","author":{"id","displayName"},"updatedAt"}]`:同一环境、同一服务、同一 ruleKey、状态为 `DRAFT`/`PENDING`/`CONFLICT` 的修改单(不含已撤回、驳回、发布)。
- `GET /changes/{id}` 每项增加 `"overlaps":[{"changeId","title","status"}]`:**其它**未完成修改单里同环境、同规则的条目。
- 只提示,不阻止;真正的冲突仍由发布时的版本比较决定(1.0 §2.3)。

## 6. 审计哈希链(C)

目的:发现有人绕过团队版直接改、删审计记录。

### 6.1 计算

每条审计写入时:

```
hash = lowercase_hex( SHA-256( prev_hash + "\n" + canonical ) )
```

- `prev_hash`:上一条审计(按 `id`)的 `hash`;链的第一条用 64 个 `0`。
- `canonical`:下列 19 个值组成的 JSON 数组,用紧凑格式(无空格、无换行)序列化,字符串按 JSON 标准转义(非 ASCII 字符**原样输出**,不转成 `\uXXXX`),整数按十进制,缺失为 `null`:

  `[id, at, actor_type, actor_id, actor_name, source, ip, action, project_id, env_key, service, rule_key, change_id, before_value, after_value, diff, reason, oss_history_id, prev_hash]`

  示例:`[12,1763452800000,"USER",1,"张三","CONSOLE","10.0.0.1","change.publish",1,"prod","order-service","k",12,null,"{}",null,null,345,"0000…"]`
- UTF-8 编码后做 SHA-256。
- 文档里给出这个算法和一段独立的校验脚本(Python 3 标准库,不依赖团队版),第三方可以只凭导出的 JSON 校验。

### 6.2 写入串行化

- 计算 `prev_hash` 必须读到"真正的上一条"。实现:在写审计的同一主库事务里 `SELECT ... FOR UPDATE` 锁 `csap_team_setting` 中名为 `audit.chain.head` 的行(值为 `{"id":n,"hash":"..."}`),插入新记录(先拿到 id,再算 hash 并更新这一行的 `prev_hash`/`hash` 两列——这是唯一允许对审计表做的 UPDATE,且只能在插入它的同一事务里),再更新 `audit.chain.head`。
- 因为 id 由数据库生成,"插入后在同一事务里补 hash"是允许的;提交后的审计行不得再改。代码检查:除这一处和保留期清理外,不得出现对 `csap_team_audit` 的 UPDATE/DELETE。
- 审计写入失败要让所在业务操作失败(与 1.0 一致)。

### 6.3 升级时封存旧记录

- schema 升级到 2 时(§7),按 `id` 顺序给已有审计逐条计算并写入 `prev_hash`/`hash`(一次性回填,分批 1000 条),然后写 `audit.chain.head`,并追加一条 `action=audit.chain.sealed`、`source=SYSTEM` 的审计,`reason` 写 `sealed N records at upgrade`。
- 文档写清楚:1.1 升级前的记录是在**升级时**封存的,只能证明升级之后没被改过。

### 6.4 保留期清理与链

- 清理(1.0 §6.4)删掉最早的一段后,剩下的第一条的 `prev_hash` 指向已删除的记录。清理时把被删的最后一条的 `{id, hash}` 存到 setting `audit.chain.anchor`,校验从 anchor 接上;`audit.purge` 审计的 `reason` 里也带上 anchor 的 id 和 hash。

### 6.5 校验与外部锚定

- `GET /api/v1/audit/verify?fromId=&toId=`(仅管理员)→ `{"ok":bool,"checked":n,"headId","headHash","firstBrokenId":id|null,"problem":"HASH_MISMATCH|PREV_MISMATCH|MISSING_HASH|GAP"|null}`。不传范围 = 全部(从 anchor 或链首开始),流式分批计算,不一次读进内存。
  - `GAP`:id 不连续(有记录被删)。注意自增 id 在事务回滚时可能跳号,所以 `GAP` 只在"下一条的 `prev_hash` 不等于上一条的 `hash`"时才算错;id 跳号但 `prev_hash` 接得上不算错。即实际判断以 `prev_hash` 为准,`GAP` 只用于说明原因。
  - 每次校验记一条审计 `audit.verify`(`reason` 带结果)。
- 导出(CSV / JSON)增加 `envKey`、`prevHash`、`hash` 列,JSON 导出在开头带 `{"anchor":{...}|null}`,便于离线校验。JSON 导出格式改为 `{"anchor":...,"items":[...]}`(1.0 是数组;这是 1.1 唯一的不兼容变化,在 CHANGELOG 里写明)。
- 新 Webhook 事件 `audit.checkpoint`:每天 00:05(服务器时区)发一次当前 `headId`/`headHash`,订阅了才发。用途:把链头存到团队版之外(群消息、外部系统),之后有人整体重算整条链也能发现。GENERIC 体 `{"event":"audit.checkpoint","at","checkpoint":{"headId","headHash","count"},"text"}`。
- 控制台审计页增加"校验"按钮(管理员),显示结果。

## 7. 数据库升级(schema 版本 2)

`csap_team_setting.schema.version` 从 `1` 升到 `2`。新库直接按 `schema-template.sql`(已含 2 的全部内容)建表并写 `2`;旧库(`1`)启动时执行 `META-INF/csap-team/schema/upgrade/2/{dialect}.sql`,再执行 Java 部分的数据迁移,全部成功后写 `2`。`create-tables=false` 时不自动升级,启动失败并提示手工执行升级脚本(Java 部分的迁移在下次 `create-tables=true` 启动或首次启动时自动做;文档说明)。

升级脚本由 `generate-schema.sh` 从模板生成(同 1.0 的方式,另加 `upgrade-2-template.sql`)。

**新增表**

| 表 | 列 | 说明 |
|---|---|---|
| `csap_team_service_env` | (`service`, `env_key`) PK, `base_url`(可空), `doc_path` NOT NULL, `source`(REGISTERED/MANUAL), `last_seen_at`, `created_at` | 服务在各环境的地址 |
| `csap_team_env_instance` | (`env_key`, `service`, `instance_id`) PK, `base_url` NOT NULL, `last_seen_at` NOT NULL | 各环境的实例,取代 `csap_team_service_instance` |
| `csap_team_comment` | `id`, `change_id` NOT NULL, `item_id`, `author_id` NOT NULL, `body`(TEXT,可空=已删除), `deleted`(0/1), `created_at`, `updated_at`;索引 (`change_id`, `id`) | 评论 |

**新增列**(都可空)

- `csap_team_change.env_key VARCHAR(32)`、`csap_team_change.source_env VARCHAR(32)`;索引 (`env_key`, `status`)
- `csap_team_audit.env_key VARCHAR(32)`
- `csap_team_token.env_key VARCHAR(32)`
- `csap_team_webhook.env_keys VARCHAR(512)`

**数据迁移(Java,在同一次启动里)**

1. 取"默认环境"的 key(配置里第一个,未配置为 `default`),下称 `K`。
2. `csap_team_project_service` 每行 → `csap_team_service_env (service, K, base_url, doc_path, source, last_seen_at, created_at)`。`project_service` 的 `base_url/doc_path/source/last_seen_at` 列保留但 1.1 起不再读写(以便回退)。
3. `csap_team_change.env_key` 为 null 的全部填 `K`。审计的 `env_key` **不回填**(审计只追加;旧记录按"默认环境"展示即可,前端显示为"—")。
4. `csap_team_service_instance` 的数据丢弃(实例一个心跳周期内会重新注册),表保留不删。
5. 游标 `oss.history` → `oss.history.K`(复制后删旧名);`oss.mcp_audit` 同理。
6. 审计哈希链回填(§6.3)。

文档写清楚:升级后再改默认环境的 key,需要手工改上述表里的 `env_key`。

**测试要求**:H2 上从 1.0 的建表脚本 + 样例数据启动 1.1 升级成功;MySQL 8 / PostgreSQL 16 的 Testcontainers 测试各跑一遍升级;达梦、人大金仓的升级脚本标注"未在真实库验证"。

## 8. 前端(B 面的界面也在这里)

- 顶栏增加**环境切换器**(只在配置了 2 个以上环境时显示),当前环境存在 URL 查询参数 `env`(便于分享链接)并记在 localStorage;规则、文档中心、修改单列表、审计都按当前环境。受保护环境用醒目颜色标注(例如生产红色标签)。
- 环境 `DOWN` 时切换器上显示不可用,选中它的页面给出错误说明。
- 新页面"**环境对比**":选来源 / 目标环境与项目,列出差异(按服务分组,状态标签),点开看差异,勾选后"推广"(弹窗填标题、原因,选项"包含删除""创建后立即提交")。
- 修改单详情:显示目标环境(和推广来源);评论区(列表 + 发表框,可选关联到某条规则;自己的可编辑删除);每条规则显示 `overlaps` 提示。
- 规则编辑:打开时开始 `PUT /presence` 心跳,顶部显示"张三 正在编辑这条规则"和"有 2 张未完成的修改单涉及这条规则"(可点开)。
- 服务列表:多环境时显示每个环境的状态;手工添加服务可选环境。
- 令牌:创建 REGISTER 令牌可选绑定环境。Webhook:可选订阅环境,事件列表加 `change.commented`、`audit.checkpoint`。
- 审计页:环境筛选和列;管理员"校验哈希链"按钮。
- 只配置一个环境时,界面与 1.0 基本一致(不显示环境相关控件,除了修改单详情里不显示环境)。
- 文案全部进 `i18n/zh-CN.ts`;mock(msw)补齐新接口,`npm run dev` 不连后端也能看全部新页面。

## 9. 配置汇总(新增)

```yaml
csap:
  team:
    environments: []          # §1.1;空 = 单环境 default
    presence:
      ttl-seconds: 90
    audit:
      checkpoint-cron: "0 5 0 * * *"   # audit.checkpoint 事件
```

## 10. 测试与验收

- 后端:1.0 的测试全部保留并通过;新增覆盖 §1–§7。至少:
  - 多环境用 H2 两个独立库(两个内存库)跑:按环境读规则、推广(含 includeDeletes、submit=true、受保护环境进 PENDING、只读环境拒绝)、差异对比、按环境 HistorySync、环境 DOWN 时其它环境正常;
  - 评论权限(只读不能发、只能改自己的、维护者能删)、软删除、`change.commented` 事件;presence 过期;`openChanges` / `overlaps`;
  - 哈希链:连续写入校验通过;改一条记录的内容 → `HASH_MISMATCH`;删中间一条 → `PREV_MISMATCH`;保留期清理后校验仍通过;并发写 50 条审计链不断;独立 Python 脚本对导出 JSON 校验通过(测试里调用 `python3`,本机没有就跳过);
  - 1.0 → 1.1 升级(H2 + Testcontainers MySQL / PostgreSQL)。
- 前端:`npm run typecheck`、`lint`、`test`、`build` 通过;新组件有 Vitest 测试(环境切换、对比与推广弹窗、评论、presence 提示、校验按钮)。
- 主会话验收时做:Docker 跑两个环境(两个 PostgreSQL 库),Java 和 Go 服务各一个分别注册到 dev / test,推广后服务在轮询周期内生效。

## 11. 不在 1.1

按环境的细粒度权限、SSO、API 监控、AI 辅助、评论 @提醒与邮件通知、多实例部署(presence 仍是单实例内存)。
