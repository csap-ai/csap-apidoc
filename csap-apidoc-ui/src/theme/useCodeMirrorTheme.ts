import { githubDark, githubLight } from '@uiw/codemirror-theme-github';
import { useTheme } from './ThemeContext';

/** CodeMirror theme matching the current light / dark theme. */
export function useCodeMirrorTheme() {
  const { theme } = useTheme();
  return theme === 'dark' ? githubDark : githubLight;
}
