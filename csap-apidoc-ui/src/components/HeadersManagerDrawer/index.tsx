/**
 * HeadersManagerDrawer — 3-scope global headers CRUD.
 *
 * Tabs: Global · Service · Environment. Each tab is an inline-editable
 * table of rules. Service and Environment tabs show a scope-ref column
 * (free text or env-select) so one rule can target a specific service
 * or environment.
 *
 * Variable tokens `{{key}}` are allowed in both key and value and are
 * resolved at request time by `headersResolver.ts`.
 */

import React, { useMemo, useState } from 'react';
import {
  Drawer,
  Tabs,
  Table,
  Button,
  Input,
  Switch,
  Select,
  Popconfirm,
  Tooltip,
  Empty,
  Tag,
  message,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useHeaders } from '@/contexts/HeadersContext';
import { useEnvironments } from '@/contexts/EnvironmentContext';
import {
  HeaderRule,
  HeaderScope,
} from '@/stores/headersStore';
import { serviceRefIdFor } from '@/stores/serviceRefId';
import './index.less';

interface Props {
  open: boolean;
  onClose: () => void;
  /** Optional list of known services (for the Service tab scope-ref dropdown). M5 will thread this from the layout. */
  knownServices?: Array<{ url: string; name: string }>;
}

const TAB_KEYS: HeaderScope[] = ['global', 'service', 'environment'];

