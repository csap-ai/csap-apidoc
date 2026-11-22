# 多环境与推广(1.1)

读者:部署运维、管理员、项目维护者、开发。读完能配置开发 / 测试 / 生产多个环境,让服务按环境注册,对比环境差异并把规则推广到下一个环境。

> 本页功能从团队版 **1.1.0** 起提供。1.0 没有环境概念。

## 先看这几条

- 环境**只在配置文件里定义**,控制台里不能增删。数据库连接和密码不进团队版的表,也不出现在界面和接口里。
- 不配置环境 = 1.0 的行为:自动有一个环境 `default`(显示名"默认"),用主数据源和 `csap.team.rule-table-prefix`。老前端、老脚本不改也能用。
- 一个环境对应**一套开源规则表**(`{前缀}rule / history / meta / mcp_audit`),通常是该环境自己的数据库。团队版自己的表(用户、项目、修改单、审计等)**只在主数据源**。
- 服务名跨环境是同一个服务,**项目归属不分环境**;实例、地址、在线状态、文档缓存、规则、历史、MCP 审计按环境区分。
- 角色仍按项目,不分环境。环境只通过 `protected`、`read-only` 影响行为。

## 配置环境

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

### 字段说明

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `key` | 是 | | 环境标识,格式 `[a-z0-9-]{1,32}`,唯一。写进各表的 `env_key`,**定了以后不要改**(见下文) |
| `name` | 是 | | 显示名,界面、通知里用 |
| `datasource.url` | | 主数据源 | 该环境的开源规则表所在的库。省略整个 `datasource` = 用主数据源(`spring.datasource`) |
| `datasource.username` / `password` | | | 账号、密码 |
| `datasource.driver-class-name` | | 按 URL 识别 | 可省略 |
| `rule-table-prefix` | | `csap.team.rule-table-prefix` | 该环境的开源规则表前缀,只允许字母、数字、下划线,**必须与该环境里服务的 `table-prefix` 一致** |
| `protected` | | `false` | `true`:发布到这个环境一律要审批,不看项目的"需要审批"开关 |
| `read-only` | | `false` | `true`:不能向这个环境发布(建修改单、编辑、提交、通过、rebase、回滚、推广都返回 `FORBIDDEN`,提示"环境只读"),只能看 |

### 规则

- **顺序**即界面里的展示顺序,也是推广的默认方向(dev → test → prod)。**第一个是默认环境**:接口不传 `env` 时用它。
- 启动时校验,有错则启动失败并给出中文说明:`key` 格式、重复 `key`、`name` 为空、前缀格式、同一数据源 + 同一前缀被两个环境共用。
- 各环境的服务要连**各自环境**的那个库、用**对应的前缀**,这样团队版往该环境发布的规则,该环境的服务才收得到。
- 团队版自己的表只在主数据源。各环境的数据源只用来读写该环境的开源表。
- 环境的开源表不存在时:`create-tables=true` 会在该环境的库里建开源三张表(方式同 1.0);为 `false` 时该环境状态为 `DOWN`(不影响其它环境和启动),错误信息给出原因。
- 每个独立数据源有一个小连接池(最大 5)。启动时连不上不会阻止启动,该环境状态为 `DOWN`,之后每 60 秒重试。
- 日志、接口、错误信息里不会出现数据源密码。

### 密码用环境变量

`password: ${DEV_DB_PASSWORD}` 读取同名环境变量。**不要把密码明文写进配置文件。**

```bash
export DEV_DB_PASSWORD='<开发库密码>'
export PROD_DB_PASSWORD='<生产库密码>'
```

### protected 与 read-only

| | 创建 / 编辑修改单 | 发布 | 回滚、推广到此环境 | 查看规则、文档 |
|---|---|---|---|---|
| 普通 | 可以 | 项目需要审批才审批 | 同修改单 | 可以 |
| `protected: true` | 可以 | **一律要审批**(项目关了审批也一样) | 同修改单,要审批 | 可以 |
| `read-only: true` | 拒绝(`FORBIDDEN`) | 拒绝 | 拒绝 | 可以 |

