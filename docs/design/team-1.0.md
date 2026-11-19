# CSAP Apidoc 团队版 1.0 设计与契约

状态:契约冻结(2026-11-18,主会话制定;同日修订三次:PostgreSQL 12+、修改单操作权限、登录锁定按账号+IP、Webhook 地址隐藏;错误码 HTTP 状态与 `LOCKED`、`setting_value`/`cursor_value` 列名、MCP 导出参数;补 `/auth/me` 的 license、成员与候选人、审计/MCP/Webhook/用户/令牌的返回字段)。实现会话按本文开发;要改契约先找主会话,不要各自改。

发布目标:2026-12-31。

## 0. 定位与边界

- 团队版是**商业产品**,私有仓库 `csap-apidoc-team`,企业自部署(jar / Docker),按团队规模授权。
- 开源版(Apache-2.0)功能**不阉割**。团队版只做"多人、多服务"的事:成员与角色、草稿与审批、审计、文档中心、通知。
- 团队版**直接读写开源版数据库模式的三张表**(`{prefix}rule` / `{prefix}history` / `{prefix}meta`,默认前缀 `csap_apidoc_`),服务(Java 或 Go)按开源版已有的版本号轮询拿到变更。**不得改开源表结构**。
- 团队版自己的表一律以 `csap_team_` 开头。
- 产品名:**CSAP Apidoc 团队版**。界面、代码、文档里不得出现任何下游业务名。
- 多语言:文档中心汇聚任何语言实现的服务(协议 `/csap/apidoc`),不要写成"只支持 Java / Spring Boot"。

## 1. 仓库结构与技术栈

```
csap-apidoc-team/
  pom.xml                 # 父 pom,groupId ai.csap.apidoc.team,版本 1.0.0-SNAPSHOT
  server/                 # Spring Boot 3.3.x,Java 17,包名 ai.csap.apidoc.team
  license-tool/           # 授权文件签发命令行(Java 17,不依赖 server)
  web/                    # 控制台前端:React 18 + antd 5 + Vite 5 + TypeScript 5 + react-router 6
  docker/                 # Dockerfile、docker-compose 示例(MySQL / PostgreSQL)
  docs/                   # 设计、部署、使用文档(中文)
```

- `server` 依赖开源 `ai.csap.apidoc:csap-apidoc-database:1.4.0`(Maven Central),复用 `DatabaseDialect`、`DatabaseRuleStore`、`RuleJson`。不要复制开源代码。
- 构建:`mvn -pl server -am package` 产出单个可执行 jar,`web` 的构建产物打进 `server` 的 `static/`(用 frontend-maven-plugin 或在 `server` 打包前执行 `npm ci && npm run build`,由后端会话决定,前端会话保证 `npm run build` 输出到 `web/dist`)。
- 前端开发时 Vite 代理 `/api` 到 `http://localhost:8090`。
- 默认端口 `8090`。配置前缀 `csap.team.*`。

## 2. 数据库

### 2.1 支持范围

与开源数据库模式一致:MySQL 5.7+/8、PostgreSQL 12+、达梦 DM8、人大金仓;另支持 H2(仅试用 / 测试)。方言识别复用开源 `DatabaseDialect`。

- 团队版连的是**服务共用的那个库**(服务的 `csap.apidoc.database` 指向同一个库、同一个前缀)。
- 建表:启动时检查,缺表按方言执行 `META-INF/csap-team/schema/{dialect}.sql`(风格同开源:不写 `DEFAULT ''`,时间用毫秒 BIGINT,字符串列值都由程序显式写入)。`csap.team.database.create-tables`(默认 true)。
- 表版本:`csap_team_setting` 里 `schema.version`,1.0 为 `1`。以后升级用增量脚本。

### 2.2 团队版的表(1.0)

所有 id 为 BIGINT(程序生成或自增,由后端定;跨方言要可用)。`*_at` 为毫秒。

