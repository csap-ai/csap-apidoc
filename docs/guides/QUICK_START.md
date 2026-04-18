# CSAP Framework API Doc - Quick Start Guide

This guide will help you integrate CSAP Framework API Doc into your Spring Boot project in just 5 minutes.

## 📋 Prerequisites

Before you begin, ensure you have:
- ✅ JDK 8 or higher installed (Recommended: JDK 8 or JDK 11)
- ✅ Maven 3.6+ or Gradle 6+
- ✅ An existing Spring Boot 2.x project

## 🚀 Step 1: Add Dependency

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```groovy
implementation 'com.csap.framework.boot:csap-framework-apidoc-boot-starter:1.0.3'
```

## ⚙️ Step 2: Enable API Documentation (Annotation-Based - Recommended)

**Add `@EnableApidoc` annotation to your Spring Boot Application class:**

```java
package com.yourcompany.yourproject;

import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableApidoc("com.yourcompany.yourproject")  // Specify package to scan
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Parameter Strategy Types

CSAP Apidoc supports multiple strategies for managing API parameters. Configure via `paramType`:

**1. ANNOTATION Strategy (Default - Recommended)**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.ANNOTATION  // Use annotations to define parameters
)
```
- Parameters are defined using `@ApiProperty`, `@ApiModelProperty` annotations
- Most flexible and easy to maintain
- **This is the default and recommended approach**

**2. SQLite Strategy**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,  // Store parameter configs in SQLite
    path = "csap-example",                  // Storage path
    fileName = "example-db"                 // SQLite database file name
)
```
- Parameter configurations stored in SQLite database
- Supports visual parameter configuration via DevTools
- Good for dynamic parameter management

**Using Environment Variables (Recommended for path and fileName):**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${csap.apidoc.path:csap-example}",       // Use env variable with default
    fileName = "${csap.apidoc.filename:example-db}"  // Use env variable with default
)
```

Then configure in `application.yml`:
```yaml
csap:
  apidoc:
    path: csap-production    # Override path
    filename: prod-db        # Override database name
```

Or use system environment variables:
```bash
export CSAP_APIDOC_PATH=/var/data/apidoc
export CSAP_APIDOC_FILENAME=prod-db
```

**3. YAML Strategy**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.YAML,  // Store parameter configs in YAML files
    path = "csap-docs"                  // YAML file storage path
)
```
- Parameter configurations stored in YAML files
- Human-readable and version control friendly
- Easy to review and edit manually

**4. Advanced Configuration with Multiple Packages**

```java
@EnableApidoc(
    apiPackages = {"com.yourcompany.yourproject.controller"},  // API controller packages
    enumPackages = {"com.yourcompany.yourproject.enums"},      // Enum packages
    modelPackages = {"com.yourcompany.yourproject.model"},     // Model packages
    paramType = ApiStrategyType.ANNOTATION,                     // Strategy type
    showChildPackageFlag = true                                 // Scan child packages
)
```

### Optional: YAML Configuration (For DevTools)

If you want to enable DevTools, add to `application.yml`:

```yaml
csap:
  apidoc:
    devtool:
      enabled: true    # Enable DevTools interface
      cache: true      # Enable caching
```

## 📝 Step 3: Annotate Your Controllers

Add documentation annotations to your REST controllers:

### Simple Example

```java
package com.yourcompany.yourproject.controller;

import ai.csap.apidoc.annotation.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Api(tags = "User Management", description = "User related operations")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation(value = "Get User by ID", description = "Retrieve user information by user ID")
    public User getUser(@PathVariable Long id) {
        // Your business logic
        return userService.getById(id);
    }

    @PostMapping
    @ApiOperation(value = "Create User", description = "Create a new user account")
    public User createUser(@Valid @RequestBody User user) {
        // Your business logic
        return userService.create(user);
    }
}
```

### Document Your Models

```java
package com.yourcompany.yourproject.model;

import ai.csap.apidoc.annotation.apidoc.ApiModel;
import ai.csap.apidoc.annotation.apidoc.ApiModelProperty;

@ApiModel(description = "User entity")
public class User {
    
    @ApiModelProperty(value = "User ID", example = "1001")
    private Long id;
    
    @ApiModelProperty(value = "Username", required = true, example = "john_doe")
    private String username;
    
    @ApiModelProperty(value = "Email address", example = "john@example.com")
    private String email;
    
    @ApiModelProperty(value = "User status", example = "ACTIVE")
    private String status;
    