一张修改单是否需要审批 = 项目的"需要审批"开启 **或** 环境 `protected: true`。修改单详情里的 `requireApproval` 字段就是这个结果。

建议生产环境设 `protected: true`;需要冻结某个环境(例如封网期间)时临时设 `read-only: true` 并重启。

### 环境不可用(DOWN)

某个环境的库连不上或缺表时,该环境状态为 `DOWN`:

- 读写该环境开源表的操作(规则、历史、差异、推广、文档中的规则部分)返回 `ENV_UNAVAILABLE`(HTTP 503),提示里带环境名;
- 其它环境照常工作;
- 控制台环境切换器上显示该环境不可用。恢复后最多 60 秒自动回到 `UP`。

### 只有一个环境

不配置 `environments`,或只配一个环境,界面与 1.0 基本一致:不显示环境切换器和环境相关控件。

### 改默认环境的 key 要手工处理

升级时 1.0 的数据会归入"默认环境"(配置里第一个,未配置时为 `default`)。升级**之后**如果再改默认环境的 `key`(或调整顺序让别的环境排第一再改名),团队版不会自动迁移已有数据。需要手工把下列表里的 `env_key` 改成新值(先备份):

- `csap_team_service_env`、`csap_team_env_instance`、`csap_team_change`、`csap_team_token`(绑定了该环境的)、`csap_team_audit`(审计只追加,历史记录**不建议改**;旧记录的 `env_key` 本来就可能为空)、`csap_team_webhook.env_keys`;
- `csap_team_setting` 里的游标 `oss.history.{key}`、`oss.mcp_audit.{key}` 改名。

最稳妥的做法:**第一次部署就把 key 定好,以后不改。** 只想换显示名,改 `name` 即可,随时可改。

## 服务按环境注册

服务注册协议**不变**。区别在于令牌:

1. "管理 → 令牌"创建 `REGISTER` 令牌时,可以**绑定环境**(下拉选择)。
2. 用这个令牌注册上来的服务,落到令牌绑定的环境;令牌没有绑定环境的,落到默认环境。
3. 每个环境给每组服务发一个绑定了该环境的令牌,开发环境的服务用 dev 令牌,测试环境的用 test 令牌。
4. 服务注册请求体里即使带了 `environment` 字段也会被忽略,环境只由令牌决定。

同一个服务名在 dev 和 test 各注册一次,就是同一个服务在两个环境里各有一套地址和实例。文档中心的服务列表里能看到每个环境的状态。

手工添加服务时可以选环境;同一服务在另一个环境再添加一次,就是给那个环境加地址。

示例(Java 服务注册到 test 环境,令牌为占位符,`<test 环境的 REGISTER 令牌>` 在控制台创建时绑定了 test):

```yaml
csap:
  apidoc:
    team:
      enabled: true
      url: https://team.example.com
      token: ${CSAP_TEAM_TOKEN}      # 环境变量里放 test 环境绑定的令牌
```

Go 服务同理,把 `Token` 换成对应环境的令牌即可。服务这一侧不需要任何改动。

## 看某个环境

- 顶栏的**环境切换器**(配置了 2 个及以上环境时显示)选环境。当前环境记在 URL 的 `env` 参数里,复制链接发给同事,对方打开的就是同一个环境。
- 规则、文档中心、修改单列表、审计都按当前环境显示。受保护环境用醒目颜色标注(例如生产的红色标签)。
- 接口调用不传 `env` 时用默认环境。

## 环境差异对比

菜单"环境对比"。

1. 选**来源环境**、**目标环境**(两个不同的环境)和项目。
2. 列表按服务分组,只显示**不同**的规则,每条有状态标签和两边的版本号:

   | 状态 | 含义 |
   |---|---|
   | 不同(`DIFFERENT`) | 两个环境都有这条规则,内容不同 |
   | 仅来源有(`ONLY_FROM`) | 目标环境没有这条规则 |
   | 仅目标有(`ONLY_TO`) | 来源环境没有这条规则 |

3. 点开一条,看差异。差异是**"目标 → 来源"**的统一差异,也就是"推广之后目标环境会怎么变"。

