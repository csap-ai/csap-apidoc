import fs from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi } from 'vitest';
import {
  DARK_QUERY,
  DEFAULT_THEME_MODE,
  THEME_STORAGE_KEY,
  applyThemeAttribute,
  parseThemeMode,
  readThemeMode,
  resolveTheme,
  systemPrefersDark,
  watchSystemTheme,
  writeThemeMode,
} from './themeMode';

function memoryStorage(initial: Record<string, string> = {}) {
  const data = new Map(Object.entries(initial));
  return {
    getItem: (k: string) => (data.has(k) ? data.get(k)! : null),
    setItem: (k: string, v: string) => void data.set(k, v),
    removeItem: (k: string) => void data.delete(k),
    data,
  };
}

const throwingStorage = {
  getItem: () => {
    throw new Error('SecurityError');
  },
  setItem: () => {
    throw new Error('QuotaExceededError');
  },
  removeItem: () => {
    throw new Error('SecurityError');
  },
};

function fakeWindow(dark: boolean) {
  const listeners = new Set<(e: { matches: boolean }) => void>();
  const mql = {
    matches: dark,
    media: DARK_QUERY,
    addEventListener: (_: string, l: any) => listeners.add(l),
    removeEventListener: (_: string, l: any) => listeners.delete(l),
  };
  return {
    win: { matchMedia: vi.fn(() => mql as unknown as MediaQueryList) },
    emit: (matches: boolean) => listeners.forEach((l) => l({ matches })),
    listeners,
  };
}

describe('parseThemeMode', () => {
  it('accepts the three modes', () => {
    expect(parseThemeMode('light')).toBe('light');
    expect(parseThemeMode('dark')).toBe('dark');
    expect(parseThemeMode('system')).toBe('system');
  });

  it('falls back to system for anything else', () => {
    expect(DEFAULT_THEME_MODE).toBe('system');
    for (const v of [null, undefined, '', 'Dark', 'auto', 1, {}]) {
      expect(parseThemeMode(v)).toBe('system');
    }
  });
});

describe('resolveTheme', () => {
  it('explicit modes ignore the system preference', () => {
    expect(resolveTheme('light', true)).toBe('light');
    expect(resolveTheme('dark', false)).toBe('dark');
  });

  it('system mode follows prefers-color-scheme', () => {
    expect(resolveTheme('system', true)).toBe('dark');
    expect(resolveTheme('system', false)).toBe('light');
  });
});

describe('read / write theme mode', () => {
  it('defaults to system when nothing is stored', () => {
    expect(readThemeMode(memoryStorage())).toBe('system');
    expect(readThemeMode(null)).toBe('system');
  });

  it('round-trips through storage', () => {
    const storage = memoryStorage();
    expect(writeThemeMode('dark', storage)).toBe(true);
    expect(storage.data.get(THEME_STORAGE_KEY)).toBe('dark');
    expect(readThemeMode(storage)).toBe('dark');
    expect(writeThemeMode('system', storage)).toBe(true);
    expect(readThemeMode(storage)).toBe('system');
  });

  it('ignores garbage values', () => {
    expect(readThemeMode(memoryStorage({ [THEME_STORAGE_KEY]: 'purple' }))).toBe('system');
  });

  it('survives storage that throws', () => {
    expect(readThemeMode(throwingStorage)).toBe('system');
    expect(writeThemeMode('dark', throwingStorage)).toBe(false);
    expect(writeThemeMode('dark', null)).toBe(false);
  });
});

describe('system preference', () => {
  it('reads matchMedia', () => {
    expect(systemPrefersDark(fakeWindow(true).win)).toBe(true);
    expect(systemPrefersDark(fakeWindow(false).win)).toBe(false);
  });

  it('is false without matchMedia or when it throws', () => {
    expect(systemPrefersDark(null)).toBe(false);
    expect(systemPrefersDark({} as any)).toBe(false);
    expect(
      systemPrefersDark({
        matchMedia: () => {
          throw new Error('nope');
        },
      } as any),
    ).toBe(false);
  });

  it('notifies on change and unsubscribes', () => {
    const { win, emit, listeners } = fakeWindow(false);
    const seen: boolean[] = [];
    const stop = watchSystemTheme((d) => seen.push(d), win);
    emit(true);
    emit(false);
    expect(seen).toEqual([true, false]);
    stop();
    expect(listeners.size).toBe(0);
    emit(true);
    expect(seen).toEqual([true, false]);
  });

  it('supports the legacy addListener API', () => {
    const listeners = new Set<any>();
    const win = {
      matchMedia: () =>
        ({
          matches: false,
          addListener: (l: any) => listeners.add(l),
          removeListener: (l: any) => listeners.delete(l),
        }) as any,
    };
    const stop = watchSystemTheme(() => {}, win);
    expect(listeners.size).toBe(1);
    stop();
    expect(listeners.size).toBe(0);
  });

  it('is a no-op without a window', () => {
    expect(() => watchSystemTheme(() => {}, null)()).not.toThrow();
  });
});

describe('applyThemeAttribute', () => {
  it('sets data-theme and color-scheme on the root element', () => {
    const attrs: Record<string, string> = {};
    const root = {
      setAttribute: (k: string, v: string) => (attrs[k] = v),
      style: {} as Record<string, string>,
    };
    applyThemeAttribute('dark', root as any);
    expect(attrs['data-theme']).toBe('dark');
    expect(root.style.colorScheme).toBe('dark');
    applyThemeAttribute('light', root as any);
    expect(attrs['data-theme']).toBe('light');
  });

  it('is a no-op without a root', () => {
    expect(() => applyThemeAttribute('dark', null)).not.toThrow();
  });
});

describe('index.html early theme script', () => {
  it('uses the same storage key, values and attribute as themeMode.ts', () => {
    const html = fs.readFileSync(path.resolve(__dirname, '../../index.html'), 'utf8');
    expect(html).toContain(`'${THEME_STORAGE_KEY}'`);
    expect(html).toContain(DARK_QUERY);
    expect(html).toContain("setAttribute('data-theme'");
  });
});
