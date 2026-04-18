# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

