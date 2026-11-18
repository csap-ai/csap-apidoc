<div align="center">

# CSAP Apidoc

**Runtime API documentation, for many languages**

Docs generated when your service starts · per-endpoint control of visible fields · try requests right in the docs page

[![Maven Central](https://img.shields.io/maven-central/v/ai.csap.apidoc.boot/csap-apidoc-boot-starter?label=Maven%20Central)](https://central.sonatype.com/namespace/ai.csap.apidoc)
[![CI](https://github.com/csap-ai/csap-apidoc/actions/workflows/ci.yml/badge.svg)](https://github.com/csap-ai/csap-apidoc/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Website](https://img.shields.io/badge/docs-apidoc.csap.ai-4f46e5)](https://apidoc.csap.ai)

[简体中文](README.md) | English

</div>

## What it is

CSAP Apidoc builds your API documentation from the code at runtime: no separate doc files, no extra doc server. Annotate controllers and models (or write YAML rules), start the service, and `/csap-api.html` shows the current docs, where you can also call the endpoints.

The doc protocol, the rule file format and the docs page are shared across languages, so services written in different languages use the same docs page and the same rule files.

## Language support

| Language | Status | Frameworks |
|---|---|---|
| **Java** | ✅ Released [1.2.0](https://central.sonatype.com/namespace/ai.csap.apidoc) | Spring Boot 2.x / 3.x |
| **Go** | ✅ Released [v0.1.1](https://pkg.go.dev/github.com/csap-ai/csap-apidoc/csap-apidoc-go) (`go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.1.1`) | gin, net/http |
| **Rust** | 📋 Planned | axum, actix-web |
| More | 📋 Coming | see the [roadmap](docs/ROADMAP.md) |

## What you get today (Java)

- **Runtime docs**: annotations are scanned at startup; open `/csap-api.html`.
- **Field rules**: annotations, YAML files or SQLite decide which request parameters and response fields each endpoint shows; one model can show different fields in different endpoints.
- **Try it out**: send requests from the docs page with environments, global headers, auth, request history and replay, and named snapshots you can import and export.
- **Export**: OpenAPI 3.0, Postman v2.1 and Markdown from the docs page, or copy as TypeScript types.
- **DevTools**: in development, pick fields and configure validation rules in a UI that writes back to YAML.
- **Gateway aggregation**: browse several services' docs from one page.
- **Response field filtering** (since 1.2.0, off by default): endpoints with rules return only the fields the docs show; Jackson, fastjson and fastjson2. See [Response field filtering](https://apidoc.csap.ai/guide/runtime-filter).
- **Request validation** (since 1.2.0, off by default): off / observe / enforce modes with per-endpoint overrides. See [Validation](https://apidoc.csap.ai/guide/validation).

## Quick start (Java)

**1. Add the dependency**:

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

**2. Enable it** with the packages to scan:

```java
@SpringBootApplication
@EnableApidoc("com.example.demo")
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**3. Annotate**:

```java
@RestController
@RequestMapping("/api/users")
@Api(value = "Users")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation("Get a user")
    public User get(@ApiProperty(value = "User ID", required = true) @PathVariable Long id) { ... }
}

@ApiModel(description = "User")
public class User {
    @ApiModelProperty(value = "User ID", forceRep = true)
    private Long id;
    @ApiModelProperty(value = "User name", forceReq = true, forceRep = true)
    private String name;
}
```

**4. Start** the service and open <http://localhost:8080/csap-api.html>.

Why `forceReq` / `forceRep`, and how to choose fields per endpoint: see [Quick start](https://apidoc.csap.ai/guide/quick-start) (Chinese).

## Documentation

Full documentation (currently in Chinese) lives at **<https://apidoc.csap.ai>**. See also the [changelog](CHANGELOG.md) and the [roadmap](docs/ROADMAP.md).

## Modules

| Coordinates | Purpose |
|---|---|
| `ai.csap.apidoc.boot:csap-apidoc-boot-starter` | Spring Boot starter: core, docs page, YAML/SQLite strategies, DevTools |
| `ai.csap.apidoc.boot:csap-validation-boot-starter` | Validation starter, optional: add it only if you need its validation exception handler or `ValidationProperties` |
| `ai.csap.apidoc:csap-apidoc-annotation` | Annotations |
| `ai.csap.apidoc:csap-apidoc-core` | Scanning and doc generation |
| `ai.csap.apidoc:csap-apidoc-ui` | Docs page |
| `ai.csap.apidoc:csap-apidoc-devtools` | DevTools |
| `ai.csap.apidoc:csap-apidoc-yaml` / `csap-apidoc-sqlite` / `csap-apidoc-standard` | Rule storage strategies |
| `ai.csap.apidoc:csap-validation-core` | Validation engine |

Java modules live at the repository root, the Go implementation in `csap-apidoc-go/`, and the website in `website/`.

## AI coding assistants

The repository ships a [`csap-apidoc` skill](skills/csap-apidoc/SKILL.md). Install it in Claude Code or any assistant that supports Agent Skills and it will know how to integrate and maintain CSAP Apidoc; see the [install guide](https://apidoc.csap.ai/guide/ai-skill) (Chinese).

## Contributing

Issues and pull requests are welcome; please read [CONTRIBUTING.md](CONTRIBUTING.md) first. Report security issues privately as described in [SECURITY.md](SECURITY.md), not in public issues.

## License

[Apache License 2.0](LICENSE)
