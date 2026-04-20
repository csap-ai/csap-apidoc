# Frequently Asked Questions (FAQ)

## General Questions

### What is CSAP Framework API Doc?

CSAP Framework API Doc is a comprehensive API documentation framework for Spring Boot applications. It automatically generates, manages, and displays API documentation based on your code annotations, providing features like online testing, multi-format export, and a modern web interface.

### How is it different from Swagger/Springfox?

While Swagger/Springfox focuses on OpenAPI specification compliance, CSAP Framework API Doc provides:
- More flexible storage strategies (SQLite, YAML, etc.)
- Built-in DevTools for visual documentation management
- Modern React-based UI
- Enhanced testing capabilities
- Better integration with Chinese development environments
- Postman collection export
- Data masking for sensitive information

### Is it free and open source?

Yes! CSAP Framework API Doc is open source under the Apache License 2.0. You can use it freely in both personal and commercial projects.

## Installation & Setup

### What are the system requirements?

- **Java**: JDK 8 or higher
- **Spring Boot**: 2.x or 3.x
- **Maven**: 3.6+ or Gradle 6+
- **Node.js**: 16+ (only for frontend development)

### How do I add it to my Spring Boot project?

Simply add the Maven dependency:

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

And configure in `application.yml`:

```yaml
csap:
  apidoc:
    enabled: true
    base-package: com.yourcompany.yourproject
```

### Can I use it with Spring Boot 3.x?

Yes! CSAP Framework API Doc supports both Spring Boot 2.x and 3.x.

### Do I need to modify my existing code?

No significant modifications are needed. You just need to add documentation annotations like `@Api`, `@ApiOperation`, and `@ApiModel` to your controllers and models.

## Configuration

### How do I change the documentation UI path?

Configure in `application.yml`:

```yaml
csap:
  apidoc:
    ui-path: /my-api-docs.html
```

### Can I disable API documentation in production?

Yes, use Spring profiles:

```yaml
# application-prod.yml
csap:
  apidoc:
    enabled: false
```

### How do I configure authentication for the documentation?

```yaml
csap:
  apidoc:
    security:
      enabled: true
      username: admin
      password: your-secure-password
```

### Which storage strategy should I choose?

- **SQLite**: Best for most use cases, persistent storage, good performance
- **YAML**: Good for version control, easy to read and edit
- **Standard**: Simple output, good for development/testing

Configure in `application.yml`:

```yaml
csap:
  apidoc:
    storage-strategy: sqlite  # or yaml, standard
```

## Usage

### How do I document a REST API endpoint?

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "User Management", description = "User related APIs")
public class UserController {

    @ApiOperation(value = "Get User", description = "Get user by ID")
    @GetMapping("/{id}")
    public User getUser(
        @ApiProperty(value = "User ID", required = true) 
        @PathVariable Long id) {
        // Implementation
    }
}
```

### How do I document request/response models?

```java
@ApiModel(description = "User entity")
public class User {
    
    @ApiModelProperty(value = "User ID", example = "1001")
    private Long id;
    
    @ApiModelProperty(value = "Username", required = true)
    private String username;
    
    @ApiModelProperty(value = "Email address")
    private String email;
}
```

### Can I test APIs directly from the documentation?

Yes! The DevTools interface provides an online testing feature. Just click the "Test" button on any API endpoint.

### How do I export documentation to Postman?

You can export via the UI or programmatically:

```java
@Autowired
private PostmanConverterService postmanService;

public String exportPostman() {
    CsapDocParentResponse apiDoc = apidocService.getApiDoc();
    return postmanService.convertToPostmanCollection(apiDoc);
}
```

### How do I mask sensitive data in documentation?

Use the `sensitive` and `maskPattern` properties:

```java
@ApiModelProperty(
    value = "Phone number", 
    sensitive = true, 
    maskPattern = "phone"
)
private String mobile;  // Displays as: 130****5678
```

## Troubleshooting

### The documentation UI shows a blank page

**Possible causes:**
1. **Static resources not loaded**: Check browser console for 404 errors
2. **Context path issue**: If your app has a context path, access `/context-path/csap-api.html`
3. **Base package not configured**: Ensure `base-package` in configuration matches your package structure

**Solution:**
```yaml
csap:
  apidoc:
    enabled: true
    base-package: com.yourcompany  # Make sure this is correct
```

### My APIs are not showing up

**Check these items:**
1. Ensure controllers are in the configured `base-package`
2. Verify controllers have `@RestController` or `@Controller` annotation
3. Check if methods have proper mapping annotations (`@GetMapping`, etc.)
4. Look for errors in application logs during startup

### Documentation is not updating after code changes

**Solutions:**
1. Restart the Spring Boot application
2. If using YAML storage, delete and regenerate the YAML files
3. If using SQLite, clear the database or switch storage strategy temporarily

### DevTools port conflict (9528 already in use)

Change the port in configuration:

```yaml
csap:
  apidoc:
    devtools:
      port: 9529  # Use a different port
