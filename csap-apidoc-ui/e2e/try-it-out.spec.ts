/**
 * M8.2 — Environment + Headers + Auth + Try-it-out happy path.
 *
 * Goal: regression-proof the four pieces of M1–M5 working together end-to-end.
 * Not exhaustive coverage; just one user-visible scenario per drawer plus
 * one Try-it-out send/response cycle, with enough assertions that any
 * major refactor breaking the wiring fails this test.
 *
 * Strategy:
 *   - Mock `/csap/apidoc/parent` + `/csap/apidoc/method` so we don't need a
 *     real backend (the doc viewer needs at least one endpoint to mount the
 *     right pane).
 *   - Mock the outbound Try-it-out URL host so the actual `Send` round-trip
 *     resolves with a fixture; capture the request to verify env/headers/auth
 *     wiring landed on the wire.
 *
 * The UI strings are Chinese; we use them verbatim in selectors.
 */
import { test, expect, type Request } from '@playwright/test';
import {
  mockApidocBackend,
  MOCK_SERVICE_NAME,
  MOCK_SERVICE_URL,
  MOCK_ENDPOINT_PATH,
} from './fixtures/apidoc';

const MOCK_BACKEND_HOST = 'http://localhost:9999';
const ENV_NAME = 'E2E Local';
const TRACE_HEADER_NAME = 'X-Trace-Id';
const TRACE_HEADER_VALUE = 'e2e-trace';
const FAKE_TOKEN = 'fake-jwt';

test.beforeEach(async ({ page }) => {
  // Stores persist to localStorage; clean slate per test so a stale env /
  // auth scheme from a previous run doesn't leak in.
  await page.addInitScript(() => {
    try {
      window.localStorage.clear();
    } catch {
      /* ignore — happens when origin not yet set */
    }
  });
  await mockApidocBackend(page);
});

