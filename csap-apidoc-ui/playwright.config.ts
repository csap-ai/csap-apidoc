/**
 * Playwright config — bootstrapped in M8.2.
 *
 * Single-browser (chromium) regression suite that spawns the vite dev
 * server, drives Environment + Headers + Auth + Try-it-out together via
 * `page.route(...)` mocks instead of a real backend.
 *
 * Vite has no `VITE_PORT` set in `.env.development`, so the dev server
 * defaults to port 5173. If that ever changes, update DEV_PORT to match.
 */
import { defineConfig, devices } from '@playwright/test';

const DEV_PORT = 5173;
const BASE_URL = `http://127.0.0.1:${DEV_PORT}`;

export default defineConfig({
  testDir: './e2e',
  // First run pulls vite + builds the dep graph; bump per-test timeout to
  // tolerate that without making the whole suite feel slow on CI.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port ' + DEV_PORT,
    url: BASE_URL,
    // Cold-start of vite + heavy dep pre-bundle can take a while.
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
