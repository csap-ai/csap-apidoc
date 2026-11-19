# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Planned for Java 1.5.0 / Go v0.3.0 (the Go side is tracked separately). Both features are
**off by default**; without configuration behavior is identical to 1.4.0.

### Added

- **Team edition service registration** (`csap-apidoc-team-client`, pulled in by
  `csap-apidoc-boot-starter`; Spring Boot 2 and 3, Java 11 bytecode). With
  `csap.apidoc.team.enabled=true` and `csap.apidoc.team.url` the service registers itself
  (`POST {url}/api/open/v1/services/register`, Bearer token) from a background daemon
  thread, repeats the same request as a heartbeat at the interval the server returns
  (10-300 s, default 30 s), and sends a deregister on shutdown. An unreachable team server
  never blocks startup; failures are logged with throttling, `401` / `403` back off to
  300 s, and the token never appears in logs. `service` defaults to
  `csap.apidoc.team.service` -> `csap.apidoc.database.service` -> `spring.application.name`
  -> `default`; `base-url` is inferred from the first non-loopback IPv4, the server port
  and the context path. See `docs/design/team-integration.md`.
- **MCP audit in the database** (`csap.apidoc.mcp.audit.store: memory | sqlite | database`).
  `database` writes audit rows to `{prefix}mcp_audit` in the database-mode database
  (same DataSource, dialect, table prefix and `service`), asynchronously through a bounded
  queue (a full queue drops and counts, never blocks MCP requests), and trims to
  `csap.apidoc.mcp.audit.max-rows` per service every 10 minutes. Create-table scripts for
  MySQL, PostgreSQL, DM, KingbaseES, SQLite and H2 are added to
  `META-INF/csap-apidoc/database/`. Without database mode, or when the table is not
  available, it logs a warning and falls back to `memory`. `persist: true` keeps working
  and means `store: sqlite`. `csap-apidoc-mcp` only has an optional dependency on
  `csap-apidoc-database`.

## [1.4.0] - 2026-11-18

Java 1.4.0 and Go v0.2.0 are released together and share the rule tables, the Redis
data convention, the Mock / JSON Schema rules and the validation golden cases.

### Changed (behavior change)

- **Request validation: rules on optional fields are now enforced when a value is sent.**
  Since 1.2.0 a field that is not required was never validated at all. Now, like the
  Go implementation, an optional field is skipped only when it is not sent (`null` /
  absent); when a value is sent (including an empty string `""`, an empty array or
  object) its rules (`Pattern`, `NotEmpty`, `@Pattern`, `@Size`, ...) run. This can
  **reject requests that used to pass**, e.g. an optional field with a `@Pattern`
  that callers always filled in with a malformed value. To upgrade safely run
  `csap.validation.mode=observe` first and read the violation log, or set
  `csap.validation.optional-field-rules=skip` to restore the old behavior
  (default `validate`). The Java and Go implementations share the golden cases in
  `docs/conformance/validation-cases.json`; the full definition of "value sent" is in
  `docs/design/1.4.0-validation-alignment.md`. `Pattern` matches the whole value
  (an unanchored regex such as `[0-9]+` no longer matches `abc123`).
- Field rules now also show their `description` and `example` in the document
  (previously only the annotation values were used for model fields).

### Added

- **Database mode** (`paramType = ApiStrategyType.DATABASE`, module `csap-apidoc-database`,
  pulled in by the starter): field rules live in the application's own relational database
  (MySQL 5.7.7+ / 8.x, PostgreSQL 12+, DM8 and KingbaseES as a trial, SQLite, H2) through
  its own `DataSource`; only JDBC, no driver bundled; one jar for Spring Boot 2 and 3.
  Config `csap.apidoc.database.*` (`datasource-bean`, `dialect`, `table-prefix`,
  `init-schema`, `poll-interval`, `service`, `read-only`). Each save bumps a global version,
  writes the rule and a history row in one transaction; other instances poll the version
  and regenerate the documents and field rules of the changed classes. Schema SQL per
  dialect ships in the jar (`META-INF/csap-apidoc/database/*.sql`). The tables
  (`{prefix}rule`, `{prefix}history`, `{prefix}meta`, with a `service` column) are shared
  with csap-apidoc-go v0.2.0. Design: `docs/design/database-mode.md`.
