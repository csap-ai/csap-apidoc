# CSAP Apidoc 团队版 · 控制台(web)

React 18 + antd 5 + Vite 5 + TypeScript 5 + react-router 6。接口契约见 `../docs/design/team-1.0.md` 第 7 节。

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

## 结构

- `src/api/` 类型、请求层(Cookie、`X-CSAP-Team`、错误码分发)、各接口封装
- `src/auth/` 登录态与按角色的权限判断(`permissions.ts` 对应契约第 3 节)
- `src/i18n/` 全部界面文案(`zh-CN.ts`),以后加英文新增字典即可
- `src/pages/` 文档中心、规则、修改单(含冲突 / Rebase)、审计、管理
- `src/mock/` MSW 模拟后端(仅开发 / 测试)

截图见 `docs/screenshots/`。
