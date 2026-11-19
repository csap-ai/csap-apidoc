# CSAP Apidoc 团队版

CSAP Apidoc 团队版,免费开源(Apache-2.0),企业自部署。面向多服务、多成员的团队:文档中心、成员与角色、修改单(草稿、冲突检测、审批)、审计追溯、变更通知。

团队版是开源仓库 [csap-apidoc](../README.md) 的一部分(`csap-apidoc-team/` 目录),同样以 Apache-2.0 许可发布,无用户数限制、无授权文件。下文命令都在 `csap-apidoc-team/` 目录下执行;它不在仓库根 pom 的模块里(需要 JDK 17),单独构建。

## 文档

面向客户的文档在 [docs/](docs/README.md):

| 文档 | 读者 |
|---|---|
| [docs/README.md](docs/README.md) 产品简介与目录 | 所有人 |
| [docs/install.md](docs/install.md) 部署与运维 | 部署运维 |
| [docs/connect-services.md](docs/connect-services.md) 服务接入 | 管理员、服务开发者 |
| [docs/user-guide.md](docs/user-guide.md) 使用手册 | 开发、维护者、只读成员 |
| [docs/admin-guide.md](docs/admin-guide.md) 管理员手册 | 管理员、项目维护者 |
| [docs/security.md](docs/security.md) 安全说明 | 运维、安全评审 |

内部资料:

- [设计与接口契约](docs/design/team-1.0.md)
- [后端冒烟演示(curl)](docs/dev/backend-smoke.md)
- [后端实现说明与契约解读](docs/dev/backend-notes.md)
- [控制台前端说明](web/README.md)

## 目录

| 目录 | 说明 |
|---|---|
| `server/` | 后端(Spring Boot 3.3,Java 17,包 `ai.csap.apidoc.team`),单个可执行 jar,内含控制台前端 |
| `web/` | 控制台前端(React + antd) |
| `docker/` | Dockerfile 与 MySQL / PostgreSQL 的 docker-compose 示例 |
| `docs/` | 客户文档、设计、开发说明 |

## 构建与运行(开发)

需要 JDK 17 和 Maven 3.9。

依赖同仓库的 `csap-apidoc-database`(版本与仓库根 pom 一致)。该版本已在 Maven Central 时直接构建即可;从未发布的源码构建时,先在仓库根目录执行 `mvn -B install -DskipTests -pl csap-apidoc-strategy/csap-apidoc-database -am`。

```bash
mvn -B verify                       # 全部测试(含 Testcontainers:需要本机 Docker)
mvn -B -pl server -am package -DskipTests
java -jar server/target/csap-apidoc-team-server-1.0.0-SNAPSHOT.jar
```

- `web/dist` 存在时会被打进 jar 的 `static/`;不存在时后端构建照常通过(只是没有控制台页面)。前端先执行 `cd web && npm ci && npm run build`。
- 默认端口 `8090`,默认使用本地文件 H2 库(`./data/csap-team`,仅供本地体验 / 测试)。首次访问完成初始化。
- 生产部署、数据库、全部配置项、反向代理、Docker:见 [docs/install.md](docs/install.md)。

## 许可

Apache-2.0。没有授权文件、没有用户数上限、没有试用期。
