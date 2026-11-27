# CSAP Apidoc 团队版 · 控制台(web)

React 18 + antd 5 + Vite 5 + TypeScript 5 + react-router 6。接口契约见 `../docs/design/team-1.0.md` 第 7 节与 `../docs/design/team-1.1.md`(多环境与推广、评论与编辑中提示、审计哈希链)。

```bash
npm ci
npm run dev        # 开发,/api 代理到 http://localhost:8090
npm run dev:mock   # 脱离后端演示(MSW 模拟全部接口,首次进入走初始化向导)
npm run build      # 类型检查 + 构建到 web/dist
npm run lint
npm run typecheck
npm test           # Vitest 组件测试
```

## Mock 模式

- 只在 `--mode mock` 时加载 `src/mock/`,生产构建不包含 MSW 与夹具(`publicDir` 也只在 mock 模式带 service worker 文件)。
- 数据存在 `sessionStorage`,刷新不丢,关闭标签页即重置。
- 演示账号(初始化向导完成后自动生成):管理员为你在向导里填的账号;`zhangsan`(订单维护者)、`lisi`(订单开发 / 用户中心维护者)、`wangwu`(订单只读),密码均为 `demo1234`。
- 制造冲突:浏览器控制台执行
  `__mock.editOutside('order-service', 'com.demo.order.OrderController.create', '{"amount":{"min":5}}')`,模拟服务侧 DevTools 直接改规则;之后提交基于旧版本的修改单即得到 409。

## 1.1 的界面与 mock 场景

- **环境**:后端 `GET /envs` 返回 2 个以上环境时,顶栏出现环境切换器(受保护环境红色标注,DOWN 显示"不可用"),当前环境存 URL 的 `env` 参数并记在 localStorage(读写都包了 try/catch)。规则、文档中心、修改单列表、审计、服务、MCP 审计都按当前环境取数;只配置一个环境(或连的是 1.0 后端、没有 `/envs`)时不显示任何环境控件,请求也不带 `env` 参数,界面与 1.0 一致。
- **环境对比**(`/envs`):选来源 / 目标环境与项目 → 差异列表 → 勾选 → 推广弹窗(标题、原因、包含删除、创建后立即提交)。
- **修改单详情**:目标环境 / 推广来源、评论区、每条规则的 `overlaps` 提示。**规则编辑**:打开后每 30 秒 `PUT /presence`,显示"某某 正在编辑这条规则"和"有 N 张未完成的修改单涉及这条规则"。
- **审计**:环境筛选与列;管理员"校验哈希链"。**令牌**:REGISTER 令牌可绑定环境。**Webhook**:可订阅环境,事件新增 `change.commented`、`audit.checkpoint`。
- mock 默认场景 `multi`:开发 / 测试 / 生产(生产受保护),带一张待审批的修改单、两条评论、一个"正在编辑"的李四,方便看全部新页面。要看"只有一个环境"的 1.0 外观:先在地址里加 `?scenario=single` 打开(记在 sessionStorage,必须在走初始化向导之前选定),或在测试里 `loginAs(user, 'single')`。
- 控制台辅助:`__mock.setEnvStatus('test', 'DOWN')` 让环境不可用(`'UP'` 恢复);`__mock.tamperAudit(id)` 改一条审计记录,之后"校验哈希链"会报 `HASH_MISMATCH`(mock 的哈希是简化指纹,真实算法见契约 §6)。

## 结构

- `src/api/` 类型、请求层(Cookie、`X-CSAP-Team`、错误码分发)、各接口封装
- `src/auth/` 登录态与按角色的权限判断(`permissions.ts` 对应契约第 3 节)
- `src/i18n/` 全部界面文案(`zh-CN.ts`),以后加英文新增字典即可
- `src/env/` 环境上下文(URL `env` 参数 + localStorage)
- `src/pages/` 文档中心、规则、修改单(含冲突 / Rebase / 评论)、环境对比与推广、审计、管理
- `src/mock/` MSW 模拟后端(仅开发 / 测试)

截图见 `docs/screenshots/`。
