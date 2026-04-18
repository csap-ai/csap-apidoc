# Feature: Environment + Global Headers + Auth Schemes + Try-it-out

> Branch: `feat/environment-auth-headers`
> Status: **APPROVED — ready for M1**
> Owner: yangchengfu
> Last updated: 2026-04-18

This document is the working spec for the first major feature shipped after the
public release of `csap-apidoc`. It covers four tightly coupled capabilities
that together upgrade the doc viewer from a read-only reference into an
interactive API workbench.

---

## 1. Goals & Non-goals

### Goals

1. Reader-side users (frontend devs, QAs) can pick an **Environment**
   (`dev`/`staging`/`prod`/...) and have all subsequent requests target the
   correct base URL with the correct variables.
2. **Global Headers** can be configured at three scopes — environment, service,
   user — and are auto-injected into every request issued from the doc UI.
3. **Auth Schemes** are first-class: Bearer, Basic, API Key (header / query /
   cookie), OAuth2 Client Credentials. Credentials are encrypted at rest and
   never exposed to the browser.
4. A **Try it out** panel sends real requests through a backend proxy with the
   above context applied, displaying status, latency, response body and
   headers.
5. All four are persisted server-side, scoped by workspace, and follow the
   existing RBAC model (`viewer` / `editor` / `admin`).

### Non-goals (will be addressed in later branches)

- Mock server (Phase 1.3 of the devtools roadmap)
- Code-sample generation (cURL / axios / fetch / Java)
- Deep links / shareable URLs with prefilled params
- OAuth2 Authorization Code / PKCE / Device flow (only Client Credentials in
  this iteration)
- Non-HTTP protocols (WebSocket, SSE, gRPC)
- Internationalisation of new screens (will follow in a separate i18n PR using
  the same key conventions as `agent-admin-web`)

---

## 2. User stories

| # | As a … | I want to … | So that … |
|---|---|---|---|
| US-1 | Frontend developer | switch the doc viewer to `staging` and see the staging base URL applied | I can call staging APIs without copy-pasting URLs |
| US-2 | Backend developer | define a global header `X-Tenant-Id: 42` once for the `staging` environment | I don't have to type it on every endpoint test |
| US-3 | QA engineer | configure a Bearer token credential per environment | I can switch envs without re-pasting tokens |
| US-4 | Tech lead | mark a Bearer scheme as "team shared" | new joiners inherit the auth setup |
| US-5 | Anyone | press "Send" on an endpoint and see the real response | I don't have to context-switch to Postman |
| US-6 | Admin | review who changed which env/header/auth in the last 30 days | for compliance audit |

---

## 3. Architecture overview

```
┌─────────────────────────────┐                          ┌──────────────────────────┐
│  csap-apidoc-ui (reader)    │                          │  csap-apidoc-devtools    │
│  ──────────────────────     │                          │  (mgmt console + server) │
│  • Env switcher (top bar)   │                          │  ──────────────────────  │
│  • Headers/Auth drawer      │   GET env/headers/auth   │                          │
│  • Try-it-out tab in detail │ ───────────────────────► │  REST API (§5)           │
│                             │                          │      │                   │
│                             │   POST /apidoc/proxy     │      │ JPA / MyBatis     │
│                             │ ───────────────────────► │      ▼                   │
│                             │                          │  Storage  (§4 / §8)      │
└─────────────────────────────┘                          └──────────────────────────┘
                                                              │
                                                              ▼
                                                     Audit log (separate table)
```

The reader UI talks **only** to devtools server. The proxy hop is required so
that:

- Browsers don't deal with target-service CORS.
- Encrypted credentials are decrypted only on the server.
- Every test request can be audited (who, when, which endpoint, which env).

---

## 4. Data model (logical)

> ⚠ Physical schema depends on the storage choice in §8. Below is logical.

### 4.1 Environment