- **Redis mode** (`paramType = ApiStrategyType.REDIS`, module `csap-apidoc-redis`, Spring
  Data Redis optional): rules in a Redis Hash, saved atomically by a Lua script that Java and
  Go embed byte for byte; Pub/Sub plus version polling for multi-instance sync; history keeps
  the latest `history-limit` entries. Config `csap.apidoc.redis.*`. Keys use a Cluster hash
  tag (cluster / sentinel / auth not tested). Convention: `docs/design/redis-mode.md`.
- **Rule migration** (module `csap-apidoc-migrate`): YAML directory / SQLite file → database
  or Redis, and database / Redis → YAML, via `csap.apidoc.migrate.from/to/export-to/overwrite/dry-run`
  at startup or the `RuleMigrator` API. Existing rules are not overwritten by default; every
  imported rule is a normal save by `migrate` and can be rolled back. Project field templates
  (`@templates`) are not migrated yet.
- **DevTools "Rule history"** page (`/devtools-ui/rules`) for the database and Redis modes:
  list, side-by-side diff and roll back (`devtools/rules/status|history|rollback`); read-only
  stores refuse with 1007. Page hints are language neutral; backends may return `hint`
  (`docs/design/devtools-hints.md`).
- **API status (in development / stable / deprecated)**: the effective status of an
  endpoint is rule store override > `@ApiOperation(status)` > `@Api(status)` > default.
  It is shown (and filterable) on the document page, exported as `deprecated: true` /
  `x-csap-status` in OpenAPI, prefixed `[过时]` in Postman, and can be changed in
  DevTools (`POST /csap/yaml/setStatus`); the status is stored with the field rules
  (YAML, database, Redis, SQLite) as `status` / `statusNote`. The MCP endpoint does
  not expose deprecated endpoints by default (`csap.apidoc.mcp.expose-deprecated`) and
  puts the status in the tool description. Format: `docs/design/1.4.0-api-status.md`.
- **Field templates**: reusable field definitions (description, example, required,
  validation rules) with built-in templates and project templates stored with the rule
  store (reserved key `@templates` in database / Redis, `field-template/api-field-template.yaml`
  for YAML / SQLite). Apply them from the DevTools field editor (copy, not linked; the
  source template name is recorded as `template`). Format:
  `docs/design/1.4.0-field-library.md`; API: `GET/POST/DELETE /csap/yaml/fieldTemplates`.
- Rule model: optional `example`, `template` and `mock` (any JSON value, stored and passed
  through to the document parameter as-is) on field rules.
- DevTools "Rule history" page for the database and Redis modes (list, diff, roll back).
- **Embedded Mock (`csap-apidoc-mock`, off by default).** With
  `csap.apidoc.mock.enabled=true` the service answers `/csap/mock/{original path}`
  (any HTTP method) with data generated from the documented return structure
  (including the unified result wrapper). Output is reproducible: every field value
  derives from the seed and the field path (`seed` defaults to `csap`; `random`
  for a new seed per request; header `X-CSAP-Mock-Seed` per request). Generation
  order: rule `mock` > `example` > `defaultValue` > enum > `Min`/`Max` > field-name
  heuristics (`email`, `phone`, `id`, ..., switch `csap.apidoc.mock.heuristics`) >
  type default; `Length`/`Size` and array sizes are honoured. Field rules gain an
  optional `mock` key (literal value, or `@int(lo,hi)`, `@oneOf(a,b,c)`, `@uuid`).
  Responses carry `X-CSAP-Mock`; `GET /csap/mock-status` lets the doc page offer a
  one-click "Mock" environment in Try-it-out and tag mock responses. The rules are
  shared with the Go implementation and verified against common golden samples
  (`docs/design/1.4.0-mock-golden`). Pulled in by `csap-apidoc-boot-starter`; one
  jar for Spring Boot 2 and 3.
- **JSON Schema export (2020-12).** `GET /csap/schema.json` (all endpoints, `?download=true`
  to save as a file) and `GET /csap/schema/method?key=` (one endpoint). The request is
  split by location (`path` / `query` / `header` / `form` / `body`); field descriptions,
  required, examples, enums and validation rules map to `description`, `required`,
  `examples`, `enum`, `minLength` / `maxLength`, `pattern`, `minimum` / `maximum`, ...;
  the original rules are kept losslessly in `x-csap-validate`. The doc page's export menu
  gets a "JSON Schema" entry.