| 表 | 主要列 | 说明 |
|---|---|---|
| `csap_team_setting` | `name` PK, `setting_value` | 系统设置:schema 版本、令牌签名密钥(首次启动随机生成)、授权文本等 |
| `csap_team_user` | `id`, `username` 唯一, `display_name`, `password_hash`(BCrypt), `status`(ACTIVE/DISABLED), `org_role`(ADMIN/MEMBER), `created_at`, `updated_at` | 1.0 单组织:组织信息存 setting `org.name`;`org_role=ADMIN` 即"管理员" |
| `csap_team_project` | `id`, `name` 唯一, `description`, `require_approval`(0/1,默认 1), `created_at`, `updated_at` | 项目 = 一组服务 |
| `csap_team_project_service` | `service` PK, `project_id`(可空=未分配), `base_url`, `doc_path`, `language`, `framework_version`, `source`(REGISTERED/MANUAL), `last_seen_at`, `created_at` | 一个服务只属于一个项目 |
| `csap_team_service_instance` | (`service`, `instance_id`) PK, `base_url`, `last_seen_at` | 注册上来的实例;超过 3 个心跳周期没上报视为离线 |
| `csap_team_member` | (`project_id`, `user_id`) PK, `role`(MAINTAINER/DEVELOPER/VIEWER), `created_at` | 项目角色 |
| `csap_team_token` | `id`, `name`, `token_hash`(SHA-256), `prefix`(明文前 8 位,便于识别), `scope`(REGISTER/OPEN_API), `project_id`(可空), `created_by`, `created_at`, `last_used_at`, `revoked`(0/1) | 令牌明文只在创建时返回一次 |
| `csap_team_change` | `id`, `project_id`, `title`, `reason`, `author_id`, `status`, `created_at`, `updated_at`, `submitted_at`, `decided_by`, `decided_at`, `decision_note`, `published_at`, `kind`(EDIT/ROLLBACK) | 修改单(草稿、审批、发布都是它) |
| `csap_team_change_item` | `id`, `change_id`, `service`, `rule_key`, `base_version`, `before_rules`(可空), `after_rules`(可空=删除), `published_version`(可空), `published_history_id`(可空) | 修改单里的每条规则 |
| `csap_team_audit` | `id`, `at`, `actor_type`(USER/TOKEN/SERVICE/SYSTEM), `actor_id`, `actor_name`, `source`, `ip`, `action`, `project_id`, `service`, `rule_key`, `change_id`, `before_value`, `after_value`, `diff`, `reason`, `oss_history_id`, `prev_hash`(可空), `hash`(可空) | **只追加**,代码里不得有 UPDATE/DELETE(保留期清理除外,见 6.4);哈希链列 1.1 才填 |
| `csap_team_webhook` | `id`, `project_id`(可空=全部), `name`, `kind`(GENERIC/WECOM/DINGTALK/FEISHU), `url`, `secret`(可空), `events`(逗号分隔), `enabled`, `created_at`, `updated_at` | |
| `csap_team_webhook_delivery` | `id`, `webhook_id`, `event`, `at`, `attempt`, `status`(SUCCESS/FAILED), `http_status`, `error` | 保留最近 1000 条/每个 webhook |
| `csap_team_cursor` | `name` PK, `cursor_value` | 同步游标,例如 `oss.history` 最后处理的 history id、`oss.mcp_audit` |

### 2.3 读写开源的表

- **读**:`{prefix}rule`、`{prefix}history`、`{prefix}meta`;`{prefix}mcp_audit`(开源 Java 新增,见开源仓库 `docs/design/team-integration.md`;表不存在时相关功能显示"服务未开启 MCP 审计落库")。
- **写**:只通过"发布修改单"写 `{prefix}rule` / `{prefix}history` / `{prefix}meta`,写入结果必须和开源 `DatabaseRuleStore.save(ruleKey, rulesJson, updatedBy)` 完全一致(同样 bump `meta.version`、写 history、`updated_by` = 团队版用户名),这样 Java 和 Go 服务都能按原逻辑同步。
- **并发安全**:发布时对每条规则做比较并交换:当前 `{prefix}rule.version`(不存在记 0)必须等于 `base_version`,否则整单失败、状态置 `CONFLICT`。实现可以在同一事务里先 `SELECT ... FOR UPDATE`(五种库都支持)再调用与 `save` 等价的写入;不允许"先查后写"不加锁。
- 规则 JSON 比较用开源 `RuleJson.sameJson`;内容与库里相同的条目发布时跳过(不算冲突)。

## 3. 角色与权限

