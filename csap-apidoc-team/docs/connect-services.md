# 服务接入

读者:项目管理员、各服务的开发者。读完能把一个 Java 或 Go 服务接到团队版,并在文档中心看到它。

**版本要求:服务注册客户端从开源 Java 1.5.0 / Go v0.3.0 起提供。** 更早的版本(Java 1.4.x、Go v0.2.x)可以共用数据库、被团队版发布的修改单热更新,但不会自动注册;它们要靠管理员[手工添加](#手工添加服务)。

## 接入前要明白的三件事

1. **共用一个库。** 团队版通过数据库修改规则,服务通过数据库读到规则。服务必须先开启开源的数据库模式,并且连接和团队版相同的库、相同的表前缀。
2. **服务名要一致。** 数据库模式里每个服务有一个 `service` 标识(规则表的 `service` 列)。团队版按这个名字找规则,注册时的服务名也是它。同一个库里每个服务的名字不能重复。
3. **注册只管"文档中心看得到",不管"规则能改"。** 规则能否在团队版里修改,取决于服务是否用了数据库模式、`service` 是否对得上。

| 能力 | 需要什么 |
|---|---|
| 文档中心里看到服务、搜索接口 | 服务注册(或手工添加),团队版能访问到服务的文档地址 |
| 在团队版查看规则、历史、发布修改单 | 服务开启数据库模式,同库同前缀,`service` 与团队版里的服务名相同 |
| 团队版里查 MCP 调用 | Java 服务把 MCP 审计写入数据库(`store: database`) |

## 第一步:服务端开启数据库模式

### Java

在启动类上开启数据库模式(`csap-apidoc-boot-starter` 已包含,无需额外依赖):

```java
@SpringBootApplication
@EnableApidoc(apiPackages = "com.example.controller", paramType = ApiStrategyType.DATABASE)
public class Application { /* ... */ }
```

配置(与团队版对应的项已标出):

```yaml
csap:
  apidoc:
    database:
      table-prefix: csap_apidoc_   # 必须与团队版 csap.team.rule-table-prefix 一致
      service: order-service       # 服务名;空时取 spring.application.name,再空为 default
      poll-interval: 5s            # 服务轮询规则版本号的间隔,团队版发布后最长等这么久生效
```

应用本身的 `DataSource` 要指向和团队版**同一个库**。完整配置项见开源文档"数据库模式"一页。

### Go

```go
s := sqlstrategy.New(sqlstrategy.Options{
    DB:          db,                 // 与团队版同一个库
    Dialect:     sqlstrategy.MySQL,  // 或 sqlstrategy.Postgres
    Service:     "order-service",    // 服务名
    TablePrefix: "csap_apidoc_",     // 与团队版一致
})
```

Go 的 `Service` 不填时是 `default`。**同一个库里多个服务都用 `default` 会互相覆盖规则**,务必各自设置。

## 第二步:在团队版创建令牌

注册要用 `REGISTER` 令牌。

1. 管理员登录控制台,进入"管理 → 令牌"。
2. 点"新建令牌":名称写服务或用途(例如 `order-register`),用途选 `REGISTER(服务注册)`。
3. "限定项目"可选。选了项目后,用这个令牌注册上来的**新服务**自动归属该项目;令牌也只能操作该项目的服务。
4. 创建后**令牌明文只显示一次**(格式 `csapt_` 开头),立刻复制保存。关闭窗口后无法再次查看,丢了只能撤销重建。
5. 把令牌放进服务的**环境变量或密钥管理**,不要写进代码仓库和配置文件。

令牌只在团队版里存哈希值;控制台列表里只显示前 8 位前缀用来识别。撤销令牌后,用它的服务注册请求会被拒绝(HTTP 401),已注册的服务因为不再心跳,几分钟后在文档中心显示离线。

## 第三步:配置注册客户端

### Java(Spring Boot)

`csap-apidoc-boot-starter` 1.5.0 起带注册客户端,配置前缀 `csap.apidoc.team`(Spring Boot 2 和 3 都支持):

```yaml
csap:
  apidoc:
    team:
      enabled: true
      url: https://team.example.com          # 团队版地址
      token: ${CSAP_TEAM_TOKEN}              # REGISTER 令牌,用环境变量
      # service: order-service               # 默认取 database.service → spring.application.name → default
      # base-url: http://10.0.0.5:8080       # 团队版能访问到的本服务地址,含 context-path,不带末尾 /
      # instance-id: 10.0.0.5:8080           # 同一服务内唯一,默认 host:port
      # doc-path: /csap/apidoc               # 文档接口路径
```

| 配置项 | 默认 | 说明 |
|---|---|---|
| `enabled` | `false` | 开关。关闭或 `url` 为空时不创建线程、不发请求 |
| `url` | 空 | 团队版地址 |
| `token` | 空 | `REGISTER` 令牌 |
| `service` | 见右 | 依次取 `csap.apidoc.team.service` → `csap.apidoc.database.service` → `spring.application.name` → `default` |
| `base-url` | 自动推断 | 推断为 `http://{本机首个非回环 IPv4}:{server.port}{context-path}`,启用 `server.ssl.enabled` 时为 `https`。**容器、网关、多网卡环境请显式配置** |
| `instance-id` | `host:port` | 同一服务多个实例时每个实例要不同 |
| `doc-path` | `/csap/apidoc` | 文档接口路径 |

### Go

```go
reg := team.Register(ctx, team.Options{
    URL:     "https://team.example.com",
    Token:   os.Getenv("CSAP_TEAM_TOKEN"),
    Service: s.Service(),                  // 与数据库模式的服务名保持一致
    BaseURL: "http://10.0.0.5:8080",       // 必填
    // InstanceID、DocPath、HTTPClient、Logger(slog)可选
})
defer reg.Close() // 发送注销并停止心跳
```

包路径 `github.com/csap-ai/csap-apidoc/csap-apidoc-go/team`。Go 无法可靠得知监听地址,**`BaseURL` 必填**,为空时不注册并打一条警告。`Service` 不填是 `default`,建议传数据库模式的服务名。

### 客户端行为(两种语言一致)

- **绝不影响业务**:注册在后台线程 / goroutine 里进行,团队版不可达、返回错误都不会阻止服务启动。
- 注册成功后按团队版返回的心跳周期(默认 30 秒)重复发送同一个请求;团队版配置的值会被客户端限制在 10–300 秒。
- 服务正常关闭时发送一次注销。
- 请求超时 3 秒(注销 2 秒)。失败后按心跳周期重试,日志降噪:首次失败告警,之后每 10 次告警一次,恢复时记一条 info。
- 返回 401 / 403:告警"令牌无效或权限不足",之后每 300 秒重试一次。
- 令牌不会出现在日志和异常信息里。

### 注册后在团队版看到什么

- 团队版收到注册后,服务出现在"管理 → 服务归属"。令牌限定了项目的,服务自动归属该项目;没限定的,显示在"未分配的注册服务"里,等管理员分配。
- 服务在 3 个心跳周期(默认 90 秒)内有上报就是"在线";否则"离线"。
- 团队版每 60 秒拉一次 `{baseUrl}{docPath}`(默认 `/csap/apidoc`)。**团队版所在网络必须能访问到 `baseUrl`**。拉取失败时保留上次内容并标记"缓存(拉取失败)"。
- 服务的文档接口如果加了鉴权,在团队版配置 `csap.team.docs.headers`(见 [部署与运维](install.md#配置项))。

## 服务归属项目

一个服务只属于一个项目。归属决定了谁能看到这个服务(项目成员),以及修改单属于哪个项目。

三种方式:

1. **令牌限定项目**:创建令牌时选项目,新注册的服务自动归属。
2. **管理员或项目维护者分配**:"管理 → 服务归属"里,把"未分配的注册服务"分配到项目;也可以改归属。
3. **手工添加时指定项目**。

未分配项目的服务**只有管理员能看到**,也不能发起修改单(回滚会提示"服务尚未分配到项目")。已经归属某项目的服务,令牌限定了另一个项目时,注册会被拒绝(HTTP 403)。

## 手工添加服务

适用于:服务版本低于 Java 1.5.0 / Go v0.3.0,或不方便让服务访问团队版。

1. "管理 → 服务归属"→"手工添加服务"。
2. 填服务名、服务地址 `baseUrl`(`http` 或 `https`)、文档路径(默认 `/csap/apidoc`)、所属项目。
   - 服务名只能含字母、数字和 `.` `_` `-`,长度 1–128。
   - 项目维护者可以给自己的项目添加;不指定项目只有管理员能添加。
3. 保存后团队版立即拉取一次文档。

手工服务没有心跳。它的状态按最近一次拉取是否成功判断:成功为在线,失败但有缓存为"缓存(拉取失败)",否则离线。

手工添加的服务名要与数据库模式里的 `service` 对得上,才能在团队版修改它的规则。

## MCP 调用审计落库(Java)

想在团队版"审计 → MCP 调用"里看到某个服务的 MCP 调用,需要让服务把审计写进数据库。Go 没有 MCP,只有 Java 提供。

```yaml
csap:
  apidoc:
    mcp:
      enabled: true
      audit:
        store: database     # memory(默认) / sqlite / database
        max-rows: 10000     # 每个服务保留最新的这么多条
```

- 需要先按第一步开启数据库模式。审计使用同一个 DataSource、方言、表前缀和 `service`,写入 `{前缀}mcp_audit` 表(默认 `csap_apidoc_mcp_audit`)。
- 表不存在时服务按 `init-schema`(默认 `true`)自动创建。
- 写入在后台线程进行,队列有界;写库变慢或失败时新记录会被丢弃,不会阻塞 MCP 请求。
- 团队版只读这张表,不复制数据。表不存在(没有任何服务开启落库)时,"MCP 调用"标签显示"服务未开启 MCP 审计落库"。

## 验证接入是否成功

1. 启动服务。几秒内,"管理 → 服务归属"里应出现该服务(令牌限定了项目的,直接在项目下)。
2. "文档中心"里服务状态为"在线",点开能看到接口文档。
3. 在"规则"页选该服务,能看到规则条目(服务至少保存过一次规则后才有)。
4. 在团队版发布一张修改单,服务在 `poll-interval`(默认 5 秒)内热更新。

自己用 curl 模拟一次注册(令牌为占位符):

```bash
curl -s -X POST https://team.example.com/api/open/v1/services/register \
  -H 'Authorization: Bearer <REGISTER 令牌>' -H 'Content-Type: application/json' \
  -d '{"service":"order-service","instanceId":"10.0.0.5:8080","baseUrl":"http://10.0.0.5:8080",
       "docPath":"/csap/apidoc","language":"java","frameworkVersion":"1.5.0"}'
# {"heartbeatSeconds":30}
```

`service`、`instanceId`、`baseUrl`、`language`、`frameworkVersion` 必填。

| 现象 | 原因 |
|---|---|
| 返回 401 | 令牌错误或已撤销 |
| 返回 403 | 令牌用途不是 `REGISTER`,或令牌限定的项目与服务已归属的项目不一致 |
| 返回 400 `VALIDATION` | 字段缺失或格式不对(`baseUrl` 必须是 http/https) |
| 服务一直离线 | 服务没有心跳,或心跳停了超过 3 个周期 |
| 在线但文档为空 / "缓存" | 团队版访问不到 `baseUrl + docPath`,检查网络、防火墙、文档鉴权 |
| 文档里有,规则页没有 | 服务没开数据库模式,或 `service` 对不上,或表前缀 / 库不一致 |

## 用令牌读写规则(开放接口)

团队版另有 `OPEN_API` 用途的令牌,供脚本和 CI 使用。令牌绑定项目时只能操作该项目的服务。

```bash
# 读规则
curl -s https://team.example.com/api/open/v1/rules/order-service \
  -H 'Authorization: Bearer <OPEN_API 令牌>'

# 创建修改单并提交审批
curl -s -X POST https://team.example.com/api/open/v1/changes \
  -H 'Authorization: Bearer <OPEN_API 令牌>' -H 'Content-Type: application/json' \
  -d '{"projectId":1,"title":"调整下单金额校验","reason":"产品要求","submit":true,
       "items":[{"service":"order-service","ruleKey":"<规则 key>","afterRules":{"amount":{"min":1}}}]}'
```

要点:

- `afterRules` 必须出现;`null` 表示删除该规则。
- `baseVersion` 可省略,省略时取当前版本。
- 令牌**不能审批**修改单。修改单的作者记为令牌的创建者,审计来源为 `OPEN_API`,操作者类型为令牌。
- 令牌的创建者被停用后,令牌调用失败(HTTP 401)。
