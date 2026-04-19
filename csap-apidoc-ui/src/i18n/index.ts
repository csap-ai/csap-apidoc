/**
 * react-i18next bootstrap (M8.1).
 *
 * Default language is **zh-CN** so that the existing UI literals stay
 * pixel-identical for current users (and so the Playwright E2E spec,
 * which asserts on Chinese button labels, keeps passing).
 *
 * en-US is opt-in via the LanguageSwitcher in the header. The choice is
 * persisted to localStorage under `csap-apidoc:locale`.
 *
 * NOTE: The detector intentionally only reads from `localStorage` (no
 * `navigator`). If we let it fall back to `navigator.language`, an
 * English-locale browser — which is the default for Playwright's
 * "Desktop Chrome" device profile — would auto-pick en-US and break
 * the Try-it-out E2E spec which selects on Chinese button labels. The
 * trade-off (English browsers don't auto-localise on first load) is
 * acceptable because (a) existing zh-CN users get a zero-diff upgrade
 * and (b) the LanguageSwitcher is a single click away.
 *
 * Import side-effect only — call sites use `useTranslation()` from
 * react-i18next; the `i18n` instance is exported for the few places that
 * need to subscribe to language changes (e.g. AntD ConfigProvider locale
 * swap in main.tsx) or call `i18n.changeLanguage(...)` directly.
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import zhCN from './locales/zh-CN.json';
import enUS from './locales/en-US.json';

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      'zh-CN': { translation: zhCN },
      'en-US': { translation: enUS },
    },
    fallbackLng: 'zh-CN',
    supportedLngs: SUPPORTED_LOCALES as unknown as string[],
    load: 'currentOnly',
    // The UI uses literal `{{var}}` strings extensively (env variables,
    // header tokens) — switch interpolation to `<<var>>` so those literals
    // pass through translations unchanged.
    interpolation: {
      escapeValue: false,
      prefix: '<<',
      suffix: '>>',
    },
    detection: {
      order: ['localStorage'],
      caches: ['localStorage'],
      lookupLocalStorage: 'csap-apidoc:locale',
    },
    returnNull: false,
  });

export default i18n;
