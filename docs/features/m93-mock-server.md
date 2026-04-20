# Feature: Built-in Mock Server (M9.3)

> Branch (proposed): `feat/m93-mock-server-design` (docs only) → then `feat/m93a-*` per sub-task.
> Status: **DESIGN — not yet implemented**
> Owner: yangchengfu
> Last updated: 2026-04-20
> Predecessors: M9.1 ([#7](https://github.com/csap-ai/csap-apidoc/pull/7)) history drawer, M9.2 ([#8](https://github.com/csap-ai/csap-apidoc/pull/8)) named snapshots.

This document is the working spec for the third leg of the "Try-it-out
workbench" triad. M9.1 and M9.2 gave users memory of *what they've sent*;
M9.3 gives them the ability to exercise the UI **without a live backend at
all** by synthesising responses from the existing apidoc metadata (or
from an M9.2 snapshot).

---

## 1. Problem & Motivation

Today, every Try-it-out send hits a real upstream service:

- **Frontend devs** who arrive before the backend is ready have no way to
  exercise the UI. They either mock by hand in DevTools or wait.
- **QA** can't deterministically reproduce a `503`-on-3rd-call flake or an
  edge-case payload (e.g. `orderStatus: "PENDING_SETTLEMENT"` for a discount
  coupon path) unless the real backend is already in that state.
- **Contract reviews** want to confirm "does the UI render this schema?"
  but until today that required coordinating a backend deploy with the exact
  shape.

M9.3 answers all three: a drop-in mock server that serves the shapes
*already documented in apidoc*, with optional per-route overrides driven by
M9.2 snapshots.

## 2. Goals & Non-goals

### Goals

1. Any service that already uses `csap-apidoc-annotation` or YAML or SQLite
   registration can flip a **single property** and serve realistic
   responses for every documented endpoint — no extra code.
2. Users with M9.2 snapshots can point a snapshot at "mock mode" and have
   the mock return the snapshot's last-known response shape (if we capture
   it — §9).
3. The mock is **transparent** to the UI: Try-it-out just sees a different
   baseURL (`http://localhost:PORT/mock/...`); M5's auth/header/env pipeline
   is unchanged.
4. Zero new runtime dependencies outside the existing Spring Boot /
   Jackson stack. No WireMock, no MockServer, no third-party JVM library.

### Non-goals (separate branches)

- **Stateful** mocking (e.g. "POST then GET returns the just-created
  record") — keep M9.3 request-response-stateless; revisit as M9.3.x only
  if demand materialises.
- **Fault injection** (chaos engineering: random 503, latency jitter,
  connection reset) — layered feature, out of scope.
- **Contract testing** (PACT-style consumer/provider verification) —
  orthogonal.
- **Port forwarding / TLS termination** — the mock serves plain HTTP on the
  same JVM; users handle TLS the same way they handle it for the host
  service.

## 3. Module positioning & deployment shape

### 3.1 Chosen: new Maven module `csap-apidoc-mock`

A **new Spring Boot starter sibling** of `csap-apidoc-devtools`, published
as a first-class Maven artifact so host services can add a single
`<dependency>` and opt in.

```
csap-framework-apidoc/
├── csap-apidoc-annotation/          (existing)
├── csap-apidoc-core/                (existing)
├── csap-apidoc-boot/                (existing)
├── csap-apidoc-devtools/            (existing — backend dev metadata UI)
├── csap-apidoc-strategy/
│   ├── csap-apidoc-annotation-loader/
│   ├── csap-apidoc-yaml/
│   └── csap-apidoc-sqlite/
├── csap-apidoc-ui/                  (existing — frontend SPA)
└── csap-apidoc-mock/                ← NEW in M9.3a
```

### 3.2 Alternatives considered and rejected

| Option | Why rejected |
|---|---|
| **Pure frontend** (MSW / axios interceptor intercepts in `csap-apidoc-ui`) | Would duplicate the registry data-reading logic already in `csap-apidoc-core`; can't help non-UI callers (curl, QA scripts, contract tests). Forfeits the "one dependency flip" selling point. |
| **Standalone JAR / container** (separate process) | Extra deploy artefact; loses direct access to the host app's apidoc registry and its on-the-fly updates (e.g. `csap-apidoc-devtools` field edits). Harder to wire into existing Spring profiles. |
| **Extend `csap-apidoc-devtools` in place** | `devtools` is for metadata editing, not response serving. Mixing concerns bloats both; also devtools is commonly excluded from `prod` profiles where mocking doesn't belong anyway. |

### 3.3 Activation

Starter is **opt-in via property**, default OFF:

```properties
# application.properties (or -Dcsap.apidoc.mock.enabled=true)
csap.apidoc.mock.enabled=true

# Optional — defaults shown
csap.apidoc.mock.base-path=/mock          # mounted under this prefix
csap.apidoc.mock.response-delay-ms=0      # add fake latency (nice for loading states)
csap.apidoc.mock.default-status=200       # fallback when no override matches
csap.apidoc.mock.synthesize-missing=true  # serve schema-derived examples when no snapshot hit
```

**Profile guard** is encouraged but not enforced by the starter:

```properties
# spring.profiles.active=dev,mock
```

Host teams should wire `csap.apidoc.mock.enabled=${spring.profiles.active:default} == 'mock'` as they see fit.

### 3.4 Port / URL shape

**Same JVM, same port, different prefix.** Mock lives under
`${csap.apidoc.mock.base-path}/${originalPath}` so the host app retains its
real endpoints alongside. Example:

| Real endpoint | Mock endpoint |
|---|---|
| `GET /api/v1/orders/123` | `GET /mock/api/v1/orders/123` |
| `POST /api/v1/orders` | `POST /mock/api/v1/orders` |

The UI's existing **Environment Switcher** (M1) gets a new preset
`mock` that points to the prefixed URL; users never see the
implementation detail.

## 4. Response synthesis pipeline

Each mock request resolves its response via a priority-ordered chain; the
first match wins.

```
 ┌─────────────┐      ┌────────────────┐     ┌─────────────────┐     ┌────────────┐
 │ 1. Snapshot │  →   │ 2. Schema      │  →  │ 3. Static rule  │  →  │ 4. Default │
 │    override │      │    example     │     │    (YAML/SQLite)│     │    200 {} │
 └─────────────┘      └────────────────┘     └─────────────────┘     └────────────┘
```

### 4.1 Snapshot override (M9.2 interoperability)

If the host app opts into cross-device snapshot delivery (§9) AND the
incoming request matches a snapshot tagged `mock-response`, serve the
snapshot's stored response. This is the **only** path that can reproduce a
full multi-field payload a user captured previously.

### 4.2 Schema example (default path)

Use `CsapDocMethod.responseFields` to synthesise a payload that satisfies
the documented schema:

- For every field with an explicit `example` or `defaultValue` → use it.
- Otherwise synthesise by type:
  - `string` → `"string"` (or field-name hint: `*Id` → `"example-id"`,
    `email` → `"user@example.com"`, …)
  - `integer` / `long` → `1`
  - `boolean` → `true`
  - `date` / `datetime` → `"2026-04-20"` / `"2026-04-20T00:00:00Z"`
  - `array<T>` → `[synthesize(T)]` (one element)
  - `object` → recurse
  - `enum` → first value
- Size cap: max depth 6, max array length 1, max string length 128 —
  prevents accidentally hitting a recursive schema and blowing up.

### 4.3 Static rule file (optional)

A `mock-rules.yaml` on the classpath (or an absolute path in
`csap.apidoc.mock.rules-file`) lets backend devs pin specific responses
without touching code:

```yaml
rules:
  - method: GET
    path: /api/v1/orders/{id}
    match:
      pathParams:
        id: "999"
    response:
      status: 404
      body: { code: "ORDER_NOT_FOUND", message: "no such order" }
  - method: GET
    path: /api/v1/health
    response:
      status: 200
      body: { status: "UP" }
```

Matching order: `pathParams` > `queryParams` > `headers` > `bodyContains`.

### 4.4 Default 200

No match + `synthesize-missing=false` → return `200 {}`. If the endpoint
isn't documented in apidoc at all → `404 {"error": "not registered in
apidoc"}` with a hint header `X-CSAP-Mock: unregistered`.

## 5. Matching algorithm

1. Strip the mock prefix (`/mock`) from the incoming URI.
2. Find the best apidoc method by:
   - Method (`HttpMethod`) exact match.
   - Path match: compare segment-by-segment, `{name}` placeholders capture
     param values.
   - On ambiguity, prefer **fewer placeholders** (specificity bias).
3. Run the 4-step synthesis chain (§4).
4. Apply response-delay if configured.
5. Emit the response with added debug headers:
   - `X-CSAP-Mock: synthesized` | `snapshot` | `rule` | `unregistered`
   - `X-CSAP-Mock-Method-Id: <apidoc methodId>` (for traceability)

## 6. Implementation sketch

### 6.1 `csap-apidoc-mock` module layout

```
csap-apidoc-mock/
  pom.xml
  src/main/java/ai/csapidoc/mock/
    CsapApidocMockAutoConfiguration.java        (@AutoConfiguration, conditional on property)
    MockProperties.java                         (@ConfigurationProperties "csap.apidoc.mock")
    MockDispatcherServletRegistrar.java         (registers the /mock/** servlet)
    MockController.java                         (@RestController serving /mock/**)
    synthesis/
      ResponseSynthesiser.java                  (§4 pipeline orchestrator)
      SchemaExampleSynthesiser.java             (§4.2 type-based)
      StaticRuleSynthesiser.java                (§4.3 YAML rules)
      SnapshotSynthesiser.java                  (§4.1 stub for M9.3b)
    matcher/
      PathMatcher.java                          (§5 path + placeholder)
    rules/
      MockRulesParser.java                      (Jackson YAML → List<Rule>)
      Rule.java
  src/main/resources/META-INF/
    spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/ai/csapidoc/mock/
    (JUnit 4 tests mirroring the sqlite module's style)
```

### 6.2 Integration touchpoints

| Module | What changes |
|---|---|
| `csap-apidoc-core` | No change. Mock reads the same `CsapDocMethod` registry. |
| `csap-apidoc-boot` | No change. `csap-apidoc-mock` depends on boot's auto-config transitively. |
| `csap-apidoc-ui` | **M9.3c** adds a "Mock" env preset + a subtle `🎭 Mock` badge in the response meta when `X-CSAP-Mock` header is present. |
| `csap-apidoc-devtools` | No change in M9.3; a future "rules editor" could live here. |
| `example/example-spring-boot3` | Add a `mock` Spring profile to demo the feature end-to-end. |

## 7. Sub-task breakdown (proposed PRs)

**M9.3a — Module scaffold + schema synthesis** *(medium)*
- Create `csap-apidoc-mock` Maven module.
- `MockProperties`, `CsapApidocMockAutoConfiguration`, `MockController`.
- `SchemaExampleSynthesiser` (§4.2) — handles all primitive types, arrays,
  nested objects, enums with max-depth guard.
- JUnit 4 tests covering the synthesiser's type matrix + a
  Spring-integration test that calls `/mock/...` against an in-memory
  apidoc registry.
- Opt-in via single property; default OFF.

**M9.3b — Static rule overrides** *(small)*
- `MockRulesParser` loads `mock-rules.yaml` via Jackson YAML dataformat.
- `StaticRuleSynthesiser` integrates into the pipeline between snapshot and
  schema example.
- Matching: path > pathParams > queryParams > headers > bodyContains.
- Unit tests for rule priority + a golden-path integration test.

**M9.3c — Frontend integration** *(small)*
- `EnvironmentSwitcher` ships with an optional `mock` preset in the env
  defaults, pointing at `${origin}/mock`.
- When a Try-it-out response has the `X-CSAP-Mock` header, the response
  meta line renders a small `🎭 mock` tag (i18n: `tryout.mock.badge`).
- Unit tests + i18n keys in zh-CN/en-US.

**M9.3d — Example app demo + docs** *(small)*
- Add a `mock` Spring profile to `example/example-spring-boot3`.
- Update `docs/features/m93-mock-server.md` with a "How to use" section.
- Hook the docs into the MkDocs nav.

**M9.3e (optional, follow-up) — Snapshot override**
- Adds `SnapshotSynthesiser` that reads snapshot JSON served through a
  REST endpoint exposed by the host app. Requires a tiny server-side
  snapshot store (feels like scope creep; only do if explicitly asked).

## 8. Risks

| Risk | Mitigation |
|---|---|
| Recursive schema blows stack (`A.field: B`, `B.field: A`) | Hard depth cap of 6 in `SchemaExampleSynthesiser`; returns `null` beyond. |
| Field-name heuristics (`email` → `"user@example.com"`) surprise users who actually want `"string"` | Disable by default; flag: `csap.apidoc.mock.smart-examples=false`. |
| `/mock/**` path clash with a real host-app route | Configurable via `csap.apidoc.mock.base-path`; default `/mock` is clearly synthetic. |
| Starter gets accidentally included in a production build | `@ConditionalOnProperty(csap.apidoc.mock.enabled=true, havingValue = "true")` + docs warn to gate on Spring profile. |
| ARM64 JDK 21 regression (cf. the existing SkyWalking agent incident) | Pure Jackson / Spring MVC — no native or instrumentation. Regression test matrix already includes JDK 11/17/21. |

## 9. Open questions

1. **Snapshot override scope.** If M9.3e (snapshot → mock response) ships,
   does the snapshot need to include response data (it currently doesn't
   per M9.2 §2 security note)? Two options: (a) add an opt-in
   `captureResponse: true` flag when saving; (b) store the response only in
   memory while the tab is open. Defer the decision until M9.3e is
   actually on the table — not required for M9.3a-d.

2. **Multi-tenant / workspace-scoped rules.** Out of scope for the OSS
   framework; if a commercial deployment needs it, `agent-admin-web` can
   layer on top via the `mock-rules.yaml` file path.

3. **OpenAPI spec ingestion.** Should `csap-apidoc-mock` also serve mocks
   derived from a raw OpenAPI spec (not apidoc)? **No** — that's
   [Prism](https://stoplight.io/open-source/prism)'s job. We derive from
   apidoc because that's what distinguishes this starter from the existing
   ecosystem.

## 10. Timeline

- **M9.3a** (scaffold + schema synthesis) — ~1 day. Ship first because
  it's the minimum viable mock and is fully self-contained.
- **M9.3b** (rule overrides) — ~half day. Ships once M9.3a has users.
- **M9.3c** (frontend integration) — ~half day. Must wait for M9.3a.
- **M9.3d** (example + docs) — ~2 hours. Can parallelise with M9.3c.
- **M9.3e** (snapshot override) — on-demand.

Total estimated M9.3a-d: **~2 working days**.

## 11. Success criteria

- `mvn -DskipTests=false -pl csap-apidoc-mock -am test` passes.
- Example app with `-Dspring.profiles.active=mock` starts, mounts
  `/mock/**`, and a curl against `/mock/api/v1/orders/1` returns a valid
  apidoc-synthesised `Order` body.
- Adding `<dependency>csap-apidoc-mock</dependency>` to a random host
  Spring Boot app + flipping the property produces mocks for every
  apidoc-registered method with zero code changes.
- Starter excluded-by-default: `mvn dependency:tree` of a non-mock app
  shows no `csap-apidoc-mock` transitive drag.
- MkDocs `Docs` workflow passes (we add one file to nav).