test('happy path: env + global header + bearer auth flow into try-it-out', async ({
  page,
}) => {
  // ── 1. Land on the home page; wait for the apidoc tree to mount. ────────
  // The "试运行 v2" tab is rendered as a <div class="postGet">, not a
  // semantic <button>; lock onto the class + text combo for stability.
  const tryV2Tab = page.locator('.postGet').filter({ hasText: '试运行 v2' });
  await page.goto('/');
  await expect(tryV2Tab).toBeVisible({ timeout: 20_000 });

  // Pick the (mocked) service from the top-bar dropdown so `serviceRefId`
  // becomes the URL we'll bind auth against. Scope option search to the
  // currently-open Antd Select popup; otherwise leftover popups in the DOM
  // confuse the click attempt later in the test.
  await page.locator('.api-item').filter({ hasText: '切换服务' }).locator('.ant-select').click();
  await page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .filter({ hasText: MOCK_SERVICE_NAME })
    .first()
    .click();
  // Tree refetches after switching; just give it a beat to settle.
  await expect(tryV2Tab).toBeVisible();

  // ── 2. Create + activate the "E2E Local" environment. ──────────────────
  // Open the EnvironmentManagerDrawer via the gear icon (avoids fighting
  // the Select dropdown which only opens "管理环境..." entry on click).
  // Antd v4 Drawer doesn't expose a proper dialog accessible name, so we
  // identify drawers by their open class + title text.
  await page.locator('.env-switcher__gear').click();
  const envDialog = page
    .locator('.ant-drawer.ant-drawer-open')
    .filter({ has: page.locator('.ant-drawer-title', { hasText: '环境管理' }) });
  await expect(envDialog).toBeVisible();

  await envDialog.getByRole('button', { name: /新建环境/ }).click();
  // Name + base URL — Antd v4 Form labels aren't always linked via for/id,
  // so we target the inputs by placeholder which is deterministic here.
  await envDialog.getByPlaceholder('例如：Dev / Staging / Prod').fill(ENV_NAME);
  await envDialog
    .getByPlaceholder('https://api-staging.example.com')
    .fill(MOCK_BACKEND_HOST);

  // Add one variable: token=fake-jwt. The "新增" button inside the variables
  // section appends a row with two inputs (placeholders 如 tenantId / 如 42).
  await envDialog
    .locator('.env-drawer__variables')
    .getByRole('button', { name: '新增' })
    .click();
  const varNameInput = envDialog.locator('input[placeholder="如 tenantId"]').last();
  const varValueInput = envDialog.locator('input[placeholder="如 42"]').last();
  await varNameInput.fill('token');
  await varValueInput.fill(FAKE_TOKEN);

  // Save. The first env auto-activates so 设为当前 is disabled — click
  // it only when it isn't. Antd v4 inserts a hairline space between two
  // adjacent Chinese characters, so use \s* in the matchers.
  await envDialog.getByRole('button', { name: /保\s*存/ }).click();
  const setActiveBtn = envDialog.getByRole('button', { name: /设为当前/ });
  if (await setActiveBtn.isEnabled()) {
    await setActiveBtn.click();
  }
  // Close the drawer.
  await envDialog.locator('.ant-drawer-close').click();
  await expect(envDialog).toBeHidden();

  // Sanity: the top-bar Select now reflects the active env.
  await expect(page.locator('.env-switcher__label').getByText(ENV_NAME)).toBeVisible();

  // ── 3. Add a global X-Trace-Id header. ─────────────────────────────────
  // 3-char "请求头" — Antd doesn't split this one.
  await page.getByRole('button', { name: /请求头/ }).click();
  const headersDialog = page
    .locator('.ant-drawer.ant-drawer-open')
    .filter({ has: page.locator('.ant-drawer-title', { hasText: '全局请求头' }) });
  await expect(headersDialog).toBeVisible();
  // The "全局" tab is active by default; click the only "新增...请求头" button.
  await headersDialog.getByRole('button', { name: /新增全局请求头/ }).click();
  await headersDialog
    .locator('input[placeholder="如 X-Tenant-Id"]')
    .last()
    .fill(TRACE_HEADER_NAME);
  await headersDialog
    .locator('input[placeholder^="如 42"]')
    .last()
    .fill(TRACE_HEADER_VALUE);
  await headersDialog.locator('.ant-drawer-close').click();
  await expect(headersDialog).toBeHidden();

  // ── 4. Create a Bearer scheme using {{token}} and bind it to our service.
  // Antd splits 2-char CJK button text with a hairline space ("认 证").
  await page.getByRole('button', { name: /认\s*证/ }).click();
  const authDialog = page
    .locator('.ant-drawer.ant-drawer-open')
    .filter({ has: page.locator('.ant-drawer-title', { hasText: '认证方案' }) });
  await expect(authDialog).toBeVisible();
  await authDialog.getByRole('button', { name: /新建认证方案/ }).click();
  // Default name is "Scheme N" / type bearer — fine. Type the token.
  const tokenInput = authDialog.locator('input[placeholder="eyJhbGciOi..."]');
  await tokenInput.fill('{{token}}');
  // SecretInput commits to the vault on blur; tab away to trigger it.
  await tokenInput.blur();

  // Bind to the mocked service. The dropdown is the only ant-select within
  // the "服务绑定" section of the drawer.
  const bindingsSection = authDialog.locator('.auth-drawer__bindings');
  await bindingsSection.locator('.ant-select').click();
  await page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .filter({ hasText: MOCK_SERVICE_NAME })
    .first()
    .click();
  await bindingsSection.getByRole('button', { name: /绑\s*定/ }).click();

  // Save the scheme so its config (including tokenRef) is persisted.
  await authDialog.getByRole('button', { name: /保\s*存/ }).click();
  await authDialog.locator('.ant-drawer-close').click();
  await expect(authDialog).toBeHidden();

  // ── 5. Open Try-it-out v2 and verify the env-aware badges. ─────────────
  await tryV2Tab.click();

  // Badges row should reflect env name + auth scheme name.
  const tryout = page.locator('.tryout');
  await expect(tryout).toBeVisible();
  await expect(tryout.locator('.tryout__badges')).toContainText('环境');
  await expect(tryout.locator('.tryout__badges')).toContainText(ENV_NAME);
  await expect(tryout.locator('.tryout__badges')).toContainText('认证');

  // ── 6. Intercept the outbound request, click Send, verify wiring. ──────
  const captured: Array<{ url: string; method: string; headers: Record<string, string> }> = [];
  await page.route(`${MOCK_BACKEND_HOST}/**`, async (route) => {
    const req: Request = route.request();
    captured.push({
      url: req.url(),
      method: req.method(),
      headers: await req.allHeaders(),
    });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ ok: true, message: 'mocked' }),
    });
  });

  await tryout.getByRole('button', { name: /发\s*送/ }).click();

  // Status tag should show 200 — that's our primary success signal.
  await expect(tryout.locator('.tryout__status-tag')).toHaveText(/200/, {
    timeout: 15_000,
  });

  // Body tab should show the JSON we returned.
  await expect(tryout.locator('.tryout__response')).toContainText('mocked');

  // ── 7. Inspect the Raw tab — env + headers + auth must all have landed.
  await tryout.getByRole('tab', { name: /^Raw/ }).click();
  const rawPre = tryout.locator('.tryout__raw pre').first();
  // Env baseUrl prepended; endpoint path preserved as the final segment.
  // (The middle segment is layout-derived and not a contract under test.)
  await expect(rawPre).toContainText(MOCK_BACKEND_HOST);
  await expect(rawPre).toContainText(MOCK_ENDPOINT_PATH);
  // Both headers present (case-insensitive header names; we set them with
  // their canonical casing so the literal match is fine here).
  await expect(rawPre).toContainText(TRACE_HEADER_NAME);
  await expect(rawPre).toContainText(TRACE_HEADER_VALUE);
  await expect(rawPre).toContainText('Authorization');
  await expect(rawPre).toContainText(`Bearer ${FAKE_TOKEN}`);

  // Cross-check via the captured network request itself.
  expect(captured.length).toBeGreaterThan(0);
  const sent = captured[captured.length - 1];
  expect(sent.method).toBe('GET');
  expect(sent.url.startsWith(MOCK_BACKEND_HOST)).toBe(true);
  expect(sent.url.endsWith(MOCK_ENDPOINT_PATH)).toBe(true);
  // Header keys may be lower-cased by the fetch layer.
  const lowerHeaders: Record<string, string> = {};
  for (const [k, v] of Object.entries(sent.headers)) lowerHeaders[k.toLowerCase()] = v;
  expect(lowerHeaders[TRACE_HEADER_NAME.toLowerCase()]).toBe(TRACE_HEADER_VALUE);
  expect(lowerHeaders['authorization']).toBe(`Bearer ${FAKE_TOKEN}`);

  // Bind to the wildcard service is not exercised — that's a separate
  // negative path tracked for a future round.

  // Sanity: localStorage has persisted the env + auth scheme so a reload
  // would re-hydrate them. Lightweight check; full reload-and-verify is
  // the optional bonus path that requires the encrypted vault flow.
  const persisted = await page.evaluate(() => ({
    env: window.localStorage.getItem('csap-apidoc:environments'),
    auth: window.localStorage.getItem('csap-apidoc:auth-schemes'),
    headers: window.localStorage.getItem('csap-apidoc:headers'),
  }));
  expect(persisted.env).toContain(ENV_NAME);
  expect(persisted.auth).toContain('bearer');
  expect(persisted.headers).toContain(TRACE_HEADER_NAME);
});
