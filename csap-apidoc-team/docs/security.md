# 安全说明与部署清单

读者:部署运维、安全评审。先给清单,后面说明每一项的原因和团队版实际做了什么。

## 部署安全清单

上线前逐项确认:

| # | 项 | 怎么做 |
|---|---|---|
| 1 | 全程 HTTPS | 反向代理终止 TLS,团队版只监听内网;不要让 8090 直接暴露公网 |
| 2 | 登录 Cookie 带 `Secure`(代理终止 TLS 时**必做**) | 设置 `SERVER_FORWARD_HEADERS_STRATEGY=native`(即 `server.forward-headers-strategy=native`),并配置 `trusted-proxies`,代理传 `X-Forwarded-Proto`(见 [部署与运维](install.md#反向代理与-https)) |
| 3 | 配置 `trusted-proxies` | 只填你自己的代理 IP / 网段,审计里才有真实客户端 IP,也防止伪造 `X-Forwarded-For` |
| 4 | 配置 `public-url` | 填外部 HTTPS 地址 |
| 5 | 不用 H2 做生产库 | 生产用 MySQL / PostgreSQL(达梦、人大金仓未在真实库验证) |
| 6 | 数据库账号最小权限 | 日常账号只有增删改查,不要 DDL 和库管理权限;`mcp_audit` 表只读 |
| 7 | 数据库只对团队版和服务开放 | 防火墙限制来源;库传输加密按你们规范(JDBC URL 开启 SSL) |
| 8 | 令牌保管 | 令牌放环境变量或密钥管理,不进代码仓库;按用途拆分、限定项目;定期撤销不用的 |
| 9 | 管理员账号 | 初始化后立即使用强密码;管理员数量保持最少;离职立即停用 |
| 10 | 备份 | 每天备份数据库,备份文件按敏感数据保管,并演练恢复 |
| 11 | 单实例 | 只运行一个团队版实例 |
| 12 | 配置文件和环境变量文件权限 | `600`,只有运行账号可读 |
| 13 | Webhook 地址 | 只填受信任的地址;地址里的机器人凭据按敏感信息对待,数据库里是明文 |

## 通信与会话

| 项 | 做法 |
|---|---|
| 登录会话 | Cookie `CSAP_TEAM_SESSION`,`HttpOnly`、`SameSite=Strict`、路径 `/`;请求是 HTTPS 时带 `Secure` |
| 会话令牌 | HS256 签名;密钥在首次启动时随机生成,存在数据库 `csap_team_setting`;默认 12 小时滑动过期(`csap.team.session-hours`) |
| 改密码 / 停用 | 用户改密码、被重置密码、被停用后,旧会话立即失效 |
| CSRF | 除 GET 外的控制台接口必须带请求头 `X-CSAP-Team: 1`;结合 `SameSite=Strict`,跨站请求无法带上 Cookie |
| 开放接口 | `Authorization: Bearer <令牌>`,与 Cookie 会话互不通用 |

**要点:登录 Cookie 的 `Secure` 取决于团队版看到的请求是不是 HTTPS。** 反向代理终止 TLS 时,必须设置 `SERVER_FORWARD_HEADERS_STRATEGY=native`,否则 Cookie 不会带 `Secure`。

## 密码与登录锁定

- 密码用 BCrypt 存储;最少 8 位,最多 72 字节。
- 同一账号在同一来源 IP 连续登录失败 5 次,锁定 15 分钟,锁定期间返回 `LOCKED`(HTTP 429)。另外,同一账号 15 分钟内所有 IP 合计失败 50 次,也锁定 15 分钟。登录失败和锁定都进审计。
- 按"账号 + 来源 IP"计数,别人从其他地址输错密码不会锁住你的账号;不存在的账号名也会计数,避免被用来探测账号是否存在。来源 IP 取决于 `trusted-proxies` 配置。
- 锁定状态在进程内存里,重启后清零。
- 登录时用户不存在也会做一次哈希比较,使"用户不存在"和"密码错误"的响应时间接近,响应内容相同。
- 1.0 没有 SSO(OIDC / LDAP)和多因素认证。需要的话请在反向代理层加访问控制(例如内网、VPN、网关认证)。

## 令牌

- 令牌格式 `csapt_` + 40 位随机字符;团队版只保存 SHA-256 哈希和前 8 位前缀,**明文只在创建时显示一次**。
- 令牌有用途:`REGISTER`(只能注册)和 `OPEN_API`(读规则、创建修改单);可以限定项目。令牌不能审批修改单。
- 撤销立即生效。令牌创建者被停用后,令牌随之失效。
- 令牌不会写进日志和审计(审计里只有名称和前缀)。
- 服务端接入时,令牌放环境变量(例如 `CSAP_TEAM_TOKEN`)。

## 审计

- 审计表**只追加**:代码里没有修改审计的入口,接口和界面也没有。唯一会删除审计的是保留期清理任务,且清理本身会记一条审计。
- 记录范围:登录成功 / 失败、用户、成员、项目、服务、令牌、Webhook 的变更,修改单的全部动作,服务侧直接改规则,导出审计。
- 审计里的客户端 IP:默认取直连地址;配置 `trusted-proxies` 后,只信任来自这些代理的 `X-Forwarded-For`,从右往左跳过可信代理取第一个不可信地址。
- 服务侧(DevTools)直接改规则也会被记录,约十几秒内出现。团队版首次启动之前的历史不会回填。
- 导出的 CSV 对以 `=`、`+`、`-`、`@` 开头的单元格加前导 `'`,防止公式注入。
- **1.1 起审计带哈希链**:每条记录的哈希包含上一条的哈希,改、删记录会被发现(控制台"校验哈希链"、接口 `GET /api/v1/audit/verify`、离线脚本)。1.1 升级前的记录是升级时封存的,只能证明升级之后没被改过。要防住有数据库权限的人**重算整条链**,订阅 `audit.checkpoint` 事件,把每天的链头存到团队版之外。见 [审计哈希链](audit-chain.md)。
- 1.0 没有哈希链:具备数据库写权限的人理论上可以改表。请用数据库权限和备份保护审计表。

## 数据与权限

- 数据库访问全部使用参数化 SQL;表名前缀只允许字母、数字、下划线。
- 不在项目里的人访问项目、服务、修改单,得到"不存在"而不是"无权限",不暴露存在性。
- 日志不打印密码、令牌和 Webhook 密钥。
- 数据库里的敏感内容:`csap_team_setting`(会话签名密钥)、`csap_team_webhook`(Webhook 地址和密钥,**明文存储**,因为需要用它发请求;界面和接口只显示地址的协议和主机)、`csap_team_user.password_hash`(BCrypt)、`csap_team_token.token_hash`(SHA-256)。能读这个库的人就能读到前两项,请据此控制数据库访问和备份文件的保管。
- 规则内容本身(字段校验规则的 JSON)不含密钥,但可能暴露业务约束。

## 最小权限数据库账号

建议两个账号:

| 账号 | 用途 | 权限 |
|---|---|---|
| 建表账号(临时) | 首次建表或升级 | 目标库的 `CREATE`(表、索引) |
| 运行账号 | 团队版日常运行 | `csap_team_*` 和 `{前缀}rule`、`{前缀}history`、`{前缀}meta` 的 `SELECT / INSERT / UPDATE / DELETE`;`{前缀}mcp_audit` 的 `SELECT` |

用法:先用建表账号执行建表脚本(见 [部署与运维](install.md#由-dba-手工建表)),把 `csap.team.database.create-tables` 设为 `false`,运行账号不需要 DDL 权限。

PostgreSQL 示例(库、账号、密码为占位符):

```sql
CREATE USER csap_team WITH PASSWORD '<强密码>';
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO csap_team;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO csap_team;
```

MySQL 示例:

```sql
CREATE USER 'csap_team'@'<团队版所在主机>' IDENTIFIED BY '<强密码>';
GRANT SELECT, INSERT, UPDATE, DELETE ON csap_apidoc.* TO 'csap_team'@'<团队版所在主机>';
```

团队版发布修改单时使用行级锁(`SELECT ... FOR UPDATE`),不需要额外的锁权限。

**1.1 多环境**:每个环境用自己的数据库账号(`environments[].datasource`)。该账号只需要这个环境的 `{前缀}rule`、`{前缀}history`、`{前缀}meta` 的 `SELECT / INSERT / UPDATE / DELETE` 和 `{前缀}mcp_audit` 的 `SELECT`,**不需要**团队版自有表的权限;团队版自有表只在主数据源。环境的数据源密码用环境变量(`password: ${PROD_DB_PASSWORD}`),不要明文写进配置文件;它不会出现在日志、接口和错误信息里。生产环境建议配置 `protected: true`。

**1.1 升级**:自动升级要对团队版表执行 `CREATE TABLE`、`ALTER TABLE`、`CREATE INDEX`,升级那一次用建表账号,或手工执行升级脚本(见 [升级指南](upgrade-1.1.md))。

## 备份与恢复

- 每天备份整个库(团队版表和开源规则表同库一起备份)。
- 备份文件含会话签名密钥、Webhook 密钥,按敏感数据加密保管。
- 定期在测试环境恢复演练。
- 恢复到旧备份时,修改单、审计和规则都回到备份时刻;已发给 Webhook 的通知不会撤回。

## 已知限制

| 项 | 说明 |
|---|---|
| 单实例 | 登录锁定、服务离线通知去重、服务状态缓存、(1.1)编辑中提示在内存里,只支持单实例 |
| 无 SSO / 多因素 | 1.0 只有账号密码 |
| 审计哈希链(1.1) | 能发现篡改,不能阻止;需配合外部锚定,见上文 |
| 数据库中的 Webhook 地址和密钥为明文存储 | 界面和接口只显示地址的协议和主机,密钥只写不回显;数据库里是明文,请限制数据库访问权限 |
| 达梦、人大金仓 | 未在真实库验证 |

## 报告安全问题

发现安全问题请联系供应商的安全联系人,不要在公开渠道披露细节。
