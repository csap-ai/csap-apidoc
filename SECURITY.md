# Security Policy

## Supported Versions

We release patches for security vulnerabilities for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.3   | :white_check_mark: |
| 1.0.2   | :white_check_mark: |
| 1.0.1   | :x:                |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of CSAP Framework API Doc seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Please DO NOT

- **Do not** open a public GitHub issue for security vulnerabilities
- **Do not** disclose the vulnerability publicly until we've had a chance to address it

### Please DO

**Report security vulnerabilities via email to: security@csap.com**

Please include the following information in your report:

- Type of vulnerability (e.g., XSS, SQL injection, authentication bypass)
- Full paths of source file(s) related to the vulnerability
- Location of the affected source code (tag/branch/commit or direct URL)
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit it

### What to Expect

1. **Acknowledgment**: We will acknowledge receipt of your vulnerability report within 48 hours.

2. **Initial Assessment**: We will provide an initial assessment of the report within 5 business days, including:
   - Confirmation of the vulnerability
   - Severity assessment (Critical/High/Medium/Low)
   - Expected timeline for a fix

3. **Progress Updates**: We will keep you informed about the progress of fixing the vulnerability.

4. **Resolution**: Once the vulnerability is fixed:
   - We will notify you before public disclosure
   - We will credit you in the release notes (if you wish)
   - We will publish a security advisory

5. **Public Disclosure**: We aim to disclose vulnerabilities within 90 days of the initial report.

## Security Best Practices

### For Users

When using CSAP Framework API Doc in production:

1. **Disable Documentation in Production**
   ```yaml
   # application-prod.yml
   csap:
     apidoc:
       enabled: false
   ```

2. **Enable Authentication**
   ```yaml
   csap:
     apidoc:
       security:
         enabled: true
         username: admin
         password: strong-password-here
   ```

3. **Use IP Whitelisting**
   ```yaml
   csap:
     apidoc:
       security:
         allowed-ips:
           - 127.0.0.1
           - 192.168.1.0/24
   ```

4. **Enable Data Masking**
   ```java
   @ApiModelProperty(value = "Phone", sensitive = true, maskPattern = "phone")
   private String phone;
   ```

5. **Keep Dependencies Updated**
   - Regularly update to the latest version
   - Monitor security advisories

6. **Use HTTPS**
   - Always use HTTPS in production
   - Configure proper SSL/TLS certificates

7. **Review Access Logs**
   - Monitor who accesses the documentation
   - Check for suspicious activity

### For Contributors

When contributing code:

1. **Never commit sensitive data**
   - No passwords, API keys, or tokens
   - Use environment variables for sensitive config

2. **Validate all inputs**
   - Sanitize user inputs
   - Use parameterized queries
   - Validate file uploads

3. **Follow secure coding practices**
   - Use prepared statements for database queries
   - Implement proper error handling
   - Avoid exposing stack traces

4. **Review dependencies**
   - Check for known vulnerabilities
   - Keep dependencies up to date
   - Use tools like OWASP Dependency-Check

5. **Handle authentication properly**
   - Use strong password hashing (BCrypt)
   - Implement rate limiting
   - Use secure session management

## Known Security Considerations

### 1. Information Disclosure

**Risk**: API documentation exposes API endpoints and data structures.

**Mitigation**:
- Disable in production environments
- Use authentication
- Apply IP whitelisting

### 2. Data Masking

**Risk**: Documentation might expose sensitive data in examples.

**Mitigation**:
- Use the data masking feature
- Mark sensitive fields appropriately
- Review generated documentation

### 3. Cross-Site Scripting (XSS)

**Risk**: User-provided content in documentation could execute scripts.

**Mitigation**:
- All user inputs are sanitized
- Content Security Policy headers
- Regular security audits

### 4. SQL Injection (SQLite Storage)

**Risk**: Malformed data could lead to SQL injection.

**Mitigation**:
- Parameterized queries used throughout
- Input validation on all data
- Regular security testing

### 5. Dependency Vulnerabilities

**Risk**: Third-party dependencies might have vulnerabilities.

**Mitigation**:
- Regular dependency updates
- Security scanning in CI/CD
- Monitoring security advisories

## Security Updates

Security updates are released as soon as possible after a vulnerability is confirmed. We use the following severity levels:

- **Critical**: Immediate patch release, public advisory
- **High**: Patch within 7 days, public advisory
- **Medium**: Patch in next minor release
- **Low**: Patch in next release

## Acknowledgments

We would like to thank the following security researchers for responsibly disclosing vulnerabilities:

<!-- Security researchers will be listed here -->

## Contact

For security-related questions or concerns:
- **Email**: security@csap.com
- **PGP Key**: [Coming soon]

For general questions, please use:
- **GitHub Issues**: For bugs and features
- **Email**: support@csap.com

---

Last updated: 2025-10-20

