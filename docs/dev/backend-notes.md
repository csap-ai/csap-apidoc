# 后端实现说明与契约解读

契约是 [../design/team-1.0.md](../design/team-1.0.md)。本文只记录契约没有写死、后端实现时做的选择,以及已知边界。
**契约字段、路径、错误码、状态值均未改动。**

## 代码结构(`server/src/main/java/ai/csap/apidoc/team`)

| 包 | 职责 |
|---|---|
| `config` | `TeamProperties`(`csap.team.*`)、静态资源与 SPA 回退 |
| `db` | `Db`(JdbcTemplate、事务、方言、开源表名)、`SchemaInitializer`(缺表建表)、`Settings`(setting / cursor) |
| `auth` | `AuthFilter`(Cookie 会话 + `X-CSAP-Team` + 令牌)、`SessionTokens`(HS256)、`LoginGuard`、`Access`(权限矩阵)、`ClientIp` |
| `user` `project` `service` `token` | 用户、项目与成员、服务目录与注册、令牌 |
| `rule` | `RuleReader`(读开源三张表)、`RulePublisher`(比较并交换发布)、`RuleController` |
| `change` | `ChangeService`(状态机)、`ChangeController` |
| `docs` | 文档拉取、缓存、搜索索引、服务状态与离线事件 |
| `audit` | 审计写入 / 查询 / 导出 / 保留期、MCP 审计读取 |
| `sync` | `HistorySync`:`{prefix}history` → `rule.changed_outside` |
| `webhook` | 四种格式、签名、重试、投递记录 |
| `license` | Ed25519 授权校验与人数限制 |

## 发布与开源 `DatabaseRuleStore.save` 的一致性

`RulePublisher.publish` 在调用方事务里做:

1. 对涉及的服务按名称排序,`SELECT version FROM {prefix}meta WHERE service=? FOR UPDATE`(meta 行不存在时,在**事务之外**先插入 `version=0`,
   避免并发插入时 PostgreSQL 事务被唯一键冲突中止);
2. 对每条规则 `SELECT ... FOR UPDATE` 读当前行;`version != base_version` 且内容不同 → 整单冲突,什么都不写;内容相同(`RuleJson.sameJson`)→ 跳过;
3. 其余条目逐条:`UPDATE meta SET version = version + 1`、写 `{prefix}rule`(删除则 DELETE)、写 `{prefix}history`(取回自增 id 存进 `published_history_id`)。

写入的列和开源 `save` 完全相同(`updated_by` = 发布人的用户名;`updated_at` 毫秒)。`CoreFlowTests.publishWritesSameRowsAsOpenSourceSave`
用同一组操作分别走团队版和 `DatabaseRuleStore.save`,逐行比对 history,`interopWithOpenSourceDatabaseRuleStore` 验证服务侧 `load()` 与反向的审计。
没有复制开源代码,只复用了 `DatabaseDialect`、`DatabaseRuleStore.schema(...)`(缺表时建开源三张表)、`RuleJson.sameJson`。

## 契约没写死的地方,实现时的选择

| 问题 | 选择 |
|---|---|
| `SETUP_REQUIRED`、`LICENSE_LIMIT` 的 HTTP 状态码 | 412 / 403;错误码字段 `code` 为准 |
| 管理员在 `/auth/me` 的 `projects[].role` | 有成员关系取实际角色,否则 `MAINTAINER`(管理员在项目里等同维护者) |
| `GET /license`、`POST /license` | 仅管理员(契约 3 节)。非管理员拿不到试用提示,前端如需全员横幅需要契约补充 |
| `GET /rules/{service}/diff` 的 `from` / `to` | 都是 history id;比较"该条历史之后的规则内容",`to` 省略 = 与当前规则比较 |
| 历史条目 | 在契约字段外多返回 `ruleKey`、`diff`(before→after 的统一差异) |
| `GET /changes` 列表 | 每张单附 `itemCount` 和精简 `items`(service/ruleKey/baseVersion);完整内容(含 diff、currentVersion、conflict)用 `GET /changes/{id}` |
| `afterRules` 入参 | JSON 字符串或 JSON 对象均可;**必须出现**(`null` = 删除),缺失报 `VALIDATION`,防止漏传导致误删;`baseVersion` 缺省取当前版本 |
| 修改单权限 | 编辑 / 提交 / rebase 仅作者(管理员也不行);reject 的 note 必填 |
| Webhook url | 列表 / 详情只返回协议 + 主机 + `/***`;PATCH 不传 url 保持原值;审计不记 url |
| 撤回 | 只允许 DRAFT / PENDING(按状态机图);CONFLICT 只能 rebase。作者或项目维护者 / 管理员可撤回 |
| 审批 / 驳回 | 管理员可审批自己的;维护者不能审批自己提交的;令牌不能审批 |
| 项目角色之外的资源可见性 | 不在项目里的人访问项目 / 服务 / 修改单返回 `NOT_FOUND`(不暴露存在性);权限不足返回 `FORBIDDEN` |
| 发布时 `updated_by` | 发布动作的执行人(审批人;无需审批的项目为提交人;令牌创建修改单时为令牌创建者的用户名) |
| 审计里发布记录 | 每条真正写入的规则一条 `change.publish`(含 before / after / diff / `oss_history_id`);内容相同被跳过的不记 |
| 无需审批项目 | 提交即发布,审计 `change.submit` + `change.publish`,事件 `change.submitted` + `change.published` |
| `rule.changed_outside` 游标 | 首次启动把游标置为当前最大 history id,不为接入前的历史回填审计;只处理写入超过 3 秒(`csap.team.sync.settle-millis`)的记录,避免并发事务提交顺序与 id 不一致时漏记 |
| MCP 审计导出 | `GET /audit/export?kind=mcp&format=csv|json&from&to&service&caller&tool`(契约只说"导出同样支持",路径未定) |
| 审计筛选 `action` | 精确匹配;以 `*` 结尾为前缀匹配(如 `change.*`) |
| 审计可见范围 | 管理员全部;查看需要开发及以上,导出需要维护者及以上;只能看所在项目的记录(项目为空的系统记录仅管理员) |
| 登录锁定 | 内存计数(单实例,重启清零):账号(不区分大小写)+ 来源 IP 连续失败 5 次锁 15 分钟;同一账号 15 分钟内所有 IP 合计失败 50 次也锁 15 分钟;含不存在的用户名;来源 IP 遵守 `trusted-proxies` |
| 会话令牌 | HS256,载荷含用户 id、签发 / 过期时间、密码指纹(改密码后旧会话失效);超过 5 分钟的请求会换发新 Cookie(滑动过期) |
| 文档拉取目标 | 注册来源取最近上报的在线实例;手工来源取 `baseUrl`;`last_seen_at`:注册服务为最近心跳,手工服务为最近一次成功拉取 |
| ID | 团队版各表用数据库自增主键(开源 `DatabaseDialect.idColumn()` 同款写法) |

## 已知边界 / 未验证

- 达梦 DM8、人大金仓:有建表脚本和方言处理,**没有在真实库验证**。
- 登录锁定、`service.offline` 10 分钟去重、服务状态缓存都是单实例内存实现,**团队版 1.0 只支持单实例部署**(多实例 1.1 再做)。
- 授权公钥:仓库里是占位公钥(私钥已销毁),任何授权都会判为 INVALID(试用),正式发行前替换。
- 审计哈希链列(`prev_hash` / `hash`)只建列不填,1.1 再做。
