# CSAP Framework API Doc

<div align="center">

[中文](README.md) | [English](README.en.md)

[![Maven Central](https://img.shields.io/maven-central/v/com.csap/csap-apidoc-core)](https://central.sonatype.com/artifact/com.csap/csap-apidoc-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://github.com/csap-ai/csap-framework-apidoc/actions/workflows/ci.yml/badge.svg)](https://github.com/csap-ai/csap-framework-apidoc/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)

**Contract-first API documentation and validation framework for Spring Boot applications**

</div>

---

CSAP Apidoc scans your Spring MVC annotations at startup, generates a live API reference with a built-in testing UI, and exports to OpenAPI, Postman, Markdown, and more -- all with a single `@EnableApidoc` annotation.

## Key Features

- **Zero Intrusion** -- annotation-driven; no changes to your existing controllers
- **Auto Generation** -- builds API docs from `@RequestMapping`, `@PathVariable`, `@RequestBody`, etc.
- **Modern UI** -- React 18 + TypeScript + Ant Design 5 documentation interface
- **Online Testing** -- built-in HTTP client, no external tools required
- **Multi-Format Export** -- OpenAPI 3.0, Postman Collection, Markdown, JSON Schema, TypeScript types
- **Validation Display** -- renders JSR-303 constraints (`@NotNull`, `@Size`, `@Email`, ...) in the docs
- **Pluggable Storage** -- Annotation (default), SQLite, YAML strategies for parameter persistence
- **DevTools** -- visual editor for parameter configuration, field management, and data masking
- **Gateway Aggregation** -- aggregate docs from multiple microservices behind a single gateway UI

## Quick Start

### Requirements

| Dependency | Version |
|---|---|
| JDK | 8+ (17+ recommended) |
| Maven | 3.6+ |
| Spring Boot | 2.x or 3.x |

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

### 2. Enable on your application class

```java
@SpringBootApplication
@EnableApidoc("com.yourcompany.yourproject")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. Annotate your controllers

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "User Management", description = "User CRUD operations")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation(value = "Get User", description = "Fetch a user by ID")
    public Response<User> getUser(@PathVariable Long id) {
        return Response.success(userService.findById(id));
    }

    @PostMapping
    @ApiOperation(value = "Create User", description = "Create a new user")
    public Response<User> createUser(@Valid @RequestBody UserCreateRequest body) {
        return Response.success(userService.create(body));
    }
}
```

### 4. Open the docs

Start your application and visit:

| URL | Description |
|---|---|
| `http://localhost:8080/csap-api.html` | API documentation UI |
| `http://localhost:8080/api/csap/doc` | Raw API data endpoint |
| `http://localhost:8080/csap/openapi/postman` | Postman Collection export |

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Spring Boot Application                  │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  ┌────────────┐   ┌──────────────┐   ┌───────────────┐  │
│  │ Controller │──▶│  Apidoc Core │──▶│   Storage     │  │
│  │   Layer    │   │   Scanner    │   │   Strategy    │  │
│  └────────────┘   └──────┬───────┘   └───────┬───────┘  │
│                          │                     │          │
│                          ▼                     ▼          │
│                   ┌──────────────┐   ┌───────────────┐  │
│                   │  Annotation  │   │ SQLite / YAML │  │
│                   │  Processor   │   │ / Standard    │  │
│                   └──────────────┘   └───────────────┘  │
│                                                            │
│  ┌────────────┐   ┌──────────────┐   ┌───────────────┐  │
│  │  REST API  │──▶│   DevTools   │──▶│   Web UI      │  │
│  │  Endpoints │   │   Service    │   │   (React 18)  │  │
│  └────────────┘   └──────────────┘   └───────────────┘  │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

## Modules

### Core

| Module | Description |
|---|---|
| `csap-framework-apidoc-annotation` | `@Api`, `@ApiOperation`, `@ApiModel`, `@ApiProperty` annotations |
| `csap-framework-apidoc-common` | Shared utilities, constants, and base models |
| `csap-framework-apidoc-core` | Document scanning, parsing, and generation engine |
| `csap-framework-apidoc-boot-starter` | Spring Boot auto-configuration starter |

### Extensions

| Module | Description |
|---|---|
| `csap-framework-apidoc-strategy` | Storage strategy abstraction layer |
| `csap-framework-apidoc-sqlite` | SQLite-based document persistence |
| `csap-framework-apidoc-yaml` | YAML file-based document persistence |
| `csap-framework-apidoc-standard` | Standard-format document output |
| `csap-framework-apidoc-devtools` | Visual configuration and management interface |
| `csap-framework-apidoc-web` | React-based documentation UI |
| `csap-framework-validation-core` | JSR-303 validation rule integration |

## Configuration

### Parameter Strategy

Choose how parameter metadata is stored via `paramType`:

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.ANNOTATION   // default
)
```

| Strategy | Best For | Key Trait |
|---|---|---|
| **ANNOTATION** (default) | Most projects | Type-safe, lives in source code |
| **SQL_LITE** | Dynamic parameter management | Visual config via DevTools |
| **YAML** | Version-controlled docs | Human-readable, easy to review |

### Multi-Package Scanning

```java
@EnableApidoc(
    apiPackages   = {"com.example.controller"},
    enumPackages  = {"com.example.enums"},
    modelPackages = {"com.example.model"},
    showChildPackageFlag = true
)
```

### DevTools

Add the optional DevTools dependency for a visual editor:

```xml
<dependency>
    <groupId>com.csap.framework</groupId>
    <artifactId>csap-framework-apidoc-devtools</artifactId>
    <version>1.0.3</version>
    <scope>provided</scope>
</dependency>
```

Then visit `http://localhost:8080/csap-api-devtools.html` after startup.

### Gateway Aggregation

Aggregate API docs from multiple microservices behind a gateway:

```yaml
csap:
  apidoc:
    resources:
      - name: Admin API
        url: /admin-service/csap/apidoc/parent
        version: v1.0
      - name: Store API
        url: /store-service/csap/apidoc/parent
        version: v1.0
```

### Environment Variable Support

`path` and `fileName` accept Spring Boot placeholder syntax for per-environment overrides:

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path     = "${csap.apidoc.data.path:/tmp/apidoc}",
    fileName = "${csap.apidoc.db.file:${spring.application.name}}"
)
```

## Core Annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@Api` | Class | Define an API group on a controller |
| `@ApiOperation` | Method | Describe an API endpoint |
| `@ApiModel` | Class | Describe a data model / DTO |
| `@ApiModelProperty` | Field | Describe a model field (type, example, required, sensitive) |
| `@ApiProperty` | Method | Supplement parameter docs for a method |
| `@ApiPropertys` | Method | Define docs for multiple parameters at once |
| `@EnumValue` | Field | Mark the stored code field of an enum |
| `@EnumMessage` | Field | Mark the display label field of an enum |

