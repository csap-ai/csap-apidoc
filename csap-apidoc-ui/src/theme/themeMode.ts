/**
 * Theme selection logic (light / dark / follow system).
 *
 * Pure functions with injectable storage / window so they can be unit-tested
 * in the node environment. The React side lives in ThemeContext.tsx.
 *
 * NOTE: index.html contains a tiny inline copy of `readThemeMode` +
 * `resolveTheme` that runs before the bundle loads (to set `data-theme`
 * early and avoid a white flash). Keep THEME_STORAGE_KEY and the attribute
 * name in sync with it.
 */

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

export const THEME_MODES: readonly ThemeMode[] = ['light', 'dark', 'system'];
export const DEFAULT_THEME_MODE: ThemeMode = 'system';
export const THEME_STORAGE_KEY = 'csap-apidoc:theme';
export const THEME_ATTRIBUTE = 'data-theme';
export const DARK_QUERY = '(prefers-color-scheme: dark)';

type ReadableStorage = Pick<Storage, 'getItem'>;
type WritableStorage = Pick<Storage, 'setItem' | 'removeItem'>;
type MatchMediaWindow = Pick<Window, 'matchMedia'>;

export function isThemeMode(value: unknown): value is ThemeMode {
  return typeof value === 'string' && (THEME_MODES as readonly string[]).includes(value);
}

/** Unknown / missing values fall back to following the system. */
export function parseThemeMode(value: unknown): ThemeMode {
  return isThemeMode(value) ? value : DEFAULT_THEME_MODE;
}

/**
 * `window.localStorage` itself throws in some sandboxed iframes and when
 * cookies are blocked, so even the property access is guarded.
 */
export function getLocalStorage(): Storage | null {
  try {
    return typeof window !== 'undefined' && window.localStorage ? window.localStorage : null;
  } catch {
    return null;
  }
}

export function readThemeMode(storage: ReadableStorage | null = getLocalStorage()): ThemeMode {
  if (!storage) return DEFAULT_THEME_MODE;
  try {
    return parseThemeMode(storage.getItem(THEME_STORAGE_KEY));
  } catch {
    return DEFAULT_THEME_MODE;
  }
}

/**
 * Persist the choice. `system` is stored explicitly (rather than removing the
 * key) so the user's last choice is visible in devtools; both read the same.
 * Returns false when storage is unavailable or throws (quota, private mode).
 */
export function writeThemeMode(
  mode: ThemeMode,
  storage: WritableStorage | null = getLocalStorage(),
): boolean {
  if (!storage) return false;
  try {
    storage.setItem(THEME_STORAGE_KEY, parseThemeMode(mode));
    return true;
  } catch {
    return false;
  }
}

export function systemPrefersDark(win: MatchMediaWindow | null = typeof window !== 'undefined' ? window : null): boolean {
  try {
    return !!win && typeof win.matchMedia === 'function' && win.matchMedia(DARK_QUERY).matches;
  } catch {
    return false;
  }
}

export function resolveTheme(mode: ThemeMode, systemDark: boolean): ResolvedTheme {
  if (mode === 'system') return systemDark ? 'dark' : 'light';
  return mode;
}

/**
 * Subscribe to OS-level color scheme changes. Returns an unsubscribe fn.
 * Falls back to the legacy addListener API (Safari < 14).
 */
export function watchSystemTheme(
  onChange: (dark: boolean) => void,
  win: MatchMediaWindow | null = typeof window !== 'undefined' ? window : null,
): () => void {
  let mql: MediaQueryList;
  try {
    if (!win || typeof win.matchMedia !== 'function') return () => {};
    mql = win.matchMedia(DARK_QUERY);
  } catch {
    return () => {};
  }
  const listener = (e: MediaQueryListEvent) => onChange(e.matches);
  if (typeof mql.addEventListener === 'function') {
    mql.addEventListener('change', listener);
    return () => mql.removeEventListener('change', listener);
  }
  if (typeof (mql as any).addListener === 'function') {
    (mql as any).addListener(listener);
    return () => (mql as any).removeListener(listener);
  }
  return () => {};
}

/** Reflect the resolved theme on <html> so CSS variables switch. */
export function applyThemeAttribute(theme: ResolvedTheme, root: HTMLElement | null =
  typeof document !== 'undefined' ? document.documentElement : null): void {
  if (!root) return;
  root.setAttribute(THEME_ATTRIBUTE, theme);
  root.style.colorScheme = theme;
}
