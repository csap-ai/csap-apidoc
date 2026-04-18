# csap-apidoc-ui — E2E

Playwright happy-path coverage for **Environment + Headers + Auth + Try-it-out**
(M8.2). The dev server is auto-started by `playwright.config.ts`'s `webServer`
block, so a fresh checkout only needs:

```bash
npm install
npm run test:e2e:install   # one-time chromium download (~150 MB)
npm run test:e2e
```

`npm run test:e2e:ui` opens Playwright's interactive runner. Failed runs leave
traces + screenshots under `playwright-report/` and `test-results/` (both
gitignored).

The suite is single-browser (chromium) on purpose — it's a regression net, not
a cross-browser matrix. The doc-fetch endpoints and the outbound try-it-out
request are mocked via `page.route(...)`, so no real backend is required.
