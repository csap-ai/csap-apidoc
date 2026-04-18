# Parameter Strategies Examples

This document demonstrates how to use different parameter strategies in CSAP Framework API Doc.

## 📚 Available Strategies

### 1. ANNOTATION Strategy (Default - Recommended)

This is the default and recommended approach. Parameters are defined using annotations in your code.

**Configuration:**

```java
@SpringBootApplication
@EnableApidoc("ai.csap.example")
public class ExampleApplication { }
```

**Usage:**

```java
@GetMapping("/{id}")
@ApiOperation(value = "Get User")
public User getUser(
    @ApiProperty(value = "User ID", required = true, example = "1001")
    @PathVariable Long id
) { }
```

**Advantages:**
- ✅ Type-safe
- ✅ IDE support
- ✅ Easy to maintain
- ✅ No external files needed

**When to use:**
- Standard API documentation
- Code-first approach
- Type safety is important

### 2. SQLite Strategy

Parameter configurations are stored in a SQLite database file, allowing dynamic configuration via DevTools.

**Configuration:**

```java
@SpringBootApplication
@EnableApidoc(
    value = "ai.csap.example",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${apidoc.path:csap-data}",
    fileName = "${apidoc.filename:apidoc-db}"
)
public class ExampleApplication { }
```

**application-sqlite.yml:**

```yaml
apidoc:
  path: csap-sqlite-data
  filename: example-db
```

**Run with:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=sqlite
```

**Advantages:**
- ✅ Dynamic configuration via DevTools
- ✅ No code changes needed for parameter updates
- ✅ Query and filter parameter data
- ✅ Good for visual management

**When to use:**
- Need visual parameter configuration
- Non-developers manage API documentation
- Dynamic parameter management required

### 3. YAML Strategy

Parameter configurations are stored in YAML files, making them easy to read and version control.

**Configuration:**

```java
@SpringBootApplication
@EnableApidoc(
    value = "ai.csap.example",
    paramType = ApiStrategyType.YAML,
    path = "${apidoc.path:csap-docs}"
)
public class ExampleApplication { }
```

**application-yaml.yml:**

```yaml
apidoc:
  path: csap-yaml-docs
```

**Run with:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=yaml
```

**Advantages:**
- ✅ Human-readable format
- ✅ Easy to version control (Git)
- ✅ Easy to review and edit manually
- ✅ Good for documentation-first approach

**When to use:**
- Documentation needs version control
- Easy manual editing required
- Documentation-first workflow

## 🔄 Strategy Comparison

| Feature | ANNOTATION | SQL_LITE | YAML |
|---------|-----------|----------|------|
| Type Safety | ✅ Yes | ❌ No | ❌ No |
| IDE Support | ✅ Yes | ⚠️ Limited | ⚠️ Limited |
| Visual Configuration | ❌ No | ✅ Yes (DevTools) | ⚠️ Manual |
| Version Control | ✅ Yes (code) | ❌ Binary file | ✅ Yes (text) |
| Easy to Update | ✅ In code | ✅ In DevTools | ✅ Edit files |
| Performance | ⚡ Fast | ⚡ Fast | ⚠️ Slower parsing |
| Learning Curve | 📘 Low | 📗 Medium | 📗 Medium |

## 🎯 Switching Strategies

### From ANNOTATION to SQLite

1. Update `@EnableApidoc`:
   ```java
   @EnableApidoc(
       value = "ai.csap.example",
       paramType = ApiStrategyType.SQL_LITE,
       path = "csap-data",
       fileName = "apidoc-db"
   )
   ```

2. Restart application
3. Configure parameters via DevTools

### From ANNOTATION to YAML

1. Update `@EnableApidoc`:
   ```java
   @EnableApidoc(
       value = "ai.csap.example",
       paramType = ApiStrategyType.YAML,
       path = "csap-docs"
   )
   ```

2. Restart application
3. Edit YAML files in the `csap-docs` directory

## 💡 Environment Variables

All strategies support environment variables:

```java
@EnableApidoc(
    value = "ai.csap.example",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${apidoc.path:/tmp/apidoc}",
    fileName = "${apidoc.filename:${spring.application.name}}"
)
```

**application.yml:**

```yaml
apidoc:
  path: /var/apidoc/data
  filename: myapp-db
```

**Environment variables:**

```bash
export APIDOC_PATH=/var/apidoc/data
export APIDOC_FILENAME=myapp-db
```

## 📝 Best Practices

### For Development
- Use **ANNOTATION** strategy
- Keep documentation close to code
- Easy to refactor

### For Testing
- Use **SQLite** strategy
- Quick parameter adjustments via DevTools
- No code changes needed

### For Production
- Use **ANNOTATION** strategy
- Type safety and performance
- Or disable documentation entirely

### For Documentation Teams
- Use **YAML** strategy
- Easy to review and edit
- Good for non-developers

## 🔍 Example Files Location

After starting with different strategies, files are created at:

- **SQLite**: `./csap-sqlite-data/example-sqlite-db.db`
- **YAML**: `./csap-yaml-docs/*.yaml`
- **ANNOTATION**: No external files (in-memory)

## 🛠️ Troubleshooting

### Q: Can I use multiple strategies?

A: No, only one `paramType` can be active at a time per application instance.

### Q: How do I migrate from one strategy to another?

A: Change the `@EnableApidoc` configuration and restart. The framework will use the new strategy. Previous data is not automatically migrated.

### Q: Which strategy is fastest?

A: ANNOTATION is fastest as it doesn't require file I/O. SQLite and YAML have similar performance for small to medium projects.

### Q: Can I use different strategies in different environments?

A: Yes! Use Spring profiles:

```java
@Profile("dev")
@EnableApidoc(paramType = ApiStrategyType.SQL_LITE, ...)

@Profile("prod")
@EnableApidoc(paramType = ApiStrategyType.ANNOTATION, ...)
```

---

**Need more help?** Check the [main documentation](../../README.md) or [quick start guide](../../QUICK_START.md).