| 能力 | 管理员(org ADMIN) | 维护者 | 开发 | 只读 |
|---|---|---|---|---|
| 看文档中心、规则、历史 | 全部服务 | 所在项目 | 所在项目 | 所在项目 |
| 建草稿、提交修改单 | ✓ | ✓ | ✓ | ✗ |
| 审批(通过 / 驳回) | ✓ | ✓(不能审批自己提交的) | ✗ | ✗ |
| 回滚(发起回滚修改单) | ✓ | ✓ | ✓(同样走审批) | ✗ |
| 查审计、导出 | 全部 | 所在项目 | 所在项目(只查不导出) | ✗ |
| 项目成员管理 | ✓ | ✓(不能设管理员) | ✗ | ✗ |
| 项目设置(是否需要审批)、服务归属 | ✓ | ✓ | ✗ | ✗ |
| Webhook | ✓ | 所在项目 | ✗ | ✗ |
| 用户、令牌、授权、全局设置 | ✓ | ✗ | ✗ | ✗ |

- 管理员审批自己的修改单允许(小团队需要),审计里照常记录。
- 项目 `require_approval=0`:提交即发布(仍记审计,仍可回滚)。

## 4. 修改单状态机

```
DRAFT --submit--> PENDING --approve--> PUBLISHED
  ^                  |--reject---> REJECTED
  |                  |--withdraw-> WITHDRAWN
  |                  '--(发布时版本不符)--> CONFLICT
  '------rebase------ CONFLICT
DRAFT --submit(项目无需审批)--> PUBLISHED / CONFLICT
DRAFT --withdraw--> WITHDRAWN
```

- 提交(submit)时就检查一次版本:已过期直接 `CONFLICT`,避免审批人白审。
- 审批通过时再检查一次并发布(同一事务)。
- `rebase`:作者用当前库里的内容更新 `base_version` / `before_rules`(前端展示三方差异后让作者确认 `after_rules`),状态回 `DRAFT`。
- 回滚:`POST /rules/{service}/rollback` 生成 `kind=ROLLBACK` 的修改单,`after_rules` 取目标 history 的 `after_rules`(目标是删除则为 null),之后流程与普通修改相同。
- 每次状态变化都写审计、发对应 webhook 事件。

## 5. 文档中心

- 服务来源:① 服务通过开放接口注册(见 7.1);② 管理员在控制台手工添加 `service + baseUrl`。
- 后端每 60 秒(可配 `csap.team.docs.refresh-seconds`)从每个在线服务拉 `{baseUrl}{docPath}`(默认 `/csap/apidoc`),缓存在内存,失败保留上次结果并标记 `stale`。多个实例取任一在线实例。
- 拉取可带 header:`csap.team.docs.headers`(例如服务开了文档鉴权)。超时 5 秒。
- 搜索:内存索引,按接口路径、方法、标题、描述、请求/返回字段名与描述做包含匹配(中文直接子串匹配),返回前 100 条。1.0 不引入搜索引擎。
- 服务状态:ONLINE(有实例在 3 个心跳周期内上报,或手工服务最近一次拉取成功)、OFFLINE、STALE(拉取失败但有缓存)。服务离线触发 `service.offline` 事件(同一服务 10 分钟内只发一次)。

## 6. 审计

### 6.1 记录什么

每次写操作一条:登录成功/失败、用户/成员/项目/服务归属/令牌/webhook/授权的增删改、修改单的创建/编辑/提交/通过/驳回/撤回/发布/冲突/rebase、导出审计本身。

`source` 取值:`CONSOLE`(控制台)、`OPEN_API`(令牌调用)、`SERVICE`(服务侧直接修改,见 6.2)、`MCP`(见 6.3)、`MIGRATE`、`SYSTEM`。`ip` 取请求来源(信任 `X-Forwarded-For` 需配置 `csap.team.trusted-proxies`,默认不信任)。

`action` 用点分小写,例如 `change.publish`、`member.update`、`auth.login.failed`。`diff` 存规则 JSON 的统一差异文本(unified diff,按格式化后的 JSON 行比较)。

### 6.2 服务侧直接修改

服务自己的 DevTools 也能改规则(开源功能)。后端每 10 秒读 `{prefix}history` 中 `id > cursor` 的记录:

- `id` 等于某个 `csap_team_change_item.published_history_id` → 已由团队版记录,跳过;
- 否则写一条 `source=SERVICE`、`actor_type=SERVICE`、`actor_name=history.updated_by`、`action=rule.changed_outside` 的审计,并发 `rule.changed_outside` 事件。

### 6.3 MCP 调用

