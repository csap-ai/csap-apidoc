# csap-apidoc-ui

> 面向 **前端开发 / QA** 的多服务 API 文档工作台。
> 当前分支:`feat/environment-auth-headers`(M1–M6 已落地,M8 测试 + 文档收尾中)。

---

## 1. 概览

`csap-apidoc-ui` 是一个独立的 React SPA,直接读取每个业务服务暴露的
`/csap/apidoc/*` 端点(由 `csap-apidoc-devtools` 在业务服务进程内挂载),
把 API 资产渲染成可浏览、可调试的文档。它没有自己的后端,所有偏好都落
在浏览器本地;与 `csap-apidoc-devtools`(后端注解管理工具)是 **平行的两
个独立产品**,共用 Maven 仓库但没有客户端–服务端关系。

完整的产品定位与数据流见
[`../docs/features/environment-auth-headers.md`](../docs/features/environment-auth-headers.md) §1–§3。

---

## 2. 核心能力

- **多环境管理**(`<EnvironmentSwitcher />`):顶栏环境下拉,按环境记录
  `baseUrl` 与 `{{var}}` 变量字典;切换后所有试运行请求自动指向新环境。
- **全局 / 服务 / 环境级请求头**(`<HeadersButton />`):三层作用域,
  后定义覆盖前定义,同名键大小写不敏感。
- **认证方案管理**(`<AuthButton />`):一等公民支持
  - `bearer` — Bearer Token
  - `basic` — HTTP Basic
  - `apikey` — API Key(可放 header / query / cookie)
  - `oauth2_client` — OAuth2 Client Credentials(带 token 缓存)
- **试运行 v2**(`<TryItOutPanel />`,API 详情页 `⚡ 试运行 v2` 标签):
  浏览器直发请求,显示真实状态码、耗时、响应头和响应体。
- **凭据加密保险箱**(`<SettingsButton />` → 设置抽屉):PBKDF2-SHA256
  (200k iter)派生密钥 + AES-GCM 256 加密敏感字段,主密码模式可选,
  默认明文。

---

## 3. 快速开始

包管理器:**pnpm**(也支持 npm)。Node ≥ 18。

```bash
pnpm install
pnpm dev          # 默认 http://localhost:5173
pnpm build        # tsc --noEmit + vite build,产物输出 dist/
pnpm preview      # 预览构建产物
```

> 当前 `package.json` 仅声明了 `dev` / `build` / `preview` 三个脚本。
> M8a 之后会追加 `test` 等脚本,届时更新本节。

### 环境变量

通过 Vite `import.meta.env` 读取,在 `.env` / `.env.development` /
`.env.production` 中配置:

| 变量 | 必填 | 用途 |
|---|---|---|
| `VITE_API_URL` | 是 | 默认指向的业务服务地址,如 `http://localhost:8080`。文档拉取 (`/csap/apidoc/*`) 与试运行均默认走这个值,可在运行时被「环境管理」里的 `baseUrl` 覆盖。 |

---

## 4. 使用指引

### 4.1 切换环境

顶栏的环境下拉(带颜色圆点)是当前激活环境。每个环境包含:

- `baseUrl` — 该环境业务服务地址
- `variables` — 字典,如 `{ tenantId: "42", token: "abc" }`

在 URL、请求头值、查询串、Body 中出现的 `{{tenantId}}`、`{{token}}`
等占位符,会在发送请求时按当前环境的 `variables` 解析。

点环境下拉里的「⚙ 管理环境」打开管理抽屉,可以新增 / 编辑 / 删除环境。

### 4.2 配置全局请求头

点顶栏 `Headers`(`<HeadersButton />`)打开三标签抽屉:

| 作用域 | 何时生效 |
|---|---|
| `global` | 所有请求一律附加 |
| `service` | 仅当 `serviceUrl` 与当前服务匹配时附加 |
| `environment` | 仅当当前环境 ID 与 `scopeRefId` 匹配时附加 |

合并顺序:`global` → `service` → `environment` → 接口文档自身声明的
header。**后者覆盖前者,同名键大小写不敏感**(`Authorization` 与
`authorization` 视为同一键)。

### 4.3 配置认证

点顶栏 `Auth`(`<AuthButton />`)打开方案管理抽屉。每个方案绑定到
某个 `serviceUrl`,可选地按环境 ID(`envBindings`)切换凭据。

| 类型 | 必填字段 |
|---|---|
| `bearer` | `tokenRef`(指向 vault 中的 token,如 `vault:tok_1`) |
| `basic` | `username` + `passwordRef` |
| `apikey` | `in`(`header`/`query`/`cookie`) + `name` + `valueRef` |
| `oauth2_client` | `tokenUrl` + `clientId` + `clientSecretRef` + `scope`,运行时把拿到的 access_token 缓存到 `cachedTokenRef` |