- **JSON Schema import (off by default).** `POST /csap/schema/import` with
  `csap.apidoc.schema.import-enabled=true`: turns the description / required / example /
  validation of a schema (typically an exported one, edited) into field rules for fields
  that already exist in the docs. It previews by default; `apply=true` writes through the
  same save path as DevTools (the change history records `schema-import`). It never
  creates fields or changes types.

### Upgrade notes

- **All instances sharing a database / Redis rule store must be upgraded to 1.4.0 together.**
  An older DevTools rewrites a method's rules without the new `status`, `template`,
  `example` and `mock` fields and would drop them.
- SQLite databases get a `status_note` column on first start (automatic).

### Changed

- The "rules inside the jar cannot be saved" message (code 1007) now suggests the database
  and Redis modes.

### Documentation

- New guides: database mode, Redis mode, Mock, JSON Schema, version diff / breaking-change
  detection (`csap-apidoc-diff`, a single binary shipped with csap-apidoc-go that compares two
  `/csap/apidoc` documents of any language), endpoint status and the field library; the
  validation and DevTools pages describe the 1.4.0 behavior.

## [1.3.0] - 2026-11-18

Verified with real MCP clients: Claude Code and opencode (DeepSeek `deepseek-flash`)
listed the tools and called GET tools against the Spring Boot 3 example; calls
without a token were rejected, and calls were audited under the token name.
Cursor was not tested.

### Added

- **Embedded MCP endpoint (`csap-apidoc-mcp`, off by default).** With
  `csap.apidoc.mcp.enabled=true` the service exposes `POST /csap/mcp`
  (MCP Streamable HTTP, JSON responses). Every documented endpoint becomes an
  MCP tool: name, description and input JSON Schema (path / query / header
  parameters and `body`) come from the docs; parameter locations follow the
  Spring MVC mapping. Supports MCP `2026-07-28` (stateless, `server/discover`,
  header validation) and the `initialize`-based `2025-11-25`, `2025-06-18`,
  `2025-03-26`. Pulled in by `csap-apidoc-boot-starter`; one jar for Spring
  Boot 2 and 3, no Servlet API or MCP SDK dependency.
- `tools/call` is forwarded to the endpoint as a loopback HTTP request, so all
  servlet filters (Spring Security etc.) apply and response field filtering
  (1.2.0) applies too. Non-2xx responses come back as `isError: true`.
- Only GET endpoints are exposed by default; write operations, tool names and
  descriptions are configured per endpoint and saved to the rule store
  (`{path}/mcp.yaml`, or the `mcp_setting` / `mcp_tool` tables for SQLite).
- Auth: `token` mode (`Authorization: Bearer`, tokens from configuration,
  fail-closed when none is set) or `passthrough` (forward caller headers to the
  endpoint). `Origin` validation.
- Audit log of tool calls and rejected requests (caller, tool, request,
  argument / result summaries with passwords, tokens etc. masked, status,
  duration): in-memory ring buffer, optionally persisted to SQLite.
- **DevTools "MCP" page** (Chinese / English): choose exposed endpoints,
  rename tools, edit descriptions, switch auth mode, browse the audit log,
  copy client configuration for Claude Code / Cursor.
- `csap-apidoc` agent skill: how to query and debug endpoints through MCP.
- **Stale field-rule check at startup (WARN only).** After the docs are
  generated, `YAML` and `SQL_LITE` field rules are compared with the current
  code: rules for a method that no longer exists (e.g. after a rename) are
  reported with similar existing method names, and request / response field
  paths that are not found in the current structure (e.g. `data.id` after the
  return type became `Result<Page<Item>>`) are listed. One log line per
  method, not repeated when the result is unchanged; the check only reads the
  already-loaded rules and changes neither the docs nor filtering. Disable
  with `csap.apidoc.rule-check.enabled=false`.

### Changed

- **Required flag of simple method parameters follows the rules (behavior
  change).** For parameters like `@RequestParam String id` under the `YAML` /
  `SQL_LITE` strategies, a rule for that parameter now decides `required`
  alone; `@ApiProperty(required = true)` only applies when there is no rule for
  the parameter. 1.2.x showed the OR of rule and annotation in the docs while
  runtime validation only looked at the rule; now the docs and runtime
  validation use the same value. Effects: a rule with `required: false` makes
  the parameter optional even if the annotation says required; a parameter
  with no rule but `@ApiProperty(required = true)` is now registered for
  runtime validation (only matters when `csap.validation.mode` is enabled).
  A `@PathVariable` parameter without a rule is shown as `PATH` under all
  strategies.