从 `{prefix}mcp_audit` 读(开源 Java 新增),审计页"MCP 调用"标签里按服务、时间、调用方、工具查询;导出同样支持。团队版不复制这张表的数据。

### 6.4 查询、导出、保留

- 筛选:时间段、人、服务、接口(rule_key)、动作、来源;分页。
- 导出 CSV(UTF-8 BOM,便于 Excel)/ JSON,最多 100,000 条一次,流式输出。
- 保留期 `csap.team.audit.retention-days`(默认 0 = 永久)。清理任务是唯一允许删除审计的代码,删除本身记一条 `audit.purge`。

## 7. 接口

通用约定:JSON;时间为毫秒数;分页参数 `page`(从 1)、`size`(默认 20,最大 200),分页响应 `{"items":[...],"total":n}`;错误响应 HTTP 4xx/5xx + `{"code":"...","message":"中文说明"}`。错误码与 HTTP 状态:`UNAUTHORIZED` 401、`FORBIDDEN` 403、`LICENSE_LIMIT` 403、`NOT_FOUND` 404、`VALIDATION` 400、`CONFLICT` 409、`SETUP_REQUIRED` 412、`LOCKED` 429(登录失败次数过多,账号暂时锁定)、`INTERNAL` 500。客户端以 `code` 为准。列名避开各库保留字(例如不用 `value`)。

### 7.1 开放接口 `/api/open/v1`(令牌鉴权 `Authorization: Bearer <token>`)

服务注册(scope REGISTER),**协议在开源仓库 `docs/design/team-integration.md` 定义,两边以那份为准**:

- `POST /api/open/v1/services/register`
- `POST /api/open/v1/services/deregister`

规则读写(scope OPEN_API,令牌绑定项目时只能操作该项目的服务):

- `GET /api/open/v1/rules/{service}` → `{"service","version","rules":[{"ruleKey","rules","version","updatedBy","updatedAt"}]}`
- `POST /api/open/v1/changes` body 同 7.2 的创建修改单,外加 `"submit": true|false`;返回修改单。审计 `source=OPEN_API`,`actor_type=TOKEN`。

### 7.2 控制台接口 `/api/v1`

鉴权:登录后服务端下发 HttpOnly、SameSite=Strict 的 Cookie `CSAP_TEAM_SESSION`(签名令牌,HS256,密钥存 setting,默认 12 小时滑动过期)。除 GET 外的请求必须带 header `X-CSAP-Team: 1`(简单 CSRF 防护)。

