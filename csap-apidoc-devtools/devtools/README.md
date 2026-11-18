# CSAP API Devtools 前端

`csap-apidoc-devtools` 模块的前端源码(React 18 + TypeScript + Vite),构建产物 `csap-api-devtools.html` 与静态资源会打进后端模块,由应用的 `/devtools-ui` 路径提供。使用方式见上级目录的 [EXAMPLE-APPLICATION.md](../EXAMPLE-APPLICATION.md)。

## 技术栈

- 构建: Vite 5
- 框架: React 18、TypeScript 5.3
- UI: Ant Design 5
- 路由: React Router v6(`basename` 为 `/devtools-ui`)
- 状态管理: Zustand
- HTTP: Axios
- 样式: SCSS

## 快速开始

环境要求: Node.js >= 16、npm >= 7(仓库 `.nvmrc` 指定 22.14.0)。

```bash
npm install
npm run dev          # 开发服务器 http://localhost:9528
```

构建:

```bash
npm run build:prod   # 生产构建(会把 index.html 重命名为 csap-api-devtools.html)
npm run build:test   # test 模式
npm run build:stage  # staging 模式
npm run preview      # 预览构建结果
npm run lint
```

在 `csap-apidoc-devtools` 目录下执行 `./build.sh` 会构建前端并把产物放进 `src/main/resources/static`,供后端打包。

## 开发时连接后端

开发环境的 `VITE_API_BASE_URL` 为 `/api`(见 `.env.development`),`vite.config.ts` 把 `/api/csap`、`/api/devtools`、`/api/actuator` 代理到 `http://localhost:8182` 并去掉 `/api` 前缀。请先启动一个已启用 Devtools(`csap.apidoc.devtool.enabled: true`)的应用,端口为 8182,或修改 `vite.config.ts` 中的 `target`。

## 目录结构

```
src/
├── main.tsx          # 入口
├── App.tsx           # 根组件
├── router/           # 路由
├── store/            # Zustand 状态(用户、侧边栏、设置)
├── layout/           # 布局
├── components/       # 通用组件
├── views/            # 页面(login、api 等)
├── api/              # 后端接口封装
├── utils/            # request、auth 等工具
├── styles/
└── permission.ts     # 路由守卫
```

## 说明

- 登录页是前端模拟登录(`src/store/index.ts`),不会调用后端校验,登录页提示的 `admin / 111111` 仅为占位
- `mock/` 与 `tests/`、`test-browser/` 为开发与测试辅助文件

## License

MIT