### Deprecated

- `@Group`, `@Request`, `@Response` and `@ApiModelProperty.groups` are
  deprecated and will be removed in 2.0. Behavior is unchanged in 1.3.x. To
  choose fields per endpoint, use `YAML` or `SQL_LITE` rules
  (`@EnableApidoc(paramType = ...)`); for fields shown on every endpoint use
  `forceReq` / `forceRep`.
- Method-level `@ApiProperty` / `@ApiPropertys` (never read when generating
  docs) are deprecated and will be removed in 2.0; put `@ApiProperty` on the
  parameter instead.

### Fixed

- DevTools frontend could not be built from source (`autoprefixer` missing
  from `package.json`); the bundled DevTools page is now rebuilt from source.
- Saving rules in DevTools from a packaged jar (`java -jar`) returned HTTP 500
  with a `NullPointerException` (`getResource("")` is `null` inside a fat jar).
  The YAML / JSON strategies now answer with HTTP 200 and an `ApidocResult`
  failure (`code = 1007`) that the DevTools page shows, plus one WARN log line.
  The message says the rule files are inside the jar and lists what does work:
  running from the source tree, or `paramType = SQL_LITE`; the database mode
  (MySQL / PostgreSQL / DM / KingbaseES) is planned for 1.4.0. Reading rules
  from a jar no longer throws either.
- SQLite rules can now be saved from a packaged jar: the database is kept
  outside the jar at `<working dir>/<path>/<fileName>.db` (default
  `./csap-api-doc/apidoc.db`), created on the first DevTools save (from the
  template in the jar if there is one) and read back after a restart. Before,
  saving failed with a `NullPointerException` when no database existed yet,
  and SQLite write errors were only logged while DevTools reported success;
  they now return the same `code = 1007` failure with the file path. The
  SQLite JDBC driver is registered explicitly, so reading rules on the startup
  scan threads of a fat jar no longer fails with "No suitable driver".
- DevTools "save selected controllers" (`/csap/yaml/writeSelect` with class
  names) failed with a `NullPointerException` before writing anything.

## [1.2.0] - 2026-11-17

Runtime response filtering and runtime request validation are new and
both **off by default**; enabling either changes what clients receive, so try
them on a few endpoints first.

### Added

- **`csap-apidoc-runtime`: response field filtering.** Endpoints with
  response rules only return the fields the rules allow, matching what the
  docs page shows. Engines for Jackson, fastjson and fastjson2 (`engine: auto`
  picks the converter the endpoint actually uses); `path` mode (field-path
  whitelist) and `csap` mode (per-type white/black lists, compatible with
  csap-framework); per-endpoint overrides. Pulled in by
  `csap-apidoc-boot-starter`, enabled with
  `csap.apidoc.runtime.filter.enabled=true`. Endpoints without rules are not
  touched; `@JsonView` keeps working.
- **Runtime request validation with an observe mode.** Rules registered while
  generating docs are now actually executed (in 1.1.x nothing ran them).
  `csap.validation.mode`: `off` (default) / `observe` (log + Micrometer
  counter `csap.validation.violations`, request passes) / `enforce`;
  per-endpoint overrides via `csap.validation.endpoints`; failure status via
  `csap.validation.http-status`. The 1.1.x switch `csap.validation.enabled`
  still works (`true` alone means `enforce`). Custom checks can be plugged in
  as `ApiMethodHandle` / `ViolationRecorder` beans.
- Fields that are required but have no rule are checked for emptiness when
  validation is on (code / message from `csap.validation.required-code` /
  `required-message`, default `"400"` / `不能为空`, same as the Go version).
- **`javax.validation` and `jakarta.validation` constraint annotations** are
  both recognised, so the same jar works on Spring Boot 2 and 3. Built-in
  constraints run on the application's Hibernate Validator, including the
  time constraints (`@Past`, `@Future`, ...).
- **Dark mode for the docs page** (light / dark / follow system, follows the
  system by default). Covers the docs page only.
- **`GET /csap/openapi.json`**: OpenAPI 3.0 export of the full document,
  including plain (non-global) controllers; same path as the Go version.

