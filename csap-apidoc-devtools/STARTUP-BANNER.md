# 🎉 Devtools 启动横幅功能

## 📋 功能说明

在应用启动成功后，**自动在控制台打印 Devtools 访问地址**。

## ✨ 特性

- ✅ **条件触发**：只有启用 `csap.apidoc.devtool.enabled=true` 时才打印
- ✅ **自动获取端口**：自动读取 `server.port` 配置
- ✅ **支持 context-path**：自动拼接 `server.servlet.context-path`
- ✅ **漂亮的横幅**：使用 ASCII 艺术框架美化输出
- ✅ **双重输出**：同时输出到日志和控制台

---

## 🎨 效果预览

启动应用后，控制台会显示：

```
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

## 🔧 使用方法

### 1. 启用 Devtools

在你的 Spring Boot 应用配置文件中添加：

**application.yml**
```yaml
csap:
  apidoc:
    devtool:
      enabled: true  # 启用 Devtools 功能
```

**或 application.properties**
```properties
csap.apidoc.devtool.enabled=true
```

### 2. 启动应用

```bash
java -jar your-application.jar
```

或在 IDE 中直接运行 `main` 方法。

### 3. 查看输出

应用启动成功后，控制台会自动打印访问地址：

```
...（Spring Boot 启动日志）...

╔══════════════════════════════════════════════════════════════════════╗
║   🎉  CSAP API Devtools Started Successfully!                       ║
║   📱  访问地址：http://localhost:8085/devtools-ui                    ║
╚══════════════════════════════════════════════════════════════════════╝

...（应用继续运行）...
```

---

## 🎯 实现原理

### 核心类：`DevtoolsStartupBanner`

```java
public class DevtoolsStartupBanner implements ApplicationListener<ApplicationReadyEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        
        // 获取配置
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        
        // 构建访问地址
        String devtoolsUrl = "http://localhost:" + port + contextPath + "/devtools-ui";
        
        // 打印横幅
        printBanner(devtoolsUrl);
    }
}
```

### 自动配置：`ApidocDevtoolsAutoConfiguration`

```java
@Configuration
@ConditionalOnProperty(prefix = "csap.apidoc.devtool", name = "enabled", havingValue = "true")
public class ApidocDevtoolsAutoConfiguration {
    
    @Bean
    public DevtoolsStartupBanner devtoolsStartupBanner() {
        return new DevtoolsStartupBanner();
    }
}
```

### 关键点

1. **条件加载**
   - `@ConditionalOnProperty` 确保只有配置 `csap.apidoc.devtool.enabled=true` 时才注册
   - 未启用时不会打印，不影响启动速度

2. **事件监听**
   - 监听 `ApplicationReadyEvent` 事件
   - 在应用完全启动后才触发（所有 Bean 初始化完成）

3. **动态读取配置**
   - 从 `Environment` 读取端口和 context-path
   - 自动适配不同的部署环境

---

## 🔍 不同配置的输出示例

### 默认端口（8080）

```yaml
# 配置
csap:
  apidoc:
    devtool:
      enabled: true
```

```
# 输出
http://localhost:8080/devtools-ui
```

---

### 自定义端口（8085）

```yaml
# 配置
server:
  port: 8085

csap:
  apidoc:
    devtool:
      enabled: true
```

```
# 输出
http://localhost:8085/devtools-ui
```

---

### 带 Context Path

```yaml
# 配置
server:
  port: 8085
  servlet:
    context-path: /api

csap:
  apidoc:
    devtool:
      enabled: true
```

```
# 输出
http://localhost:8085/api/devtools-ui
```

---

## ⚙️ 配置选项

### 禁用横幅

如果不想看到启动横幅，只需不启用 Devtools：

```yaml
csap:
  apidoc:
    devtool:
      enabled: false  # 或直接不配置
```

---

## 📝 日志级别

横幅信息会输出到两个地方：

1. **日志文件**（通过 `log.info()`）
   - 级别：INFO
   - Logger：`ai.csap.apidoc.devtools.DevtoolsStartupBanner`

2. **控制台**（通过 `System.out.println()`）
   - 确保在控制台一定可见
   - 即使日志级别设置为 WARN 也能看到

---

## 🎨 自定义横幅

如果你想自定义横幅样式，可以创建自己的 `DevtoolsStartupBanner` Bean：

```java
@Bean
public DevtoolsStartupBanner devtoolsStartupBanner() {
    return new DevtoolsStartupBanner() {
        @Override
        protected void printBanner(String devtoolsUrl) {
            System.out.println("==============================================");
            System.out.println("Devtools URL: " + devtoolsUrl);
            System.out.println("==============================================");
        }
    };
}
```

---

## 🐛 故障排除

### 问题1：启动后没有看到横幅

**检查清单**：
1. ✅ 确认配置了 `csap.apidoc.devtool.enabled=true`
2. ✅ 确认依赖了 `csap-framework-apidoc-boot-starter`
3. ✅ 确认应用完全启动成功
4. ✅ 检查日志级别是否过滤了 INFO 级别

---

### 问题2：端口号不正确

**原因**：可能使用了随机端口

**解决**：显式配置端口

```yaml
server:
  port: 8085  # 明确指定端口
```

---

### 问题3：URL 路径不正确

**原因**：配置了 context-path 但 URL 没有包含

**检查**：`DevtoolsStartupBanner` 会自动读取 `server.servlet.context-path`

如果还有问题，检查你的配置：
```yaml
server:
  servlet:
    context-path: /api  # 确保这个配置正确
```

---

## 📊 与其他组件的集成

### Spring Boot Admin

如果使用 Spring Boot Admin，横幅会在主应用启动时打印，不会在 Admin Server 中显示。

### Docker 部署

在 Docker 容器中运行时，横幅会正常显示在容器日志中：

```bash
docker logs -f your-container-name
```

---

## 🎯 最佳实践

1. **开发环境**：启用横幅，方便快速访问
   ```yaml
   # application-dev.yml
   csap:
     apidoc:
       devtool:
         enabled: true
   ```

2. **生产环境**：禁用 Devtools（包括横幅）
   ```yaml
   # application-prod.yml
   csap:
     apidoc:
       devtool:
         enabled: false
   ```

3. **测试环境**：根据需要启用
   ```yaml
   # application-test.yml
   csap:
     apidoc:
       devtool:
         enabled: true
   ```

---

## 📖 相关文档

- [Devtools 部署文档](./DEVTOOLS-DEPLOY.md)
- [Controller 方案对比](./CONTROLLER-SOLUTIONS.md)
- [404 问题解决](./404-PROBLEM-SOLVED.md)

---

**最后更新**: 2025-10-20  
**功能状态**: ✅ 已实现并测试

