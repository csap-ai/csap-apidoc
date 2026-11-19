# 部署与运维

读者:部署运维。读完能把团队版装起来、接到数据库、放到反向代理后面,并知道怎么升级和备份。

## 先看这几条

- 团队版是**一个 Java 17 的可执行 jar**(也提供 Docker 镜像构建文件),内含控制台前端。
- 它必须连接**你的服务共用的那个数据库**,并且表前缀与服务一致。不要给团队版单独建库。
- **1.0 只支持单实例部署**。登录锁定、服务状态等放在进程内存里,起两个实例会互相看不见。
- 默认端口 `8090`;默认数据库是本地文件 H2,**仅供试用**。
- 首次访问控制台,完成初始化向导(创建组织和第一个管理员)。

## 运行要求

| 项 | 要求 |
|---|---|
| Java | 17(Docker 镜像已带) |
| 内存 | 镜像默认堆 `-Xms256m -Xmx768m`,按数据量调整 |
| 数据库 | 见下表,必须与被管理的服务共用同一个库 |
| 网络 | 团队版要能访问各服务的文档地址(默认 `{baseUrl}/csap/apidoc`);各服务要能访问团队版(服务注册用) |
| 浏览器 | 现代浏览器(Chrome / Edge / Firefox / Safari 近两年的版本) |

### 支持的数据库

| 数据库 | 版本 | 状态 | 驱动 |
|---|---|---|---|
| MySQL | 5.7.7 及以上、8.x | 已用 MySQL 8 做集成测试 | 随 jar 带 |
| PostgreSQL | 12 及以上 | 已用 PostgreSQL 16 做集成测试 | 随 jar 带 |
| H2 | 内置 | **仅试用 / 测试** | 随 jar 带 |
| 达梦 DM8 | DM8 | **未在真实库验证,试用** | 自行获取,见下文 |
| 人大金仓 KingbaseES | V8 / V9 | **未在真实库验证,试用** | 自行获取,见下文 |

说明:

- MySQL 低于 5.7.7 时,开源规则表的复合主键超出索引长度限制,建表会失败。库字符集用 `utf8mb4`。
- 达梦、人大金仓有建表脚本和方言处理,但没有连真实库跑过。上线前请先在测试库验证建表、发布修改单、服务热更新。
- 与服务侧一致:Java 服务用 `csap.apidoc.database`,Go 服务用 `strategy/sql`,三张规则表结构两种语言相同。

## 数据库准备

团队版读写两类表,**都在同一个库里**:

| 类别 | 表 | 谁创建 |
|---|---|---|
| 开源规则表 | `{前缀}rule`、`{前缀}history`、`{前缀}meta`(默认前缀 `csap_apidoc_`) | 服务启动时创建;团队版发现缺表时也会按相同结构创建 |
| 团队版自有表 | `csap_team_*`(用户、项目、修改单、审计、令牌、Webhook 等) | 团队版首次启动时创建 |

要点:

1. 服务的 `csap.apidoc.database` 与团队版指向**同一个库**,表前缀(服务侧 `table-prefix`,团队版 `csap.team.rule-table-prefix`)**必须一致**。
2. 如果连到一个空库,团队版会在里面创建开源三张表,之后服务再连这个库才能共用。连错库的症状是:服务的规则在团队版里看不到,团队版发布的规则服务收不到。
3. 开源表的结构团队版**不会修改**。
4. 数据库账号需要对上述表有 `SELECT / INSERT / UPDATE / DELETE`;首次建表需要 `CREATE TABLE / CREATE INDEX`(见"最小权限")。
5. 团队版查看 MCP 审计时读取 `{前缀}mcp_audit`(服务端开启落库后才有,见 [服务接入](connect-services.md)),只需要 `SELECT`。

### 由 DBA 手工建表

账号没有建表权限时:

