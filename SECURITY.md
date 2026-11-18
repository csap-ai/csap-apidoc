# Security Policy / 安全策略

## Supported versions / 支持的版本

| Version | Supported |
| ------- | --------- |
| 1.2.x   | ✅ |
| 1.1.x   | ❌ (superseded by 1.2.x / 已被 1.2.x 取代) |
| < 1.1   | ❌ (never published to Maven Central / 未发布到 Maven Central) |

Security fixes are released as a new patch version of the latest minor line.

安全修复以最新次版本线的补丁版本发布。

## Reporting a vulnerability / 报告漏洞

**Please do not open a public issue for security problems.** / **请不要用公开 Issue 报告安全问题。**

1. **Preferred / 首选**: GitHub private vulnerability reporting — open <https://github.com/csap-ai/csap-apidoc/security/advisories/new>. Only the maintainers can see the report.
   通过 GitHub 私密漏洞报告提交,只有维护者可见。
2. **Alternative / 备用**: email **support@csap.ai** with the subject `[SECURITY] csap-apidoc`.
   发送邮件至 **support@csap.ai**,标题写 `[SECURITY] csap-apidoc`。

Please include / 请尽量包含:

- the affected version and module / 受影响的版本和模块
- steps to reproduce, or a proof of concept / 复现步骤或概念验证
- the impact as you understand it / 你理解的影响范围

## What to expect / 处理流程

- We acknowledge the report and start the assessment as soon as possible, and keep you updated while we work on it.
  我们会尽快确认收到并开始评估,处理过程中保持沟通。
- Once fixed, we publish a patch release and a GitHub Security Advisory, and credit you unless you prefer to stay anonymous.
  修复后发布补丁版本和 GitHub 安全公告;除非你希望匿名,我们会在公告中致谢。

## Deployment notes / 部署注意事项

- **DevTools is for development only.** It reads and writes rule files and exposes class inspection endpoints (`/csap/yaml/*`, `/devtools/*`). Keep `csap.apidoc.devtool.enabled` off in production (it is off by default).
  **DevTools 只用于开发环境**:它会读写规则文件,并提供类结构查询接口。生产环境保持 `csap.apidoc.devtool.enabled` 关闭(默认关闭)。
- **The docs endpoints are public by default.** `/csap-api.html` and `/csap/apidoc*` have no authentication of their own. If your API structure is sensitive, protect these paths with your application's security (for example Spring Security) or do not expose them outside your network.
  **文档接口默认对外开放**:`/csap-api.html`、`/csap/apidoc*` 本身不做鉴权。如果接口结构属于敏感信息,请用应用自身的安全机制(如 Spring Security)保护这些路径,或不对外网开放。
- **Try-it-out secrets stay in the browser.** Environments, headers and credentials used by the docs page are stored in the browser's local storage; the optional vault encrypts them with a passphrase. Do not use production credentials on shared machines.
  **在线调试的凭据保存在浏览器本地**:可选的保险库会用口令加密。不要在公用电脑上使用生产环境凭据。