### Fixed

- Spring Boot 2: adding `csap-validation-boot-starter` made startup fail with
  `NoClassDefFoundError` (jakarta types in signatures).
- Spring Boot 3: turning a validation failure into a response failed with
  `NoSuchMethodError` (`ResponseEntity.status(HttpStatus)` no longer exists in
  Spring 6).
- OpenAPI export: `NullPointerException` when `required` was unset; plain
  controllers were not exported; path variables and empty request bodies are
  taken from the scanned data.
- The JSON doc strategy wrote YAML into its `.json` files; it now writes JSON
  and still reads old YAML content.
- SQLite databases created from the 1.1.0 template are migrated automatically
  when opened (existing rows kept).
- `ApiDocService` cleared all field rules before registering them again, so
  requests during a rescan could see no rules; rules are now built first and
  swapped in at once.
- DevTools: saving rules now regenerates that class immediately, and the
  per-class refresh no longer appends a second copy of the class to the doc.
- YAML / SQLite strategy: rebuilding the doc no longer appends the annotation
  rules to the cached YAML rules again on every scan.

### Known issues

- Response filtering still adds some serialization cost on endpoints that
  have rules (endpoints without rules are unaffected). Filtering no longer
  turns on the JSON library's per-property filters; serializers containing
  only the allowed fields are compiled per rule node and type and reused.
  Extra time per response compared with the same response unfiltered
  (100 rows, about 17 KB of JSON, JDK 17, median ratio of 41 interleaved
  rounds in one JVM; typical rules write less JSON, so they can come out
  faster than no filtering):

  | Engine    | No filter | Typical rules | Rules allow all fields | csap mode, typical rules |
  | --------- | --------- | ------------- | ---------------------- | ------------------------ |
  | Jackson   | 29 µs     | 0%            | +31%                   | -15%                     |
  | fastjson  | 29 µs     | -5%           | +24%                   | -22%                     |
  | fastjson2 | 13 µs     | +2%           | +32%                   | +3%                      |

  Endpoints without rules only pay for two `ResponseBodyAdvice` checks
  (under 0.1 µs). Measured with `FilterOverheadBenchmarkTest`
  (`-Dcsap.perf=true -Djacoco.skip=true`) on a busy machine.

  The compiled fast path is skipped, and the slower per-property filters
  are used for the whole response, when the converter has other filters
  configured, or uses ReferenceDetection, WriteClassName, BeanToArray,
  PrettyFormat or the null-as-empty features (fastjson2), or WriteClassName,
  BeanToArray or PrettyFormat (fastjson 1.x). Values written by a custom
  serializer are also filtered the old way. The output is the same either
  way.

  Rules that allow every field still cost 24%-32%: nested and polymorphic
  values are checked so that fields added by a runtime subclass are not
  leaked. If an endpoint should return everything, leave it without rules
  instead.
- The DevTools page has no dark mode yet.
- Overloaded methods with the same name share one set of rules and one
  per-endpoint setting (the key is `ClassName.methodName`), for both
  filtering and validation.
- `Map` values are not filtered by rules: once a `Map` field is allowed, the
  whole value is written.

## [1.1.1] - 2026-11-17

Patch release fixing the defects found after 1.1.0 was published. Upgrading
is recommended for everyone on 1.1.0.

### Fixed

- **Bytecode level.** 1.1.0 was built with JDK 17 and shipped Java 17 class
  files (major 61), so it could not be loaded on Java 11. Every module is now
  compiled with `--release 11`, whatever JDK runs the build.
- **Documentation starter alone works.** With only
  `csap-apidoc-boot-starter` on the classpath, generating docs failed with a
  `NullPointerException` because `IValidateFactory` came from the validation
  starter. A default factory is now provided; `csap-validation-boot-starter`
  is optional again.
- **Docs page is built from source.** The 1.1.0 jar contained an old build of
  `csap-api.html` without environments, auth, history, snapshots or i18n. The
  page is rebuilt, and the release workflow now builds it from source every
  time.
- `GET /csap/apidoc/{name}?key=` no longer corrupts the shared doc cache
  (afterwards the full doc permanently lost methods).
- `flush=true` rescans again; requests during startup wait for the scan to
  finish instead of seeing a half-built document.
