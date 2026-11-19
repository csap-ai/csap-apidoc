# CSAP Apidoc 团队版

CSAP Apidoc 的商业版本,企业自部署。面向多服务、多成员的团队:文档中心、成员与角色、修改单(草稿、冲突检测、审批)、审计追溯、变更通知。

开源核心见 csap-apidoc(Apache-2.0)。本仓库为私有,版权所有,未经授权不得分发。

设计与接口契约:[docs/design/team-1.0.md](docs/design/team-1.0.md)

## 目录

| 目录 | 说明 |
|---|---|
| `server/` | 后端(Spring Boot 3.3,Java 17,包 `ai.csap.apidoc.team`),单个可执行 jar,内含控制台前端 |
| `license-tool/` | 授权文件签发命令行(`keygen` / `issue` / `verify`),不依赖 server |
| `web/` | 控制台前端(React + antd) |
| `docker/` | Dockerfile 与 MySQL / PostgreSQL 的 docker-compose 示例 |
| `docs/` | 设计、开发、部署文档 |

## 构建与运行

需要 JDK 17 和 Maven 3.9。

```bash
mvn -B verify                       # 全部测试(含 Testcontainers:需要本机 Docker)
mvn -B -pl server -am package -DskipTests
java -jar server/target/csap-apidoc-team-server-1.0.0-SNAPSHOT.jar
```

- `web/dist` 存在时会被打进 jar 的 `static/`;不存在时后端构建照常通过(只是没有控制台页面)。前端先执行 `npm ci && npm run build`。
- 默认端口 `8090`;默认使用本地文件 H2 库(`./data/csap-team`,仅试用)。首次访问完成初始化(创建管理员)。
- 数据库:`CSAP_TEAM_DB_URL` / `CSAP_TEAM_DB_USER` / `CSAP_TEAM_DB_PASSWORD`,必须是**服务共用的那个库**,
  `CSAP_TEAM_RULE_TABLE_PREFIX` 与服务侧 `csap.apidoc.database.table-prefix` 一致(默认 `csap_apidoc_`)。
  MySQL、PostgreSQL 的驱动随包;达梦、人大金仓的驱动放进一个目录,用 PropertiesLauncher 加载:
  `java -Dloader.path=./drivers -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher`,同时设置
  `csap.team.database.dialect`(`dm` / `kingbase`)。这两种库未在真实环境验证。
- H2 的 JDBC URL 需要带 `NON_KEYWORDS=VALUE`(`csap_team_setting.value` 在 H2 2.x 里是保留字),默认 URL 已带。
- 完整配置项见契约第 10 节;另有 `csap.team.heartbeat-seconds`(注册心跳,默认 30)、`csap.team.session-hours`(默认 12)、
  `csap.team.license-public-key-file`(自行编译、替换发行公钥时用)。
- 建表脚本:`server/src/main/resources/META-INF/csap-team/schema/{mysql,postgresql,dm,kingbase,h2}.sql`
  (由 `server/schema-template.sql` 经 `server/generate-schema.sh` 生成,DBA 也可手工执行)。

## 授权文件

```bash
java -jar license-tool/target/csap-apidoc-team-license-tool-1.0.0-SNAPSHOT.jar keygen --out ./keys
java -jar license-tool/target/csap-apidoc-team-license-tool-1.0.0-SNAPSHOT.jar issue --key ./keys/private.pem \
    --licensee "某某公司" --max-members 50 --expires 2027-12-31 --out team.lic
java -jar license-tool/target/csap-apidoc-team-license-tool-1.0.0-SNAPSHOT.jar verify --pub ./keys/public.pem team.lic
```

仓库里的 `server/src/main/resources/META-INF/csap-team/license-public.pem` 是**占位公钥**(对应私钥已销毁),
正式发行前用 `keygen` 生成的公钥替换它。**私钥不得入库、不得写进日志。**

## 文档

- [后端冒烟演示(curl)](docs/dev/backend-smoke.md)
- [后端实现说明与契约解读](docs/dev/backend-notes.md)