1. 设置 `csap.team.database.create-tables=false`。
2. 由 DBA 执行 jar 内 `META-INF/csap-team/schema/{mysql,postgresql,dm,kingbase,h2}.sql`(源码仓库里在 `server/src/main/resources/META-INF/csap-team/schema/`)。
3. 开源三张表由服务侧的建表脚本创建(见开源文档"数据库模式"一页)。

关闭自动建表后,缺表时团队版启动会失败,并在日志里指出要执行哪个脚本。

### 最小权限数据库账号

- 日常运行:对团队版表和开源三张表的增删改查;对 `{前缀}mcp_audit` 只读。
- 不需要 `DROP`、`ALTER`、库级管理权限。
- 建表用一个临时的高权限账号执行,或交给 DBA,之后运行账号只保留日常权限。

## 用 jar 部署

```bash
java -jar csap-apidoc-team-server-1.0.0.jar
```

用环境变量指定数据库(MySQL 示例,账号密码为占位符):

```bash
export CSAP_TEAM_DB_URL='jdbc:mysql://db.example.com:3306/csap_apidoc?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export CSAP_TEAM_DB_USER='csap'
export CSAP_TEAM_DB_PASSWORD='<数据库密码>'
export CSAP_TEAM_RULE_TABLE_PREFIX='csap_apidoc_'
export CSAP_TEAM_PUBLIC_URL='https://team.example.com'
java -Xms256m -Xmx768m -jar csap-apidoc-team-server-1.0.0.jar
```

PostgreSQL 的 URL 写成 `jdbc:postgresql://db.example.com:5432/csap_apidoc`。

改端口:`SERVER_PORT=8091` 或 `--server.port=8091`。

### 配置的三种写法

团队版是 Spring Boot 应用,配置可以用以下任一方式(后者覆盖前者):

1. jar 同目录下的 `config/application.yml`(或 `application.yml`);
2. 环境变量:`csap.team.public-url` 写成 `CSAP_TEAM_PUBLICURL`,`csap.team.docs.refresh-seconds` 写成 `CSAP_TEAM_DOCS_REFRESHSECONDS`(去掉连字符,点换下划线);列表用逗号分隔,例如 `CSAP_TEAM_TRUSTEDPROXIES=10.0.0.0/8,192.168.0.0/16`;
3. 命令行参数:`--csap.team.public-url=https://team.example.com`。

`application.yml` 里的 `CSAP_TEAM_DB_URL`、`CSAP_TEAM_DB_USER`、`CSAP_TEAM_DB_PASSWORD`、`CSAP_TEAM_PUBLIC_URL`、`CSAP_TEAM_RULE_TABLE_PREFIX`、`CSAP_TEAM_LICENSE_FILE` 这六个环境变量是预置好的快捷写法。

### 作为系统服务运行

示例(systemd,路径和用户按实际修改):

```ini
[Unit]
Description=CSAP Apidoc Team
After=network.target

[Service]
User=csap
WorkingDirectory=/opt/csap-team
EnvironmentFile=/opt/csap-team/team.env
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/csap-team/csap-apidoc-team-server-1.0.0.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

`team.env` 里放上面的 `CSAP_TEAM_*` 变量,文件权限设为 `600`。

### 健康检查

团队版不带 actuator。可用公开接口探活:

```bash
curl -s http://localhost:8090/api/v1/setup/status
# {"initialized":false}   首次;完成初始化后为 true
```

## 用 Docker 部署

仓库 `docker/` 目录有 `Dockerfile` 和两个 docker-compose 示例(MySQL 8、PostgreSQL 16)。

构建镜像(先在仓库根目录构建 jar):

```bash
mvn -B -pl server -am package -DskipTests
docker build -f docker/Dockerfile -t csap-apidoc-team:1.0.0 .
```

镜像基于 `eclipse-temurin:17-jre`,以非 root 用户 `csap` 运行,暴露 `8090`,数据目录 `/app/data`(`VOLUME`)。环境变量 `JAVA_OPTS` 控制 JVM 参数,默认 `-Xms256m -Xmx768m`。

用 docker-compose 起一套带 MySQL 的演示环境:

```bash
export DB_PASSWORD='<数据库密码>'
export DB_ROOT_PASSWORD='<数据库 root 密码>'
export PUBLIC_URL='https://team.example.com'      # 可选
docker compose -f docker/docker-compose.mysql.yml up -d
```

PostgreSQL 版只需要 `DB_PASSWORD`,文件换成 `docker/docker-compose.postgres.yml`。两个变量缺失时 compose 会直接报错并提示。

示例 compose 为了演示自带数据库。**生产环境**把 `CSAP_TEAM_DB_URL` 指向你已有的、服务共用的库,并删掉示例里的数据库服务。

用已有数据库运行镜像:

```bash
docker run -d --name csap-team -p 8090:8090 \
  -e CSAP_TEAM_DB_URL='jdbc:postgresql://db.example.com:5432/csap_apidoc' \
  -e CSAP_TEAM_DB_USER='csap' \
  -e CSAP_TEAM_DB_PASSWORD='<数据库密码>' \
  -e CSAP_TEAM_RULE_TABLE_PREFIX='csap_apidoc_' \
  -e CSAP_TEAM_PUBLIC_URL='https://team.example.com' \
  --restart unless-stopped \
  csap-apidoc-team:1.0.0
