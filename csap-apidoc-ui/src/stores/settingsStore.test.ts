// @vitest-environment jsdom

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  DEFAULT_SETTINGS,
  SETTINGS_STORAGE_KEY,
  settingsStore,
} from './settingsStore';

beforeEach(() => {
  window.localStorage.clear();
  settingsStore.reset();
  vi.resetModules();
});

describe('settingsStore.getState', () => {
  it('returns DEFAULT_SETTINGS when nothing is persisted', () => {
    const s = settingsStore.getState();
    expect(s).toEqual(DEFAULT_SETTINGS);
  });

  it('defaults tryItOutWithCredentials to false (privacy-safe)', () => {
    expect(DEFAULT_SETTINGS.tryItOutWithCredentials).toBe(false);
    expect(settingsStore.getState().tryItOutWithCredentials).toBe(false);
  });
});

/**
 * Cold-start tests use vi.resetModules() so that the module's lazily-
 * initialised `cache` is forced to read from localStorage on first access.
 * That's the only path that exercises `safeRead` + `isValidSettings`.
 */
describe('settingsStore cold-start', () => {
  it('hydrates from a valid persisted payload', async () => {
    window.localStorage.setItem(
      SETTINGS_STORAGE_KEY,
      JSON.stringify({
        ...DEFAULT_SETTINGS,
        language: 'fr',
        vaultMode: 'encrypted',
        tryItOutProxyUrl: 'http://proxy:8080',
      }),
    );
    vi.resetModules();
    const mod = await import('./settingsStore');
    const s = mod.settingsStore.getState();
    expect(s.language).toBe('fr');
    expect(s.vaultMode).toBe('encrypted');
    expect(s.tryItOutProxyUrl).toBe('http://proxy:8080');
  });

  it('falls back to DEFAULT_SETTINGS when persisted JSON is malformed', async () => {
    window.localStorage.setItem(SETTINGS_STORAGE_KEY, '{not json');
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.resetModules();
    const mod = await import('./settingsStore');
    expect(mod.settingsStore.getState()).toEqual(mod.DEFAULT_SETTINGS);
    warn.mockRestore();
  });

  it('falls back to DEFAULT_SETTINGS when persisted schema is invalid', async () => {
    // Wrong version, missing fields → should fail isValidSettings.
    window.localStorage.setItem(
      SETTINGS_STORAGE_KEY,
      JSON.stringify({ version: 99 }),
    );
    vi.resetModules();
    const mod = await import('./settingsStore');
    expect(mod.settingsStore.getState()).toEqual(mod.DEFAULT_SETTINGS);
  });

  it('rejects invalid vaultMode values', async () => {
    window.localStorage.setItem(
      SETTINGS_STORAGE_KEY,
      JSON.stringify({ ...DEFAULT_SETTINGS, vaultMode: 'bogus' }),
    );
    vi.resetModules();
    const mod = await import('./settingsStore');
    expect(mod.settingsStore.getState()).toEqual(mod.DEFAULT_SETTINGS);
  });
});

describe('settingsStore.update', () => {
  it('shallow-merges a patch into the current state', () => {
    const next = settingsStore.update({ vaultMode: 'encrypted' });
    expect(next.vaultMode).toBe('encrypted');
    expect(next.vaultLockTimeoutMin).toBe(DEFAULT_SETTINGS.vaultLockTimeoutMin);
    expect(settingsStore.getState().vaultMode).toBe('encrypted');
  });

  it('persists updates to localStorage', () => {
    settingsStore.update({ language: 'en' });
    const raw = window.localStorage.getItem(SETTINGS_STORAGE_KEY);
    expect(raw).toBeTruthy();
    const parsed = JSON.parse(raw as string);
    expect(parsed.language).toBe('en');
    expect(parsed.version).toBe(1);
  });

  it('toggles tryItOutWithCredentials and persists the new value', () => {
    const next = settingsStore.update({ tryItOutWithCredentials: true });
    expect(next.tryItOutWithCredentials).toBe(true);
    expect(settingsStore.getState().tryItOutWithCredentials).toBe(true);
    const raw = window.localStorage.getItem(SETTINGS_STORAGE_KEY);
    expect(JSON.parse(raw as string).tryItOutWithCredentials).toBe(true);
    // and back off
    settingsStore.update({ tryItOutWithCredentials: false });
    expect(settingsStore.getState().tryItOutWithCredentials).toBe(false);
  });

  it('always pins version back to 1', () => {
    const next = settingsStore.update({
      // @ts-expect-error — deliberately attempt to override version
      version: 99,
      tryItOutTimeoutMs: 5000,
    });
    expect(next.version).toBe(1);
    expect(next.tryItOutTimeoutMs).toBe(5000);
  });
});

describe('settingsStore.subscribe', () => {
  it('notifies on update and unsubscribes cleanly', () => {
    const fn = vi.fn();
    const off = settingsStore.subscribe(fn);
    settingsStore.update({ tryItOutTimeoutMs: 1234 });
    expect(fn).toHaveBeenCalledTimes(1);
    off();
    settingsStore.update({ tryItOutTimeoutMs: 5678 });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('isolates listener errors so other listeners still fire', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const bad = vi.fn(() => {
      throw new Error('boom');
    });
    const good = vi.fn();
    const off1 = settingsStore.subscribe(bad);
    const off2 = settingsStore.subscribe(good);
    settingsStore.update({ language: 'ja' });
    expect(bad).toHaveBeenCalled();
    expect(good).toHaveBeenCalled();
    off1();
    off2();
    warn.mockRestore();
  });
});

describe('settingsStore.replace + reset', () => {
  it('replace fills in any missing fields from DEFAULT_SETTINGS', () => {
    const partial = {
      version: 1 as const,
      vaultMode: 'encrypted' as const,
      // intentionally omit other fields
    } as any;
    const next = settingsStore.replace(partial);
    expect(next.vaultMode).toBe('encrypted');
    expect(next.vaultLockTimeoutMin).toBe(DEFAULT_SETTINGS.vaultLockTimeoutMin);
    expect(next.tryItOutTimeoutMs).toBe(DEFAULT_SETTINGS.tryItOutTimeoutMs);
  });

  it('reset restores DEFAULT_SETTINGS', () => {
    settingsStore.update({ language: 'en', vaultMode: 'encrypted' });
    settingsStore.reset();
    expect(settingsStore.getState()).toEqual(DEFAULT_SETTINGS);
  });
});
