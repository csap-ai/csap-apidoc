# Devtools 使用示例

在 Spring Boot 应用中启用 `csap-apidoc-devtools`(随 `csap-apidoc-boot-starter` 一起引入)。

## 1. 添加依赖

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

## 2. 配置

```yaml
server:
  port: 8085

csap:
  apidoc:
    devtool:
      enabled: true   # 启用 Devtools,默认 false
```

或 `application.properties`:

```properties
server.port=8085
csap.apidoc.devtool.enabled=true
```

## 3. 启动类

```java
@SpringBootApplication
@EnableApidoc("com.example.yourapp")   // 指定要生成文档的包
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

## 4. 启动输出

应用就绪后控制台会打印 Devtools 访问地址(由 `DevtoolsStartupBanner` 输出),形如:

```
╔══════════════════════════════════════════════════════════════════════╗
║   🎉  CSAP API Devtools Started Successfully!                       ║
║   📱  访问地址：                                                      ║
║      http://localhost:8085/devtools-ui                              ║
║   🔌  API 接口：                                                      ║
║      • /devtools/*  - Devtools API                                  ║
║      • /csap/yaml/* - YAML API                                      ║
╚══════════════════════════════════════════════════════════════════════╝
```

## 5. 访问

- Devtools 控制台: `http://localhost:8085/devtools-ui`(所有 `/devtools-ui/**` 子路径都会转发到同一个前端页面)
- 文档界面: `http://localhost:8085/csap-api.html`

配置了 `server.servlet.context-path` 时,地址要带上它,例如 context-path 为 `/api` 时是 `http://localhost:8085/api/devtools-ui`。

## 不同环境的配置

按 profile 拆分配置,生产环境关闭 Devtools:

```yaml
# application-dev.yml / application-test.yml
csap:
  apidoc:
    devtool:
      enabled: true
```

```yaml
# application-prod.yml
csap:
  apidoc:
    devtool:
      enabled: false
```

切换 profile:

```bash
java -jar your-app.jar --spring.profiles.active=dev
```

也可以用命令行临时覆盖:

```bash
java -jar your-app.jar --csap.apidoc.devtool.enabled=true
```

## 运行方式

```bash
# Maven
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# JAR
mvn clean package
java -jar target/your-app-1.0.0.jar --spring.profiles.active=dev
```

## 验证清单

- [ ] 控制台显示 Devtools 横幅,端口号正确
- [ ] 访问 `/devtools-ui` 能打开页面(不是 404)
- [ ] 能看到你的 API 接口列表

## 故障排除

**没有显示横幅**: 确认 `csap.apidoc.devtool.enabled=true` 已生效,例如 `curl http://localhost:8085/actuator/env | grep devtool`(需要开启 actuator)。

**页面 404**: 确认 jar 中包含 `csap-api-devtools.html` 静态资源,并且访问路径为 `/devtools-ui`。

**端口号显示错误**: 横幅读取 `server.port`,请在配置里明确指定端口。

## 更多

- [Try-it-out 反向代理](./PROXY.md)
- 完整示例见仓库 `example/` 目录与官网 https://apidoc.csap.ai
