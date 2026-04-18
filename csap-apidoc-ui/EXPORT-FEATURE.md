# API 文档导出功能说明

## 功能概述

CSAP API Documentation 支持将 API 文档导出为以下三种标准格式：

1. **OpenAPI 3.0** - 业界标准的 API 规范格式
2. **Postman Collection 2.1** - 可直接导入 Postman 进行测试
3. **Markdown** - 适合文档分享和版本控制

## 使用方法

### 1. 在界面中导出

1. 在页面右上角找到 **"导出文档"** 按钮
2. 点击按钮打开下拉菜单
3. 选择需要的导出格式：
   - OpenAPI 3.0
   - Postman Collection
   - Markdown
4. 文件将自动下载到本地

### 2. 导出格式说明

#### OpenAPI 3.0

- **文件格式**: JSON
- **文件名**: `openapi.json`
- **用途**: 
  - 导入到 Swagger UI 查看
  - 导入到 API 管理平台（如 Apifox、Postman、Insomnia）
  - 用于自动生成 SDK 和文档
  - 支持 CI/CD 集成

**特性**:
- 完整的 API 路径和方法定义
- 请求参数（Query、Header、Body）
- 响应数据结构
- 数据类型映射
- 嵌套对象支持

#### Postman Collection 2.1

- **文件格式**: JSON
- **文件名**: `postman_collection.json`
- **用途**:
  - 直接导入 Postman 进行 API 测试
  - 团队协作共享 API 集合
  - 自动化测试脚本

**特性**:
- 分组文件夹结构
- 预填充的请求参数示例
- Headers 配置
- 示例响应数据
- 环境变量支持（baseUrl）

**导入 Postman 步骤**:
1. 打开 Postman
2. 点击左上角 "Import" 按钮
3. 选择下载的 `postman_collection.json` 文件
4. 导入成功后即可在集合中看到所有 API

#### Markdown

- **文件格式**: Markdown
- **文件名**: `api-documentation.md`
- **用途**:
  - 快速预览和分享
  - 版本控制（Git）
  - 生成静态文档网站
  - 项目文档集成

**特性**:
- 清晰的层级结构
- 完整的参数表格
- JSON 示例代码块
- 枚举值说明
- 验证规则说明
- Markdown 标准格式

## 技术实现

### 核心文件

```
src/utils/
├── exportOpenApi.ts      # OpenAPI 3.0 导出
├── exportPostman.ts      # Postman Collection 导出
├── exportMarkdown.ts     # Markdown 导出
└── exportUtils.ts        # 统一导出入口
```

### 数据类型映射

| Java 类型 | OpenAPI 类型 | Postman 默认值 |
|----------|-------------|---------------|
| String | string | "" |
| Integer | integer (int32) | 0 |
| Long | integer (int64) | 0 |
| Double | number (double) | 0.0 |
| Float | number (float) | 0.0 |
| Boolean | boolean | false |
| Date | string (date) | ISO 8601 |
| DateTime | string (date-time) | ISO 8601 |
| Object | object | {} |
| List/Array | array | [] |

### API 结构支持

- ✅ 嵌套对象
- ✅ 数组类型
- ✅ 枚举值
- ✅ 验证规则
- ✅ 必填/可选标识
- ✅ 默认值和示例值
- ✅ Headers 配置
- ✅ Query/Body 参数

## 开发者指南

### 自定义导出

```typescript
import { exportApi, downloadFile } from '@/utils/exportUtils';

// 导出 API
const content = await exportApi(
  'openapi', // 或 'postman', 'markdown'
  treeData,
  getApiDetail,
  {
    baseUrl: 'https://api.example.com',
    title: 'My API',
    collectionName: 'My API Collection'
  }
);

// 下载文件
downloadFile('openapi', content, 'my-api.json');
```

### 扩展新格式

1. 在 `src/utils/` 下创建新的导出文件，如 `exportSwagger.ts`
2. 实现导出和下载函数
3. 在 `exportUtils.ts` 中添加新格式支持
4. 在 Header 组件中添加菜单项

## 注意事项

1. **大量 API 导出**: 如果 API 数量较多（>100 个），导出过程可能需要几秒钟时间，请耐心等待
2. **网络请求**: 导出过程需要获取每个 API 的详细信息，确保网络连接正常
3. **数据完整性**: 确保 API 文档数据完整，包括必要的字段如 title、path、method
4. **浏览器兼容**: 下载功能支持现代浏览器（Chrome、Firefox、Edge、Safari）

## 示例文件

项目中包含了参考示例：

- `API Documentation #reference.postman_collection.json` - Postman Collection 标准格式参考

## 常见问题

### Q: 导出的 OpenAPI 文件可以在哪里使用？

A: 可以导入到以下工具：
- Swagger UI / Swagger Editor
- Apifox
- Postman
- Insomnia
- Stoplight
- 任何支持 OpenAPI 3.0 的工具

### Q: Postman Collection 中的 baseUrl 在哪里配置？

A: 导入 Postman 后，在 Collection 的 Variables 标签中可以看到 `baseUrl` 变量，可以根据环境修改。

### Q: Markdown 文档可以直接放到 GitHub 吗？

A: 可以。导出的 Markdown 文档完全符合 GitHub 语法规范，可以直接提交到仓库中。

### Q: 如何处理中文字符？

A: 所有导出格式都正确处理了中文字符编码，确保导出文件中文显示正常。

## 更新日志

### v1.0.0 (2024-10)
- ✨ 新增 OpenAPI 3.0 导出功能
- ✨ 新增 Postman Collection 2.1 导出功能
- ✨ 新增 Markdown 导出功能
- 🎨 优化导出按钮 UI 设计
- 📝 完善文档说明

## 反馈与支持

如有问题或建议，请联系开发团队或提交 Issue。