**初始化与登录**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/setup/status` | `{"initialized":bool}`;未初始化时除 setup 外的接口返回 `SETUP_REQUIRED` |
| POST | `/setup` | `{"orgName","username","password","displayName"}`,仅在没有用户时可用,创建管理员 |
| POST | `/auth/login` | `{"username","password"}` → 用户信息;同一账号 + 同一来源 IP 连续失败 5 次锁 15 分钟,锁定期间返回 `LOCKED`(429) |
| POST | `/auth/logout` | |
| GET | `/auth/me` | `{"id","username","displayName","orgRole","projects":[{"id","name","role"}],"license":{"status","maxMembers","activeMembers","expiresAt"}}`;`license` 对所有登录用户返回(用于授权提示条,不含 `licensee`) |
| POST | `/auth/password` | `{"oldPassword","newPassword"}` |

**用户(管理员)**:`GET /users` → `[{"id","username","displayName","status","orgRole","createdAt"}]`、`POST /users {"username","displayName","password","orgRole"}`、`PATCH /users/{id} {"displayName"?,"status"?,"orgRole"?,"password"?}`。

**项目**:`GET /projects`(当前用户可见的)、`POST /projects`(管理员)、`GET /projects/{id}`、`PATCH /projects/{id} {"name"?,"description"?,"requireApproval"?}`、`DELETE /projects/{id}`(无服务时)。

**成员**:`GET /projects/{id}/members` → `[{"userId","username","displayName","role","createdAt"}]`、`GET /projects/{id}/candidates`(管理员、该项目维护者)→ 不在本项目的启用用户 `[{"id","username","displayName"}]`、`PUT /projects/{id}/members/{userId} {"role"}`、`DELETE /projects/{id}/members/{userId}`。

**服务**:

- `GET /services?projectId=&unassigned=true` → `[{"service","projectId","projectName","baseUrl","docPath","language","frameworkVersion","source","status","instances":n,"lastSeenAt","lastFetchedAt"}]`
- `POST /services {"service","baseUrl","docPath"?,"projectId"?}`(手工添加)
- `PATCH /services/{service} {"projectId"?,"baseUrl"?,"docPath"?}`、`DELETE /services/{service}`

**文档中心**:

- `GET /docs/{service}` → 缓存的 `/csap/apidoc` 原样 JSON,外加响应头 `X-CSAP-Team-Fetched-At`、`X-CSAP-Team-Stale`
- `GET /docs/search?q=&projectId=` → `[{"service","method","path","title","matchedIn":"path|title|description|field","snippet"}]`

**规则**:

- `GET /rules/{service}` → `{"service","version","items":[{"ruleKey","version","updatedBy","updatedAt"}]}`
- `GET /rules/{service}/item?key=` → `{"ruleKey","rules"(JSON 字符串或 null),"version"}`
- `GET /rules/{service}/history?key=&limit=50` → `[{"id","version","updatedBy","updatedAt","beforeRules","afterRules","changeId"(若由团队版发布)}]`
- `GET /rules/{service}/diff?key=&from=&to=` → `{"from","to","diff"}`(`from`/`to` 是 history id;`to` 省略 = 当前)
- `POST /rules/{service}/rollback {"key","historyId","reason"}` → 新建的修改单(状态 DRAFT,前端随后可直接 submit)

**修改单**:

- `GET /changes?projectId=&status=&mine=true&page=&size=`
- `POST /changes {"projectId","title","reason","items":[{"service","ruleKey","baseVersion","afterRules"}]}` → 修改单(DRAFT)。`before_rules` 由服务端按当前库取。
- `GET /changes/{id}` → 修改单 + `items[]`,每项附 `currentVersion`、`conflict`(bool)、`diff`
- `PATCH /changes/{id}`(仅 DRAFT,仅作者)body 同创建;`submit`、`rebase` 也仅作者;`withdraw` 作者、该项目维护者、管理员均可(仅 DRAFT / PENDING);`reject` 的 `note` 必填(空则 `VALIDATION`)
- `POST /changes/{id}/submit`、`/approve {"note"}`、`/reject {"note"}`、`/withdraw`、`/rebase {"items":[{"service","ruleKey","afterRules"}]}`
- 冲突时 HTTP 409:`{"code":"CONFLICT","message","items":[{"service","ruleKey","baseVersion","currentVersion","currentRules"}]}`

修改单 JSON:`{"id","projectId","projectName","title","reason","kind","status","author":{"id","displayName"},"createdAt","submittedAt","decidedBy":{...}|null,"decidedAt","decisionNote","publishedAt","items":[...]}`。

**审计**:

- `GET /audit?from=&to=&actor=&projectId=&service=&ruleKey=&action=&source=&page=&size=` → 分页,审计项为表列驼峰:`{"id","at","actorType","actorId","actorName","source","ip","action","projectId","service","ruleKey","changeId","beforeValue","afterValue","diff","reason"}`(列表页可省略 `beforeValue`/`afterValue`/`diff`,详情 `GET /audit/{id}` 返回完整)
- `GET /audit/export?format=csv|json&...`(同上筛选);`kind=mcp` 时导出 MCP 调用,筛选参数同 `/audit/mcp`
- `GET /audit/mcp?from=&to=&service=&caller=&tool=&page=&size=` → `{"available":bool,"items":[...],"total"}`(表不存在时 `available=false`);项为表列驼峰 `{"id","service","at","caller","remoteAddress","tool","target","arguments","status","success","result","durationMs"}`

**Webhook**:`GET /webhooks?projectId=`、`POST /webhooks`、`PATCH /webhooks/{id}`、`DELETE /webhooks/{id}`、`POST /webhooks/{id}/test`(同步发送一次,返回这条投递记录)、`GET /webhooks/{id}/deliveries`。Webhook 项 `{"id","projectId","name","kind","url","hasSecret","events":["change.published",...],"enabled","createdAt","updatedAt"}`,创建/修改 body 同字段但用 `secret` 代替 `hasSecret`;投递项 `{"id","webhookId","event","at","attempt","status","httpStatus","error"}`。`secret` 只写不回显(返回 `"hasSecret":true`);`url` 在列表和详情中部分隐藏(保留协议与主机,路径与查询串用 `***`),修改时不传 `url` 则保持原值。

**令牌(管理员)**:`GET /tokens` → `[{"id","name","prefix","scope","projectId","createdBy","createdAt","lastUsedAt","revoked"}]`、`POST /tokens {"name","scope","projectId"?}` → 含一次性明文 `token`(格式 `csapt_` + 40 位随机 base62)、`DELETE /tokens/{id}`(撤销)。

**授权(管理员)**:`GET /license` → `{"status":"TRIAL|VALID|EXPIRED|INVALID","licensee","maxMembers","expiresAt","activeMembers"}`;`POST /license {"license":"<文本>"}`。

## 8. Webhook 事件

事件:`change.submitted`、`change.approved`、`change.rejected`、`change.published`、`change.conflict`、`rule.changed_outside`、`service.offline`。

GENERIC 的请求体:

```json
{"event":"change.published","at":1763452800000,"project":{"id":1,"name":"订单"},
 "change":{"id":12,"title":"...","author":"张三","url":"https://team.example.com/changes/12"},
 "items":[{"service":"order-service","ruleKey":"..."}],
 "text":"[订单] 张三 的修改单「...」已发布(2 条规则)"}
