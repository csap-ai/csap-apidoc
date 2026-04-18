# Example Project Files

## 📁 Project Structure

```
example-apidoc-spring-boot/
├── src/
│   ├── main/
│   │   ├── java/com/csap/framework/example/
│   │   │   ├── ExampleApplication.java                 # Main application class
│   │   │   ├── controller/
│   │   │   │   ├── UserController.java                 # User management APIs
│   │   │   │   └── ProductController.java              # Product management APIs
│   │   │   └── model/
│   │   │       ├── User.java                           # User entity
│   │   │       ├── UserStatus.java                     # User status enum
│   │   │       ├── Product.java                        # Product entity
│   │   │       └── Response.java                       # Common response wrapper
│   │   └── resources/
│   │       ├── application.yml                         # Default configuration
│   │       ├── application-sqlite.yml                  # SQLite strategy config
│   │       └── application-yaml.yml                    # YAML strategy config
│   └── test/
│       └── java/                                       # Test directory
├── api-test.http                                       # HTTP test file (IntelliJ/VS Code)
├── test-api.sh                                         # Automated test script
├── run.sh                                              # Run application script
├── pom.xml                                             # Maven configuration
├── README.md                                           # Project documentation
├── STRATEGIES.md                                       # Strategy comparison guide
├── FILES.md                                            # This file
└── .gitignore                                          # Git ignore rules
```

## 📝 File Descriptions

### Source Code

#### `ExampleApplication.java`
- Main Spring Boot application class
- Configured with `@EnableApidoc` annotation
- Demonstrates basic annotation usage

#### `UserController.java`
- Complete CRUD operations for users
- Demonstrates:
  - GET (list and details)
  - POST (create)
  - PUT (update)
  - PATCH (partial update)
  - DELETE (remove)
  - Query parameters
  - Path variables
  - Request body

#### `ProductController.java`
- Complete CRUD operations for products
- Additional features:
  - Complex filtering
  - BigDecimal for prices
  - Batch operations
  - Stock management

#### `User.java`
- User entity with validation
- Demonstrates:
  - `@ApiModelProperty` annotations
  - JSR-303 validations (@NotBlank, @Email, @Size, etc.)
  - Enum fields
  - DateTime fields

#### `Product.java`
- Product entity with validation
- Demonstrates:
  - BigDecimal for monetary values
  - @Digits validation
  - Boolean fields

#### `UserStatus.java`
- Enum with `@EnumValue` and `@EnumMessage` on fields
- Demonstrates correct enum usage:
  - `@EnumValue` on code field (database value)
  - `@EnumMessage` on message field (display text)
- Example: ACTIVE(1, "激活")

#### `ProductCategory.java`
- Another enum example
- Shows category enumeration with code and name

#### `Response.java`
- Generic response wrapper
- Provides consistent API responses

### Configuration Files

#### `application.yml`
- Default configuration
- ANNOTATION strategy (default)
- DevTools enabled

#### `application-sqlite.yml`
- SQLite strategy configuration
- Custom database path
- For dynamic parameter management

#### `application-yaml.yml`
- YAML strategy configuration
- Custom YAML file path
- For version-controlled documentation

### Testing Files

#### `api-test.http`
- HTTP test requests
- Can be used with:
  - IntelliJ IDEA HTTP Client
  - VS Code REST Client extension
- Covers all API endpoints

#### `test-api.sh`
- Automated test script using curl
- Tests major API operations
- Requires jq for JSON formatting

### Scripts

#### `run.sh`
- Builds and runs the application
- Checks for Maven installation
- Shows startup information

### Documentation

#### `README.md`
- Complete project documentation
- Quick start guide
- API examples
- Configuration examples

#### `STRATEGIES.md`
- Detailed strategy comparison
- Usage examples for each strategy
- Best practices
- Migration guide

#### `FILES.md`
- This file
- Project structure overview
- File descriptions

### Other Files

#### `.gitignore`
- Git ignore rules
- Excludes target/, IDE files, and generated databases

#### `pom.xml`
- Maven project configuration
- Dependencies declaration
- Spring Boot plugin configuration

## 🎯 Key Features Demonstrated

### ✅ Annotations
- `@EnableApidoc` - Enable API documentation
- `@Api` - Controller-level documentation
- `@ApiOperation` - Method-level documentation
- `@ApiProperty` - Parameter documentation
- `@ApiModel` - Entity documentation
- `@ApiModelProperty` - Field documentation
- `@EnumMessage` & `@EnumValue` - Enum documentation

### ✅ Validation
- `@NotBlank` - Not blank validation
- `@NotNull` - Not null validation
- `@Email` - Email format validation
- `@Pattern` - Regex pattern validation
- `@Size` - String length validation
- `@Min` & `@Max` - Numeric range validation
- `@Digits` - Decimal precision validation
- `@DecimalMin` - Decimal minimum value

### ✅ HTTP Methods
- GET - Retrieve resources
- POST - Create resources
- PUT - Update resources (full)
- PATCH - Update resources (partial)
- DELETE - Delete resources

### ✅ Parameter Types
- Path Variables - `@PathVariable`
- Query Parameters - `@RequestParam`
- Request Body - `@RequestBody`
- Headers - (can be added)

### ✅ Data Types
- String
- Integer/Long
- BigDecimal
- Boolean
- LocalDateTime
- Enum
- List/Array

## 🚀 Quick Commands

```bash
# Build project
mvn clean package

# Run with default (ANNOTATION) strategy
mvn spring-boot:run

# Run with SQLite strategy
mvn spring-boot:run -Dspring-boot.run.profiles=sqlite

# Run with YAML strategy
mvn spring-boot:run -Dspring-boot.run.profiles=yaml

# Run tests
./test-api.sh

# Make scripts executable
chmod +x run.sh test-api.sh
```

## 📚 Generated Files

When running with different strategies:

### ANNOTATION Strategy
- No external files generated
- Everything in memory

### SQLite Strategy
- `./csap-sqlite-data/example-sqlite-db.db` - SQLite database file

### YAML Strategy
- `./csap-yaml-docs/*.yaml` - YAML configuration files

## 🔗 Related Links

- [Main Documentation](../../README.md)
- [Quick Start Guide](../../QUICK_START.md)
- [Example README](./README.md)
- [Strategy Guide](./STRATEGIES.md)

---

**Total Files Created**: 17 files
**Lines of Code**: ~1500+ lines
**Test Cases**: 15+ HTTP test cases

