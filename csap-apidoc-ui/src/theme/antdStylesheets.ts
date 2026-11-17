/**
 * AntD 4 has no runtime dark algorithm, so both prebuilt stylesheets are
 * shipped (antd.min.css / antd.dark.min.css) and toggled via the `media`
 * attribute: the inactive one gets `media="not all"`, which browsers still
 * download at low priority, so switching later is instant.
 *
 * Both links are inserted BEFORE the app's own stylesheet so that the custom
 * styles in src/**.less keep overriding AntD the same way they did when
 * `antd/dist/antd.less` was bundled first.
 */
import lightHref from 'antd/dist/antd.min.css?url';
import darkHref from 'antd/dist/antd.dark.min.css?url';
import type { ResolvedTheme } from './themeMode';

const LINK_IDS: Record<ResolvedTheme, string> = {
  light: 'csap-antd-light',
  dark: 'csap-antd-dark',
};
const HREFS: Record<ResolvedTheme, string> = {
  light: lightHref,
  dark: darkHref,
};

/** Give up waiting for the stylesheet after this long and render anyway. */
const LOAD_TIMEOUT_MS = 3000;

function ensureLink(theme: ResolvedTheme): HTMLLinkElement {
  const existing = document.getElementById(LINK_IDS[theme]) as HTMLLinkElement | null;
  if (existing) return existing;
  const link = document.createElement('link');
  link.id = LINK_IDS[theme];
  link.rel = 'stylesheet';
  link.href = HREFS[theme];
  const head = document.head;
  const firstStyle = head.querySelector('link[rel="stylesheet"], style');
  head.insertBefore(link, firstStyle);
  return link;
}

function waitForLoad(link: HTMLLinkElement): Promise<void> {
  if (link.sheet) return Promise.resolve();
  return new Promise((resolve) => {
    const done = () => {
      clearTimeout(timer);
      resolve();
    };
    const timer = setTimeout(done, LOAD_TIMEOUT_MS);
    link.addEventListener('load', done, { once: true });
    link.addEventListener('error', done, { once: true });
  });
}

/**
 * Activate the stylesheet for `theme`; resolves once it has loaded.
 * The previously active sheet stays enabled until the new one has loaded so
 * that switching never shows unstyled components.
 */
export function setAntdTheme(theme: ResolvedTheme): Promise<void> {
  if (typeof document === 'undefined') return Promise.resolve();
  const otherTheme: ResolvedTheme = theme === 'dark' ? 'light' : 'dark';
  const otherIsNew = !document.getElementById(LINK_IDS[otherTheme]);
  const active = ensureLink(theme);
  const other = ensureLink(otherTheme);
  active.media = 'all';
  if (otherIsNew || active.sheet) {
    other.media = 'not all';
  }
  return waitForLoad(active).then(() => {
    // A newer call may have re-activated `other` in the meantime.
    if (active.media === 'all') {
      other.media = 'not all';
    }
  });
}