| field | type | notes |
|---|---|---|
| id | uuid | PK |
| workspace_id | string | RBAC scope; matches `agent-admin-web` workspace concept |
| service_key | string | Maps to one document service (e.g. `order-service`) |
| name | string | `dev`, `staging`, `prod`, `mock`, ... |
| base_url | string | e.g. `https://api-staging.example.com` |
| color | string | UI dot colour (#hex) |
| is_default | bool | One default per (workspace, service) |
| created_by, created_at, updated_at | audit |

### 4.2 Environment variable

| field | notes |
|---|---|
| id, environment_id (FK) | |
| key, value | template `{{key}}` substituted into URLs/headers |
| is_secret | secrets stored AES-GCM encrypted |
| description | |

### 4.3 Global header

| field | notes |
|---|---|
| id, workspace_id | |
| scope | `environment` / `service` / `user` |
| scope_ref_id | id of env / service_key / user_id depending on scope |
| header_key, header_value, enabled | |
| is_secret | encrypted if true |
| description | |

Resolution order at request time: `service` → `environment` → `user`. Later
scopes override earlier ones for the same key.

### 4.4 Auth scheme + credential

```text
auth_scheme {
  id, workspace_id, service_key, name,
  type: 'bearer'|'basic'|'apikey'|'oauth2_client',
  config_json: type-specific  (e.g. apikey: { in: 'header', name: 'X-API-Key' })
  is_team_shared: bool
}

auth_credential {
  id, scheme_id, env_id (nullable), user_id (nullable when shared),
  credential_cipher: AES-GCM(secret)
}
```

`is_team_shared=false` ⇒ each user keeps their own credential row, never
visible to others. `=true` ⇒ a single shared credential, visible to anyone with
view rights to that service.

### 4.5 Audit log entry

| field | notes |
|---|---|
| id, workspace_id, actor_id, actor_email | |
| target_type | `environment` / `header` / `auth_scheme` / `auth_credential` / `proxy_request` |
| target_id | |
| action | `create` / `update` / `delete` / `proxy_call` |
| diff_json | for create/update, the redacted diff |
| created_at | |

---

## 5. REST API (devtools server)

All endpoints are mounted under `/api/v1/apidoc/`. All are RBAC-guarded:

- read endpoints → `viewer+`
- write endpoints → `editor+`
- delete & audit endpoints → `admin`

```
GET    /environments?service=<key>
POST   /environments
GET    /environments/{id}
PUT    /environments/{id}
DELETE /environments/{id}

GET    /headers?scope=<scope>&ref=<id>
POST   /headers
PUT    /headers/{id}
DELETE /headers/{id}

GET    /auth-schemes?service=<key>
POST   /auth-schemes
GET    /auth-schemes/{id}
PUT    /auth-schemes/{id}
DELETE /auth-schemes/{id}

GET    /auth-schemes/{id}/credentials              -- self only unless shared
POST   /auth-schemes/{id}/credentials              -- save my credential
DELETE /auth-schemes/{id}/credentials/{credId}

POST   /proxy
       body: { service, env_id, method, path, query, body, override_headers }
       resp: { status, latency_ms, headers, body }

GET    /audit?target_type=&from=&to=               -- admin
```

Response envelope follows existing `ApidocResult<T>` (see `DevtoolsController`).

---

## 6. UI design (high-level)

### 6.1 Reader (`csap-apidoc-ui`)

- **Top bar**: add `EnvironmentSwitcher` (dropdown with coloured dot) +
  `RequestContextButton` (opens drawer with tabs: Headers / Auth).
- **Detail pane**: existing tabs become `Config | Example | Try it out`.
  - `Try it out` builds a form from the API definition, pre-fills body using
    `example` values, shows a `Send` button, and renders response in a
    Monaco/CodeMirror viewer.
- All env/header/auth UI is **read-only for `viewer` role** (form fields
  disabled, a hint banner explains).

### 6.2 Manager (`csap-apidoc-devtools` console)

- Sidebar gets three new entries under a "Runtime context" group:
  `Environments`, `Global Headers`, `Auth Schemes`.
- Each is a standard table + create/edit Modal, reusing the existing
  `RequestParamModal` / `ValidateTableModal` styling.
- `Auth Schemes` page has a sub-tab for "My credentials" (per-user cred mgmt).

---

## 7. Milestones (each = 1 PR into `develop`)

| # | PR title | Scope | Estimate |
|---|---|---|---|
| M1 | `feat(env): data model + REST api + tests` | Storage adapter + EnvironmentService + 5 endpoints + JUnit | 2-3d |
| M2 | `feat(env-ui): devtools console for environments` | React table + Modal + i18n keys | 1-2d |
| M3 | `feat(headers): scoped global headers (model+api+ui)` | Both reader and console | 2-3d |
| M4 | `feat(auth): four auth schemes with encrypted credentials` | AES-GCM, 4 type forms, per-user creds | 3-4d |
| M5 | `feat(proxy): try-it-out backend proxy + reader UI panel` | `/proxy` endpoint + request/response panes | 2-3d |
| M6 | `feat(reader): env switcher + auto-injection wiring` | Glue M1-M4 into reader top bar | 1-2d |
| M7 | `feat(audit): audit log for env/header/auth/proxy` | Cross-cutting interceptor | 1d |
| M8 | `test+docs: e2e playwright happy path + README` | Quality gate | 2d |

**Total**: ~14-20 working days.

Each milestone PR:
- Targets `develop`, not `main`.
- Must include unit tests and at least one integration test.
- Must update this doc's "Implementation status" section (§9) to keep it live.
- Auto-runs the existing `ci.yml` + `security-scan.yml`.

When all M1-M8 merged: a single release PR `develop → main`, tagged `v0.x.0`.

---

## 8. Decisions (locked in 2026-04-18)

### ✅ D-1. Storage backend — **SQLite default, Postgres opt-in**

JPA / Hibernate entities written once; `csap.apidoc.datasource.type=sqlite|postgres`
selects driver at boot. Flyway migrations are SQL-92 compatible across both.

- SQLite path: `${user.home}/.csap-apidoc/data.db`, auto-created on first run
- Postgres path: standard `spring.datasource.url`, opt-in for team deployments
- BLOB / BYTEA used for encrypted secret columns (not VARCHAR + base64)

**Why**: zero-friction onboarding for individual open-source users while
keeping the door open for team / enterprise deployments without a code rewrite.

### ✅ D-2. Encryption key — **env var `CSAP_APIDOC_SECRET_KEY` + `SecretProvider` interface**

```java
public interface SecretProvider {
    byte[] encrypt(byte[] plaintext);
    byte[] decrypt(byte[] ciphertext);
    String keyId();
}
```

Default impl: `EnvVarSecretProvider`. Future plug-ins (`AwsKmsSecretProvider`,
`JceksSecretProvider`) ship in separate PRs without touching call sites.

- AES-GCM, 12-byte random nonce per encryption, never reused
- If env var unset on first boot: generate random 32-byte key, persist in a
  dedicated `_secret_key` table, log a WARNING with explicit guidance to set
  `CSAP_APIDOC_SECRET_KEY` for production

**Why**: balances "open the box and it just works" with a clean upgrade path
to KMS-grade key management.

### ✅ D-3. RBAC / Identity — **devtools owns it; `IdentityProvider` interface for future SSO**

```java
public interface IdentityProvider {
    Optional<UserPrincipal> authenticate(HttpServletRequest req);
    UserPrincipal currentUser();
}
```

Default: `LocalDbIdentityProvider` (Session cookie, BCrypt password hashes).

Three roles aligned with `agent-admin-web` semantics:

| role | env / header / auth scheme | own credential | others' credentials | audit log |
|---|---|---|---|---|
| `viewer` | read | read+write | hidden | hidden |
| `editor` | create / update / delete | read+write | hidden (presence only) | hidden |
| `admin`  | full | read+write | hidden (presence only)* | full read |

\* **Even `admin` cannot decrypt others' personal credentials.** This is a
hard rule, not a permission setting — it protects the compliance story for
finance / healthcare customers.

First-boot flow: when user table is empty, redirect to `/setup` to create the
initial `admin` account (Gitea / Wiki.js style).

`workspace_id` column is present on every table from day one even though
single-user mode only ever has one workspace; this avoids a schema migration
when multi-tenant is enabled later.

**Why**: open-source product must work standalone. Commercial SSO integration
ships as a separate plug-in without touching this codebase.

### ✅ D-4. Frontend UI library — **stick with Antd 4 for this feature; spin up `chore/antd5-upgrade` in parallel**

Reader UI (`csap-apidoc-ui`) stays on Antd 4 / Vite 3 / TS 4.6 for the entire
duration of this feature. All new components in this branch use Antd 4 APIs
only.

Antd 4 vs 5 cannot coexist in one React tree (style conflicts), so a partial
upgrade is not viable. The full upgrade is tracked separately:

- Branch: `chore/antd5-upgrade` (created after this feature lands)
- Out of scope here: anything in `csap-apidoc-ui/` outside the new code paths

The devtools console (`csap-apidoc-devtools/devtools/`) is **already on Antd
5.12** and will use v5 APIs for new screens — no inconsistency within that
sub-app.

**Why**: keeps PR diffs reviewable; framework upgrade is its own engineering
risk that deserves dedicated review and test attention.

---

## 9. Implementation status

> Updated by each milestone PR.

- [ ] M1 — environment data model + REST API
- [ ] M2 — devtools console UI for environments
- [ ] M3 — global headers (model + API + UI)
- [ ] M4 — auth schemes + encrypted credentials
- [ ] M5 — try-it-out proxy + reader panel
- [ ] M6 — reader env switcher wiring
- [ ] M7 — audit log
- [ ] M8 — E2E tests + docs

---

## 10. References

- Devtools roadmap (Phase 1.1, 1.5, 1.6): `csap-apidoc-devtools/APIDOC开发工具产品路线图.md`
- Existing controller pattern: `csap-apidoc-devtools/src/main/java/ai/csap/apidoc/devtools/DevtoolsController.java`
- Reader layout: `csap-apidoc-ui/src/layouts/index.tsx` (970 LoC, top-level container)
- RBAC reference (sister product): `products/csap/src/agent-admin-web/backend/app/core/auth.py`
