# 后端冒烟演示(curl)

用 H2 文件库启动团队版 jar,走一遍:初始化 → 登录 → 建项目 → 手工添加服务 → 建用户并加入项目 → 建修改单 → 提交 → 审批 → 发布,
再演示服务注册开放接口。本文由 `mvn -B verify` 通过后实际执行生成(端口 18090 是因为本机 8090 被占用,默认端口是 8090)。

启动:

```bash
mvn -B -pl server -am package -DskipTests
cd /tmp/team-smoke && java -jar <仓库>/server/target/csap-apidoc-team-server-1.0.0-SNAPSHOT.jar --server.port=18090
```

默认用 `./data/csap-team` 文件 H2 库(本地体验);接着的所有请求里,非 GET 都带 `X-CSAP-Team: 1`,会话 Cookie 用 `-c` / `-b` 保存。
(下面的 JSON 为便于阅读经 `jq` 格式化。)

### 1. 初始化状态

```bash
curl -s http://localhost:18090/api/v1/setup/status
```

结果:

```json
{
  "initialized": false
}
```

### 2. 初始化(创建组织与管理员)

```bash
curl -s -c admin.jar -X POST http://localhost:18090/api/v1/setup -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"orgName":"演示组织","username":"admin","password":"Admin#12345","displayName":"管理员"}'
```

结果:

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "管理员",
  "orgRole": "ADMIN",
  "projects": []
}
```

### 3. 登录(初始化时已下发会话;这里演示显式登录)

```bash
curl -s -c admin.jar -X POST http://localhost:18090/api/v1/auth/login -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"username":"admin","password":"Admin#12345"}'
```

结果:

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "管理员",
  "orgRole": "ADMIN",
  "projects": []
}
```

### 4. 缺少 X-CSAP-Team 头的写请求被拒绝

```bash
curl -s -w '\nHTTP %{http_code}' -b admin.jar -X POST http://localhost:18090/api/v1/projects -H 'Content-Type: application/json' -d '{"name":"x"}'
```

结果:

```json
{"code":"FORBIDDEN","message":"缺少请求头 X-CSAP-Team: 1"}
HTTP 403
```

### 5. 建项目(需要审批)

```bash
curl -s -b admin.jar -X POST http://localhost:18090/api/v1/projects -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"name":"订单","description":"订单域服务","requireApproval":true}'
```

结果:

```json
{
  "id": 1,
  "name": "订单",
  "description": "订单域服务",
  "requireApproval": true,
  "createdAt": 1795062401891,
  "updatedAt": 1795062401891,
  "serviceCount": 0,
  "memberCount": 0,
  "myRole": "MAINTAINER"
}
```

### 6. 手工添加服务(服务地址不可达也能添加,状态为 OFFLINE)

```bash
curl -s -b admin.jar -X POST http://localhost:18090/api/v1/services -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"service":"order-service","baseUrl":"http://127.0.0.1:9","projectId":1}'
```

结果:

```json
{
  "service": "order-service",
  "projectId": 1,
  "projectName": "订单",
  "baseUrl": "http://127.0.0.1:9",
  "docPath": "/csap/apidoc",
  "language": null,
  "frameworkVersion": null,
  "source": "MANUAL",
  "status": "OFFLINE",
  "instances": 0,
  "lastSeenAt": null,
  "lastFetchedAt": null
}
```

### 7. 建一个普通用户

```bash
curl -s -b admin.jar -X POST http://localhost:18090/api/v1/users -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"username":"zhangsan","displayName":"张三","password":"Passw0rd!!","orgRole":"MEMBER"}'
```

结果:

```json
{
  "id": 2,
  "username": "zhangsan",
  "displayName": "张三",
  "status": "ACTIVE",
  "orgRole": "MEMBER",
  "createdAt": 1795062402031,
  "updatedAt": 1795062402031
}
```

### 8. 把张三加入项目(开发者)

```bash
curl -s -b admin.jar -X PUT http://localhost:18090/api/v1/projects/1/members/2 -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"role":"DEVELOPER"}'
```

结果:

```json
{
  "userId": 2,
  "role": "DEVELOPER"
}
```

### 9. 张三登录并查看自己的项目

```bash
curl -s -c dev.jar -X POST http://localhost:18090/api/v1/auth/login -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"username":"zhangsan","password":"Passw0rd!!"}'
```

结果:

```json
{
  "id": 2,
  "username": "zhangsan",
  "displayName": "张三",
  "orgRole": "MEMBER",
  "projects": [
    {
      "id": 1,
      "name": "订单",
      "role": "DEVELOPER"
    }
  ]
}
```

### 10. 张三建修改单(草稿;baseVersion=0 表示规则尚不存在)

