# 修复返回参数字段名称不显示问题

## 问题描述

在返回参数展示中，字段名称没有显示出来。经过分析发现，问题是由于数据中包含 `$ref` 引用对象导致的。

### 问题数据示例

```json
{
  "name": "data",
  "dataType": "UserRegisterResponse",
  "children": {
    "parameters": [
      {
        "name": "registerTime",
        "dataType": "LocalDateTime",
        "description": "注册时间"
      },
      {
        "name": "message",
        "dataType": "String",
        "description": "消息"
      }
    ]
  },
  "parameters": [
    {"$ref": "$.data.apiList[0].methodList[0].response[0].parameters[1].children.parameters[0]"},
    {"$ref": "$.data.apiList[0].methodList[0].response[0].parameters[1].children.parameters[1]"}
  ]
}
```

**问题原因：** `parameters` 数组中包含的是 `$ref` 引用对象，这些对象只有 `$ref` 字段，没有 `name` 字段，导致 Table 无法显示字段名称。

## 解决方案

添加了 `filterRefParameters` 函数，递归过滤掉所有嵌套的 `$ref` 对象，只保留真实的字段数据。

### 核心修复代码

```typescript
// 递归过滤掉所有$ref对象
const filterRefParameters = (fields: any[]): any[] => {
  if (!fields || !Array.isArray(fields)) return []
  
  return fields
    .filter(field => !field.$ref) // 过滤掉$ref引用
    .map(field => {
      const cleaned = { ...field }
      // 递归处理嵌套的parameters
      if (cleaned.parameters && Array.isArray(cleaned.parameters)) {
        cleaned.parameters = filterRefParameters(cleaned.parameters)
      }
      return cleaned
    })
}
```

### 使用方式

在加载数据时，先调用 `filterRefParameters` 过滤 `$ref`，再调用 `cleanEmptyParameters` 清理空数组：

```typescript
// 先过滤掉$ref引用，再清理空parameters
const filteredData = filterRefParameters(firstParam.parameters || [])
const cleanedData = cleanEmptyParameters(filteredData)
setSelectedFields(cleanedData)
```

## 修改的文件

1. `ResponseParamModal.tsx` - 返回参数管理模态框
2. `ResponseParamFieldModal.tsx` - 返回参数字段管理模态框
3. `RequestParamModal.tsx` - 请求参数管理模态框
4. `RequestParamFieldModal.tsx` - 请求参数字段管理模态框

所有处理 `parameters` 数组的组件都已添加 `$ref` 过滤逻辑。

## 验证

修复后，返回参数和请求参数的字段名称都能正常显示，嵌套字段也能正确展开。

## 日期

2025-10-30

