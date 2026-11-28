# 从 1.0 升级到 1.1

读者:部署运维。读完能把 1.0 的团队版平滑升到 1.1,知道哪些会自动完成、哪些要手工做、怎么回退。

## 先看这几条

- **不需要开源侧任何改动。** 依赖的开源 `csap-apidoc-database` 仍是 1.5.0,服务注册协议不变,Java / Go 服务不用升级、不用改配置。
- **不配置环境 = 1.0 的行为。** 升级后不动配置也能用,1.0 的前端和脚本照常工作。
- 表结构版本从 `1` 升到 `2`(`csap_team_setting` 的 `schema.version`)。默认**启动时自动升级**。
- **唯一不兼容的变化:审计 JSON 导出格式**,见下文。

## 升级步骤(自动升级)

1. **备份数据库**(团队版表和开源规则表一起备份,见 [部署与运维](install.md#备份与恢复))。哈希链是在升级时对已有审计封存的,备份就是升级前状态的凭证。
2. 停止旧进程,替换 jar(`csap-apidoc-team-server-1.1.0.jar`)或换新镜像,启动。
3. 启动时团队版发现 `schema.version=1`,依次做:
   1. 执行升级脚本 `META-INF/csap-team/schema/upgrade/2/{方言}.sql`:新增表 `csap_team_service_env`、`csap_team_env_instance`、`csap_team_comment`,新增列 `csap_team_change.env_key / source_env`、`csap_team_audit.env_key`、`csap_team_token.env_key`、`csap_team_webhook.env_keys`,以及索引;
   2. 数据迁移(见下表);
   3. 全部成功后把 `schema.version` 写成 `2`。
4. 启动完成后打开控制台,检查服务列表、修改单、审计页。

### 自动迁移的内容

"默认环境" = 配置里第一个环境的 `key`(未配置环境时为 `default`),下称 `K`。

| 步骤 | 内容 |
|---|---|
| 服务地址 | `csap_team_project_service` 每行复制到 `csap_team_service_env`(环境 `K`)。原表的 `base_url / doc_path / source / last_seen_at` 列保留但 1.1 起不再读写,便于回退 |
| 修改单 | `csap_team_change.env_key` 为空的全部填 `K` |
| 审计 | `env_key` **不回填**(审计只追加)。旧记录的环境显示为"—" |
| 实例 | `csap_team_service_instance` 的数据丢弃,表保留不删。服务下一个心跳周期(默认 30 秒)内重新注册,实例数会恢复 |
| 游标 | `oss.history` 改名为 `oss.history.K`,`oss.mcp_audit` 同理 |
| 哈希链 | 给已有审计逐条计算并写入哈希(分批 1000 条),写入链头,追加一条 `audit.chain.sealed`(原因 `sealed N records at upgrade`)。审计很多时这一步需要一段时间,请等启动完成 |

升级后再改默认环境的 `key` 需要手工处理,见 [多环境与推广](environments.md#改默认环境的-key-要手工处理)。**建议升级时就把 `key` 定好。**

## `create-tables=false` 时手工升级

团队版账号没有建表 / 改表权限(`csap.team.database.create-tables=false`)时**不会自动升级**:启动失败,并提示手工执行升级脚本。

1. 备份数据库。
2. 由 DBA 执行对应方言的升级脚本:jar 内 `META-INF/csap-team/schema/upgrade/2/{mysql,postgresql,dm,kingbase,h2}.sql`(源码仓库里在 `server/src/main/resources/META-INF/csap-team/schema/upgrade/2/`)。
3. 启动 1.1。**Java 部分的数据迁移**(上表的服务地址复制、修改单填 `K`、游标改名、哈希链回填)不是 SQL,会在启动时自动做;这一步只需要对团队版表的 `SELECT / INSERT / UPDATE / DELETE`,不需要建表权限。
4. 全部成功后 `schema.version` 变为 `2`。

达梦、人大金仓的升级脚本**未在真实库验证**,请先在测试库执行并核对。新装的 1.1 直接按完整建表脚本建表并写入 `2`,不需要升级脚本。

最小权限提示:自动升级需要 `CREATE TABLE`、`ALTER TABLE`、`CREATE INDEX`。1.0 文档里说"运行账号不需要 `ALTER`",升级那一次例外;升级完成后可以收回。

## 审计 JSON 导出格式变化

**这是 1.1 唯一的不兼容变化。**

| | 1.0 | 1.1 |
|---|---|---|
| JSON 导出 | 数组 `[ {...}, {...} ]` | 对象 `{"anchor":{...}\|null,"items":[ {...} ]}` |
| 新增字段 | | 每项增加 `envKey`、`prevHash`、`hash` |
| CSV 导出 | | 增加 `envKey`、`prevHash`、`hash` 三列 |

如果你有脚本解析导出的 JSON,把读取位置从根数组改成 `items`。例如:

```python
data = json.load(open("audit.json", encoding="utf-8"))
items = data["items"] if isinstance(data, dict) else data   # 兼容 1.0 和 1.1
```

## 配置与接口兼容

- 新增配置 `csap.team.environments`、`csap.team.presence.ttl-seconds`、`csap.team.audit.checkpoint-cron`,都有默认值,不配置即为 1.0 行为。
- 所有接口的 `env` 参数可省略,省略 = 默认环境。响应只**新增**字段,不删改已有字段。
- 新增错误码 `ENV_UNAVAILABLE`(HTTP 503)。
- 新增 Webhook 事件 `change.commented`、`audit.checkpoint`;所有事件的通用请求体增加 `env`。已建的 Webhook 订阅不变,不会自动收到新事件,也不会因为新增事件改变投递行为;通用 Webhook 的接收方要容忍请求体多出字段。

## 升级后检查

- [ ] 控制台能登录,服务列表里服务在一个心跳周期后恢复"在线";
- [ ] 修改单、历史、审计的记录数与升级前一致;审计页出现一条 `audit.chain.sealed`;
- [ ] 管理员在审计页点"校验哈希链",结果通过;
- [ ] 配置了多个环境时,"环境"切换器出现,每个环境状态为 `UP`;
- [ ] 如有解析审计导出 JSON 的脚本,已改为读取 `items`。

## 回退

- **升级前备份是唯一完整的回退方式。** 回退 = 恢复备份的数据库 + 换回 1.0 的 jar。恢复后修改单、审计、规则都回到备份时刻(服务按版本号轮询,规则也回到备份时刻的版本);升级后新建的评论、环境相关数据会丢失。
- 不要只换回 1.0 的 jar 而不恢复数据库:1.0 不认识 schema 版本 `2`,而且升级时已写入的哈希链、新表对它没有意义;1.0 能否在已升级的库上正常工作**未做保证**。
- 为了便于回退,1.1 保留了旧表和旧列(`csap_team_project_service` 的地址列、`csap_team_service_instance`),但 1.1 起不再更新它们,回退后这些数据停留在升级时刻。
- 升级过程中失败(例如迁移中途出错)时,`schema.version` 不会写成 `2`,修复原因后重新启动会再次尝试;如有疑问,恢复备份重来。
