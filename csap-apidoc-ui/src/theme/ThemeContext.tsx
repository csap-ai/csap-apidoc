import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  applyThemeAttribute,
  readThemeMode,
  resolveTheme,
  systemPrefersDark,
  watchSystemTheme,
  writeThemeMode,
  type ResolvedTheme,
  type ThemeMode,
} from './themeMode';
import { setAntdTheme } from './antdStylesheets';

interface ThemeContextValue {
  /** What the user picked: light / dark / follow system. */
  mode: ThemeMode;
  /** What is actually shown. */
  theme: ResolvedTheme;
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  mode: 'system',
  theme: 'light',
  setMode: () => {},
});

/**
 * Apply the stored / system theme before the first React render and wait
 * (bounded) for the matching AntD stylesheet, so the first paint is styled.
 */
export function bootstrapTheme(): Promise<void> {
  const theme = resolveTheme(readThemeMode(), systemPrefersDark());
  applyThemeAttribute(theme);
  return setAntdTheme(theme);
}

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mode, setModeState] = useState<ThemeMode>(() => readThemeMode());
  const [systemDark, setSystemDark] = useState<boolean>(() => systemPrefersDark());

  useEffect(() => watchSystemTheme(setSystemDark), []);

  const theme = resolveTheme(mode, systemDark);

  useEffect(() => {
    applyThemeAttribute(theme);
    void setAntdTheme(theme);
  }, [theme]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    writeThemeMode(next);
  }, []);

  const value = useMemo(() => ({ mode, theme, setMode }), [mode, theme, setMode]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
