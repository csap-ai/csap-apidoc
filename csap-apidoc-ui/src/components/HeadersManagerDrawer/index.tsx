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

const SCOPE_LABEL: Record<HeaderScope, string> = {
  global: '全局',
  service: '服务',
  environment: '环境',
};

const HeadersManagerDrawer: React.FC<Props> = ({
  open,
  onClose,
  knownServices,
}) => {
  const { state, add, update, remove } = useHeaders();
  const { state: envState } = useEnvironments();
  const [activeTab, setActiveTab] = useState<HeaderScope>('global');

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
          placeholder="选择环境"
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
          placeholder="选择服务"
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
        placeholder="服务 URL 或标识"
        value={row.scopeRefId ?? ''}
        onChange={(e) => update(row.id, { scopeRefId: e.target.value })}
      />
    );
  };

  const buildColumns = (scope: HeaderScope) => {
    const cols: any[] = [
      {
        title: '启用',
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
            placeholder="如 X-Tenant-Id"
            value={row.key}
            onChange={(e) => update(row.id, { key: e.target.value })}
          />
        ),
      },
      {
        title: (
          <span>
            Value{' '}
            <Tooltip title="支持变量，如 {{tenantId}}、{{baseUrl}}；将使用当前活跃环境展开。">
              <QuestionCircleOutlined className="headers-drawer__muted" />
            </Tooltip>
          </span>
        ),
        dataIndex: 'value',
        render: (_: string, row: HeaderRule) => (
          <Input
            size="small"
            placeholder="如 42 或 {{tenantId}}"
            value={row.value}
            onChange={(e) => update(row.id, { value: e.target.value })}
          />
        ),
      },
    ];

    if (scope !== 'global') {
      cols.push({
        title: scope === 'service' ? '所属服务' : '所属环境',
        width: '22%',
        render: (_: any, row: HeaderRule) => renderScopeRefCell(row),
      });
    }

    cols.push(
      {
        title: '说明',
        dataIndex: 'description',
        width: '18%',
        render: (_: string, row: HeaderRule) => (
          <Input
            size="small"
            placeholder="可选"
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
            title="删除此请求头？"
            okText="删除"
            cancelText="取消"
            onConfirm={() => {
              remove(row.id);
              message.success('已删除');
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
            新增{SCOPE_LABEL[scope]}请求头
          </Button>
          <span className="headers-drawer__hint">
            共 {rows.length} 条 ({rows.filter((r) => r.enabled).length} 启用)
          </span>
        </div>
        {rows.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={`暂无${SCOPE_LABEL[scope]}请求头`}
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
          全局请求头{' '}
          <Tag className="headers-drawer__title-tag">
            {state.items.filter((r) => r.enabled).length} 启用 /{' '}
            {state.items.length} 总计
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
              {SCOPE_LABEL[scope]}{' '}
              <Tag className="headers-drawer__count-tag">
                {byScope[scope].length}
              </Tag>
            </span>
          ),
          children: renderTabContent(scope),
        }))}
      />
      <div className="headers-drawer__legend">
        <strong>合并规则</strong>：全局 → 服务 → 环境 → 接口自带请求头。
        同名请求头（大小写不敏感）以更具体的作用域为准。
      </div>
    </Drawer>
  );
};

export default HeadersManagerDrawer;
