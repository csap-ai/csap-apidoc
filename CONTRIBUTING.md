# Contributing to CSAP Framework API Doc

First off, thank you for considering contributing to CSAP Framework API Doc! It's people like you that make it such a great tool.

## Table of Contents

- [Contributing to CSAP Framework API Doc](#contributing-to-csap-framework-api-doc)
  - [Table of Contents](#table-of-contents)
  - [Code of Conduct](#code-of-conduct)
  - [How Can I Contribute?](#how-can-i-contribute)
    - [Reporting Bugs](#reporting-bugs)
    - [Suggesting Enhancements](#suggesting-enhancements)
    - [Pull Requests](#pull-requests)
  - [Development Setup](#development-setup)
    - [Prerequisites](#prerequisites)
    - [Backend Setup](#backend-setup)
    - [Frontend Setup (DevTools)](#frontend-setup-devtools)
    - [Running the Example](#running-the-example)
  - [Style Guidelines](#style-guidelines)
    - [Java Code Style](#java-code-style)
    - [TypeScript/JavaScript Code Style](#typescriptjavascript-code-style)
    - [CSS/SCSS Style](#cssscss-style)
  - [Commit Messages](#commit-messages)
  - [Testing](#testing)
    - [Running Tests](#running-tests)
    - [Writing Tests](#writing-tests)
  - [Documentation](#documentation)
  - [Questions?](#questions)
  - [License](#license)

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to support@csap.com.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples to demonstrate the steps**
- **Describe the behavior you observed and what you expected**
- **Include screenshots if possible**
- **Include your environment details** (OS, Java version, Spring Boot version, etc.)

**Template for Bug Reports:**

```markdown
**Description:**
A clear description of what the bug is.

**To Reproduce:**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected behavior:**
A clear description of what you expected to happen.

**Screenshots:**
If applicable, add screenshots to help explain your problem.

**Environment:**
- OS: [e.g. macOS 14.0]
- Java Version: [e.g. Java 17]
- Spring Boot Version: [e.g. 3.2.0]
- CSAP Apidoc Version: [e.g. 1.0.3]

**Additional context:**
Add any other context about the problem here.
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- **Use a clear and descriptive title**
- **Provide a detailed description of the suggested enhancement**
- **Explain why this enhancement would be useful**
- **List some examples of how it would be used**

**Template for Feature Requests:**

```markdown
**Feature Description:**
A clear description of the feature you'd like to see.

**Problem it Solves:**
Describe the problem this feature would solve.

**Proposed Solution:**
Describe how you envision this feature working.

**Alternatives Considered:**
Describe any alternative solutions you've considered.

**Additional Context:**
Add any other context, mockups, or examples about the feature request.
```

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Follow the coding style** of the project
3. **Write clear, descriptive commit messages**
4. **Include tests** for any new functionality
5. **Update documentation** as needed
6. **Ensure all tests pass** before submitting
7. **Link related issues** in your PR description

**PR Template:**

```markdown
## Description
Brief description of what this PR does.

## Related Issues
Closes #(issue number)

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
Describe the tests you ran and how to reproduce them.

## Checklist
- [ ] My code follows the style guidelines of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
```

## Development Setup

### Prerequisites

- JDK 8 or higher
- Maven 3.6+
- Node.js 16+ (for frontend development)
- Git

### Backend Setup

```bash
# Clone the repository
git clone https://github.com/csap-ai/csap-framework-apidoc.git
cd csap-framework-apidoc

# Build the project
mvn clean install

# Run tests
mvn test
```

### Frontend Setup (DevTools)

```bash
# Navigate to devtools directory
cd csap-framework-apidoc-devtools/devtools

# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build
```

### Running the Example

```bash
# Navigate to example directory
cd example/example-apidoc-spring-boot

# Run the example application
mvn spring-boot:run

# Access the documentation
# http://localhost:8080/csap-api.html
```

## Style Guidelines

### Java Code Style

- Follow standard Java naming conventions
- Use 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Use `@Override` annotation when appropriate

**Example:**

```java
/**
 * Converts API documentation to Postman collection format.
 *
 * @param apiDoc the API documentation to convert
 * @return Postman collection JSON string
 * @throws IllegalArgumentException if apiDoc is null
 */
public String convertToPostmanCollection(CsapDocParentResponse apiDoc) {
    if (apiDoc == null) {
        throw new IllegalArgumentException("API documentation cannot be null");
    }
    // Implementation
}
```

### TypeScript/JavaScript Code Style

- Use 2 spaces for indentation
- Use semicolons
- Use single quotes for strings
- Use meaningful variable and function names
- Add JSDoc comments for exported functions
- Use TypeScript types/interfaces

**Example:**

```typescript
/**
 * Fetches API documentation from the server
 * @param projectId - The ID of the project
 * @returns Promise with the API documentation
 */
export async function fetchApiDoc(projectId: string): Promise<ApiDoc> {
  const response = await request.get(`/api/csap/doc/${projectId}`);
  return response.data;
}
```

### CSS/SCSS Style

- Use 2 spaces for indentation
- Use kebab-case for class names
- Group related properties
- Use variables for colors and common values

## Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Code style changes (formatting, semicolons, etc.)
- `refactor`: Code refactoring without adding features or fixing bugs
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `chore`: Maintenance tasks (build, dependencies, etc.)

**Examples:**

```
feat(api): add support for JSON Schema export

fix(devtools): resolve duplicate event handling issue

docs(readme): update installation instructions

refactor(core): simplify document scanning logic
```

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
cd csap-framework-apidoc-core
mvn test

# Run specific test class
mvn test -Dtest=PostmanConverterServiceTest

# Run frontend tests
cd csap-framework-apidoc-devtools/devtools
npm test
```

### Writing Tests

- Write unit tests for all new functionality
- Aim for high code coverage (>80%)
- Use meaningful test names that describe what is being tested
- Follow the Arrange-Act-Assert pattern

**Example:**

```java
@Test
public void testConvertToPostmanCollection_WithValidInput_ShouldReturnValidJson() {
    // Arrange
    CsapDocParentResponse apiDoc = createTestApiDoc();
    
    // Act
    String result = postmanService.convertToPostmanCollection(apiDoc);
    
    // Assert
    assertNotNull(result);
    assertTrue(result.contains("\"info\""));
    assertTrue(result.contains("\"item\""));
}
```

## Documentation

- Update README.md for significant changes
- Add JSDoc/JavaDoc comments for public APIs
- Update relevant documentation in the `/docs` folder
- Include code examples where appropriate

## Questions?

Feel free to:
- Open an issue for questions
- Join our community discussions
- Email us at support@csap.com

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for contributing to CSAP Framework API Doc! 🎉

