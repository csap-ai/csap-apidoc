# 请求参数类型区分功能

## 功能概述

实现了按照 `paramType` 字段区分和显示不同类型的请求参数，并在在线测试时正确处理各种参数类型。

## 支持的参数类型

| 参数类型 | 说明 | 标签颜色 | 在线测试处理方式 |
|---------|------|---------|----------------|
| PATH | 路径参数 | 蓝色 | 替换 URL 中的占位符 (如 `/user/{id}`) |
| QUERY | 查询参数 | 青色 | 作为 URL query string |
| BODY | Body 参数 | 绿色 | 作为 JSON body 发送 |
| FORM_DATA | 表单数据 | 橙色 | 作为 multipart/form-data 发送 |
| HEADER | 请求头参数 | 紫色 | 添加到请求 headers 中 |
| DEFAULT | 默认参数 | 灰色 | 当作 QUERY 参数处理 |

## 功能特性

### 1. 参数显示优化

#### 表格列添加
在请求参数表格中，新增了"参数类型"列，使用彩色标签显示每个参数的类型：

```typescript
{
  title: '参数类型',
  dataIndex: 'paramType',
  key: 'paramType',
  width: 100,
  render: (text: string) => (
    <Tag color={typeColorMap[text]}>
      {text === 'DEFAULT' ? 'QUERY' : text}
    </Tag>
  ),
}
```

#### 分组显示
参数按类型分组显示，每个类型有独立的表格和标题：

- **Path 参数** - 显示需要在 URL 路径中替换的参数
- **Query 参数** - 显示 URL 查询字符串参数
- **Body 参数** - 显示请求体中的 JSON 参数
- **Form Data 参数** - 显示表单数据参数
- **Header 参数** - 显示自定义请求头

### 2. 在线测试增强

#### 智能参数处理
根据不同的 `paramType`，自动构建正确的请求格式：

```typescript
// 示例：混合参数类型
{
  "id": 123,          // PATH 参数 -> /api/user/123
  "page": 1,          // QUERY 参数 -> ?page=1
  "name": "张三",     // BODY 参数 -> body JSON
  "token": "abc123"   // HEADER 参数 -> headers
}
```

#### PATH 参数自动替换
自动将 PATH 参数替换到 URL 中：

```
原始 URL: /api/user/{id}/posts/{postId}
参数: { id: 123, postId: 456 }
实际请求: /api/user/123/posts/456
```

#### 多种请求方法支持
- **GET**: Query 参数 + Path 参数 + Headers
- **POST**: Body/Form Data + Query 参数 + Path 参数 + Headers
- **PUT/PATCH**: 同 POST
- **DELETE**: Query 参数 + Path 参数 + Headers

#### Form Data 处理
当检测到 `FORM_DATA` 类型参数时，自动使用 `multipart/form-data` 格式：

```typescript
if (hasFormDataParams) {
  const formDataObj = new FormData();
  Object.keys(formData).forEach(key => {
    formDataObj.append(key, formData[key]);
  });
  requestConfig.headers['Content-Type'] = 'multipart/form-data';
}
```

### 3. 导出功能优化

#### Markdown 导出
导出的 Markdown 文档会按参数类型分组显示：

```markdown
#### 请求参数 - Path
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id     | int  | 是   | 用户ID |

#### 请求参数 - Query
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page   | int  | 否   | 页码 |
```

## 数据结构

### 参数对象结构
```typescript
interface Parameter {
  name: string;           // 参数名
  dataType: string;       // 数据类型
  required: boolean;      // 是否必填
  paramType?: string;     // 参数类型：PATH | QUERY | BODY | FORM_DATA | HEADER | DEFAULT
  value?: string;         // 说明
  defaultValue?: string;  // 默认值
  example?: string;       // 示例值
  children?: Parameter[]; // 子参数（嵌套对象）
}
```

### API 详情结构
```typescript
interface ApiDetail {
  title: string;
  path: string;
  method: string;
  request: Parameter[];   // 请求参数数组（每个参数都有 paramType 字段）
  response: Parameter[];
  headers: Array<{...}>;
}
```