```

需要更多配置时,挂载配置文件到 `/app/config/application.yml`:

```bash
docker run ... -v $(pwd)/application.yml:/app/config/application.yml:ro csap-apidoc-team:1.0.0
```

## 配置项

前缀 `csap.team`。表中"环境变量"一列是直接可用的写法(见上文"配置的三种写法")。

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `public-url` | 空 | 控制台的外部访问地址。Webhook 通知里的链接用它;不配置则通知不带链接 |
| `rule-table-prefix` | `csap_apidoc_` | 开源规则表前缀,**必须与服务一致**。只允许字母、数字、下划线 |
| `database.create-tables` | `true` | 启动时缺表就建表。关闭后缺表会启动失败 |
| `database.dialect` | 空 | 数据库方言:`mysql`、`postgresql`、`dm`、`kingbase`、`h2`。空 = 按 JDBC URL 识别;识别不了会启动失败,要显式设置 |
| `docs.refresh-seconds` | `60` | 从各服务拉取文档的间隔(秒) |
| `docs.timeout-seconds` | `5` | 拉取单个服务文档的超时(秒) |
| `docs.headers` | 空 | 拉取服务文档时附带的请求头,例如服务给文档接口加了鉴权。写法见下 |
| `sync.history-seconds` | `10` | 检查"服务侧直接修改"的间隔(秒) |
| `sync.settle-millis` | `3000` | 只处理写入超过这个时间的修改记录,避免并发事务漏记 |
| `audit.retention-days` | `0` | 审计保留天数,`0` = 永久保留 |
| `trusted-proxies` | 空列表 | 信任其 `X-Forwarded-For` 的代理 IP 或 CIDR。不配置则审计里的 IP 是直连来源 |
| `license-file` | 空 | 授权文件路径。也可以在控制台上传(上传的优先) |
| `license-public-key-file` | 空 | 授权公钥文件。空 = 用 jar 内置公钥。自行签发授权、替换公钥时用 |
| `heartbeat-seconds` | `30` | 注册接口返回给服务的心跳周期(秒);3 个周期没有心跳视为实例离线 |
| `session-hours` | `12` | 登录会话的滑动过期时间(小时) |
| `webhook.retry-delay-seconds` | `10, 60, 300` | Webhook 失败后的重试间隔(秒),列表长度即重试次数 |

`docs.headers` 示例(YAML):

```yaml
csap:
  team:
    docs:
      headers:
        Authorization: "Bearer <服务文档接口的令牌>"