```

### Getting 404 when accessing /csap-api.html

**Check:**
1. Ensure the dependency is correctly added
2. Verify `enabled: true` in configuration
3. Check application logs for initialization errors
4. Try accessing with the full context path if configured

### Performance issues with large projects

**Optimization tips:**
1. Use more specific `base-package` to scan fewer classes
2. Consider using YAML storage for better startup performance
3. Disable documentation in production environments
4. Use lazy initialization if supported

## Features

### Does it support multiple API versions?

Currently, basic version tracking is available. Full version management with comparison is planned for v3.0.

### Can I customize the UI theme?

The current version supports light mode by default. Dark mode and theme customization are planned for v2.0.

### Does it support GraphQL APIs?

Not yet. GraphQL support is planned for future releases. Current focus is on REST APIs.

### Can multiple developers work on documentation simultaneously?

Basic multi-user support exists. Advanced team collaboration features (comments, real-time sync) are planned for v3.0.

### Does it support API monitoring and analytics?

Basic monitoring is available. Advanced monitoring features (performance analytics, alerting) are planned for v4.0 enterprise edition.

## Integration

### How do I integrate with CI/CD pipelines?

You can export documentation as part of your build:

```bash
# Maven
mvn clean package

# The documentation will be generated during startup
# Export can be automated via API calls
```

### Can I integrate with external tools?

Yes! Export options include:
- **Postman**: Export and import collections
- **OpenAPI/Swagger**: Standard format for tool integration
- **Markdown**: For documentation sites
- **JSON Schema**: For validation and code generation

### Does it work with Spring Cloud microservices?

Yes! Each microservice can have its own documentation. You can also aggregate multiple microservice documentations at the gateway level using the `resources` configuration.

### Can I use it with API Gateway?

Yes, it works well behind API gateways like Spring Cloud Gateway, Zuul, or Kong. Just ensure proper routing is configured.

### How do I aggregate multiple service documentations in an API Gateway?

You can use the `resources` configuration to aggregate multiple microservice documentations in one interface:

**Step 1:** Each microservice exposes its own API documentation endpoint (e.g., `/csap/apidoc/parent`)

**Step 2:** Configure the gateway project's `application.yml`:

```yaml
csap:
  apidoc:
    resources:
      - name: Admin API
        url: /example-admin-api/csap/apidoc/parent
        version: v1.0
      - name: Store API
        url: /example-store-api/csap/apidoc/parent
        version: v1.0
      - name: Order API
        url: /example-order-api/csap/apidoc/parent
        version: v1.0
```

**Step 3:** Access the gateway's documentation UI at `http://gateway:port/csap-api.html`

**Step 4:** Use the service dropdown in the UI to switch between different service documentations

**Benefits:**
- ✅ Single entry point for all service documentation
- ✅ No need to access each service separately
- ✅ Perfect for microservice architectures
- ✅ Works with Spring Cloud Gateway, Kong, APISIX, Nginx

**Example Gateway Routes:**

```yaml
# Spring Cloud Gateway routes
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
```

## Commercial & Licensing

### Is there a paid version?

While the core is open source, we plan to offer:
- **Community Edition** (Free): Open source with core features
- **Professional Edition**: Enhanced features, priority support
- **Enterprise Edition**: Full features, SLA, custom development

See the [Product Roadmap](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md) for details.

### Can I use it in commercial projects?

Yes! The Apache License 2.0 permits commercial use without restrictions.

### Do I need to credit CSAP in my project?

While not required by the license, we appreciate attribution and would love to hear about your use case!

### Is support available?

- **Community Support**: GitHub Issues and Discussions (free)
- **Email Support**: support@csap.com
- **Professional Support**: Available for enterprise users

## Development

### How can I contribute?

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Where can I report bugs?

Report bugs on [GitHub Issues](https://github.com/csap-ai/csap-framework-apidoc/issues).

### How do I request a feature?

Open a feature request on [GitHub Discussions](https://github.com/csap-ai/csap-framework-apidoc/discussions).

### Can I customize/extend the framework?

Absolutely! The framework is designed to be extensible:
- Custom storage strategies
- Custom annotation processors
- Custom UI components
- Custom export formats

## Migration

### Migrating from Swagger/Springfox

1. Replace Swagger dependencies with CSAP Apidoc
2. Update annotations:
   - `@ApiOperation` → works the same
   - `@ApiModel` → works the same  
   - `@ApiModelProperty` → works the same
3. Update configuration in `application.yml`
4. Most Swagger annotations are compatible!

### Upgrading from older versions

See [CHANGELOG.md](CHANGELOG.md) for migration guides between versions.

## Best Practices

### Should I use annotations on interfaces or implementations?

We recommend annotating the implementations (controller classes) for better clarity and maintainability.

### How detailed should my API descriptions be?

Include:
- What the API does
- Required parameters and their purpose
- Expected response format
- Possible error codes
- Usage examples when helpful

### Should I commit generated documentation files?

- **YAML files**: Yes, useful for version control
- **SQLite database**: Generally no, regenerate on each environment
- **Generated HTML/JSON**: No, regenerate as needed

---

## Still Have Questions?

- 📧 Email: support@csap.com
- 💬 GitHub Discussions: [Discussions](https://github.com/csap-ai/csap-framework-apidoc/discussions)
- 🐛 Bug Reports: [Issues](https://github.com/csap-ai/csap-framework-apidoc/issues)
- 📚 Documentation: [Official Docs](https://docs.csap.com)

We're here to help! 🚀

