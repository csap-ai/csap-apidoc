# Feature: Environment + Global Headers + Auth Schemes + Try-it-out

> Branch: `feat/environment-auth-headers`
> Status: **APPROVED — ready for M1**
> Owner: yangchengfu
> Last updated: 2026-04-18 (v2 — major rewrite after architecture clarification)

This document is the working spec for the first major feature shipped after
the public release of `csap-apidoc`. It upgrades the doc viewer from a
read-only reference into an interactive API workbench that frontend
developers can use to inspect and exercise endpoints without leaving the
browser.

---

## 1. Module positioning (read this first)

`csap-framework-apidoc` ships **two parallel, independent products**. They
share the same Maven multi-module repo but serve different audiences and have
no client–server relationship.

| module | audience | role | runtime shape |
|---|---|---|---|
| `csap-apidoc-ui` | **Frontend developers / QA** | Browse multi-service API docs; inspect schema; **exercise endpoints (try-it-out)**; manage personal request context (env / headers / auth) | Standalone React SPA. Calls each service's `/apidoc` endpoint directly. No backend of its own. |
| `csap-apidoc-devtools` | **Backend developers** | Manage doc metadata (field configs, regex templates, response models, hide endpoints) inside their own Spring Boot app at runtime | Embedded Spring Boot starter. Auto-mounts `/devtools-ui` and `/api/devtools/*` inside the host app, similar in spirit to `spring-boot-actuator`. |

**This feature lives entirely in `csap-apidoc-ui`.** No changes are required
in `csap-apidoc-devtools`, `csap-apidoc-boot`, `csap-apidoc-core`, or any
other Maven module for the in-scope milestones (M1–M6, M8). M7 is an
optional later enhancement that touches `csap-apidoc-annotation` and
`csap-apidoc-core`.

> **Why this matters**: the v1 of this doc assumed the two modules formed a
> client–server pair with central persistence. They do not. All
> personalisation in this feature lives in the user's browser. See §10.

---

## 2. Goals & Non-goals

### Goals

1. Frontend devs can pick an **Environment** (`dev` / `staging` / `prod` / ...)
   and have all subsequent requests target the correct base URL with the
   correct variables.
2. **Global Headers** can be configured at three scopes — global / service /
   environment — and are auto-injected into every try-it-out request.
3. **Auth Schemes** are first-class: Bearer, Basic, API Key (header / query /
   cookie), OAuth2 Client Credentials. Sensitive fields can optionally be
   encrypted with a per-browser master password (Web Crypto AES-GCM).
4. A **Try it out** panel sends real requests directly from the browser to
   the target service, displaying status, latency, response headers and body.
5. All configuration is stored in the browser (`localStorage`); nothing leaves
   the device. No server-side persistence, no accounts.

### Non-goals (separate branches)

- Server-side persistence / cross-device sync (would require integration with
  the commercial `agent-admin-web` platform; out of scope for the OSS framework)
- RBAC / multi-tenant / team-shared configurations
- Mock server (Phase 1.3 of devtools roadmap)
- Code-sample generation (cURL / axios / fetch / Java)
- Deep links / shareable URLs with prefilled params
- OAuth2 Authorization Code / PKCE / Device flow (Client Credentials only)
- Non-HTTP protocols (WebSocket, SSE, gRPC)
- Fully reactive Antd 5 redesign (tracked in `chore/antd5-upgrade`)

---

## 3. User stories

| # | As a … | I want to … | So that … |
|---|---|---|---|
| US-1 | Frontend developer | switch the doc viewer to `staging` and see the staging base URL applied | I can call staging APIs without copy-pasting URLs |
| US-2 | Frontend developer | define a global header `X-Tenant-Id: 42` once for the `staging` env | I don't have to retype it on every endpoint test |
| US-3 | QA engineer | configure a Bearer token per environment | switching env auto-switches token |
| US-4 | QA engineer | press "Send" on an endpoint and see the real response | I don't context-switch to Postman |
| US-5 | Privacy-sensitive user | set a master password to encrypt my saved tokens | a screen-share or stolen laptop doesn't leak credentials |
| US-6 | Anyone | export and re-import my full config (env + headers + auth) | I can move setup between browsers / devices manually |

