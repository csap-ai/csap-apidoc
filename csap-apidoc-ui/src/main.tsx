// M8.1 — bootstrap react-i18next BEFORE any component import that uses
// `useTranslation()`. The default language is zh-CN so existing users
// (and the Playwright E2E suite that asserts on Chinese button labels)
// see the exact same UI as before; en-US is opt-in via LanguageSwitcher.
import './i18n';

import ReactDOM from "react-dom/client";
import { ConfigProvider } from 'antd';
import { useTranslation } from 'react-i18next';
import zhCN from 'antd/es/locale/zh_CN';
import enUS from 'antd/es/locale/en_US';
import App from './App'
import "@/styles/reset.less";
import "antd/dist/antd.less";

const ANTD_LOCALES: Record<string, typeof zhCN> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

const I18nApp = () => {
  // Subscribing to `i18n.language` makes the whole tree re-render — and
  // ConfigProvider hand the new AntD locale down — when the user toggles
  // the LanguageSwitcher.
  const { i18n } = useTranslation();
  const locale = ANTD_LOCALES[i18n.language] ?? zhCN;
  return (
    <ConfigProvider locale={locale}>
      <App />
    </ConfigProvider>
  );
};

ReactDOM.createRoot(document.getElementById("root")!).render(
  <I18nApp />
);