- `@EnableApidoc(type = YAML / JSON)` registers the matching strategy (docs
  used to be silently empty). `type = SQL_LITE` now fails at startup with a
  hint to use `paramType = SQL_LITE`; that doc strategy never worked.
- `paramType = SQL_LITE` creates its database from DDL on first use instead of
  relying on a template file that was not shipped.
- Spring Boot 3: `DevtoolsProxyAutoConfiguration` is registered in
  `AutoConfiguration.imports`.
- `@RequestMapping` without `method` is documented as GET/POST/PUT/DELETE;
  methods without a mapping annotation are skipped.
- `ApiAuthorization`: example values of `BEARER_TOKEN` and `BASIC_AUTH` were
  swapped.
- `ApiStatus`, `ModelType` and `CamelCaseType` `resolve()` always returned
  `null`.
- Built-in regexes in `ValidatePatternType` corrected.
- Postman export: double slashes in URLs and an NPE when `required` is unset.
- Configuration metadata: `csap.validation.enabled` default shown correctly.
- Docs page:
  - **Security:** exporting settings no longer writes vault secrets in
    plaintext (encrypted vaults export ciphertext only; plaintext vaults omit
    secrets).
  - Try-it-out uses the proxy URL and timeout from settings, including the
    built-in DevTools proxy protocol.
  - Gateway aggregation builds detail URLs correctly and no longer repeats
    the service prefix.
  - Endpoints without declared headers no longer show a blank detail panel.
  - Headers and auth are prefilled from `@DocGlobalHeader` / `@DocAuth`
    hints.
  - Group/version dropdowns are no longer hard-coded placeholders.

### Changed

- CI runs the test suites (the root pom skips tests by default) and builds
  the examples; JUnit 5 tests actually run now (surefire 2.22.2 with both
  JUnit Platform and TestNG providers).
- Examples: Spring Boot 2 `mainClass` fixed; configuration keys that do not
  exist were removed.

### Known issues

- Runtime response-field filtering and request validation are still only
  recorded, not enforced, when the open-source modules are used on their
  own. Planned for 1.2.0.

## [1.1.0] - 2026-11-17

First version published to Maven Central.

### Known issues

Fixed in 1.1.1:

- The jars are Java 17 bytecode (built with JDK 17), so 1.1.0 needs Java 17
  or later.
- The docs page packed in `csap-apidoc-ui-1.1.0.jar` is still the 1.0.0
  build. Environments, global headers, auth, request history and replay,
  named snapshots and i18n exist in the source but are not in the jar.
- With only `csap-apidoc-boot-starter` on the classpath, doc generation
  fails with a `NullPointerException`; add
  `csap-validation-boot-starter` as a workaround.
- `@DocGlobalHeader` / `@DocAuth` hints are returned by the doc API
  (`globalHeaderHints`, `authHint`), but the docs page does not read them
  yet, so nothing is pre-filled.

Planned for 1.2.0:

- Runtime response-field filtering and request validation only record
  rules in this release; the code that applies them is not part of the
  open-source modules. They return as a standalone module in 1.2.0.

### Changed

- **Single mainline & version.** This repository (`csap-ai/csap-apidoc`,
  branch `main`) is now the only development line; the former gitee
  repository becomes a read-only mirror and its old branches are kept as
  `archive/*` tags. `1.0.0` keeps meaning the build that has been running
  in production since 2025-12 (identical to the initial public commit);
  everything below ships as `1.1.0`, the first version published to
  Maven Central.
- Docs: Maven coordinates corrected to the real artifacts
  (`ai.csap.apidoc.boot:csap-apidoc-boot-starter`,
  `ai.csap.apidoc.boot:csap-validation-boot-starter`,
  `ai.csap.apidoc:csap-apidoc-devtools`, `ai.csap.apidoc:csap-apidoc-ui`);
  the old `com.csap.framework*` / `csap-framework-apidoc-*` names in the
  READMEs and guides never matched what the build produces. Repository
  links point to `github.com/csap-ai/csap-apidoc`.

### Added