> 表单里写敏感字段(token / password / secret)时,UI 会自动把值塞进
> 保险箱,scheme 自身只持有一个 `vault:tok_xxxx` 引用。明文模式下
> vault 里就是原文;启用主密码后是 AES-GCM 密文。详细存储布局见
> [`src/stores/README-vault.md`](src/stores/README-vault.md)。

### 4.4 试运行

进入任意接口详情页 → 切到 `⚡ 试运行 v2` 标签:

- **请求构造区**:从 API 定义自动生成路径参数 / 查询参数 / 请求头 /
  Body 表单,并用 doc 中的 `example` 预填。
- **自动注入**:当前环境 `baseUrl`、合并后的全局请求头、绑定到本服务
  的认证方案会在发送时自动拼装,Send 按钮上方的「Active context」
  标签会显示 `Dev · 3 headers · Bearer` 这类小标。
- **Body 类型**:JSON / Text / Binary 三选一(JSON 自动序列化)。
- **响应区**:三个子标签 — `Body`(按 Content-Type 自动美化)、
  `Headers`(键值表)、`Raw`(原始 HTTP 响应)。状态码徽章、耗时、
  响应大小一并显示。
- **网络/CORS 失败**会落到响应区的红色提示框,而不是吞成 toast。
  CORS 报错见 [`../docs/cors.md`](../docs/cors.md)。

### 4.5 主密码与保险箱锁

点顶栏 `Settings`(`<SettingsButton />`)→「保险箱」段:

1. 切换「加密模式」→ 输入主密码 → UI 把当前 vault 中所有明文条目重
   新加密落盘。
2. 闲置自动锁:窗口 `mousemove` / `keydown` / `click` 事件超过
   `vaultLockTimeoutMin`(默认见 `settingsStore`,可在设置面板调整)
   分钟未触发 → 锁定 → 派生密钥从内存清除。
3. 锁定状态下,试运行的认证字段会取不到值,Send 按钮上方有
   `<VaultLockBanner />` 提示。
4. 忘记主密码无法恢复:设置面板「重置全部」是唯一出路(会清空所有
   localStorage 数据)。

完整安全模型见 [`src/stores/README-vault.md`](src/stores/README-vault.md) 的 *Threat model* 章节。

---

## 5. 数据存储与隐私

**所有偏好都只落在浏览器 `localStorage`**,命名空间 `csap-apidoc:*`,
没有任何服务端持久化、没有账号、没有 telemetry。设备之间不会自动同
步,但可以通过设置面板的「导出 / 导入」按下 JSON 备份文件手动迁移。

敏感凭据(token / password / API key value / OAuth2 secret)默认明文
存在 `csap-apidoc:vault`;启用主密码后迁移到 `csap-apidoc:vault.encrypted`
(AES-GCM 256 + PBKDF2-SHA256 200k iter)。

详细的存储布局、迁移语义、威胁模型见
[`src/stores/README-vault.md`](src/stores/README-vault.md)。

---

## 6. CORS 注意事项

试运行 v2 是浏览器直发 `fetch` 到目标业务服务,会受同源策略约束。绝
大多数「Send 后 status 0 / Failed to fetch」是 CORS 问题。完整排错指
南见 [`../docs/cors.md`](../docs/cors.md)(四种典型场景 + 快速 checklist
+ 生产部署推荐形态)。

---

## 7. 后续路线图

- **M7** — `csap-apidoc-devtools` 的 `@DocGlobalHeader` / `@DocAuth`
  注解 hint(后端开发可在代码里声明默认 header / auth 模板,UI 把它
  们渲染成「一键预设」)。已在设计文档 §9 D-6 标记为延后到下一个 release。
- **M8.1** — i18n 双语(zh-CN / en-US)。当前 UI 文案以中文为主。
- **M8.2** — Playwright E2E,覆盖试运行 happy path。
- **可选 IndexedDB 迁移** — 若用户数据接近 4MB localStorage 上限再做,
  接口已通过 `KeyValueStore` 抽象预留(详见设计文档 §5.6)。

---

## 8. 相关文档

- [`../docs/features/environment-auth-headers.md`](../docs/features/environment-auth-headers.md) — 完整设计文档(数据模型 / UX / 决策记录)
- [`src/stores/README-vault.md`](src/stores/README-vault.md) — Vault 存储布局与威胁模型
- [`../docs/cors.md`](../docs/cors.md) — 试运行 CORS 排错指南
