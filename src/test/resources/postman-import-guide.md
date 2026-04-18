# Postman集合导入说明

## 文件说明

- `dept-api-postman-collection.json` - 原始JSON格式，适合程序处理和Postman导入
- `dept-api-postman-collection-formatted.json` - 格式化JSON，适合人工阅读

## 导入步骤

1. 打开Postman应用
2. 点击左上角的 "Import" 按钮
3. 选择 "File" 选项卡
4. 点击 "Upload Files" 并选择 `dept-api-postman-collection.json` 文件
5. 点击 "Import" 完成导入

## 使用说明

- 导入后会在Postman中创建 "Api Documentation" 集合
- 所有API按照 "default" 分组组织
- 包含完整的部门管理CRUD操作
- 环境变量 `{{baseUrl}}` 默认设置为 `http://localhost:8080`
- 可根据实际服务地址修改环境变量

## API列表

1. 根据id查询 (GET)
2. 分页查询 (GET)
3. 分页查询返回总条数 (GET)
4. 根据ID删除 (DELETE)
5. 根据ID数组删除 (DELETE)
6. 新增 (POST)
7. 保存或者修改 (POST)
8. 批量保存或者修改 (POST)
9. 根据主键修改 (PUT)

## 注意事项

- 请根据实际API服务地址配置环境变量
- POST/PUT请求的请求体可能需要根据实际模型调整
- 查询参数的值需要根据实际需求填写
