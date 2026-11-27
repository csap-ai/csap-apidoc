# 团队版更新日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。开源版(Java / Go)的更新日志见仓库根目录的 `CHANGELOG.md`。

## [1.1.0] - 未发布

依赖的开源 `csap-apidoc-database` 仍为 1.5.0,服务注册协议不变,**服务侧不需要任何改动**。不配置环境时行为与 1.0 相同。升级步骤见 [docs/upgrade-1.1.md](docs/upgrade-1.1.md)。

### 新增

- **多环境**:`csap.team.environments` 定义开发 / 测试 / 生产等环境,每个环境独立的数据源和规则表前缀,支持 `protected`(一律要审批)和 `read-only`(只读)。不配置时自动有一个 `default` 环境。新增 `GET /api/v1/envs`。见 [docs/environments.md](docs/environments.md)。
- **按环境注册与管理**:`REGISTER` 令牌可绑定环境;服务、实例、文档缓存、规则、历史、MCP 审计、修改单、审计、Webhook 订阅都按环境。所有相关接口增加 `env` 参数,省略为默认环境。
- **环境差异对比与一键推广**:`GET /api/v1/envs/diff`、`/envs/diff/item`、`POST /api/v1/promotions`,生成类型为 `PROMOTE` 的修改单,走原有的审批、冲突、回滚流程。
- **修改单评论**:`GET/POST /api/v1/changes/{id}/comments`、`PATCH/DELETE /api/v1/comments/{id}`,可关联到某条规则;软删除;审计 `comment.create / update / delete`;Webhook 事件 `change.commented`。
- **编辑中提示**:`PUT/DELETE/GET /api/v1/presence`(进程内存)显示谁正在编辑某条规则;`GET /rules/{service}/item` 增加 `openChanges`,`GET /changes/{id}` 每项增加 `overlaps`,提示同一条规则上其它未完成的修改单。
- **审计哈希链**:每条审计带 `prev_hash` / `hash`(SHA-256 链),`GET /api/v1/audit/verify` 与控制台"校验哈希链"按钮,保留期清理后用 anchor 接上,Webhook 事件 `audit.checkpoint` 用于外部锚定,独立校验脚本 [docs/tools/verify-audit-chain.py](docs/tools/verify-audit-chain.py)。见 [docs/audit-chain.md](docs/audit-chain.md)。
- 所有 Webhook 事件的通用请求体增加 `env`;通知正文带环境名。
- 新增错误码 `ENV_UNAVAILABLE`(HTTP 503)。
- 新增配置 `csap.team.environments`、`csap.team.presence.ttl-seconds`、`csap.team.audit.checkpoint-cron`。
- 新增 Docker 示例 `docker/docker-compose.multi-env.yml`(两个 PostgreSQL 库、两个环境)。

### 变更

- 数据库表结构版本 `1` → `2`。启动时自动升级(`create-tables=false` 时需手工执行 `upgrade/2/{方言}.sql`)。新增表 `csap_team_service_env`、`csap_team_env_instance`、`csap_team_comment`,新增列 `env_key`(修改单、审计、令牌)、`source_env`、`env_keys`。
- 升级时已有审计被逐条封存进哈希链,并追加一条 `audit.chain.sealed`。**升级前的记录只能证明升级之后没被改过。**
- `csap_team_service_instance` 的数据在升级时丢弃(实例在一个心跳周期内重新注册),表保留。`csap_team_project_service` 的地址列保留但不再读写。

### 不兼容

- **审计 JSON 导出格式由数组改为对象** `{"anchor":...,"items":[...]}`,每项增加 `envKey`、`prevHash`、`hash`。解析导出文件的脚本需要改读 `items`。CSV 导出增加同样三列。这是 1.1 唯一的不兼容变化。

## [1.0.0] - 2026-11-19

首个版本,免费开源(Apache-2.0)。文档中心、成员与角色、修改单与审批、审计、Webhook 通知。
