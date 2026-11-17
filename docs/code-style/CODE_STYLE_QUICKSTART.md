# 代码风格快速上手指南

## 🚀 快速开始

### 1. 检查代码风格

```bash
# 在项目根目录执行
mvn checkstyle:check
```

### 2. 生成详细报告

```bash
mvn checkstyle:checkstyle
```

报告将生成在：`target/site/checkstyle.html`（在浏览器中打开查看）

### 3. 在构建时自动检查

```bash
# 正常构建时会自动运行checkstyle检查
mvn clean install
```

## 🔧 IDE配置

### IntelliJ IDEA

#### 方法一：使用Checkstyle插件（推荐）

1. **安装插件**
   - File → Settings → Plugins
   - 搜索 "CheckStyle-IDEA"
   - 安装并重启IDE

2. **配置Checkstyle**
   - File → Settings → Tools → Checkstyle
   - 点击 "+" 添加配置文件
   - 选择项目根目录的 `checkstyle.xml`
   - 勾选为激活配置

3. **实时检查**
   - 底部工具栏出现 "CheckStyle" 选项卡
   - 可以随时扫描当前文件或整个项目

#### 方法二：导入代码格式化配置

1. File → Settings → Editor → Code Style
2. 点击齿轮图标 → Import Scheme → CheckStyle Configuration
3. 选择 `checkstyle.xml`

#### 快捷键

- `Ctrl + Alt + L` (Windows/Linux) 或 `Cmd + Option + L` (macOS)：格式化代码
- `Ctrl + Alt + O` (Windows/Linux) 或 `Cmd + Option + O` (macOS)：优化导入

### Visual Studio Code

1. **安装扩展**
   - Java Extension Pack
   - Checkstyle for Java

2. **配置**
   创建/修改 `.vscode/settings.json`：
   ```json
   {
       "java.checkstyle.configuration": "${workspaceFolder}/checkstyle.xml",
       "java.checkstyle.version": "10.12.5"
   }
   ```

### Eclipse

1. **安装插件**
   - Help → Eclipse Marketplace
   - 搜索 "Checkstyle Plug-in"
   - 安装并重启

2. **配置**
   - Window → Preferences → Checkstyle
   - New → External Configuration File
   - 选择 `checkstyle.xml`

## 📋 常见代码风格问题及修复

### 问题1：导入使用了星号

```java
// ❌ 错误
import java.util.*;

// ✅ 正确
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
```

**修复**: 在IDEA中使用 `Ctrl+Alt+O` 优化导入

### 问题2：缩进不正确

```java
// ❌ 错误（使用tab或2个空格）
public void test() {
  return;
}

// ✅ 正确（使用4个空格）
public void test() {
    return;
}
```

**修复**: 在IDEA中使用 `Ctrl+Alt+L` 格式化代码

### 问题3：行长度超过120字符

```java
// ❌ 错误
public User processUserRegistrationWithEmailVerificationAndProfileCreation(String email, String password, UserProfile profile) {

// ✅ 正确（适当换行）
public User processUserRegistrationWithEmailVerificationAndProfileCreation(
        String email, 
        String password, 
        UserProfile profile) {
```

### 问题4：变量命名不规范

```java
// ❌ 错误
String UserName;           // 变量名应该小驼峰
int MAX_size;              // 常量应该全大写
private String user_name;  // 不应使用下划线

// ✅ 正确
String userName;           // 小驼峰
int MAX_SIZE;              // 全大写+下划线
private String userName;   // 小驼峰
```

### 问题5：缺少JavaDoc

```java
// ❌ 错误（公共方法缺少注释）
public class UserService {
    public User getUser(Long id) {
        // ...
    }
}

// ✅ 正确
/**
 * 用户服务类
 */
public class UserService {
    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 用户对象
     */
    public User getUser(Long id) {
        // ...
    }
}
```

## 🎯 开发流程建议

### 提交代码前

```bash
# 1. 格式化代码（在IDE中）
# 2. 运行checkstyle检查
mvn checkstyle:check

# 3. 如果没有错误，继续提交
git add .
git commit -m "your message"
```

### 修复现有代码

```bash
# 1. 生成报告找出问题
mvn checkstyle:checkstyle

# 2. 在浏览器打开报告
open target/site/checkstyle.html

# 3. 根据报告逐个修复
# 4. 重新检查
mvn checkstyle:check
```

## ⚙️ 配置说明

### 当前配置特点

- ✅ **基于Google Java Style Guide**
- ✅ **行长度**: 120字符（适合现代宽屏显示器）
- ✅ **缩进**: 4个空格
- ✅ **JavaDoc**: 仅要求public方法
- ✅ **警告级别**: 不会阻止构建，仅产生警告

### 调整严格程度

如需更严格的检查，可修改 `pom.xml` 中的配置：

```xml
<configuration>
    <failsOnError>true</failsOnError>        <!-- 有错误时构建失败 -->
    <failOnViolation>true</failOnViolation>  <!-- 有违规时构建失败 -->
    <violationSeverity>error</violationSeverity>  <!-- 违规级别改为error -->
</configuration>
```

## 📚 参考资源

- [完整代码风格指南](CODE_STYLE.md)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Checkstyle官方文档](https://checkstyle.org/)

## ❓ 常见问题

### Q: 检查太严格了怎么办？

A: 对于特殊情况，可以使用注解临时禁用：

```java
@SuppressWarnings("checkstyle:MethodLength")
public void veryLongMethod() {
    // 特殊情况的长方法
}
```

### Q: 如何只检查我修改的文件？

A: 使用IDEA的Checkstyle插件，在"CheckStyle"工具窗口选择"Check Current File"

### Q: 团队成员IDE配置不同怎么办？

A: `.editorconfig` 文件会自动被大多数现代IDE识别，确保基本的格式统一（缩进、编码等）

## 🔄 持续集成

建议在CI/CD中添加代码风格检查：

```yaml
# GitHub Actions 示例
name: Code Quality
on: [pull_request]
jobs:
  checkstyle:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '11'
      - name: Run Checkstyle
        run: mvn checkstyle:check
```

---

**记住**: 代码风格统一是团队协作的基础，保持一致的代码风格可以提高代码可读性和维护性！