---

## 4. Architecture overview

```
┌──────────────────────────────────────────────────┐
│             Browser (csap-apidoc-ui)             │
│  ──────────────────────────────────────────────  │
│  ┌──────────────┐    ┌──────────────────────┐    │
│  │ Top bar      │    │ Detail pane          │    │
│  │ Env switcher │    │ Config | Try it out  │    │
│  │ Headers btn  │    │ ┌──────────────────┐ │    │
│  │ Auth btn     │    │ │ Request form     │ │    │
│  └──────┬───────┘    │ │ Send → ↓         │ │    │
│         │            │ │ Response viewer  │ │    │
│         │            │ └──────────────────┘ │    │
│         ▼            └──────────┬───────────┘    │
│  ┌──────────────────────────────▼─────────────┐  │
│  │ RequestContext (in-memory + localStorage)  │  │
│  │  - current env                             │  │
│  │  - headers (merged from 3 scopes)          │  │
│  │  - active auth scheme + (decrypted) creds  │  │
│  └────────────────────────┬───────────────────┘  │
└───────────────────────────┼──────────────────────┘
                            │
                            ▼
              Direct HTTP to target service
              (e.g. https://api-staging.example.com/orders)
              Each service must allow CORS or be reached via a
              user-configured dev-time proxy. See §6.
```

**Key properties**:

- No server in the loop. All persistence is browser-local.
- `csap-apidoc-ui` already calls each service's `/apidoc` for doc data; the
  same axios instance (configured per env) issues try-it-out requests.
- The doc-data fetch and the try-it-out request hit the **same target
  service**. If `/apidoc` is reachable from the browser, try-it-out is
  reachable too (same origin, same CORS posture).

---

## 5. Data model (localStorage schema)

All keys are namespaced under `csap-apidoc:`. Format is JSON-stringified.

### 5.1 Environments

```jsonc
// key: csap-apidoc:environments
{
  "version": 1,
  "activeId": "env_dev",
  "items": [
    {
      "id": "env_dev",
      "name": "Dev",
      "color": "#52c41a",
      "baseUrl": "http://localhost:8080",
      "isDefault": true,
      "variables": {
        "tenantId": "42",
        "userId": "u_123"
      }
    },
    { "id": "env_staging", "name": "Staging", "color": "#fa8c16", ... }
  ]
}
```

Variables expand `{{key}}` template tokens inside `baseUrl`, header values,
auth credentials, and request bodies at send time.

### 5.2 Global headers

```jsonc
// key: csap-apidoc:headers
{
  "version": 1,
  "items": [
    {
      "id": "h_1",
      "scope": "global",                     // 'global' | 'service' | 'environment'
      "scopeRefId": null,                    // serviceUrl when scope=service, envId when scope=environment
      "key": "X-App-Version",
      "value": "1.0.0",
      "enabled": true,
      "isSecret": false,                     // if true, value stored in encrypted vault (§5.4)
      "description": "App version pinned by frontend"
    },
    {
      "id": "h_2",
      "scope": "environment",
      "scopeRefId": "env_staging",
      "key": "X-Tenant-Id",
      "value": "{{tenantId}}",                // resolved against env variables
      "enabled": true,
      "isSecret": false
    }
  ]
}
```

**Resolution order** at request time (later overrides earlier on same key):
`global` → `service` → `environment` → endpoint-declared headers from doc.

### 5.3 Auth schemes

