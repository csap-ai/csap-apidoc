# 列宽拖动调整功能

## 功能说明

已为**请求参数管理**和**返回参数管理**模态框中的字段配置表格添加了列宽拖动调整功能。

## 使用方法

1. 打开"请求参数管理"或"返回参数管理"模态框
2. 在"已选字段配置"表格中，将鼠标移动到表头列的右边缘
3. 当光标变为调整大小图标（↔）时，按住鼠标左键并拖动
4. 释放鼠标即可完成列宽调整

## 可调整的列

### 请求参数表格
- 字段名称（默认宽度: 200px）
- 类型（默认宽度: 150px）
- 请求类型（默认宽度: 150px）
- 备注（默认宽度: 200px）
- 必传（默认宽度: 80px）
- 操作（默认宽度: 160px）

### 返回参数表格
- 字段名称（默认宽度: 200px）
- 类型（默认宽度: 150px）
- 备注（默认宽度: 200px）
- 必传（默认宽度: 80px）
- 操作（默认宽度: 100px）

## 实现细节

### 新增文件
- `src/components/table/ResizableTitle.tsx` - 可调整大小的表头组件
- `src/components/table/index.tsx` - 组件导出文件
- `src/components/table/resizable.scss` - 样式文件

### 修改文件
- `src/views/api/components/RequestParamModal.tsx` - 添加列宽调整功能
- `src/views/api/components/ResponseParamModal.tsx` - 添加列宽调整功能

### 依赖包
- `react-resizable` - 提供拖动调整功能
- `@types/react-resizable` - TypeScript 类型定义

## 技术实现

使用 `react-resizable` 库与 Ant Design Table 组件结合：

1. **ResizableTitle 组件**: 自定义表头单元格组件，包装 `<Resizable>` 组件
2. **列宽状态管理**: 使用 `useState` 管理每列的宽度
3. **handleResize 函数**: 处理列宽调整事件，更新状态
4. **列配置**: 为每列添加 `width` 和 `onHeaderCell` 属性
5. **Table 组件**: 通过 `components` 属性应用自定义表头组件

## 注意事项

- 列宽调整仅在当前会话中有效，刷新页面后会恢复默认宽度
- 如需持久化列宽设置，可以考虑将宽度信息保存到 localStorage
- 拖动手柄区域宽度为 10px，悬停时会有蓝色背景提示

## 未来改进

可以考虑以下改进：

1. 将列宽设置保存到 localStorage，实现持久化
2. 添加"重置列宽"功能按钮
3. 支持双击列边缘自动调整列宽以适应内容
4. 添加最小/最大列宽限制