    // Getters and setters...
}
```

## 🎯 Step 4: Run and Access

1. **Start your Spring Boot application**:
   ```bash
   mvn spring-boot:run
   ```

2. **Look for the startup banner** in your console:
   ```
   ╔══════════════════════════════════════════════════════════════════════╗
   ║                                                                      ║
   ║   📚  CSAP API Documentation Generated Successfully!                ║
   ║                                                                      ║
   ║   📄  API 文档地址：                                                  ║
   ║      http://localhost:8080/csap-api.html                            ║
   ║                                                                      ║
   ╚══════════════════════════════════════════════════════════════════════╝
   ```

3. **Access the documentation** in your browser:
   - **UI Interface**: `http://localhost:8080/csap-api.html`
   - **JSON API**: `http://localhost:8080/api/csap/doc`

## 🎨 Step 5: Explore Features

### Online Testing
1. Navigate to any API endpoint in the UI
2. Click the "Test" button
3. Fill in parameters
4. Click "Send Request"
5. View the response

### Export Documentation
- **Postman**: Export collection and import into Postman
- **Markdown**: Generate readable documentation
- **OpenAPI/Swagger**: Export in standard format

### Configure Fields
1. Click on any API endpoint
2. Use the "Field Management" section
3. Add optional fields from the field library
4. Configure validation rules

## 🔧 Advanced Configuration

### Enable DevTools (Optional)

DevTools provides a visual interface for managing API documentation:

```yaml
csap:
  apidoc:
    devtool:
      enabled: true    # Enable DevTools
      cache: true      # Enable caching (default: true)
```

### Choosing the Right Strategy

| Strategy | Use Case | Pros | Cons |
|----------|----------|------|------|
| **ANNOTATION** (Default) | Standard API documentation | Type-safe, IDE support, easy maintenance | Requires code annotations |
| **SQL_LITE** | Dynamic parameter management | Visual configuration, no code changes needed | Requires database file |
| **YAML** | Version-controlled documentation | Human-readable, easy to review | Manual file editing |

**Strategy Comparison Example:**

```java
// Option 1: Pure Annotation (Default - Recommended)
@EnableApidoc("com.yourcompany.yourproject")

// Option 2: SQLite for dynamic configuration
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path = "csap-example",
    fileName = "example-db"
)

// Option 3: YAML for version control
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.YAML,
    path = "api-docs"
)

// Option 4: Using environment variables (Best Practice)
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${csap.apidoc.path:csap-default}",
    fileName = "${csap.apidoc.file:apidoc}"
)
```

### Environment Variable Support

`path` and `fileName` support Spring Boot placeholder syntax:

**Syntax:**
- `${property.name}` - Use property value, error if not found
- `${property.name:defaultValue}` - Use property value or default

**Example:**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${apidoc.data.path:/tmp/apidoc}",              // Use /tmp/apidoc if not configured
    fileName = "${apidoc.db.file:${spring.application.name}}"  // Use app name as default
)
```

Configure in `application.yml` or `application.properties`:

```yaml
# application.yml
apidoc:
  data:
    path: /var/apidoc/data
  db:
    file: myapp-docs
```

```properties
# application.properties  
apidoc.data.path=/var/apidoc/data
apidoc.db.file=myapp-docs
```

Or use system environment variables:
```bash
export APIDOC_DATA_PATH=/var/apidoc/data
export APIDOC_DB_FILE=myapp-docs
```

**Benefits:**
- ✅ Different configs for dev/test/prod environments
- ✅ Centralized configuration management
- ✅ Easy to override via environment variables
- ✅ Supports Docker and Kubernetes deployments

### Disable in Production

**Option 1: Using Spring Profiles (Recommended)**

```java
@SpringBootApplication
@EnableApidoc(value = "com.yourcompany.yourproject")
public class Application {
    // Only enable apidoc in dev/test profiles
}
```

Then in `application-prod.yml`:
```yaml
csap:
  apidoc:
    devtool:
      enabled: false  # Disable DevTools in production
```

### API Gateway Aggregation (Microservices)

For microservice architecture or API gateway scenarios, you can aggregate multiple service documentations in one place:

**Use Case:** You have multiple microservices (Admin API, Store API, Other API) behind a gateway, and you want to view all their documentation from a single interface.

**Configuration in Gateway Project:**

```yaml
# application.yml in your API Gateway project
csap:
  apidoc:
    resources:
      - name: Admin API
        url: /example-admin-api/csap/apidoc/parent
        version: v1.0
      - name: Store API
        url: /example-store-api/csap/apidoc/parent
        version: v1.0
      - name: Other API
        url: /example-other-api/csap/apidoc/parent
        version: v1.0