## 使用示例

### 示例 1: 简单 GET 请求
```json
// API: GET /api/user/{id}
{
  "id": "123"  // PATH 参数
}
// 实际请求: GET /api/user/123
```

### 示例 2: 混合参数 POST 请求
```json
// API: POST /api/article/{id}/comment
{
  "id": "456",           // PATH 参数
  "page": 1,             // QUERY 参数
  "content": "评论内容", // BODY 参数
  "token": "abc123"      // HEADER 参数
}
// 实际请求: POST /api/article/456/comment?page=1
// Headers: { token: "abc123" }
// Body: { "content": "评论内容" }
```

### 示例 3: Form Data 上传
```json
// API: POST /api/upload
{
  "file": "...",         // FORM_DATA 参数
  "description": "说明"  // FORM_DATA 参数
}
// 实际请求: POST /api/upload
// Content-Type: multipart/form-data
```

## 实现细节

### 核心工具函数

#### 1. 参数分组
```typescript
// utils/paramTypeUtils.ts
groupParametersByType(params: Parameter[]): GroupedParams
```

#### 2. 请求数据构建
```typescript
buildRequestData(params: Parameter[], jsonData: any): {
  pathParams: Record<string, any>;
  queryParams: Record<string, any>;
  bodyData: any;
  formData: Record<string, any>;
  headers: Record<string, any>;
}
```

#### 3. Path 参数替换
```typescript
replacePathParams(url: string, pathParams: Record<string, any>): string
```

### 修改的文件

1. **src/utils/paramTypeUtils.ts** (新增)
   - 参数类型工具函数
   - 分组、构建请求数据等核心逻辑

2. **src/layouts/columns.tsx**
   - 添加 `paramType` 字段到 `BodyRecord` 接口
   - 添加"参数类型"列到表格

3. **src/layouts/index.tsx**
   - `Doc()` 组件：按类型分组显示参数
   - `handleRequest()` 函数：根据参数类型构建请求

4. **src/utils/exportMarkdown.ts**
   - 更新 Markdown 导出逻辑，按类型分组

## 向后兼容

- 如果参数没有 `paramType` 字段，会自动归类为 `DEFAULT` (作为 QUERY 处理)
- 旧的 API 数据结构仍然可以正常工作
- 不影响已有的导出功能

## 测试建议

### 1. 测试不同参数类型
- 创建包含多种 paramType 的 API
- 验证显示是否正确分组
- 验证在线测试是否按预期工作

### 2. 测试 PATH 参数替换
```
URL: /api/user/{userId}/post/{postId}
参数: { userId: 123, postId: 456 }
预期: /api/user/123/post/456
```

### 3. 测试混合参数
- 同时包含 PATH、QUERY、BODY 参数
- 验证请求是否正确构建

### 4. 测试 Form Data
- 上传文件场景
- 验证 Content-Type 是否正确

## 注意事项

1. **PATH 参数命名**：URL 中的占位符需要与参数名匹配
   - 支持 `{paramName}` 格式
   - 支持 `:paramName` 格式

2. **BODY 参数优先级**：如果同时存在 BODY 和 QUERY 参数
   - POST/PUT/PATCH: BODY 作为请求体，QUERY 作为查询字符串
   - GET/DELETE: 只使用 QUERY 参数

3. **Header 参数**：会添加到所有请求的 headers 中
   - 可能会与系统默认 headers 冲突
   - 建议使用特定的前缀（如 `X-Custom-`）

4. **Form Data 限制**：
   - 文件上传需要前端特殊处理
   - 当前只支持简单的键值对

## 未来改进

可以考虑的增强功能：

1. **参数验证**：根据 paramType 进行客户端验证
2. **智能填充**：根据历史记录自动填充参数
3. **参数模板**：保存常用的参数组合
4. **批量测试**：支持多组参数批量测试
5. **文件上传**：完整的文件上传支持

## 相关文档

- [EXPORT-FEATURE.md](./EXPORT-FEATURE.md) - 导出功能说明
- [API Documentation](./API%20Documentation%20#reference.postman_collection.json) - API 参考

