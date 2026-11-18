# 示例项目文件说明

## 目录结构

```
example-spring-boot3/
├── src/
│   ├── main/
│   │   ├── java/ai/csap/example/
│   │   │   ├── ExampleApplication.java      # 启动类,@EnableApidoc
│   │   │   ├── controller/
│   │   │   │   ├── UserController.java      # 用户接口
│   │   │   │   ├── ProductApi.java          # 产品接口定义(Spring MVC 映射 + 文档注解)
│   │   │   │   ├── ProductController.java   # 实现 ProductApi
│   │   │   │   ├── AdvancedController.java  # @ApiProperty / @ApiPropertys 用法
│   │   │   │   ├── AuthController.java      # /auth/login、/auth/whoami(JWT 示例)
│   │   │   │   ├── BaseCrudController.java  # 泛型 CRUD 基类
│   │   │   │   ├── UserCrudController.java  # 继承基类,验证泛型解析
│   │   │   │   └── GenericTestController.java  # 泛型请求体示例
│   │   │   ├── model/
│   │   │       ├── User.java
│   │   │       ├── UserStatus.java          # 枚举
│   │   │       ├── Product.java
│   │   │       ├── ProductCategory.java     # 枚举
│   │   │       ├── Response.java            # 统一响应包装
│   │   │       ├── BaseRequest.java         # 泛型请求基类
│   │   │       └── UserRequest.java         # BaseRequest<User> 的子类
│   │   │   └── security/                    # 可选 JWT 安全层(默认关闭)
│   │   │       ├── SecurityConfig.java / SecurityProperties.java
│   │   │       ├── JwtService.java / JwtAuthenticationFilter.java
│   │   │       └── InMemoryUserStore.java
│   │   └── resources/
│   │       ├── application.yml              # 默认配置
│   │       ├── application-sqlite.yml       # profile 样例(不切换策略,见 STRATEGIES.md)
│   │       ├── application-yaml.yml         # profile 样例(同上)
│   │       └── application-gateway.yml      # 网关聚合多个服务文档的配置样例
│   └── test/                                # 校验、泛型字段、字段过滤的测试
├── api-test.http                            # HTTP Client 请求集合(IntelliJ / VS Code)
├── test-api.sh                              # curl 脚本(需要 jq)
├── run.sh                                   # 打包并运行
├── pom.xml
├── README.md
├── ANNOTATIONS.md
├── SECURITY.md
├── STRATEGIES.md
└── FILES.md                                 # 本文件
```

## 源码

- `ExampleApplication.java`: `@EnableApidoc(value = "ai.csap.example", paramType = ApiStrategyType.YAML)`
- `UserController.java`: 用户的列表(分页)、详情、创建、更新、删除、状态更新(PATCH),演示查询参数、路径参数与请求体
- `ProductApi.java` / `ProductController.java`: 产品的增删改查、库存更新、批量删除,含 `BigDecimal` 价格;接口与实现分离
- `AuthController.java` 与 `security/`: 可选的 JWT 示例,见 [SECURITY.md](./SECURITY.md)
- `BaseCrudController.java` / `UserCrudController.java` / `GenericTestController.java`: 泛型场景下的字段解析示例
- `AdvancedController.java`: 方法级 `@ApiProperty` / `@ApiPropertys`
- `User.java` / `Product.java`: 实体,演示 `@ApiModelProperty` 与 JSR-303 校验(`@NotBlank`、`@Email`、`@Size`、`@Pattern`、`@Min` / `@Max`、`@Digits`、`@DecimalMin` 等)
- `UserStatus.java` / `ProductCategory.java`: 在字段上使用 `@EnumValue` 与 `@EnumMessage` 的枚举
- `Response.java`: 统一响应包装

## 配置文件

- `application.yml`: 端口 8083、`csap.apidoc.*`、校验模式、示例 JWT 开关(`csap.example.security.*`)、Actuator 与日志
- `application-sqlite.yml` / `application-yaml.yml`: 日志与应用名样例;存储策略以启动类上的 `@EnableApidoc` 为准
- `application-gateway.yml`: 演示用 `csap.apidoc.resources` 聚合多个服务的 `/csap/apidoc/parent`

## 测试与脚本

- `api-test.http`、`test-api.sh`: 覆盖主要接口。默认请求 `http://localhost:8083`,`test-api.sh` 可用环境变量 `BASE_URL` 覆盖
- `src/test/`: 参数校验、泛型字段测试与 Jackson / Fastjson / Fastjson2 下的字段过滤 E2E 测试
- `run.sh`: 检查 Maven、打包后执行 `mvn spring-boot:run`

## 常用命令

```bash
mvn clean package            # 构建
mvn spring-boot:run          # 运行
mvn test                     # 单元测试
```

## 相关文档

- [示例总览](../README.md)
- [README](./README.md)
- [注解使用指南](./ANNOTATIONS.md)
- [存储策略](./STRATEGIES.md)
- [JWT 安全层](./SECURITY.md)