```

**How It Works:**

1. Each microservice exposes its own API documentation endpoint
2. Gateway aggregates all service endpoints in the `resources` configuration
3. Frontend UI provides a dropdown to switch between services
4. Users can view all service documentation from a single entry point

**Benefits:**

- ✅ Unified documentation portal for all microservices
- ✅ No need to remember different service URLs
- ✅ Quick switching between service documentation
- ✅ Perfect for Spring Cloud Gateway, Kong, APISIX, Nginx

## 📚 Complete Example

Here's a complete, working example:

### Project Structure
```
src/
├── main/
│   ├── java/
│   │   └── com/example/demo/
│   │       ├── DemoApplication.java
│   │       ├── controller/
│   │       │   └── UserController.java
│   │       ├── model/
│   │       │   └── User.java
│   │       └── service/
│   │           └── UserService.java
│   └── resources/
│       └── application.yml
```

### DemoApplication.java
```java
package com.example.demo;

import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableApidoc(
    apiPackages = {"com.example.demo.controller"},
    modelPackages = {"com.example.demo.model"}
)
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### application.yml (Optional - for DevTools)
```yaml
server:
  port: 8080

spring:
  application:
    name: demo-api

csap:
  apidoc:
    devtool:
      enabled: true  # Enable DevTools UI
```

### UserController.java
```java
package com.example.demo.controller;

import ai.csap.apidoc.annotation.*;
import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Api(tags = "User Management", description = "User CRUD operations")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @ApiOperation(value = "List All Users", description = "Get all users in the system")
    public List<User> listUsers() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "Get User", description = "Get user by ID")
    public User getUser(
            @ApiProperty(value = "User ID", required = true, example = "1")
            @PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ApiOperation(value = "Create User", description = "Create a new user")
    public User createUser(
            @ApiModel(description = "User data")
            @RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "Update User", description = "Update existing user")
    public User updateUser(
            @ApiProperty(value = "User ID", required = true)
            @PathVariable Long id,
            @ApiModel(description = "Updated user data")
            @RequestBody User user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "Delete User", description = "Delete user by ID")
    public void deleteUser(
            @ApiProperty(value = "User ID", required = true)
            @PathVariable Long id) {
        userService.delete(id);
    }
}
```

### User.java
```java
package com.example.demo.model;

import ai.csap.apidoc.annotation.apidoc.ApiModel;
import ai.csap.apidoc.annotation.apidoc.ApiModelProperty;
import jakarta.validation.constraints.*;

@ApiModel(description = "User entity")
public class User {
    
    @ApiModelProperty(value = "User ID", example = "1")
    private Long id;
    
    @ApiModelProperty(value = "Username", required = true, example = "john_doe")
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50)
    private String username;
    
    @ApiModelProperty(value = "Email", required = true, example = "john@example.com")
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email cannot be blank")
    private String email;
    
    @ApiModelProperty(value = "Age", example = "25")
    @Min(value = 18, message = "Age must be at least 18")
    @Max(value = 120, message = "Age must be less than 120")
    private Integer age;
    
    @ApiModelProperty(value = "Phone number", example = "13800138000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number")
    private String phone;
    
    @ApiModelProperty(value = "Account status", example = "ACTIVE")
    private String status;
    
    // Getters and setters
    // ...
}
```

## 🎉 That's It!

You now have a fully functional API documentation system! 

## 🆘 Troubleshooting

### Documentation not showing?
1. Check that `enabled: true` in configuration
2. Verify `base-package` matches your package structure
3. Check application logs for errors

### Can't access the UI?
1. Verify the URL (check for context path)
2. Check if port 8080 is accessible
3. Look for startup banner with the correct URL

### APIs not appearing?
1. Ensure controllers have `@RestController` or `@Controller`
2. Verify controllers are in the scanned package
3. Add `@Api` annotation to controllers

## 📖 Next Steps

- 📚 Read the [Full Documentation](README.md)
- 🔧 Explore [Advanced Configuration](docs/configuration.md)
- 🎨 Customize the [DevTools Interface](csap-framework-apidoc-devtools/devtools/README.md)
- 🚀 Check out the [Product Roadmap](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md)
- 💬 Join our [Community Discussions](https://github.com/csap-ai/csap-framework-apidoc/discussions)

## 🤝 Need Help?

- 📧 Email: support@csap.com
- 🐛 Report Issues: [GitHub Issues](https://github.com/csap-ai/csap-framework-apidoc/issues)
- 💬 Ask Questions: [GitHub Discussions](https://github.com/csap-ai/csap-framework-apidoc/discussions)

Happy documenting! 🎊