```bash
curl -s -b dev.jar -X POST http://localhost:18090/api/v1/changes -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"projectId":1,"title":"补充客户姓名说明","reason":"文档缺失","items":[{"service":"order-service","ruleKey":"OrderController.create","baseVersion":0,"afterRules":"{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}"}]}'
```

结果:

```json
{
  "id": 1,
  "projectId": 1,
  "projectName": "订单",
  "title": "补充客户姓名说明",
  "reason": "文档缺失",
  "kind": "EDIT",
  "status": "DRAFT",
  "author": {
    "id": 2,
    "displayName": "张三"
  },
  "createdAt": 1795062402403,
  "updatedAt": 1795062402403,
  "submittedAt": null,
  "decidedBy": null,
  "decidedAt": null,
  "decisionNote": null,
  "publishedAt": null,
  "itemCount": 1,
  "items": [
    {
      "id": 1,
      "service": "order-service",
      "ruleKey": "OrderController.create",
      "baseVersion": 0,
      "beforeRules": null,
      "afterRules": "{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}",
      "publishedVersion": null,
      "publishedHistoryId": null,
      "currentVersion": 0,
      "conflict": false,
      "diff": "--- before\n+++ after\n@@ -0,0 +1,8 @@\n+{\n+  \"request\" : {\n+    \"customerName\" : {\n+      \"description\" : \"客户姓名\"\n+    }\n+  },\n+  \"response\" : { }\n+}\n"
    }
  ]
}
```

### 11. 张三提交(项目需要审批 → PENDING)

```bash
curl -s -b dev.jar -X POST http://localhost:18090/api/v1/changes/1/submit -H 'X-CSAP-Team: 1'
```

结果:

```json
{
  "id": 1,
  "projectId": 1,
  "projectName": "订单",
  "title": "补充客户姓名说明",
  "reason": "文档缺失",
  "kind": "EDIT",
  "status": "PENDING",
  "author": {
    "id": 2,
    "displayName": "张三"
  },
  "createdAt": 1795062402403,
  "updatedAt": 1795062402488,
  "submittedAt": 1795062402489,
  "decidedBy": null,
  "decidedAt": null,
  "decisionNote": null,
  "publishedAt": null,
  "itemCount": 1,
  "items": [
    {
      "id": 1,
      "service": "order-service",
      "ruleKey": "OrderController.create",
      "baseVersion": 0,
      "beforeRules": null,
      "afterRules": "{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}",
      "publishedVersion": null,
      "publishedHistoryId": null,
      "currentVersion": 0,
      "conflict": false,
      "diff": "--- before\n+++ after\n@@ -0,0 +1,8 @@\n+{\n+  \"request\" : {\n+    \"customerName\" : {\n+      \"description\" : \"客户姓名\"\n+    }\n+  },\n+  \"response\" : { }\n+}\n"
    }
  ]
}
```

### 12. 开发者不能审批自己的修改单

```bash
curl -s -w '\nHTTP %{http_code}' -b dev.jar -X POST http://localhost:18090/api/v1/changes/1/approve -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"note":"自批"}'
```

结果:

```json
{"code":"FORBIDDEN","message":"只有维护者或管理员可以审批"}
HTTP 403
```

### 13. 管理员审批并发布(同一事务内写 csap_apidoc_rule / history / meta)

```bash
curl -s -b admin.jar -X POST http://localhost:18090/api/v1/changes/1/approve -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"note":"同意"}'
```

结果:

```json
{
  "id": 1,
  "projectId": 1,
  "projectName": "订单",
  "title": "补充客户姓名说明",
  "reason": "文档缺失",
  "kind": "EDIT",
  "status": "PUBLISHED",
  "author": {
    "id": 2,
    "displayName": "张三"
  },
  "createdAt": 1795062402403,
  "updatedAt": 1795062402594,
  "submittedAt": 1795062402489,
  "decidedBy": {
    "id": 1,
    "displayName": "管理员"
  },
  "decidedAt": 1795062402601,
  "decisionNote": "同意",
  "publishedAt": 1795062402601,
  "itemCount": 1,
  "items": [
    {
      "id": 1,
      "service": "order-service",
      "ruleKey": "OrderController.create",
      "baseVersion": 0,
      "beforeRules": null,
      "afterRules": "{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}",
      "publishedVersion": 1,
      "publishedHistoryId": 1,
      "currentVersion": 1,
      "conflict": false,
      "diff": "--- before\n+++ after\n@@ -0,0 +1,8 @@\n+{\n+  \"request\" : {\n+    \"customerName\" : {\n+      \"description\" : \"客户姓名\"\n+    }\n+  },\n+  \"response\" : { }\n+}\n"
    }
  ]
}
```

### 14. 读取规则(版本号 = 开源 meta.version,服务轮询它即可生效)

```bash
curl -s -b dev.jar 'http://localhost:18090/api/v1/rules/order-service/item?key=OrderController.create'
```