- **Maven Central publishing infrastructure** — this project now
  publishes **exclusively** to Maven Central via the Sonatype Central
  Portal. The parent `pom.xml` exposes a single `release` profile that
  wires `maven-source-plugin`, `maven-javadoc-plugin` (bumped 2.10.4
  → 3.6.3), `maven-gpg-plugin`, and `central-publishing-maven-plugin
  0.8.0` with `autoPublish=true` / `waitUntil=published`, so
  `mvn -P release deploy` uploads the full reactor to the Portal and
  blocks until the bundle is actually live. Retired in the same pass:
  the legacy `nexus-staging-maven-plugin` (which targeted the
  April-2025-sunset OSSRH pipeline) and the corporate-Nexus
  `<distributionManagement>` (private publishing is no longer a
  supported target for this OSS artifact). A plain `mvn deploy` without
  `-P release` is intentionally a no-op. New
  `.github/workflows/release.yml` exposes a `workflow_dispatch` entry
  point (with a `dry_run` toggle) that reads `CENTRAL_USERNAME` /
  `CENTRAL_PASSWORD` / `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` secrets,
  validates the pom version matches the requested release, runs
  `mvn -P release clean deploy`, and pushes a `vX.Y.Z` tag on
  success. Operational playbook landed in
  `docs/guides/PUBLISHING.md` (MkDocs nav → 发布 / Release).
- **M9.3 — Mock server design doc** (`docs/features/m93-mock-server.md`):
  approved spec for the built-in mock server — a new `csap-apidoc-mock`
  Spring Boot starter that serves apidoc-schema-derived responses via a
  4-step synthesis pipeline (snapshot → schema example → static YAML rule
  → default 200). Opt-in via `csap.apidoc.mock.enabled=true`; mounted at
  `/mock/**` on the same JVM. Doc covers module positioning, synthesis
  algorithm, matching, risks, and a 4-PR sub-task breakdown (M9.3a
  scaffold + schema synthesis → M9.3b rule overrides → M9.3c frontend
  badge → M9.3d example app). Landed in MkDocs nav under 特性.
- **M9.2 — Request snapshots** (`csap-apidoc-ui`): new named, never-evicted,
  user-curated pre-request templates — complements M9.1 history which is an
  ephemeral ring buffer. New `Snapshots` button (`BookOutlined`) in the
  Try-it-out URL bar opens a drawer with:
  * **Save current request** (name required, description optional) → stored
    in `csap-apidoc:tryItOutSnapshots` (schema v1, capped at 500 rows).
  * **Load** re-seeds the panel form (same no-stale-credential guarantee as
    M9.1 Replay: outbound headers are NOT restored; the panel's
    `enrichRequest` re-runs fresh on the next Send).
  * **Rename / edit description**, per-row + bulk delete.
  * **Export** downloads the full collection as JSON (filename stamped
    `csap-apidoc-snapshots-YYYY-MM-DD.json`).
  * **Import** accepts the same JSON (merge vs replace toggle) —
    id-collisions in merge mode auto-regenerate ids so nothing silently
    clobbers local data. Partially corrupt payloads import the valid rows
    and report `skipped` count.
  * 35 new `tryoutSnapshots.*` i18n keys per locale; 19 new unit tests
    (CRUD, rename-while-blank-name, schema-version guard, corrupt-row
    recovery, export↔import round-trip, id-collision safety, MAX_SNAPSHOTS
    cap, subscriber fan-out).
- **M9.1 — Try-it-out request history** (`csap-apidoc-ui`): the endpoint
  workbench now auto-records the last 50 outbound requests to a
  localStorage-backed ring buffer (`csap-apidoc:tryItOutHistory`), surfaced
  through a new `History` button in the URL bar that opens a drawer with
  per-row `Replay` (re-seeds method/url/headers/query/body into the form —
  enrichment is re-run fresh so env/auth/global-header rotation can't leak
  stale credentials) and per-row `Remove` / bulk `Clear all`. Storage is
  schema-versioned (v1), corruption-tolerant (bad rows are dropped on
  read, the rest survive), and never persists response *bodies* — only a
  summary (status/latency/byteLength or failure reason/message) — to
  avoid sensitive-data footguns and the 5 MB quota cliff. 18 new
  `tryoutHistory.*` i18n keys in both zh-CN and en-US; 12 new unit tests
  for the store (eviction, corruption recovery, subscriber fan-out,
  schema-version guard). `TryItOutHistoryDrawer` is passive — it never
  mutates panel state directly, it just hands the entry back via
  `onReplay(entry)` so the panel retains single-source-of-truth over
  method/url/headers/query/body.

### Changed