```jsonc
// key: csap-apidoc:auth-schemes
{
  "version": 1,
  "activeBindings": {
    // serviceUrl -> schemeId
    "http://localhost:8080": "scheme_bearer_dev"
  },
  "items": [
    {
      "id": "scheme_bearer_dev",
      "name": "Dev Bearer",
      "type": "bearer",                      // 'bearer' | 'basic' | 'apikey' | 'oauth2_client'
      "config": { "tokenRef": "vault:tok_1" },
      "envBindings": {                       // optional per-env credential override
        "env_dev":     { "tokenRef": "vault:tok_1" },
        "env_staging": { "tokenRef": "vault:tok_2" }
      }
    },
    {
      "id": "scheme_apikey_x",
      "type": "apikey",
      "config": { "in": "header", "name": "X-API-Key", "valueRef": "vault:tok_3" }
    }
  ]
}
```

Type-specific `config` shapes:

```jsonc
bearer:        { "tokenRef": "vault:..." }
basic:         { "username": "user", "passwordRef": "vault:..." }
apikey:        { "in": "header"|"query"|"cookie", "name": "...", "valueRef": "vault:..." }
oauth2_client: { "tokenUrl": "...", "clientId": "...", "clientSecretRef": "vault:...",
                 "scope": "...", "cachedTokenRef": "vault:..." }
```

### 5.4 Vault (sensitive values)

Two modes selectable in Settings:

```jsonc
// key: csap-apidoc:vault
// Mode A: plaintext (default for first-time users)
{
  "version": 1,
  "encrypted": false,
  "items": { "tok_1": "eyJhbGciOi...", "tok_2": "...", ... }
}

// Mode B: encrypted (after user enables master password)
{
  "version": 1,
  "encrypted": true,
  "kdf": { "name": "PBKDF2", "iter": 200000, "salt": "<base64>" },
  "items": {
    "tok_1": { "iv": "<base64>", "ct": "<base64>" },   // AES-GCM
    "tok_2": { "iv": "<base64>", "ct": "<base64>" }
  }
}
```

Only secrets go through the vault; non-secret fields (header values without
`isSecret`, env variables without `isSecret`) sit in their primary key for
fast read.

### 5.5 Settings

```jsonc
// key: csap-apidoc:settings
{
  "version": 1,
  "vaultMode": "plaintext",                  // 'plaintext' | 'encrypted'
  "vaultLockTimeoutMin": 30,                 // re-prompt after idle
  "tryItOut": {
    "timeoutMs": 30000,
    "followRedirects": true,
    "maxResponseBytes": 5_000_000,
    "proxyUrl": null                         // optional CORS proxy override (§6)
  }
}
```

### 5.6 Storage choice rationale

`localStorage` (not IndexedDB) because:

- All blobs are tiny (env <100 entries × ~200B; headers <1000 × ~150B; tokens
  <100 × ~2KB). Total <1MB, well within the 5MB localStorage cap.
- Synchronous read at app start avoids a loading-flash on the env switcher.
- Trivial to back up via export/import (US-6).

If the data grows past 4MB we can migrate to IndexedDB behind a
`KeyValueStore` interface; not blocking for v1.

---

## 6. CORS & try-it-out network strategy

The browser issues real HTTP requests directly to the target service. CORS
is the only friction. Three layers of solution, in escalating effort:

| layer | when applicable | what user does |
|---|---|---|
| **L1 — same-origin / dev proxy** | Dev mode: `vite.config.ts` proxies `/api/*` to `http://localhost:8080`. Works out of the box. | Nothing |
| **L2 — target service enables CORS** | Staging / prod where the service is reachable but blocks browsers | Backend dev adds permissive CORS to `/apidoc/**` and the API endpoints (a `csap-apidoc-cors` Spring Boot starter could be offered later) |
| **L3 — user-configured CORS proxy** | Locked-down envs where backend can't change CORS | User sets `settings.tryItOut.proxyUrl = "https://my-corsproxy.example/?url="`. UI prepends this to outbound URLs. |

The doc must clearly explain these three layers and recommend L1 for
development, L2 for shared staging, L3 only as a workaround. UI surfaces a
helpful error banner with a link to the CORS doc page when a try-it-out
request fails CORS.