```

环境变量写法:`CSAP_TEAM_DOCS_HEADERS_AUTHORIZATION='Bearer <令牌>'`。这些请求头会发给**所有**服务,各服务的文档鉴权需一致。

数据库连接用标准 Spring Boot 配置:`spring.datasource.url / username / password`(预置了环境变量 `CSAP_TEAM_DB_URL / CSAP_TEAM_DB_USER / CSAP_TEAM_DB_PASSWORD`)。

## 反向代理与 HTTPS

生产环境应放在反向代理(Nginx、网关、负载均衡)后面,由代理终止 HTTPS。团队版自己不处理 TLS。

要做三件事:

1. **配置 `public-url`**:填用户访问控制台的外部地址,例如 `https://team.example.com`。
2. **配置 `trusted-proxies`**:填代理的 IP 或网段,审计里才会记录真实客户端 IP。不信任的来源发来的 `X-Forwarded-For` 一律忽略,防止伪造。
3. **让 Cookie 带上 `Secure`(代理终止 TLS 时必做)**:登录 Cookie 的 `Secure` 属性取决于团队版看到的请求是不是 HTTPS。代理终止 TLS 后,团队版看到的是 HTTP,需要让它识别 `X-Forwarded-Proto`:设置 `SERVER_FORWARD_HEADERS_STRATEGY=native`(标准 Spring Boot 配置 `server.forward-headers-strategy=native`),并确保代理传递 `X-Forwarded-Proto`。

`trusted-proxies` 示例(代理在 `10.0.0.0/8` 内):

```bash
export CSAP_TEAM_TRUSTEDPROXIES='10.0.0.0/8'
export SERVER_FORWARD_HEADERS_STRATEGY=native
```

Nginx 示例(域名、证书路径为占位符):

```nginx
server {
    listen 443 ssl;
    server_name team.example.com;
    ssl_certificate     /etc/nginx/certs/team.example.com.pem;
    ssl_certificate_key /etc/nginx/certs/team.example.com.key;

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;      # 审计导出是流式输出,大导出需要较长读超时
        proxy_buffering off;
    }
}
```

`proxy_buffering off` 让审计导出边生成边下载。

## 首次初始化

1. 启动团队版,浏览器打开 `http://<地址>:8090`(或你的 `public-url`)。
2. 未初始化时自动进入"初始化 CSAP Apidoc 团队版":填组织名称、管理员姓名、登录账号、密码(至少 8 位,最多 72 字节),点"完成初始化"。
3. 用刚创建的账号登录。

初始化只能做一次,且只在没有任何用户时可用。完成后再调用初始化接口会返回 `CONFLICT`。

没有控制台页面(jar 里没带前端)时,也可以用接口初始化:

```bash
curl -s -X POST http://localhost:8090/api/v1/setup \
  -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' \
  -d '{"orgName":"示例公司","username":"admin","displayName":"管理员","password":"<至少 8 位的密码>"}'
```

初始化之前,除 `/api/v1/setup/status`、`/api/v1/setup`、`/api/v1/auth/login` 外所有接口都返回 `SETUP_REQUIRED`(HTTP 412)。

初始化之后的下一步:

1. 建项目、加成员、给成员分配角色(见 [管理员手册](admin-guide.md));
2. 创建 `REGISTER` 令牌,把服务接进来(见 [服务接入](connect-services.md));
3. 需要超过 5 个活跃用户时,上传授权文件。

## 达梦、人大金仓

这两种库**未在真实库验证**,属于试用。

驱动不随团队版分发,需要从厂商获取与你数据库版本匹配的 JDBC 驱动,放进一个目录(例如 `./drivers`),用 `PropertiesLauncher` 启动并指定该目录:

```bash
java -Dloader.path=./drivers \
     -cp csap-apidoc-team-server-1.0.0.jar \
     org.springframework.boot.loader.launch.PropertiesLauncher
```

并配置:

```bash
export CSAP_TEAM_DB_URL='jdbc:dm://db.example.com:5236'          # 人大金仓为 jdbc:kingbase8://host:54321/库名
export CSAP_TEAM_DB_USER='<数据库账号>'
export CSAP_TEAM_DB_PASSWORD='<数据库密码>'
export CSAP_TEAM_DATABASE_DIALECT=dm                              # 人大金仓用 kingbase
```

