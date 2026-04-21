# API Gateway Aggregation Guide

## 网关聚合多服务API文档指南

在微服务架构中，通常会有多个服务各自提供API文档。通过 CSAP Framework API Doc 的网关聚合功能，你可以在一个统一的界面中查看和切换所有服务的API文档。

## 📋 目录

- [使用场景](#使用场景)
- [工作原理](#工作原理)
- [快速开始](#快速开始)
- [配置详解](#配置详解)
- [前端界面](#前端界面)
- [常见问题](#常见问题)
- [最佳实践](#最佳实践)

## 使用场景

### 适用场景

1. **微服务架构** - 多个微服务需要统一的文档入口
2. **API网关** - Spring Cloud Gateway、Kong、APISIX、Nginx 等
3. **多租户系统** - 不同租户的API需要分开展示
4. **多环境管理** - 开发、测试、生产环境的文档聚合

### 典型架构

```
┌─────────────────────────────────────────────────────────┐
│           API Gateway (网关层)                           │
│                                                          │
│  配置 resources:                                         │
│    - 总后台API: /example-admin-api/csap/apidoc/parent   │
│    - 门店API:   /example-store-api/csap/apidoc/parent   │
│    - 其他API:   /example-other-api/csap/apidoc/parent   │
│                                                          │
└─────────────────────────────────────────────────────────┘
         │                │                │
         ▼                ▼                ▼
    ┌────────┐      ┌────────┐      ┌────────┐
    │ Admin  │      │ Store  │      │ Other  │
    │Service │      │Service │      │Service │
    └────────┘      └────────┘      └────────┘
```

## 工作原理

### 1. 各微服务配置

每个微服务独立配置 CSAP API Doc，暴露自己的文档接口：

```java
// Admin Service
@SpringBootApplication
@EnableApidoc("com.example.admin")
public class AdminServiceApplication {
    // 暴露接口: /csap/apidoc/parent
}

// Store Service
@SpringBootApplication
@EnableApidoc("com.example.store")
public class StoreServiceApplication {
    // 暴露接口: /csap/apidoc/parent
}
```

### 2. 网关层聚合

网关项目配置所有服务的文档地址：

```yaml
csap:
  apidoc:
    resources:
      - name: 总后台API
        url: /example-admin-api/csap/apidoc/parent
      - name: 门店API
        url: /example-store-api/csap/apidoc/parent
```

### 3. 前端切换

用户在界面上通过下拉框选择不同的服务，动态加载对应的API文档。

## 快速开始

### Step 1: 微服务配置

在每个微服务中添加依赖和配置：

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
@SpringBootApplication
@EnableApidoc("com.yourcompany.yourservice")
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

### Step 2: 网关项目配置

在网关项目的 `application.yml` 中配置：

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

csap:
  apidoc:
    resources:
      - name: 总后台API
        url: /example-admin-api/csap/apidoc/parent
        version: v1.0
      - name: 门店API
        url: /example-store-api/csap/apidoc/parent
        version: v1.0
      - name: 其他API
        url: /example-other-api/csap/apidoc/parent
        version: v1.0
    
    devtool:
      enabled: true
```

### Step 3: 配置路由（Spring Cloud Gateway示例）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: example-admin-api
          uri: lb://example-admin-service
          predicates:
            - Path=/example-admin-api/**
        
        - id: example-store-api
          uri: lb://example-store-service
          predicates:
            - Path=/example-store-api/**
        
        - id: example-other-api
          uri: lb://example-other-service
          predicates:
            - Path=/example-other-api/**
```

### Step 4: 访问文档

启动网关后，访问：`http://localhost:8080/csap-api.html`

在界面顶部的下拉框中选择不同的服务，即可查看对应的API文档。

## 配置详解

### Resources 配置项

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `name` | String | 是 | 服务显示名称 | 总后台API |
| `url` | String | 是 | 文档接口地址 | /example-admin-api/csap/apidoc/parent |
| `version` | String | 否 | API版本号 | v1.0 |

### 完整配置示例

```yaml
csap:
  apidoc:
    # 多服务聚合配置
    resources:
      - name: 总后台API
        url: /example-admin-api/csap/apidoc/parent
        version: v1.0
      - name: 门店API
        url: /example-store-api/csap/apidoc/parent
        version: v1.0
      - name: 其他API
        url: /example-other-api/csap/apidoc/parent
        version: v1.0
    
    # 基本信息
    api-info:
      title: "网关聚合API文档"
      description: "所有微服务的API文档聚合"
      version: "1.0.0"
    
    # 开发工具
    devtool:
      enabled: true
      cache: true
```

## 前端界面

### 服务选择下拉框

在文档界面的顶部，会显示一个"选择服务"的下拉框，包含配置的所有服务：

```
┌─────────────────────────────────────────┐
│  选择服务: [总后台API ▼]  分组  版本    │
│           - 总后台API                   │
│           - 门店API                     │
│           - 其他API                     │
└─────────────────────────────────────────┘
```

### 动态加载

当用户选择不同的服务时：
1. 前端向对应的 URL 发送请求
2. 加载该服务的完整API文档
3. 更新左侧的API列表树
4. 保留当前的视图状态

## 常见问题

### Q1: 服务列表为空怎么办？

**检查项：**
1. 确认 `resources` 配置正确
2. 检查网关路由是否配置正确
3. 验证各微服务是否正常运行
4. 查看浏览器控制台是否有跨域错误

### Q2: 切换服务没有反应？

**解决方案：**
1. 检查服务的文档接口是否可访问
2. 验证 URL 路径是否正确（通过网关的路径）
3. 查看网络请求是否成功返回

### Q3: 如何在不同环境使用不同配置？

使用 Spring Profile：

```yaml
# application-dev.yml
csap:
  apidoc:
    resources:
      - name: 总后台API
        url: http://localhost:8081/csap/apidoc/parent

# application-prod.yml
csap:
  apidoc:
    resources:
      - name: 总后台API
        url: /mq-admin-api/csap/apidoc/parent
```

### Q4: 支持外部服务的文档吗？

支持！可以配置任何可访问的文档地址：

```yaml
csap:
  apidoc:
    resources:
      - name: 内部API
        url: /example-internal-api/csap/apidoc/parent
      - name: 外部API
        url: https://external-service.com/csap/apidoc/parent
```

## 最佳实践

### 1. 命名规范

使用清晰的服务命名：

```yaml
resources:
  - name: 用户中心API     # 清晰明了
  - name: 订单服务API
  - name: 支付网关API
```

### 2. 版本管理

为不同版本的API使用不同的配置：

```yaml
resources:
  - name: 用户API v1
    url: /user-api/v1/csap/apidoc/parent
    version: v1.0
  - name: 用户API v2
    url: /user-api/v2/csap/apidoc/parent
    version: v2.0
```

### 3. 环境隔离

使用环境变量：

```yaml
resources:
  - name: ${SERVICE_NAME:总后台API}
    url: ${ADMIN_API_URL:/example-admin-api/csap/apidoc/parent}
    version: ${API_VERSION:v1.0}
```

### 4. 监控和日志

记录服务切换日志，便于追踪：

```yaml
logging:
  level:
    com.csap.framework: DEBUG
```

### 5. 生产环境安全

在生产环境关闭 DevTools：

```yaml
# application-prod.yml
csap:
  apidoc:
    devtool:
      enabled: false
```

## 相关文档

- [快速开始指南](QUICK_START.md)
- [完整文档](README.md)
- [FAQ](FAQ.md)
- [示例项目](example/example-apidoc-spring-boot)

## 示例项目

查看完整的网关聚合配置示例：

- [application-gateway.yml](example/example-apidoc-spring-boot/src/main/resources/application-gateway.yml)

---

**Happy Documenting! 🎉**

