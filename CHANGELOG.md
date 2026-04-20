# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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

## [1.0.3] - 2025-10-20

### Added
- Complete documentation generation framework
- React 18 + TypeScript + Vite frontend
- Ant Design 5 UI components
- DevTools for API management
- Postman collection converter service
- Multiple storage strategies (SQLite, YAML, Standard)
- JSR-303 validation integration
- Data masking for sensitive fields
- Online API testing capability
- Multi-format export (OpenAPI, Swagger, Markdown)
- Comprehensive annotation system

### Enhanced
- Improved TypeScript migration for frontend
- Better error handling and logging
- Optimized documentation scanning performance
- Enhanced UI/UX with modern design patterns

### Fixed
- Fixed static resource access issues
- Resolved duplicate event handling in DevTools
- Fixed 404 problems with resource routing
- Improved controller method detection

### Documentation
- Added comprehensive README in Chinese and English
- Created Postman converter documentation
- Added product roadmap and feature planning
- Included migration guides for TypeScript

## [1.0.2] - 2024-12-15

### Added
- Basic API documentation generation
- Annotation support for Spring MVC
- SQLite storage implementation
- Initial DevTools interface

### Fixed
- Various bug fixes and improvements

## [1.0.1] - 2024-11-10

### Added
- Initial release with core features
- Basic annotation system
- Document scanning capability

## [1.0.0] - 2024-10-01

### Added
- Project initialization
- Core framework setup
- Basic structure and modules

---

## Upcoming Features

### v2.0 (Planned)

#### Core Enhancements
- [ ] Enhanced online API testing
  - Environment management
  - Test case saving
  - Request history
  
- [ ] Field Library and Templates
  - Predefined field templates
  - Custom template creation
  - Team template sharing
  
- [ ] Mock Data Generation
  - Automatic mock data based on field types
  - Custom mock rules
  - Batch data generation
  
- [ ] Enhanced Documentation Export
  - JSON Schema import/export
  - TypeScript type generation
  - Improved Markdown export
  
- [ ] Data Security
  - Enhanced field masking
  - Sensitive field marking
  - API operation logging
  
- [ ] UI/UX Improvements
  - Keyboard shortcuts
  - Dark mode
  - Internationalization (i18n)
  - Drag-and-drop field ordering
  - Batch operations
  - Global search

### v3.0 (Future)

#### Collaboration & Automation
- [ ] Version Management
  - API configuration versioning
  - Change tracking and diff view
  - Version rollback capability
  
- [ ] Automated Scanning
  - Automatic API discovery
  - Change detection
  - Javadoc extraction
  
- [ ] Team Collaboration
  - Multi-user editing
  - Comments and mentions
  - Change notifications
  
- [ ] API Lifecycle Management
  - Status management (dev/test/prod)
  - Scheduled deployment
  - Deprecation warnings

### v4.0 (Vision)

#### Enterprise Features
- [ ] Fine-grained Permissions
  - Role-based access control
  - Resource-level permissions
  - Approval workflows
  
- [ ] Security & Rate Limiting
  - IP whitelist/blacklist
  - Sentinel integration
  - Rate limiting rules
  - Circuit breaker
  
- [ ] API Monitoring (Multi-service SaaS)
  - Service registration
  - Call statistics
  - Performance analysis
  - Alert notifications
  - Custom dashboards
  
- [ ] Automated Testing + AI
  - Test case management
  - Test scenario orchestration
  - AI-generated test cases
  - Smart assertions
  
- [ ] Integration Capabilities
  - Git integration
  - CI/CD integration
  - Third-party notifications (DingTalk, WeChat, Slack)
  - JIRA integration
  
- [ ] Private Deployment
  - Docker images
  - Kubernetes support
  - Backup and recovery
  - Monitoring and alerting

---

## Migration Guides

### Migrating from 1.0.x to 1.0.3

No breaking changes. Simply update your Maven dependency:

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

---

For more details on planned features, see [Product Roadmap](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md)

