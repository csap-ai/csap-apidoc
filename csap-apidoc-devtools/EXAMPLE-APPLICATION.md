# 📖 使用示例

## 🚀 在你的 Spring Boot 应用中使用 Devtools

### 1. 添加依赖

**pom.xml**
```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### 2. 配置文件

**application.yml**
```yaml
server:
  port: 8085

csap:
  apidoc:
    devtool:
      enabled: true  # ✅ 启用 Devtools 功能
```

或 **application.properties**
```properties
server.port=8085
csap.apidoc.devtool.enabled=true
```

---

### 3. 启动应用

```java
@SpringBootApplication
@EnableApidoc  // 启用 CSAP API 文档
public class YourApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

---

### 4. 查看启动输出

应用启动后，控制台会自动打印：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v2.7.0)

2025-10-20 19:00:00.000  INFO 12345 --- [           main] c.e.y.YourApplication                    : Starting YourApplication...
2025-10-20 19:00:05.000  INFO 12345 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8085 (http)
2025-10-20 19:00:05.500  INFO 12345 --- [           main] c.e.y.YourApplication                    : Started YourApplication in 5.5 seconds

╔══════════════════════════════════════════════════════════════════════╗
║                                                                      ║
║   🎉  CSAP API Devtools Started Successfully!                       ║
║                                                                      ║
║   📱  访问地址：                                                      ║
║      http://localhost:8085/devtools-ui                              ║
║                                                                      ║
║   💡  功能说明：                                                      ║
║      • API 接口管理与文档生成                                          ║
║      • 接口参数配置与验证                                              ║
║      • YAML/JSON 文档导出                                             ║
║                                                                      ║
║   🔌  API 接口：                                                      ║
║      • /devtools/*  - Devtools API                                  ║
║      • /csap/yaml/* - YAML API                                      ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

### 5. 访问 Devtools

打开浏览器访问：

```
http://localhost:8085/devtools-ui
```

页面会自动跳转到：

```
http://localhost:8085/devtools-ui/api
```

---

## 🎯 不同环境的配置

### 开发环境（application-dev.yml）

```yaml
server:
  port: 8085

csap:
  apidoc:
    devtool:
      enabled: true  # ✅ 开发环境启用
```

**效果**：启动后打印访问地址

---

### 测试环境（application-test.yml）

```yaml
server:
  port: 8086

csap:
  apidoc:
    devtool:
      enabled: true  # ✅ 测试环境启用
```

**效果**：启动后打印 `http://localhost:8086/devtools-ui`

---

### 生产环境（application-prod.yml）

```yaml
server:
  port: 80

csap:
  apidoc:
    devtool:
      enabled: false  # ❌ 生产环境禁用
```

**效果**：不打印横幅，不启用 Devtools 功能

---

## 📊 多环境切换

### 方式1：使用 Spring Profiles

```bash
# 开发环境
java -jar your-app.jar --spring.profiles.active=dev

# 测试环境
java -jar your-app.jar --spring.profiles.active=test

# 生产环境
java -jar your-app.jar --spring.profiles.active=prod
```

---

### 方式2：命令行参数覆盖

```bash
# 临时启用 Devtools
java -jar your-app.jar --csap.apidoc.devtool.enabled=true

# 临时禁用 Devtools
java -jar your-app.jar --csap.apidoc.devtool.enabled=false
```

---

## 🔍 带 Context Path 的示例

### 配置

```yaml
server:
  port: 8085
  servlet:
    context-path: /api

csap:
  apidoc:
    devtool:
      enabled: true
```

### 启动输出

```
╔══════════════════════════════════════════════════════════════════════╗
║   📱  访问地址：                                                      ║
║      http://localhost:8085/api/devtools-ui                          ║
╚══════════════════════════════════════════════════════════════════════╝
```

### 访问地址

```
http://localhost:8085/api/devtools-ui
```

---

## 🎨 完整示例项目

### 项目结构

```
your-project/
├── src/main/java/
│   └── com/example/yourapp/
│       ├── YourApplication.java
│       └── controller/
│           └── UserController.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
└── pom.xml
```

### YourApplication.java

```java
package com.example.yourapp;

import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableApidoc  // 启用 CSAP API 文档
public class YourApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### UserController.java

```java
package com.example.yourapp.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Api(value = "用户管理", description = "用户相关接口")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户信息", description = "根据ID获取用户详情")
    public User getUserById(@PathVariable Long id) {
        // 业务逻辑
        return new User(id, "张三");
    }
}
```

### application-dev.yml

```yaml
server:
  port: 8085

logging:
  level:
    com.example: DEBUG

csap:
  apidoc:
    devtool:
      enabled: true  # 开发环境启用 Devtools
```

---

## 🚀 启动命令

### IDE 运行

在 IDEA 或 Eclipse 中直接运行 `YourApplication.main()`

**Run Configuration**：
- Main class: `com.example.yourapp.YourApplication`
- VM options: `-Dspring.profiles.active=dev`

---

### Maven 运行

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

### JAR 包运行

```bash
# 打包
mvn clean package

# 运行
java -jar target/your-app-1.0.0.jar --spring.profiles.active=dev
```

---

## ✅ 验证清单

启动后检查：

- [ ] 控制台显示 Devtools 横幅
- [ ] 横幅中的端口号正确
- [ ] 访问 `http://localhost:8085/devtools-ui` 能打开页面
- [ ] 页面能正常加载（不是 404）
- [ ] 能看到你的 API 接口列表

---

## 🐛 故障排除

### 问题：没有显示横幅

**检查**：
```bash
# 查看配置是否生效
curl http://localhost:8085/actuator/env | grep devtool
```

**解决**：确认 `csap.apidoc.devtool.enabled=true`

---

### 问题：页面 404

**检查**：是否使用了正确的 basename

**解决**：确认前端已使用 `<BrowserRouter basename="/devtools-ui">`

---

### 问题：端口号显示错误

**检查**：配置文件中的端口

**解决**：
```yaml
server:
  port: 8085  # 明确指定端口
```

---

## 📚 更多示例

查看其他文档：
- [启动横幅详细说明](./STARTUP-BANNER.md)
- [Devtools 部署指南](./DEVTOOLS-DEPLOY.md)
- [404 问题解决方案](./404-PROBLEM-SOLVED.md)

---

**最后更新**: 2025-10-20  
**测试状态**: ✅ 已验证