```

- GENERIC 带 `X-CSAP-Team-Event`、配置了 secret 时带 `X-CSAP-Team-Signature: sha256=<hex(HMAC-SHA256(secret, body))>`。
- 企业微信(群机器人 markdown)、钉钉(markdown,secret 时按钉钉加签规则)、飞书(text / post,secret 时按飞书签名规则)各自按官方格式组装,正文用 `text`。
- 控制台外链地址取 `csap.team.public-url`(未配置时不带链接)。
- 失败重试 3 次(10s、60s、300s),异步,不阻塞业务。

## 9. 授权

- 授权文件:JSON `{"id","licensee","edition":"TEAM","maxMembers","issuedAt","expiresAt"}` + Ed25519 签名,整体 base64 文本。
- server 内置**发行公钥**(资源文件 `META-INF/csap-team/license-public.pem`)。仓库里只放**占位**;正式公钥由用户用 `license-tool keygen` 自行生成后替换。**任何私钥都不得进入仓库、日志或测试资源**;测试里临时生成密钥对。
- 没有授权或授权无效:试用模式,最多 5 个活跃用户,界面顶部提示。过期:同试用限制,已有数据和功能**不锁**,只限制新增用户。
- 超出人数时新增 / 启用用户返回 `LICENSE_LIMIT`。
- `license-tool`:`keygen --out <dir>`、`issue --key <private.pem> --licensee ... --max-members ... --expires 2027-12-31`、`verify --pub <public.pem> <file>`。

## 10. 配置(`csap.team.*`)

```yaml
server.port: 8090
spring.datasource.*                  # 与服务共用的库
csap:
  team:
    public-url: ""                   # 控制台外部访问地址,用于通知里的链接
    rule-table-prefix: csap_apidoc_  # 开源表前缀,必须与服务一致
    database.create-tables: true
    docs.refresh-seconds: 60
    docs.headers: {}                 # 拉取服务文档时附带的 header
    sync.history-seconds: 10
    audit.retention-days: 0
    trusted-proxies: []              # 信任其 X-Forwarded-For 的代理 IP / CIDR
    license-file: ""                 # 也可在控制台上传
```

## 11. 测试与验收

- 后端:单元测试 + H2 集成测试覆盖第 2–9 节;MySQL 8 / PostgreSQL 16 用 Testcontainers 各跑一遍发布、冲突、回滚、历史同步;达梦、人大金仓标注"未在真实库验证"。
- 互通(主会话验收时做):团队版发布修改单后,Java 1.4.0 服务和 Go v0.2.0 服务在轮询周期内生效;服务 DevTools 改规则后团队版审计出现 `rule.changed_outside`。
- 前端:`npm run build` 无错误,`npm run lint`、类型检查通过;关键页面有组件测试(Vitest)。
- 安全:密码 BCrypt;令牌只存哈希;Cookie HttpOnly;SQL 全部参数化;表名前缀只允许 `[A-Za-z0-9_]`;日志不打印密码、令牌、webhook secret。

## 12. 不在 1.0

多环境与推广、评论、编辑中提示、审计哈希链、SSO(OIDC/LDAP)、细粒度权限、API 监控、AI 辅助。表里可以预留列,但不实现。
