/**
 * ThemeSwitcher — header dropdown: light / dark / follow system.
 *
 * The choice is persisted to localStorage (see src/theme/themeMode.ts);
 * "follow system" tracks `prefers-color-scheme` live.
 */

import React from 'react';
import { Button, Dropdown, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { BulbFilled, BulbOutlined, DesktopOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { THEME_MODES, type ThemeMode } from '@/theme/themeMode';
import { useTheme } from '@/theme/ThemeContext';
import './index.less';

const ICONS: Record<ThemeMode, React.ReactNode> = {
  light: <BulbOutlined />,
  dark: <BulbFilled />,
  system: <DesktopOutlined />,
};

const ThemeSwitcher: React.FC = () => {
  const { t } = useTranslation();
  const { mode, setMode } = useTheme();

  const label = (m: ThemeMode) => {
    switch (m) {
      case 'light':
        return t('theme.light');
      case 'dark':
        return t('theme.dark');
      default:
        return t('theme.system');
    }
  };

  const items: MenuProps['items'] = THEME_MODES.map((m) => ({
    key: m,
    icon: ICONS[m],
    label: label(m),
    onClick: () => setMode(m),
  }));

  return (
    <Tooltip title={t('theme.tooltip')}>
      <Dropdown menu={{ items, selectedKeys: [mode] }} placement="bottomRight">
        <Button icon={ICONS[mode]} className="theme-switcher" data-testid="theme-switcher">
          {label(mode)}
        </Button>
      </Dropdown>
    </Tooltip>
  );
};

export default ThemeSwitcher;
