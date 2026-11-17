# Java 代码风格指南

## 概述

本项目使用基于 **Google Java Style Guide** 的代码风格配置，确保代码一致性和可维护性。

## 配置文件

### 1. EditorConfig (`.editorconfig`)

所有IDE和编辑器都支持的基础配置文件，定义了：
- 字符编码：UTF-8
- 缩进方式：空格
- Java文件缩进：4个空格
- 行尾符：LF (Unix风格)
- 最大行长度：120字符

### 2. Checkstyle (`checkstyle.xml`)

基于Google Java Style的代码检查规则，包括：
- **命名规范**：类名、方法名、变量名等
- **代码格式**：缩进、空格、换行等
- **导入管理**：禁止使用`*`导入、删除未使用的导入
- **代码质量**：检查空块、简化布尔表达式等
- **JavaDoc**：公共API需要文档注释

### 3. Maven集成

在根`pom.xml`中已配置Checkstyle插件，执行以下命令：

```bash
# 检查代码风格
mvn checkstyle:check

# 生成代码风格报告
mvn checkstyle:checkstyle
```

## 主要代码规范

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | 大驼峰 (PascalCase) | `UserService`, `ApiController` |
| 方法名 | 小驼峰 (camelCase) | `getUserById`, `processRequest` |
| 变量名 | 小驼峰 (camelCase) | `userName`, `requestCount` |
| 常量 | 全大写+下划线 | `MAX_SIZE`, `DEFAULT_TIMEOUT` |
| 包名 | 全小写 | `ai.csap.apidoc.core` |

### 代码格式

```java
// ✅ 正确示例
public class UserService {
    private static final int MAX_RETRY = 3;
    
    private UserRepository userRepository;
    
    public User getUserById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }
}

// ❌ 错误示例
public class userservice {  // 类名应该大驼峰
    private static final int max_retry=3;  // 操作符周围应该有空格
    
    public User GetUserById(long id){  // 方法名应该小驼峰
        if(id==null)  // 条件语句需要空格和大括号
            throw new IllegalArgumentException("ID cannot be null");
        return userRepository.findById(id).orElseThrow(()->new UserNotFoundException(id));
    }
}
```

### 导入规范

```java
// ✅ 正确
import java.util.List;
import java.util.ArrayList;
import ai.csap.apidoc.core.ApiDoc;

// ❌ 错误
import java.util.*;  // 禁止使用星号导入
```

### 方法长度

- 单个方法不超过 **150行**
- 方法参数不超过 **7个**
- 超过时应考虑重构或使用参数对象

### 行长度

- 每行不超过 **120个字符**
- 长表达式应适当换行

### JavaDoc规范

公共API（public类和方法）需要添加JavaDoc注释：

```java
/**
 * 用户服务类，提供用户相关的业务操作
 *
 * @author CSAP Team
 * @since 1.0.0
 */
public class UserService {
    
    /**
     * 根据ID获取用户信息
     *
     * @param id 用户ID
     * @return 用户对象
     * @throws UserNotFoundException 当用户不存在时抛出
     */
    public User getUserById(Long id) {
        // 实现代码...
    }
}
```

## IDE配置

### IntelliJ IDEA

1. 安装并启用 **CheckStyle-IDEA** 插件
2. Settings → Editor → Code Style → Java → Scheme → Import Scheme
3. 选择项目根目录的 `checkstyle.xml`

### Eclipse

1. 安装 **Eclipse Checkstyle Plugin**
2. Preferences → Checkstyle → New
3. 导入 `checkstyle.xml`

### VS Code

1. 安装 **Checkstyle for Java** 插件
2. 在 `.vscode/settings.json` 中配置：
```json
{
    "java.checkstyle.configuration": "${workspaceFolder}/checkstyle.xml"
}
```

## 持续集成

建议在CI/CD流程中集成代码风格检查：

```yaml
# GitHub Actions 示例
- name: Code Style Check
  run: mvn checkstyle:check
```

## 参考资料

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Checkstyle官方文档](https://checkstyle.org/)
- [EditorConfig官方网站](https://editorconfig.org/)

## 常见问题

### 如何临时禁用某些检查？

```java
// 禁用某一行的检查
@SuppressWarnings("checkstyle:MethodLength")
public void longMethod() {
    // 很长的方法...
}
```

### 报告在哪里查看？

执行 `mvn checkstyle:checkstyle` 后，报告位于：
```
target/site/checkstyle.html
```

### 如何修复所有格式问题？

建议在IDE中使用自动格式化功能（如IntelliJ的 `Ctrl+Alt+L`）。

