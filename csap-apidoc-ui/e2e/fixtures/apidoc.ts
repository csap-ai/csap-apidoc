/**
 * Apidoc API fixtures — minimal payloads modelled after the real
 * `/csap/apidoc/parent` and `/csap/apidoc/method` responses produced by
 * `csap-apidoc-devtools`. Just enough surface for the doc viewer to render
 * one service / one group / one endpoint with one header row, which is
 * what the right-pane panel needs to mount the "试运行 v2" tab.
 *
 * Keep the mock service URL stable — it doubles as the `serviceRefId`
 * used for auth bindings inside the test.
 */
import type { Page, Route } from '@playwright/test';

/** Stable identifier used both as the doc-fetch URL and the auth binding key. */
export const MOCK_SERVICE_URL = '/api/csap/apidoc/parent';
export const MOCK_SERVICE_NAME = 'demo-service';
export const MOCK_ENDPOINT_PATH = '/test';
export const MOCK_ENDPOINT_KEY = 'endpoint-test';

const PARENT_BODY = {
  code: '0',
  message: 'ok',
  data: {
    apiList: [
      {
        key: 'group-demo',
        title: 'Demo Group',
        children: [
          {
            key: MOCK_ENDPOINT_KEY,
            title: 'Test Endpoint',
            method: 'GET',
            path: MOCK_ENDPOINT_PATH,
          },
        ],
      },
    ],
    resources: [
      {
        url: MOCK_SERVICE_URL,
        name: MOCK_SERVICE_NAME,
        version: 'v1',
      },
    ],
  },
};

const METHOD_BODY = {
  code: '0',
  message: 'ok',
  data: {
    title: 'Test Endpoint',
    patch: MOCK_ENDPOINT_PATH,
    method: 'GET',
    paramType: 'DEFAULT',
    headers: [
      {
        key: 'Accept',
        value: 'application/json',
        required: false,
        example: '',
        description: 'Demo header',
      },
    ],
    request: [],
    response: [],
  },
};

/**
 * Install `page.route()` handlers for both apidoc endpoints so the page
 * loads without a real backend. Must be called BEFORE `page.goto('/')`.
 */
export async function mockApidocBackend(page: Page): Promise<void> {
  await page.route('**/csap/apidoc/parent**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(PARENT_BODY),
    });
  });
  await page.route('**/csap/apidoc/method**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(METHOD_BODY),
    });
  });
}
