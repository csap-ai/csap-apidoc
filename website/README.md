# CSAP Apidoc 官网

<https://apidoc.csap.ai> 的源代码,使用 [Rspress](https://rspress.dev) 构建。推送到 `main` 后由 `.github/workflows/docs.yml` 自动发布到 GitHub Pages。

## 本地预览

```bash
cd website
npm ci
npm run dev       # http://localhost:5173
npm run build     # 产物在 doc_build/,构建时会检查站内死链
```

## 目录

| 路径 | 内容 |
|---|---|
| `docs/index.md` | 首页 |
| `docs/guide/` | 指南(开始、核心功能、进阶) |
| `docs/api/` | 参考(注解、REST 接口、Java 扩展点) |
| `docs/go/` | Go 版 |
| `docs/download/` | 获取方式 |
| `docs/changelog.md` | 发布说明,构建时由 `scripts/sync.mjs` 从仓库根目录的 `CHANGELOG.md` 生成,不要手改 |
| `docs/public/` | 静态资源;`CNAME` 是自定义域名 |
| `rspress.config.ts` | 导航、侧边栏 |

## 写作约定

- 以代码为准:配置项、注解属性、类名、路径都要能在仓库里找到。
- 站内链接写 `/guide/xxx`,不带 `.md`。
- 新增页面后在 `rspress.config.ts` 的侧边栏里登记。