结果:

```json
{
  "ruleKey": "OrderController.create",
  "rules": "{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}",
  "version": 1
}
```

### 15. 规则历史(changeId 指向这张修改单)

```bash
curl -s -b dev.jar 'http://localhost:18090/api/v1/rules/order-service/history?key=OrderController.create'
```

结果:

```json
[
  {
    "id": 1,
    "ruleKey": "OrderController.create",
    "version": 1,
    "updatedBy": "admin",
    "updatedAt": 1795062402600,
    "beforeRules": null,
    "afterRules": "{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}",
    "changeId": 1,
    "diff": "--- before\n+++ after\n@@ -0,0 +1,8 @@\n+{\n+  \"request\" : {\n+    \"customerName\" : {\n+      \"description\" : \"客户姓名\"\n+    }\n+  },\n+  \"response\" : { }\n+}\n"
  }
]
```

### 16. 审计:发布记录(含差异与开源 history id)

```bash
curl -s -b admin.jar 'http://localhost:18090/api/v1/audit?action=change.publish&size=5'
```

结果:

```json
{
  "items": [
    {
      "id": 10,
      "at": 1795062402601,
      "actorType": "USER",
      "actorId": 1,
      "actorName": "admin",
      "source": "CONSOLE",
      "ip": "0:0:0:0:0:0:0:1",
      "action": "change.publish",
      "projectId": 1,
      "service": "order-service",
      "ruleKey": "OrderController.create",
      "changeId": 1,
      "beforeValue": null,
      "afterValue": "{\"request\":{\"customerName\":{\"description\":\"客户姓名\"}},\"response\":{}}",
      "diff": "--- before\n+++ after\n@@ -0,0 +1,8 @@\n+{\n+  \"request\" : {\n+    \"customerName\" : {\n+      \"description\" : \"客户姓名\"\n+    }\n+  },\n+  \"response\" : { }\n+}\n",
      "reason": "补充客户姓名说明",
      "ossHistoryId": 1
    }
  ],
  "total": 1
}
```

### 17. 发起回滚(按指定历史记录的结果新建 ROLLBACK 修改单,状态 DRAFT,之后同样提交、审批)

```bash
HID=$(curl -s -b dev.jar 'http://localhost:18090/api/v1/rules/order-service/history?key=OrderController.create' | jq -r '.[0].id'); curl -s -b dev.jar -X POST http://localhost:18090/api/v1/rules/order-service/rollback -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d "{\"key\":\"OrderController.create\",\"historyId\":$HID,\"reason\":\"演示回滚\"}" | jq '{id,kind,status,title}'
```

结果:

```json
{
  "id": 2,
  "kind": "ROLLBACK",
  "status": "DRAFT",
  "title": "回滚 OrderController.create 到历史 #1"
}
```

### 18. 管理员创建 REGISTER 令牌(明文只返回一次)

```bash
curl -s -b admin.jar -X POST http://localhost:18090/api/v1/tokens -H 'Content-Type: application/json' -H 'X-CSAP-Team: 1' -d '{"name":"演示注册令牌","scope":"REGISTER","projectId":1}'
```

结果:

```json
{
  "id": 1,
  "name": "演示注册令牌",
  "prefix": "csapt_82",
  "scope": "REGISTER",
  "projectId": 1,
  "createdBy": 1,
  "createdAt": 1795062402918,
  "lastUsedAt": null,
  "revoked": false,
  "token": "csapt_82PEZLa9S3laCn2Y7KXKFoeVF9fMJBABgBxK76lP"
}
```

### 19. 服务用令牌注册(开源 team-integration 协议;返回心跳周期)

```bash
curl -s -X POST http://localhost:18090/api/open/v1/services/register -H 'Authorization: Bearer csapt_82PEZLa9S3laCn2Y7KXKFoeVF9fMJBABgBxK76lP' -H 'Content-Type: application/json' -d '{"service":"payment-service","instanceId":"10.0.0.5:8080","baseUrl":"http://10.0.0.5:8080","docPath":"/csap/apidoc","language":"go","frameworkVersion":"0.3.0"}'
```

结果:

```json
{
  "heartbeatSeconds": 30
}
```

### 20. 注册上来的服务出现在服务列表(来源 REGISTERED)

```bash
curl -s -b admin.jar 'http://localhost:18090/api/v1/services?projectId=1' | jq '[.[] | {service,source,language,frameworkVersion,status,instances}]'
```

结果:

```json
[
  {
    "service": "order-service",
    "source": "MANUAL",
    "language": null,
    "frameworkVersion": null,
    "status": "OFFLINE",
    "instances": 0
  },
  {
    "service": "payment-service",
    "source": "REGISTERED",
    "language": "go",
    "frameworkVersion": "0.3.0",
    "status": "ONLINE",
    "instances": 1
  }
]
```

