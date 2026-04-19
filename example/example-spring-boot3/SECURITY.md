# 示例 Demo Security（JWT）

> 仅适用于 `example-spring-boot3`。`example-spring-boot2` 不受影响。

本示例可选启用一层基于 JWT 的 Spring Security，用来演示 **`csap-apidoc-ui`
里配置 Bearer / API Key / OAuth2 凭证 → Try it out 真实带上请求头 → 业务接口
返回 200** 的端到端闭环。

## 默认行为：关闭

不修改任何配置时，所有接口公开（与本次改造之前完全一致）：

```
GET  /api/users           → 200
POST /api/users {...}     → 200
```

## 启用方式

修改 `src/main/resources/application.yml`：

```yaml
csap:
  example:
    security:
      enabled: true
      # 可选：自定义签名密钥（>= 32 字节，建议 base64）
      # jwt-secret: "$(openssl rand -base64 32)"
      jwt-expiration-minutes: 60
      issuer: "csap-apidoc-example"
      # 可选：自定义用户列表；不配则启用内置 admin/admin123 与 user/user123
      # users:
      #   - { username: alice, password: alice-pw, roles: [ADMIN] }
      #   - { username: bob,   password: bob-pw,   roles: [USER]  }
```

也可以通过环境变量临时切换：

```bash
CSAP_EXAMPLE_SECURITY_ENABLED=true mvn spring-boot:run
```

## 端到端 demo 流程

### 1. 启动应用

```bash
cd example-spring-boot3
CSAP_EXAMPLE_SECURITY_ENABLED=true mvn spring-boot:run
```

启动日志会出现：

```
[csap-example] no users configured under csap.example.security.users;
installed built-in demo pair admin/admin123 and user/user123
```

### 2. 不带 token：401

```bash
curl -i http://localhost:8083/api/users
# HTTP/1.1 401
# {"code":"UNAUTHORIZED","message":"Bearer token is missing or invalid. POST /auth/login to obtain one.","status":401}
```

### 3. 登录换 token

```bash
curl -s -X POST http://localhost:8083/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123"}' | jq
```

```jsonc
{
  "code": 200,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOi...",          // ← 复制这个
    "expiresAt": "2026-04-19T03:21:00Z",
    "username": "admin",
    "roles": ["ADMIN"],
    "issuer": "csap-apidoc-example"
  }
}
```

### 4. 带 token：200

```bash
TOKEN="eyJhbGciOi..."
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8083/api/users | jq
# 200 OK
```

### 5. 在 csap-apidoc-ui 里配置（M3 完成后可用）

1. 顶栏 → 环境管理 → 新建 `Local-Demo`，Base URL `http://localhost:8083`
2. 顶栏 → 认证 → 新建 Bearer Scheme，token 粘贴第 3 步获得的 `accessToken`
3. 顶栏 → 切换到 `Local-Demo` 环境
4. 打开 `/api/users` 接口的 **Try it out** → Send → 200

## 公开端点（即便启用 JWT 也不需要 token）

```
/csap/apidoc/**          # 文档元数据（csap-apidoc-boot 暴露给 csap-apidoc-ui）
/api-doc, /api-doc/**    # OpenAPI/Swagger 数据接口
/csap-api.html           # 嵌入式文档查看器 HTML
/csap-api-devtools.html  # devtools 控制台 HTML
/devtools-ui/**          # 后端开发者用的 devtools 控制台前端
/api/devtools/**         # devtools 后端 API
/auth/**                 # 登录端点本身
/actuator/health
/error, /static/**, /assets/**, /favicon.ico
```

凡未列入上表的接口（`/api/users`、`/api/products`、`/actuator/*` 等）都需要 JWT。

## CORS

启用时会注册一份宽松 CORS（`allowedOriginPatterns: *`、`allowCredentials: true`），
方便 `csap-apidoc-ui` 在不同端口（如 5173）跨域访问。生产环境请收紧到具体域名。

## 安全声明

- 内置默认密钥与默认账号 **仅用于本地 demo**。
- 不要将本配置直接拿去生产；生产请：
  - 替换 `jwt-secret`（使用 KMS / Vault 等托管方案）
  - 替换为接入真正身份源（LDAP / OIDC / 自有用户库）
  - 收紧 CORS 与 `PUBLIC_PATHS` 列表