---

## 7. UI design (high-level)

### 7.1 Top bar (`layouts/Header/index.tsx`)

Add three controls before the existing service selector:

- `<EnvironmentSwitcher />` — dropdown listing envs, leading colour dot,
  shows current `baseUrl` on hover. Clicking the gear icon opens the
  Environment Manager drawer.
- `<HeadersButton />` — opens Global Headers drawer (3-scope tabs).
- `<AuthButton />` — opens Auth Schemes drawer.

### 7.2 Detail pane (`layouts/index.tsx`)

Existing parameter table stays under a `Config` tab. Add a sibling tab:

- `<TryItOutPanel />`
  - Auto-built form from API definition (path params, query, headers, body)
  - Pre-fills body using `example` values from doc
  - "Active context" pill row above Send button: `Dev · 3 headers · Bearer`
  - Send button → fetch → response viewer (status code badge, latency,
    headers table, formatted JSON / text body, raw toggle)

### 7.3 Drawers

All three managers (Env, Headers, Auth) follow the same layout: left list +
right form. CRUD via Modal. Reuse existing `BaseTable` and `BaseModal` styles
so no new design system work is needed.

### 7.4 Settings page

Tiny modal triggered from a gear icon next to AuthButton:

- Vault mode toggle (plaintext ↔ encrypted, with master password prompt)
- Vault lock timeout
- Try-it-out timeout / max response size / proxy URL
- Export / Import config (JSON download / upload)
- Reset all

---

## 8. Milestones (each = 1 PR into `develop`)

All in `csap-apidoc-ui` unless noted.

| # | PR title | Scope | Estimate |
|---|---|---|---|
| M1 | `feat(env): environment model + switcher (localStorage)` | `EnvironmentStore` (localStorage CRUD), `EnvironmentSwitcher` top-bar component, manager drawer, variable resolver | 1-2d |
| M2 | `feat(headers): scoped global headers + merge resolver` | `HeadersStore`, drawer with 3-scope tabs, header-merge function with override rules | 1-2d |
| M3 | `feat(auth): four auth schemes + vault (plaintext mode)` | `AuthStore`, `Vault` wrapper (plaintext only), 4 type forms, OAuth2-cc token cache | 2-3d |
| M4 | `feat(try-it-out): request panel + response viewer` | `TryItOutPanel`, request builder, axios send, response renderer (handle JSON / text / binary / SSE preview) | 2-3d |
| M5 | `feat(reader): wire env+headers+auth into try-it-out` | `RequestContext` aggregator, header merge applied at send time, env-bound credential resolution | 1-2d |
| M6 | `feat(security): Web Crypto vault encryption + master password` | PBKDF2 + AES-GCM, lock/unlock UI, idle timeout, migration from plaintext vault | 1-2d |
| M7 | `feat(devtools): @DocGlobalHeader / @DocAuth annotation hints` *(optional, may defer)* | Annotations in `csap-apidoc-annotation`, scanner emits hints in `CsapDocModel`, ui shows them as suggested presets | 1-2d |
| M8 | `test+docs+i18n` | Vitest unit tests, Playwright E2E for try-it-out happy path, README section, zh-CN + en-US strings, CORS doc page | 1-2d |

**Total** ~10-18 working days. Each milestone = its own PR into `develop`.
M1 only depends on nothing; M2/M3 only depend on M1; M4 standalone; M5 fans
in M1+M2+M3+M4; M6 depends on M3; M7 standalone (optional); M8 last.

When all merged, a single release PR `develop → main`, tagged `v0.x.0`.

---

## 9. Decisions

### ✅ D-1. Storage: `localStorage` only

JSON under `csap-apidoc:*` keys. ~1MB ceiling expected. IndexedDB migration
behind `KeyValueStore` interface if needed later. **Locked.**

### ✅ D-2. Crypto for sensitive fields: Web Crypto AES-GCM + PBKDF2-derived key