## Documentation

| Resource | Description |
|---|---|
| [Quick Start Guide](QUICK_START.md) | 5-minute integration walkthrough |
| [Annotation Reference](ANNOTATION_REFERENCE.md) | Complete annotation API |
| [Gateway Aggregation](GATEWAY-AGGREGATION.md) | Multi-service documentation setup |
| [FAQ](FAQ.md) | 60+ frequently asked questions |
| [Architecture](ARCHITECTURE.md) | System design and internals |
| [Postman Converter](csap-framework-apidoc-core/README-Postman.md) | Postman export details |

## Examples

Two example projects are provided:

- **[Spring Boot 2.x Example](example/example-spring-boot2)** -- `javax.servlet`, JDK 8/11
- **[Spring Boot 3.x Example](example/example-spring-boot3)** -- `jakarta.servlet`, JDK 17+
- [Annotation Guide](example/example-spring-boot3/ANNOTATIONS.md) -- correct usage patterns
- [Strategy Comparison](example/example-spring-boot3/STRATEGIES.md) -- ANNOTATION vs SQLite vs YAML

## Contributing

We welcome contributions of all kinds -- bug reports, feature requests, documentation improvements, and code.

Please read our **[Contributing Guide](CONTRIBUTING.md)** before submitting a pull request.

Quick overview:

1. Fork the repo and create a branch from `main`
2. Follow the existing code style ([style guide](docs/development/code-style/CODE_STYLE_README.md))
3. Add tests for new functionality
4. Ensure `mvn test` passes
5. Open a Pull Request

See also: [Security Policy](SECURITY.md)

## Roadmap

### Completed

- Core documentation generation
- Multiple annotation support (`@Api`, `@ApiOperation`, `@ApiModel`, `@ApiProperty`, ...)
- DevTools visual editor
- React 18 frontend
- Postman Collection export
- Pluggable storage strategies (Annotation, SQLite, YAML)
- JSR-303 validation integration
- Sensitive data masking

### In Progress

- Enhanced online API testing
- Mock data generation
- JSON Schema import/export
- Field library and templates

### Planned

- Version management and change tracking
- Team collaboration features
- API lifecycle management
- Internationalization (i18n) and dark mode

Full roadmap: [Product Roadmap](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md)

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Part of CSAP

CSAP Framework Apidoc is the open-source contract layer of the **[CSAP Platform](https://github.com/csap-ai/csap-platform)** (Contract-driven System Analysis Platform). CSAP ingests API contracts and SkyWalking traces to surface AI-powered runtime insights, automated regression detection, and intelligent upgrade planning.

- [CSAP Platform](https://github.com/csap-ai/csap-platform) -- full platform repository
- [csap.ai](https://csap.ai) -- project website

## Contact

- **Issues**: [GitHub Issues](https://github.com/csap-ai/csap-framework-apidoc/issues)
- **Discussions**: [GitHub Discussions](https://github.com/csap-ai/csap-framework-apidoc/discussions)
- **Email**: support@csap.ai

---

<div align="center">

If this project is useful to you, please consider giving it a star.

Made with care by the CSAP Team

</div>
