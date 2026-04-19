/**
 * LanguageSwitcher — top-bar dropdown to swap between zh-CN and en-US.
 *
 * Default language is zh-CN (see src/i18n/index.ts). Picking en-US writes
 * the choice to localStorage via i18next-browser-languagedetector's
 * `caches: ['localStorage']` config so it sticks across reloads.
 *
 * The button label flips between "中文" / "English" based on the current
 * locale rather than always showing "language" so the active mode is
 * obvious at a glance.
 */

import React from 'react';
import { Button, Dropdown, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { SUPPORTED_LOCALES, type Locale } from '@/i18n';
import './index.less';

const LABELS: Record<Locale, string> = {
  'zh-CN': '中文',
  'en-US': 'English',
};

const LanguageSwitcher: React.FC = () => {
  const { i18n, t } = useTranslation();

  const current = (
    SUPPORTED_LOCALES as readonly string[]
  ).includes(i18n.language)
    ? (i18n.language as Locale)
    : 'zh-CN';

  const items: MenuProps['items'] = SUPPORTED_LOCALES.map((lng) => ({
    key: lng,
    label: LABELS[lng],
    onClick: () => {
      if (lng !== current) {
        void i18n.changeLanguage(lng);
      }
    },
  }));

  return (
    <Tooltip title={t('lang.tooltip')}>
      <Dropdown menu={{ items, selectedKeys: [current] }} placement="bottomRight">
        <Button icon={<GlobalOutlined />} className="lang-switcher">
          {LABELS[current]}
        </Button>
      </Dropdown>
    </Tooltip>
  );
};

export default LanguageSwitcher;
