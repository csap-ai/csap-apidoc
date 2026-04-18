# 🎯 404 问题解决方案

## ❌ 问题描述

访问 `http://localhost:8085/apidoc-devtools` 显示 404 页面，但点击"返回首页"按钮后能正常显示。

---

## 🔍 问题分析

### 你看到的不是 Spring Boot 的 404！

这是 **React 应用内部的 404 页面**（`/views/404.tsx`）

### 问题流程：

```
1. 访问 http://localhost:8085/apidoc-devtools
2. ✅ Spring Controller 成功转发 HTML
3. ✅ React 应用成功加载
4. ❌ React Router 检查当前路径：/apidoc-devtools
5. ❌ 路由配置中没有 /apidoc-devtools
6. 🔀 匹配到通配符路由：* → 重定向到 /404
7. 😭 显示 404 页面

点击"返回首页"：
8. ✅ 路由跳转到 / 或 /api
9. ✅ 匹配成功，显示正常页面
```

---

## 🎯 根本原因

### React Router 配置不匹配

**原始路由配置**（`router/index.tsx`）：
```typescript
const routes: RouteObject[] = [
  { path: '/login', ... },
  { path: '/', ... },
  { path: '*', element: <Navigate to="/404" /> }  // ⚠️ 其他路径都跳404
]
```

**原始 BrowserRouter**（`main.tsx`）：
```typescript
<BrowserRouter>  // ⚠️ 没有 basename
  <App />
</BrowserRouter>
```

**问题**：
- React Router 默认 basename 是 `/`
- 但 Spring Controller 映射的是 `/apidoc-devtools`
- React Router 认为完整路径是 `/apidoc-devtools/`
- 路由表中没有这个路径 → 404

---

## ✅ 解决方案

### 方案：统一路径 + basename

#### 1. 修改 Controller（后端）

**文件**：`DevtoolsViewController.java`

```java
@Controller
public class DevtoolsViewController {
    
    // 使用 ** 通配符匹配所有子路径
    @GetMapping(value = {"/devtools-ui", "/devtools-ui/**"})
    public String devtoolsPage() {
        return "forward:/csap-api-devtools.html";
    }
}
```

**说明**：
- `/devtools-ui` → 主路径
- `/devtools-ui/**` → 所有子路径（如 `/devtools-ui/api`, `/devtools-ui/login`）
- 都转发到同一个 HTML，让 React Router 处理

---

#### 2. 修改 React Router（前端）

**文件**：`src/main.tsx`

```typescript
<BrowserRouter basename="/devtools-ui">  {/* ✅ 设置 basename */}
  <App />
</BrowserRouter>
```

**说明**：
- 告诉 React Router：所有路由都基于 `/devtools-ui`
- React Router 内部的 `/` 实际对应 `/devtools-ui/`
- React Router 内部的 `/api` 实际对应 `/devtools-ui/api`

---

## 📊 修复后的路由流程

```
访问: http://localhost:8085/devtools-ui
    ↓
Spring Controller 匹配: /devtools-ui
    ↓
返回 HTML (csap-api-devtools.html)
    ↓
React 应用加载，basename="/devtools-ui"
    ↓
React Router 解析: 当前路径 = /devtools-ui (basename)
                内部路径 = / 
    ↓
匹配路由: / → 重定向到 /api
    ↓
最终路径: /devtools-ui/api
    ↓
✅ 显示 API 管理页面
```

---

## 🎯 完整的路径映射

| 浏览器地址 | Spring 处理 | React Router 内部路径 | 显示内容 |
|-----------|------------|---------------------|---------|
| `/devtools-ui` | ✅ 返回 HTML | `/` → `/api` | API 管理 |
| `/devtools-ui/api` | ✅ 返回 HTML | `/api` | API 管理 |
| `/devtools-ui/login` | ✅ 返回 HTML | `/login` | 登录页面 |
| `/devtools-ui/xxx` | ✅ 返回 HTML | `/xxx` → `/404` | 404 页面 |

---

## 🚀 使用方法

### 访问地址
```
http://localhost:8085/devtools-ui
```

### 自动跳转
1. 访问 `/devtools-ui`
2. React Router 自动重定向到 `/devtools-ui/api`
3. 显示 API 管理界面

---

## 💡 为什么不用其他方案？

### ❌ 方案1：映射到根路径 `/`
```java
@GetMapping("/")
```
**问题**：
- 会和用户主应用的根路径冲突
- Devtools 是一个库，不应该占用根路径

---

### ❌ 方案2：使用 HashRouter
```typescript
<HashRouter>  // 路径变成 /#/api
```
**问题**：
- URL 不美观：`/devtools-ui/#/api`
- SEO 不友好
- 现代应用不推荐

---

### ✅ 当前方案：basename + 通配符
```java
@GetMapping({"/devtools-ui", "/devtools-ui/**"})
```
```typescript
<BrowserRouter basename="/devtools-ui">
```

**优点**：
- ✅ 路径清晰：`/devtools-ui/api`
- ✅ 不冲突：不占用根路径
- ✅ RESTful：符合现代 Web 规范
- ✅ 易维护：前后端路径统一

---

## 🔧 验证方法

### 1. 重新构建
```bash
cd csap-framework-apidoc-devtools
./build.sh
```

### 2. 启动应用
```bash
java -jar your-app.jar
```

### 3. 测试访问
```bash
# 应该成功，自动跳转到 /devtools-ui/api
curl -I http://localhost:8085/devtools-ui

# 应该成功，显示 API 管理页面
curl -I http://localhost:8085/devtools-ui/api

# 应该成功，显示登录页面
curl -I http://localhost:8085/devtools-ui/login

# 应该成功，显示 404 页面
curl -I http://localhost:8085/devtools-ui/not-exist
```

---

## 📝 修改的文件清单

1. ✅ `DevtoolsViewController.java` - 修改路径映射
2. ✅ `src/main.tsx` - 添加 basename
3. ✅ `build.sh` - 更新访问地址提示

---

## 🎓 学到的知识点

### 1. React Router 的 basename
- 用于部署到子路径
- 让所有路由都基于特定前缀

### 2. Spring MVC 通配符
- `/**` 匹配所有子路径
- 让 SPA 应用的所有路由都返回同一个 HTML

### 3. SPA 部署的最佳实践
- 后端配置通配符路由
- 前端配置 basename
- 保持前后端路径一致

---

## ✅ 问题已解决！

现在访问 `http://localhost:8085/devtools-ui` 应该能正常工作了！

---

**最后更新**: 2025-10-20  
**状态**: ✅ 已解决  
**访问地址**: `http://localhost:8085/devtools-ui` ⭐