内容是否相同按规则 JSON 的语义比较(键顺序、空白不同不算不同)。看差异的权限同"看规则"。

接口:`GET /api/v1/envs/diff?from=&to=&projectId=&service=`(`projectId` 与 `service` 至少给一个)、`GET /api/v1/envs/diff/item?from=&to=&service=&key=`。

## 推广

推广 = 把来源环境的规则内容带到目标环境。**推广也走修改单**:生成一张类型为"推广"(`PROMOTE`)的修改单,针对目标环境,之后的审批、冲突、rebase、撤回与普通修改单完全相同。

### 步骤

1. 在"环境对比"页选好来源、目标和项目。
2. 勾选要推广的规则(不勾选 = 该项目所有服务的"不同"和"仅来源有"的规则)。
3. 点"推广",在弹窗里填**标题**和**原因**,可选:
   - **包含删除**:连"仅目标有"的规则一起处理,推广后会在目标环境删除它们;
   - **创建后立即提交**:创建后马上提交(即"一键推广")。
4. 创建后得到一张修改单(`kind=PROMOTE`,目标环境为 `to`,来源环境为 `sourceEnv`)。

提交之后:

- 目标环境需要审批(环境 `protected` 或项目需要审批)→ 状态"待审批",等维护者通过;
- 否则直接发布;发布时发现目标环境版本已变 → "冲突",按[使用手册](user-guide.md#冲突与更新到最新版本)处理。

### 要点

- 每条规则的"修改后" = 来源环境当前内容(删除时为空),基线版本和"修改前" = 目标环境当前内容。
- 两个环境没有差异时创建会失败,提示"两个环境没有差异"。
- 不限制方向,允许从 prod 往 test 拉回;前端默认给出"推广到下一个环境"。
- 谁能推广:能在**目标环境所在项目**建修改单的人(管理员、维护者、开发)。
- 目标环境 `read-only` 时拒绝;`DOWN` 时返回 `ENV_UNAVAILABLE`。
- 审计:创建推广单记 `change.create`,原因里带 `promote dev→test`。
- 发布后服务在轮询周期内(默认约 5 秒)按该环境的库热更新。

### 接口示例

```bash
curl -s -X POST https://team.example.com/api/v1/promotions \
  -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -b cookies.txt \
  -d '{"projectId":1,"from":"dev","to":"test","includeDeletes":false,
       "title":"把 dev 的校验规则推到测试","reason":"联调通过","submit":true}'
```

`items` 可选,形如 `[{"service":"order-service","ruleKey":"<规则 key>"}]`,省略表示全部差异。

## 其他按环境的行为

- **服务离线**事件按"环境 + 服务"判定,10 分钟去重也按"环境 + 服务"。
- **服务侧直接修改**(`rule.changed_outside`)对每个正常的环境分别检测,事件和审计带环境。
- **MCP 调用审计**读该环境的 `{前缀}mcp_audit`;审计页切换环境即可。
- **审计**记录带 `env`(与环境无关的动作如登录、用户管理为空,显示"—");可按环境筛选。
- **Webhook** 可选订阅的环境(空 = 全部环境);通知正文里带环境名,例如 `[订单 / 生产] 张三 的修改单「...」已发布(2 条规则)`;通用 Webhook 请求体增加 `"env":{"key","name"}`(与环境无关的事件为 null)。

## 接口与错误码速查

| 接口 | 说明 |
|---|---|
| `GET /api/v1/envs` | 环境列表:`key`、`name`、`order`、`protected`、`readOnly`、`status`(`UP`/`DOWN`)、`error`、`default`。不含数据源地址 |
| `GET /api/v1/envs/diff` / `/envs/diff/item` | 环境差异 |
| `POST /api/v1/promotions` | 推广 |
| 其它规则、文档、修改单、审计接口 | 增加查询参数 `env`(开放接口同样),省略 = 默认环境 |

新增错误码:`ENV_UNAVAILABLE`(HTTP 503)——目标环境不可用(`DOWN`),message 带环境名。
