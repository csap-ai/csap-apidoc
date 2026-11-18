/**
 * EnvironmentSwitcher — top-bar dropdown to pick the active environment.
 *
 * Layout matches the existing Header `.api-item` style: a horizontal
 * "[label] [select] [gear]" row. The dropdown menu lists all envs plus a
 * "Manage..." entry that opens the manager drawer.
 *
 * The switcher is purely a state-toggling control. Wiring of the active
 * env into outgoing requests happens in M5; M1 only persists and exposes
 * the active env via EnvironmentContext.
 */

import React, { useEffect, useState } from 'react';
import { Select, Tooltip, Tag } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useEnvironments } from '@/contexts/EnvironmentContext';
import EnvironmentManagerDrawer from '@/components/EnvironmentManagerDrawer';
import { apidocServiceBase } from '@/utils/apidocUrl';
import {
  MOCK_ENV_COLOR,
  MOCK_ENV_MARKER,
  MOCK_ENV_NAME,
  MockStatus,
  mockBaseUrl,
  probeMock,
} from '@/services/mockStatus';
import './index.less';

const { Option } = Select;

const MANAGE_VALUE = '__manage__';
const MOCK_VALUE = '__mock__';

interface Props {
  className?: string;
  /** Doc resource URL of the selected service (gateway aggregation); empty = the page's own service. */
  serviceUrl?: string | null;
}

const EnvironmentSwitcher: React.FC<Props> = ({ className, serviceUrl }) => {
  const { t } = useTranslation();
  const { state, active, setActive, add, update } = useEnvironments();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [mock, setMock] = useState<{ status: MockStatus; baseUrl: string } | null>(null);

  // The server tells us whether Mock is on (1.4.0); when it is, offer a one-click "Mock" environment.
  const serviceBase = serviceUrl ? apidocServiceBase(serviceUrl) : (import.meta.env.VITE_API_URL as string) || '';
  useEffect(() => {
    let alive = true;
    probeMock(serviceBase).then((status) => {
      if (!alive) return;
      setMock(status ? { status, baseUrl: mockBaseUrl(window.location.origin, serviceBase, status) } : null);
    });
    return () => {
      alive = false;
    };
  }, [serviceBase]);

  const handleChange = (val: string) => {
    if (val === MANAGE_VALUE) {
      setDrawerOpen(true);
      return;
    }
    if (val === MOCK_VALUE) {
      if (!mock) return;
      const existing = state.items.find((e) => e.variables?.[MOCK_ENV_MARKER] === '1');
      if (existing) {
        if (existing.baseUrl !== mock.baseUrl) update(existing.id, { baseUrl: mock.baseUrl });
        setActive(existing.id);
      } else {
        const env = add({
          name: MOCK_ENV_NAME,
          color: MOCK_ENV_COLOR,
          baseUrl: mock.baseUrl,
          variables: { [MOCK_ENV_MARKER]: '1' },
        });
        setActive(env.id);
      }
      return;
    }
    setActive(val);
  };

  const renderLabel = (color: string, name: string) => (
    <span className="env-switcher__label">
      <span
        className="env-switcher__dot"
        style={{ backgroundColor: color }}
        aria-hidden
      />
      {name}
    </span>
  );

  return (
    <>
      <span className="env-switcher__heading">{t('env.switcher.heading')}</span>
      <Select
        size="middle"
        style={{ width: 180 }}
        value={active?.id}
        placeholder={state.items.length === 0 ? t('env.switcher.empty') : t('env.switcher.placeholder')}
        onChange={handleChange}
        dropdownClassName="env-switcher__dropdown"
        optionLabelProp="label"
        className={`env-switcher ${className ?? ''}`}
      >
        {state.items.map((env) => (
          <Option
            key={env.id}
            value={env.id}
            label={renderLabel(env.color, env.name)}
          >
            <div className="env-switcher__option">
              {renderLabel(env.color, env.name)}
              {env.baseUrl && (
                <Tag className="env-switcher__base-url" color="default">
                  {env.baseUrl}
                </Tag>
              )}
            </div>
          </Option>
        ))}
        {mock && (
          <Option value={MOCK_VALUE} label={renderLabel(MOCK_ENV_COLOR, MOCK_ENV_NAME)}>
            <Tooltip title={t('env.switcher.mockHint')} placement="left">
              <span className="env-switcher__manage">{t('env.switcher.mock')}</span>
            </Tooltip>
          </Option>
        )}
        <Option value={MANAGE_VALUE} label={t('env.switcher.manageLabel')}>
          <span className="env-switcher__manage">{t('env.switcher.manage')}</span>
        </Option>
      </Select>
      <Tooltip title={t('env.switcher.gear.tooltip')}>
        <span
          className="env-switcher__gear"
          onClick={() => setDrawerOpen(true)}
        >
          <SettingOutlined />
        </span>
      </Tooltip>

      <EnvironmentManagerDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      />
    </>
  );
};

export default EnvironmentSwitcher;