Optional, off by default. User opts in by setting a master password in
Settings. KDF: PBKDF2-SHA256, 200_000 iterations, 16-byte random salt
per-browser. AES-GCM 12-byte random IV per item. Key never leaves browser
memory; locks on tab close or idle timeout. **Locked.**

### ✅ D-3. UI library: Antd 4 (no upgrade in this branch)

Antd 4 / Vite 3 / TS 4.6 stay. Antd 5 upgrade happens in a separate
`chore/antd5-upgrade` branch. Antd 4's Drawer / Form / Tabs / Table cover
all UI needs of M1–M6. **Locked.**

### ✅ D-4. Try-it-out request transport: browser-native `fetch` (no axios proxy hop)

Reuse the existing `csap-axios` instance for consistency with
documentation-data fetches. No backend proxy. CORS handled per §6.
**Locked.**

### ✅ D-5. CORS-failure UX: explanatory banner + manual proxy switch

When a try-it-out request fails the browser CORS check, the UI shows a
clear error banner explaining the three layers (§6) with a one-click
shortcut into Settings → "Set CORS proxy URL". No automatic proxying, no
hard-coded public fallback (which would be dangerous: a public proxy
would see all request bodies, including bearer tokens). User opts in
explicitly per browser. **Locked.**

### ✅ D-6. M7 (devtools annotation hints) deferred to a follow-up release

M1–M6 + M8 ship as the first feature release of `csap-apidoc-ui`
(targeting tag `v0.x.0`). M7 (`@DocGlobalHeader` / `@DocAuth`
annotations in `csap-apidoc-annotation` + scanner emission in
`csap-apidoc-core`) is split into its own focused PR / release
(`v0.(x+1).0`). This keeps the first PR set ui-only and minimises the
scope of breaking-change risk for early adopters. **Locked.**

---

## 10. Implementation status

> Updated by each milestone PR.

- [x] M1 — environment store + switcher
- [x] M2 — global headers + merge
- [x] M3 — auth schemes + plaintext vault
- [x] M4 — try-it-out request/response panel
- [x] M5 — wire env+headers+auth into requests
- [x] M6 — Web Crypto vault encryption
- [ ] M7 — devtools annotation hints (optional, may defer)
- [ ] M8 — tests + docs + i18n

---

## 11. References

- Module layout & roles confirmed in `pom.xml` (parent), `csap-apidoc-boot`
  starter, and `csap-apidoc-devtools/src/main/java/.../DevtoolsViewController.java`
- Reader entry: `csap-apidoc-ui/src/layouts/index.tsx` (970 LoC)
- Existing axios: `csap-apidoc-ui/src/api/index.ts` (`csap-axios` wrapper,
  no auth interceptor — to be augmented in M5)
- Devtools roadmap (Phase 1.1 in-line testing): `csap-apidoc-devtools/APIDOC开发工具产品路线图.md`

---

## 12. Design history

**v1 (2026-04-18 initial draft)** assumed `csap-apidoc-ui` and
`csap-apidoc-devtools` formed a client–server pair, and proposed
server-side persistence (SQLite/Postgres + JPA + Flyway), RBAC with three
roles, AES-GCM encryption with a server-held master key, and a
`/api/v1/apidoc/proxy` endpoint for try-it-out requests.

**This was wrong.** The two modules are independent: `-ui` is a standalone
SPA for **frontend developers**; `-devtools` is an embedded Spring Boot
starter for **backend developers** to manage doc metadata inside their own
application at runtime. They share neither a server nor a database.

**v2 (this document)** moves all personalisation into the browser. No
servers, no accounts, no central database. The feature is a pure
frontend addition, and ships as `csap-apidoc-ui`-only PRs into `develop`.

If a future enterprise SKU wants cross-device sync, that can layer on top
by syncing the same JSON blobs to `agent-admin-web`, but that is explicitly
out of scope for the open-source framework.