const HeadersManagerDrawer: React.FC<Props> = ({
  open,
  onClose,
  knownServices,
}) => {
  const { t } = useTranslation();
  const { state, add, update, remove } = useHeaders();
  const { state: envState } = useEnvironments();
  const [activeTab, setActiveTab] = useState<HeaderScope>('global');

  const scopeLabel: Record<HeaderScope, string> = {
    global: t('headers.scope.global'),
    service: t('headers.scope.service'),
    environment: t('headers.scope.environment'),
  };

  const byScope = useMemo(() => {
    const out: Record<HeaderScope, HeaderRule[]> = {
      global: [],
      service: [],
      environment: [],
    };
    for (const r of state.items) out[r.scope].push(r);
    return out;
  }, [state.items]);

  const handleAdd = (scope: HeaderScope) => {
    add({
      scope,
      scopeRefId:
        scope === 'environment' ? envState.activeId ?? null : null,
      key: '',
      value: '',
      enabled: true,
    });
  };

  const renderScopeRefCell = (row: HeaderRule) => {
    if (row.scope === 'global') {
      return <span className="headers-drawer__muted">—</span>;
    }
    if (row.scope === 'environment') {
      return (
        <Select
          size="small"
          placeholder={t('headers.scopeRef.env.placeholder')}
          value={row.scopeRefId ?? undefined}
          onChange={(v) => update(row.id, { scopeRefId: v })}
          style={{ width: '100%' }}
          allowClear
        >
          {envState.items.map((e) => (
            <Select.Option key={e.id} value={e.id}>
              <span className="headers-drawer__env-opt">
                <span
                  className="headers-drawer__env-dot"
                  style={{ backgroundColor: e.color }}
                />
                {e.name}
              </span>
            </Select.Option>
          ))}
        </Select>
      );
    }
    // scope === 'service'
    if (knownServices && knownServices.length > 0) {
      // Stored value is canonical (headersStore normalizes on write); the
      // dropdown surfaces the user-typed URL as a label but the value is
      // also canonicalized so a stored binding under one form matches the
      // option for another form (e.g. trailing-slash differences).
      return (
        <Select
          size="small"
          placeholder={t('headers.scopeRef.service.placeholder')}
          value={row.scopeRefId ?? undefined}
          onChange={(v) => update(row.id, { scopeRefId: v })}
          style={{ width: '100%' }}
          allowClear
          showSearch
        >
          {knownServices.map((s) => {
            const canonical = serviceRefIdFor(s.url) ?? s.url;
            return (
              <Select.Option key={canonical} value={canonical}>
                {s.name} <span className="headers-drawer__muted">({s.url})</span>
              </Select.Option>
            );
          })}
        </Select>
      );
    }
    return (
      <Input
        size="small"
        placeholder={t('headers.scopeRef.service.input')}
        value={row.scopeRefId ?? ''}
        onChange={(e) => update(row.id, { scopeRefId: e.target.value })}
      />
    );
  };

  const buildColumns = (scope: HeaderScope) => {
    const cols: any[] = [
      {
        title: t('headers.col.enabled'),
        dataIndex: 'enabled',
        width: 60,
        render: (_: boolean, row: HeaderRule) => (
          <Switch
            size="small"
            checked={row.enabled}
            onChange={(v) => update(row.id, { enabled: v })}
          />
        ),
      },
      {
        title: 'Key',
        dataIndex: 'key',
        width: '22%',
        render: (_: string, row: HeaderRule) => (
          <Input
            size="small"
            placeholder={t('headers.row.key.placeholder')}
            value={row.key}
            onChange={(e) => update(row.id, { key: e.target.value })}
          />
        ),
      },
      {
        title: (
          <span>
            Value{' '}
            <Tooltip title={t('headers.value.tooltip')}>
              <QuestionCircleOutlined className="headers-drawer__muted" />
            </Tooltip>
          </span>
        ),
        dataIndex: 'value',
        render: (_: string, row: HeaderRule) => (
          <Input
            size="small"
            placeholder={t('headers.row.value.placeholder')}
            value={row.value}
            onChange={(e) => update(row.id, { value: e.target.value })}
          />
        ),
      },
    ];

    if (scope !== 'global') {
      cols.push({
        title: scope === 'service' ? t('headers.col.service') : t('headers.col.env'),
        width: '22%',
        render: (_: any, row: HeaderRule) => renderScopeRefCell(row),
      });
    }

    cols.push(
      {
        title: t('headers.col.description'),
        dataIndex: 'description',
        width: '18%',
        render: (_: string, row: HeaderRule) => (
          <Input
            size="small"
            placeholder={t('headers.row.description.placeholder')}
            value={row.description ?? ''}
            onChange={(e) => update(row.id, { description: e.target.value })}
          />
        ),
      },
      {
        title: '',
        width: 44,
        render: (_: any, row: HeaderRule) => (
          <Popconfirm
            title={t('headers.row.deleteConfirm')}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            onConfirm={() => {
              remove(row.id);
              message.success(t('common.deleted'));
            }}
          >
            <Button size="small" type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        ),
      },
    );

    return cols;
  };

  const renderTabContent = (scope: HeaderScope) => {
    const rows = byScope[scope];
    return (
      <div className="headers-drawer__tab">
        <div className="headers-drawer__toolbar">
          <Button
            type="dashed"
            size="small"
            icon={<PlusOutlined />}
            onClick={() => handleAdd(scope)}
          >
            {t(`headers.add.${scope}` as const)}
          </Button>
          <span className="headers-drawer__hint">
            {t('headers.toolbar.count', {
              total: rows.length,
              enabled: rows.filter((r) => r.enabled).length,
            })}
          </span>
        </div>
        {rows.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={t(`headers.empty.${scope}` as const)}
            style={{ padding: '32px 0' }}
          />
        ) : (
          <Table<HeaderRule>
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={rows}
            columns={buildColumns(scope)}
          />
        )}
      </div>
    );
  };

  return (
    <Drawer
      title={
        <span>
          {t('headers.drawer.title')}{' '}
          <Tag className="headers-drawer__title-tag">
            {t('headers.drawer.summary', {
              enabled: state.items.filter((r) => r.enabled).length,
              total: state.items.length,
            })}
          </Tag>
        </span>
      }
      width={960}
      open={open}
      onClose={onClose}
      destroyOnClose
      className="headers-drawer"
    >
      <Tabs
        activeKey={activeTab}
        onChange={(k) => setActiveTab(k as HeaderScope)}
        items={TAB_KEYS.map((scope) => ({
          key: scope,
          label: (
            <span>
              {scopeLabel[scope]}{' '}
              <Tag className="headers-drawer__count-tag">
                {byScope[scope].length}
              </Tag>
            </span>
          ),
          children: renderTabContent(scope),
        }))}
      />
      <div className="headers-drawer__legend">
        <strong>{t('headers.legend.title')}</strong>
        <span className="headers-drawer__legend-sep">: </span>
        {t('headers.legend.body')}
      </div>
    </Drawer>
  );
};

export default HeadersManagerDrawer;