- **i18n prune (M8.3)** (`csap-apidoc-ui`): dropped 19 orphaned locale keys
  that had no call site — `header.export.failed` / `layout.export.failed`
  (superseded by their `.failedRetry` replacements), `lang.zh` / `lang.en`
  (`LanguageSwitcher` uses hardcoded autonyms `中文` / `English` by i18n
  convention), `layout.params.typeSuffix`, plus a 14-key `common.*`
  utility bag that was declared but never wired. Zero behaviour change;
  both locale files shrink from 360 → 341 keys. `i18n.coverage.test.ts`
  gains a new "every locale key is either directly referenced or under a
  known dynamic prefix" assertion so regressions can't silently
  re-introduce dead keys.

### Added

- **M7.1 — SQLite hint parity** (`csap-apidoc-sqlite`):
  `SqliteApidocStrategy.load()` now reads two new optional tables —
  `api_method_global_header_hint` (multi-row, ordered) and
  `api_method_auth_hint` (1:1) — and binds them onto
  `CsapDocMethod.globalHeaderHints` / `CsapDocMethod.authHint`.
  The loader feature-detects both tables via `sqlite_master`, so legacy
  databases without the new schema continue to load unchanged. New
  `SqliteHintsTest` mirrors `YamlHintsTest` for parity. Closes the M7
  three-source matrix (annotation / YAML / SQLite).
- **i18n integrity (M8.2)**: New `csap-apidoc-ui/src/i18n/i18n.coverage.test.ts`
  scans every `src/**/*.{ts,tsx}` for literal `t('...')` keys and asserts
  each is present in BOTH `zh-CN.json` and `en-US.json`. Six known dynamic
  prefixes (`auth.type.`, `headers.add.`, `headers.empty.`,
  `layout.params.type.`, `mpModal.title.`, `mpModal.ok.`) are explicitly
  allow-listed and verified to have at least one matching key per locale,
  so a typo in the prefix itself still fails CI.
- **Docs**: GitHub Pages site (`mkdocs-material`) wired up. New `mkdocs.yml`,
  `docs/index.md` landing page, pinned `requirements-docs.txt`, and a
  `Docs (GitHub Pages)` workflow that auto-deploys on `main` whenever
  `docs/**`, `mkdocs.yml`, or its requirements change.

### Fixed

- Parent `pom.xml`: `maven-surefire-plugin` `<skipTests>` is now
  parameterised (`${skipTests}` with default `true`), so
  `mvn ... -DskipTests=false` actually re-enables the suite locally —
  the historical hard-coded `true` silently swallowed CLI overrides
  even when CI requested them.

## [1.0.0] - 2025-10-20

First public release. Baseline feature set:

### Added
- Complete API documentation generation framework for Spring Boot 2.x / 3.x
- React 18 + TypeScript + Vite frontend (csap-apidoc-ui)
- Ant Design 5 UI components with dark / light theme
- DevTools for API management (csap-apidoc-devtools)
- Postman collection converter service
- Multiple storage strategies (SQLite, YAML, Standard)
- JSR-303 validation integration
- Data masking for sensitive fields
- Online API testing capability (Try-it-out)
- Multi-format export (OpenAPI, Swagger, Markdown)
- Comprehensive annotation system (`@DocApi`, `@DocField`, `@DocGlobalHeader`, `@DocAuth`, …)
- Gateway aggregation support for micro-service deployments

### Documentation
- Comprehensive README in Chinese and English
- Postman converter documentation
- Product roadmap and feature planning
- Migration guides for TypeScript

---

## Upcoming Features

Planned releases (1.3.0 documentation-as-MCP, 1.4.0 mock / JSON Schema / field
library / version management, and the team edition) are tracked in the
[roadmap](docs/ROADMAP.md).

---

## Migration Guides

### Upgrading to 1.2.0

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

- If you set `csap.validation.enabled: true` on 1.1.x, 1.2.0 starts to
  **enforce** the rules (nothing ran them before). Switch to
  `csap.validation.mode: observe` first to see what would be rejected.
- Response field filtering is off by default; turn it on with
  `csap.apidoc.runtime.filter.enabled=true`, preferably on a few endpoints
  first (`csap.apidoc.runtime.filter.endpoints`).
- Do not use 1.1.0 (its jars are Java 17 bytecode); 1.1.1 and later target Java 11.

---

For planned features, see the [roadmap](docs/ROADMAP.md).
