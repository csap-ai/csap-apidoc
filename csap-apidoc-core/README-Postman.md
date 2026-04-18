# Postman转换器服务

## 概述

`PostmanConverterService` 是一个用于将CSAP Framework API文档转换为Postman集合格式的服务。它可以将现有的API文档模型转换为符合Postman规范的JSON格式，方便开发者在Postman中导入和使用。

## 功能特性

- **API文档转换**: 将CSAP Framework的API文档转换为Postman集合
- **标签分组**: 按API标签自动分组，创建层次化的Postman集合结构
- **请求信息完整**: 包含HTTP方法、URL、请求头、查询参数等完整信息
- **变量支持**: 自动生成基础URL等环境变量
- **响应示例**: 支持响应示例的生成
- **多控制器支持**: 支持多个控制器的API文档转换

## 核心组件

### PostmanConverterService

主要的转换服务类，提供以下方法：

- `convertToPostmanCollection(CsapDocParentResponse)`: 将API文档转换为Postman集合JSON字符串

### Postman模型类

- `PostmanCollection`: Postman集合主模型
- `PostmanCollectionInfo`: 集合信息模型
- `PostmanItem`: 集合项目模型（支持嵌套）
- `PostmanRequest`: 请求模型
- `PostmanUrl`: URL模型
- `PostmanHeader`: 请求头模型
- `PostmanBody`: 请求体模型
- `PostmanResponse`: 响应模型
- `PostmanVariable`: 变量模型

## 使用方法

### 1. 基本使用

```java
@Autowired
private PostmanConverterService postmanConverterService;

public String exportToPostman(CsapDocParentResponse apiDocResponse) {
    return postmanConverterService.convertToPostmanCollection(apiDocResponse);
}
```

### 2. 转换结果示例

转换后的Postman集合JSON结构：

```json
{
  "info": {
    "name": "User Management API",
    "description": "用户管理相关接口",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "user",
      "item": [
        {
          "name": "getUser",
          "request": {
            "method": "GET",
            "url": {
              "raw": "/api/users/{id}",
              "query": [
                {
                  "key": "id",
                  "value": "",
                  "description": ""
                }
              ]
            },
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json",
                "description": "请求内容类型"
              }
            ]
          }
        }
      ]
    }
  ],
  "variable": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8080",
      "description": "API基础URL"
    }
  ]
}
```

## 转换规则

### 1. 标签分组

- API方法按标签自动分组
- 每个标签创建一个Postman文件夹
- 支持多层嵌套结构

### 2. URL构建

- 控制器路径 + 方法路径
- 自动处理路径分隔符
- 支持查询参数

### 3. 请求头

- 从`CsapDocMethodHeaders`转换
- 包含键、值、描述信息

### 4. 查询参数

- 从`paramNames`自动生成
- 支持路径参数和查询参数

### 5. HTTP方法

- 从`methods`列表获取
- 默认使用GET方法

## 测试验证

项目包含完整的测试用例，验证以下功能：

- 基本转换功能
- 空响应处理
- 复杂API结构
- 请求头转换
- 查询参数处理
- 不同HTTP方法
- 标签分组功能

### 运行测试

```bash
# 运行所有测试
mvn test -Dtest=PostmanConverterServiceTest

# 运行简单测试程序
mvn exec:java -Dexec.mainClass="ai.csap.apidoc.service.PostmanConverterTest"
```

## 扩展性

### 1. 自定义转换逻辑

可以通过继承`PostmanConverterService`来扩展转换逻辑：

```java
@Service
public class CustomPostmanConverterService extends PostmanConverterService {
    
    @Override
    protected PostmanBody buildRequestBody(List<CsapDocModel> requestModels) {
        // 自定义请求体构建逻辑
        return super.buildRequestBody(requestModels);
    }
}
```

### 2. 添加新的Postman特性

可以在Postman模型类中添加新的字段，并在转换逻辑中处理：

```java
public static class PostmanRequest {
    private String method;
    private List<PostmanHeader> header;
    private PostmanBody body;
    private PostmanUrl url;
    private String description; // 新增字段
    
    // getters and setters
}
```

## 注意事项

1. **依赖管理**: 服务使用简单的字符串拼接进行JSON转换，避免Jackson依赖问题
2. **性能考虑**: 对于大型API文档，建议异步处理转换过程
3. **错误处理**: 转换失败时会抛出RuntimeException，需要适当处理
4. **内存使用**: 大型文档转换时注意内存使用情况

## 未来改进

1. **Jackson集成**: 集成Jackson库提供更强大的JSON处理能力
2. **模板支持**: 支持自定义Postman集合模板
3. **批量导出**: 支持批量导出多个API文档
4. **格式验证**: 添加Postman集合格式验证
5. **增量更新**: 支持增量更新Postman集合

## 贡献

欢迎提交Issue和Pull Request来改进这个服务。

## 许可证

遵循项目的整体许可证。

