# CSAP Framework API Doc - Architecture

This document describes the architecture and design principles of CSAP Framework API Doc.

## Table of Contents

- [Overview](#overview)
- [Architecture Diagram](#architecture-diagram)
- [Core Components](#core-components)
- [Module Structure](#module-structure)
- [Data Flow](#data-flow)
- [Storage Strategies](#storage-strategies)
- [Extension Points](#extension-points)
- [Design Principles](#design-principles)

## Overview

CSAP Framework API Doc is a modular, extensible API documentation framework for Spring Boot applications. It follows a clean architecture approach with clear separation of concerns.

### Key Characteristics

- **Zero Intrusion**: Uses annotations without modifying existing code
- **Pluggable**: Modular design with swappable components
- **Extensible**: Multiple extension points for customization
- **Modern**: Built with modern technologies (React 18, Spring Boot 3)

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Application                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CSAP Apidoc Framework                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌────────────────┐      ┌────────────────┐                     │
│  │   Annotation   │─────▶│    Scanner     │                     │
│  │    Processor   │      │    Engine      │                     │
│  └────────────────┘      └────────────────┘                     │
│          │                       │                               │
│          │                       ▼                               │
│          │              ┌────────────────┐                       │
│          └─────────────▶│   Parser &     │                       │
│                         │   Converter    │                       │
│                         └────────────────┘                       │
│                                 │                                │
│                                 ▼                                │
│                         ┌────────────────┐                       │
│                         │  Document      │                       │
│                         │  Generator     │                       │
│                         └────────────────┘                       │
│                                 │                                │
│         ┌───────────────────────┼───────────────────────┐       │
│         ▼                       ▼                       ▼        │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐   │
│  │   SQLite    │       │    YAML     │       │  Standard   │   │
│  │  Strategy   │       │  Strategy   │       │  Strategy   │   │
│  └─────────────┘       └─────────────┘       └─────────────┘   │
│         │                       │                       │        │
│         └───────────────────────┼───────────────────────┘       │
│                                 │                                │
│                                 ▼                                │
│                         ┌────────────────┐                       │
│                         │   REST API     │                       │
│                         └────────────────┘                       │
│                                 │                                │
└─────────────────────────────────┼────────────────────────────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                ▼                 ▼                 ▼
        ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
        │   DevTools   │  │   Web UI     │  │   Postman    │
        │  (React UI)  │  │  (React UI)  │  │  Converter   │
        └──────────────┘  └──────────────┘  └──────────────┘
```

## Core Components

### 1. Annotation Processor

**Location**: `csap-framework-apidoc-annotation`

**Responsibilities**:
- Define documentation annotations
- Provide annotation metadata
- Support validation annotations

**Key Classes**:
- `@Api`: Controller-level API documentation
- `@ApiOperation`: Method-level API documentation
- `@ApiModel`: Data model documentation
- `@ApiModelProperty`: Field-level documentation
- `@ApiProperty`: Parameter documentation

### 2. Scanner Engine

**Location**: `csap-framework-apidoc-core`

**Responsibilities**:
- Scan Spring MVC controllers
- Detect API endpoints
- Extract method signatures
- Process annotations

**Key Classes**:
- `ApidocScanner`: Main scanning logic
- `ControllerScanner`: Controller detection
- `MethodScanner`: Method analysis

### 3. Parser & Converter

**Location**: `csap-framework-apidoc-core`

**Responsibilities**:
- Parse Java types
- Convert to documentation models
- Handle nested structures
- Process validation rules

**Key Classes**:
- `TypeParser`: Java type parsing
- `ModelConverter`: Model conversion
- `ValidationProcessor`: JSR-303 integration

### 4. Document Generator

**Location**: `csap-framework-apidoc-core`

**Responsibilities**:
- Generate documentation data
- Format output
- Support multiple formats

**Key Classes**:
- `DocGenerator`: Main generation logic
- `OpenAPIGenerator`: OpenAPI format
- `PostmanConverterService`: Postman format

### 5. Storage Strategy

**Location**: `csap-framework-apidoc-strategy`

**Responsibilities**:
- Persist documentation
- Load documentation
- Support multiple backends

**Implementations**:
- **SQLite**: File-based database storage
- **YAML**: Human-readable file storage
- **Standard**: Simple JSON output

**Interface**:
```java
public interface ApidocStorageStrategy {
    void save(CsapDocParentResponse docData);
    CsapDocParentResponse load();
}
```

### 6. REST API

**Location**: `csap-framework-apidoc-core`

**Responsibilities**:
- Expose documentation data
- Handle API requests
- Serve static resources

**Endpoints**:
- `GET /api/csap/doc`: Get all documentation
- `GET /api/csap/doc/{id}`: Get specific API doc
- `POST /api/csap/doc/save`: Save configuration

### 7. Web UI

**Location**: `csap-framework-apidoc-web`, `csap-framework-apidoc-devtools`

**Responsibilities**:
- Display documentation
- Provide online testing
- Enable configuration management

**Tech Stack**:
- React 18
- TypeScript 5.3
- Ant Design 5
- Zustand (state management)
- Axios (HTTP client)

## Module Structure

```
csap-framework-apidoc/
├── csap-framework-apidoc-annotation     # Annotation definitions
├── csap-framework-apidoc-common         # Common utilities
├── csap-framework-apidoc-core           # Core logic
│   ├── scanner/                         # Scanning logic
│   ├── parser/                          # Parsing logic
│   ├── generator/                       # Generation logic
│   └── service/                         # Service layer
├── csap-framework-apidoc-boot           # Spring Boot integration
│   ├── csap-framework-apidoc-boot-starter
│   └── csap-framework-validation-boot-starter
├── csap-framework-apidoc-strategy       # Storage strategies
│   ├── csap-framework-apidoc-sqlite
│   ├── csap-framework-apidoc-yaml
│   └── csap-framework-apidoc-standard
├── csap-framework-apidoc-devtools       # DevTools UI
├── csap-framework-apidoc-web            # Web UI
└── csap-framework-validation-core       # Validation integration
```

## Data Flow

### 1. Initialization Flow

```
Application Startup
    │
    ▼
RunApplicationListener
    │
    ▼
Scan Controllers (base-package)
    │
    ▼
Process Annotations
    │
    ▼
Parse Types & Models
    │
    ▼
Generate Documentation
    │
    ▼
Save to Storage (Strategy)
    │
    ▼
Serve REST API
```

### 2. Request Flow

```
Browser Request
    │
    ▼
REST Controller
    │
    ▼
Load from Storage
    │
    ▼
Format Response
    │
    ▼
Return JSON
    │
    ▼
React UI Renders
```

### 3. Configuration Flow

```
User Edits in UI
    │
    ▼
POST to REST API
    │
    ▼
Validate Input
    │
    ▼
Update Model
    │
    ▼
Save to Storage
    │
    ▼
Broadcast Update (WebSocket)
    │
    ▼
UI Refreshes
```

## Storage Strategies

### Strategy Pattern Implementation

```java
public interface ApidocStorageStrategy {
    void save(CsapDocParentResponse docData);
    CsapDocParentResponse load();
}

// SQLite Implementation
@Component
@ConditionalOnProperty(name = "csap.apidoc.storage-strategy", havingValue = "sqlite")
public class SqliteStorageStrategy implements ApidocStorageStrategy {
    // Implementation
}

// YAML Implementation
@Component
@ConditionalOnProperty(name = "csap.apidoc.storage-strategy", havingValue = "yaml")
public class YamlStorageStrategy implements ApidocStorageStrategy {
    // Implementation
}
```

### Comparison

| Strategy | Pros | Cons | Use Case |
|----------|------|------|----------|
| **SQLite** | Fast, indexed, persistent | Binary format | Production, performance-critical |
| **YAML** | Human-readable, version control | Slower parsing | Development, documentation |
| **Standard** | Simple, no dependencies | No persistence | Testing, debugging |

## Extension Points

### 1. Custom Storage Strategy

Implement `ApidocStorageStrategy` interface:

```java
@Component
public class RedisStorageStrategy implements ApidocStorageStrategy {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Override
    public void save(CsapDocParentResponse docData) {
        redisTemplate.opsForValue().set("apidoc", docData);
    }
    
    @Override
    public CsapDocParentResponse load() {
        return (CsapDocParentResponse) redisTemplate.opsForValue().get("apidoc");
    }
}
```

### 2. Custom Annotation Processor

Extend annotation processing:

```java
@Component
public class CustomAnnotationProcessor {
    
    @EventListener(ApplicationStartedEvent.class)
    public void processCustomAnnotations() {
        // Custom processing logic
    }
}
```

### 3. Custom Export Format

Implement custom exporters:

```java
@Service
public class CustomExportService {
    
    public String exportToCustomFormat(CsapDocParentResponse apiDoc) {
        // Custom export logic
        return customFormatString;
    }
}
```

### 4. Custom UI Components

Extend React components:

```typescript
// Custom field renderer
export const CustomFieldRenderer: React.FC<FieldProps> = ({ field }) => {
  return (
    <div className="custom-field">
      {/* Custom rendering logic */}
    </div>
  );
};
```

## Design Principles

### 1. Separation of Concerns

Each module has a single, well-defined responsibility:
- **Annotation**: Define metadata
- **Core**: Process and generate
- **Storage**: Persist data
- **UI**: Display and interact

### 2. Open/Closed Principle

Open for extension, closed for modification:
- Strategy pattern for storage
- Plugin system for exporters
- Component-based UI

### 3. Dependency Inversion

Depend on abstractions, not concretions:
- `ApidocStorageStrategy` interface
- Spring dependency injection
- Loose coupling between modules

### 4. Single Responsibility

Each class has one reason to change:
- Scanner only scans
- Parser only parses
- Generator only generates

### 5. Don't Repeat Yourself (DRY)

Common functionality extracted to:
- `csap-framework-apidoc-common`
- Shared utilities
- Reusable components

## Performance Considerations

### 1. Lazy Initialization

- Documentation generated on first access
- Asynchronous processing
- Caching strategies

### 2. Efficient Scanning

- Package-based filtering
- Incremental scanning
- Annotation indexing

### 3. Optimized Storage

- Indexed database queries
- Compressed YAML
- Connection pooling

### 4. Frontend Optimization

- Code splitting
- Lazy loading
- Virtual scrolling for large lists
- Memoization

## Security Considerations

### 1. Input Validation

- All user inputs validated
- Sanitization before storage
- XSS prevention

### 2. Access Control

- Optional authentication
- IP whitelisting
- Role-based permissions (planned)

### 3. Data Protection

- Sensitive field masking
- Secure storage
- HTTPS enforcement

## Future Architecture Evolution

### Phase 1: Enhanced Core (v2.0)
- Improved caching
- Better performance
- Enhanced validation

### Phase 2: Collaboration (v3.0)
- WebSocket real-time sync
- Version control
- Team features

### Phase 3: Enterprise (v4.0)
- Microservices support
- Distributed architecture
- Advanced monitoring

---

For more details on specific modules, see:
- [README.md](README.md) - General overview
- [CONTRIBUTING.md](CONTRIBUTING.md) - Development guidelines
- [Product Roadmap](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md) - Future plans

