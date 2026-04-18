# CSAP API Devtools

> 基于 React 18 + TypeScript + Vite 的现代化 API 管理工具

## ✨ 特性

- ⚡️ **极速开发** - Vite 提供闪电般的热更新
- 🎯 **类型安全** - 完整的 TypeScript 类型支持
- 🎨 **现代UI** - Ant Design 5.x 组件库
- 📦 **轻量状态管理** - Zustand 简洁高效
- 🔐 **权限控制** - 完整的路由守卫机制
- 📱 **响应式设计** - 自动适配移动端

## 🚀 快速开始

### 环境要求

- Node.js >= 16.0.0
- npm >= 7.0.0

### 安装依赖

```bash
npm install
```

### 开发环境

```bash
# 启动开发服务器
npm run dev

# 浏览器访问
http://localhost:9528
```

### 生产构建

```bash
# 构建生产环境
npm run build

# 构建其他环境
npm run build:test    # 测试环境
npm run build:stage   # 预发布环境

# 预览构建结果
npm run preview
```

## 📁 项目结构

```
src/
├── main.tsx          # 应用入口
├── App.tsx           # 根组件
├── router/           # 路由配置
├── store/            # 状态管理
├── layout/           # 布局组件
├── components/       # 通用组件
├── views/            # 页面组件
├── utils/            # 工具函数
├── styles/           # 样式文件
└── permission.ts     # 权限控制
```

## 🔧 配置

### API 代理

修改 `vite.config.ts`：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8182',
      changeOrigin: true,
    },
  },
}
```

### 路由配置

在 `src/router/index.tsx` 添加路由：

```typescript
{
  path: '/your-page',
  element: <YourPage />,
  meta: { 
    title: '页面标题', 
    icon: 'icon-name' 
  }
}
```

## 📝 技术栈

- **构建工具**: Vite 5.x
- **框架**: React 18.2
- **语言**: TypeScript 5.3
- **UI库**: Ant Design 5.x
- **路由**: React Router v6
- **状态管理**: Zustand
- **HTTP**: Axios
- **样式**: SCSS

## 🎯 核心功能

### 状态管理

```typescript
import { useUserStore } from '@/store'

const { token, login, logout } = useUserStore()
```

### API 请求

```typescript
import request from '@/utils/request'

const data = await request({
  url: '/api/xxx',
  method: 'get'
})
```

### 路由守卫

权限控制自动生效，在 `App.tsx` 中已集成。

## 📖 文档

- [完整迁移文档](./TYPESCRIPT-MIGRATION.md) - TypeScript 迁移详解
- [React 文档](https://react.dev/)
- [TypeScript 文档](https://www.typescriptlang.org/)
- [Vite 文档](https://vitejs.dev/)
- [Ant Design](https://ant.design/)

## 🔐 默认账号

- 用户名：`admin`
- 密码：`111111`

## 📄 License

MIT

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

Made with ❤️ by CSAP Team