Docker 里把驱动目录挂载到 `/app/drivers`,并改写启动命令使用 `PropertiesLauncher`:

```bash
docker run -d --name csap-team -p 8090:8090 \
  -v $(pwd)/drivers:/app/drivers:ro \
  -e CSAP_TEAM_DB_URL='jdbc:dm://db.example.com:5236' \
  -e CSAP_TEAM_DB_USER='<数据库账号>' -e CSAP_TEAM_DB_PASSWORD='<数据库密码>' \
  -e CSAP_TEAM_DATABASE_DIALECT=dm \
  --entrypoint java csap-apidoc-team:1.0.0 \
  -Dloader.path=/app/drivers -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

人大金仓如果初始化为 MySQL 兼容模式,方言设为 `mysql` 试用;Oracle 兼容模式不支持。

## 升级

1. **先备份数据库**(见下节)。
2. 停止旧进程,替换 jar(或换新镜像),启动。
3. 启动时只在缺表时建表,已有表不会被改动。1.0 的表版本为 `1`(存在 `csap_team_setting` 的 `schema.version`)。
4. 版本之间如有表结构变更,会在对应版本的发布说明里提供增量脚本和步骤。

升级团队版**不影响**服务:服务读的是开源三张表,团队版不会改动它们的结构。

## 备份与恢复

数据全部在数据库里,备份 = 备份那个库(用 `mysqldump`、`pg_dump` 或你们现有的备份方案)。

- 团队版自有表(`csap_team_*`)和开源规则表**在同一个库**,一起备份才能保证一致。
- 数据库里的 `csap_team_setting` 存放登录会话签名密钥和授权文本,备份文件要按敏感数据保管。
- 恢复到旧备份后:修改单、审计回到备份时刻;服务按版本号轮询,规则也回到备份时刻的版本。
- 团队版进程本身没有需要备份的文件(试用的 H2 文件库除外:`./data/csap-team*`)。

## 日志与排错

| 现象 | 原因与处理 |
|---|---|
| 启动失败:"团队版表不存在" | 关闭了 `create-tables` 又没建表。执行建表脚本或打开自动建表 |
| 启动失败:"无法从 JDBC URL 识别数据库方言" | 设置 `csap.team.database.dialect` |
| 启动失败:`rule-table-prefix` 无效 | 前缀只允许字母、数字、下划线 |
| 启动失败:`trusted-proxies` 配置无效 | 只接受 IP 或 CIDR,如 `10.0.0.0/8` |
| 服务的规则在团队版里看不到 | 团队版和服务连了不同的库,或表前缀不一致 |
| 登录后马上掉线 | 浏览器没带 Cookie:检查是否通过 HTTPS 访问、代理是否改写了 `Host` |
| 审计里的 IP 都是代理地址 | 没配置 `trusted-proxies`,或代理没传 `X-Forwarded-For` |
| 授权提示"无效" | 授权文件不是用对应的私钥签发的,见 [授权文件](admin-guide.md#授权文件) |

接口错误统一返回 HTTP 4xx/5xx 加 `{"code":"...","message":"..."}`,常见错误码:

| code | HTTP | 含义 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 未登录、会话过期、令牌无效 |
| `FORBIDDEN` | 403 | 没有权限,或缺少请求头 `X-CSAP-Team: 1` |
| `LICENSE_LIMIT` | 403 | 活跃用户已达授权上限 |
| `NOT_FOUND` | 404 | 不存在,或你无权查看 |
| `VALIDATION` | 400 | 提交的内容有误 |
| `CONFLICT` | 409 | 版本冲突或重名 |
| `SETUP_REQUIRED` | 412 | 系统尚未初始化 |
| `LOCKED` | 429 | 登录失败过多,账号暂时锁定 15 分钟 |
| `INTERNAL` | 500 | 服务器内部错误 |

日志输出到标准输出,级别 `INFO`;日志里不打印密码、令牌和 Webhook 密钥。
