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
| **Java** | ✅ Released [1.4.0](https://central.sonatype.com/namespace/ai.csap.apidoc) | Spring Boot 2.x / 3.x |
| **Go** | ✅ Released [v0.2.0](https://pkg.go.dev/github.com/csap-ai/csap-apidoc/csap-apidoc-go) (`go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.2.0`) | gin, net/http |
| **Rust** | 📋 Planned | axum, actix-web |
| More | 📋 Coming | see the [roadmap](docs/ROADMAP.md) |

## What you get today

These work in both Java and Go (see the [Go guide](https://apidoc.csap.ai/go/) for Go's filtering and validation); items marked "Java" exist only in the Java version for now.

- **Runtime docs**: docs are generated from code at startup (Java scans annotations, Go reads routes and struct tags); open `/csap-api.html`.
- **Field rules**: annotations (Java), struct tags (Go), YAML files or SQLite / SQL decide which request parameters and response fields each endpoint shows; one model can show different fields in different endpoints. Since 1.4.0 rules can also be stored in a **database** (MySQL / PostgreSQL / DM / KingbaseES, with multi-instance sync, change history and rollback; Java and Go share the same tables; DM and KingbaseES are experimental) or in **Redis**, see [Database mode](https://apidoc.csap.ai/guide/database-mode) and [Redis mode](https://apidoc.csap.ai/guide/redis-mode) (Chinese).
- **Try it out**: send requests from the docs page with environments, global headers, auth, request history and replay, and named snapshots you can import and export.
- **Export**: OpenAPI 3.0, Postman v2.1 and Markdown from the docs page, or copy as TypeScript types.
- **DevTools**: in development, pick fields and configure validation rules in a UI that writes back to YAML.
- **Gateway aggregation** (Java): browse several services' docs from one page.
- **Response field filtering** (Java since 1.2.0, off by default; in Go, endpoints with response rules are filtered by default): endpoints with rules return only the fields the docs show; Jackson, fastjson and fastjson2. See [Response field filtering](https://apidoc.csap.ai/guide/runtime-filter).
- **Request validation** (Java since 1.2.0, off by default; off by default in Go too): off / observe / enforce modes with per-endpoint overrides. See [Validation](https://apidoc.csap.ai/guide/validation).
- **Mock, JSON Schema, field library, endpoint status, API diff** (since 1.4.0): reproducible mock data generated from the docs, JSON Schema export / import, field templates, deprecated / stable status, and a Go CLI (`csap-apidoc-diff`) that detects breaking changes in CI. See the [guides](https://apidoc.csap.ai/guide/mock) (Chinese).
- **Docs as MCP** (Java since 1.3.0, off by default): the service embeds `/csap/mcp`; every documented endpoint becomes a tool AI agents can call, GET only by default, with token auth and audit. See [MCP](https://apidoc.csap.ai/guide/mcp).

## Quick start (Java)

**1. Add the dependency**:

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.4.0</version>
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

## Quick start (Go)

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.2.0
```

```go
doc := apidoc.New(apidoc.Options{})
r := gin.New()
g := ginadapter.Mount(r, doc) // serves /csap/apidoc*, /csap/openapi*, /csap-api.html
g.GET("/api/v1/users/:id",
    apidoc.Op("example.UserController", "get").Summary("Get a user").Req(GetUserReq{}).Resp(User{}),
    getUser)
r.Run(":8080")
```

Then open <http://localhost:8080/csap-api.html>. `net/http`, struct tags, response filtering, validation and the admin console are covered in the [Go guide](https://apidoc.csap.ai/go/) (Chinese) and [csap-apidoc-go/README.md](csap-apidoc-go/README.md).

## Documentation

Full documentation (currently in Chinese) lives at **<https://apidoc.csap.ai>**. See also the [changelog](CHANGELOG.md) and the [roadmap](docs/ROADMAP.md).

## Modules (Java)

The Go version is a single Go module, `github.com/csap-ai/csap-apidoc/csap-apidoc-go`, see above.

| Coordinates | Purpose |
|---|---|
| `ai.csap.apidoc.boot:csap-apidoc-boot-starter` | Spring Boot starter: core, docs page, YAML/SQLite strategies, DevTools, response field filtering |
| `ai.csap.apidoc.boot:csap-validation-boot-starter` | Validation starter, optional: add it if you need runtime request validation, its validation exception handler or `ValidationProperties` |
| `ai.csap.apidoc:csap-apidoc-annotation` | Annotations |
| `ai.csap.apidoc:csap-apidoc-core` | Scanning and doc generation |
| `ai.csap.apidoc:csap-apidoc-ui` | Docs page |
| `ai.csap.apidoc:csap-apidoc-devtools` | DevTools |
| `ai.csap.apidoc:csap-apidoc-runtime` | Response field filtering (since 1.2.0) |
| `ai.csap.apidoc:csap-apidoc-yaml` / `csap-apidoc-sqlite` / `csap-apidoc-standard` | Rule storage strategies |
| `ai.csap.apidoc:csap-validation-core` | Validation engine |

Java modules live at the repository root, the Go implementation in `csap-apidoc-go/`, and the website in `website/`.

## AI coding assistants

The repository ships a [`csap-apidoc` skill](skills/csap-apidoc/SKILL.md). Install it in Claude Code or any assistant that supports Agent Skills and it will know how to integrate and maintain CSAP Apidoc in Java and Go projects; see the [install guide](https://apidoc.csap.ai/guide/ai-skill) (Chinese).

## Contributing

Issues and pull requests are welcome; please read [CONTRIBUTING.md](CONTRIBUTING.md) first. Report security issues privately as described in [SECURITY.md](SECURITY.md), not in public issues.

## License

[Apache License 2.0](LICENSE)
