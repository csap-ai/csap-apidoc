/**
 * AuthSchemesDrawer — manage auth schemes + service bindings.
 *
 * Layout: same left-list / right-form convention used by
 * EnvironmentManagerDrawer. Type-specific sub-forms (Bearer / Basic /
 * ApiKey / OAuth2-CC) expand inline based on the selected scheme's `type`.
 *
 * Sensitive values (tokens, passwords, client secrets, cached OAuth2
 * tokens) are written through `vault.put(...)` and only the returned
 * `vault:tok_xxxx` reference is persisted on the scheme. A
 * <SecretInput> component encapsulates that pattern with a
 * show/hide-value toggle.
 */

import React, { useEffect, useMemo, useState } from 'react';
import {
  Drawer,
  List,
  Button,
  Form,
  Input,
  Select,
  Popconfirm,
  Space,
  Empty,
  Tag,
  Table,
  message,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  ReloadOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/contexts/AuthContext';
import {
  AuthScheme,
  AuthSchemeType,
  ApiKeyConfig,
  BearerConfig,
  BasicConfig,
  OAuth2ClientConfig,
  defaultConfigFor,
} from '@/stores/authStore';
import { vault } from '@/stores/vault';
import { serviceRefIdFor } from '@/stores/serviceRefId';
import './index.less';

interface Props {
  open: boolean;
  onClose: () => void;
  knownServices?: Array<{ url: string; name: string }>;
}

const TYPE_LABELS: Record<AuthSchemeType, string> = {
  bearer: 'Bearer Token',
  basic: 'Basic Auth',
  apikey: 'API Key',
  oauth2_client: 'OAuth2 (Client Credentials)',
};

const TYPE_COLORS: Record<AuthSchemeType, string> = {
  bearer: '#1677ff',
  basic: '#722ed1',
  apikey: '#13c2c2',
  oauth2_client: '#fa8c16',
};

interface FormValues {
  name: string;
  type: AuthSchemeType;
  description?: string;
}

const AuthSchemesDrawer: React.FC<Props> = ({
  open,
  onClose,
  knownServices,
}) => {
  const {
    state,
    add,
    update,
    remove,
    bindToService,
  } = useAuth();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form] = Form.useForm<FormValues>();
  const [draftConfig, setDraftConfig] = useState<AuthScheme['config'] | null>(
    null,
  );

  const selected = useMemo<AuthScheme | null>(
    () => state.items.find((s) => s.id === selectedId) ?? null,
    [state.items, selectedId],
  );

  useEffect(() => {
    if (!open) return;
    setSelectedId((prev) =>
      prev && state.items.some((s) => s.id === prev)
        ? prev
        : state.items[0]?.id ?? null,
    );
  }, [open, state.items]);

  useEffect(() => {
    if (selected) {
      form.setFieldsValue({
        name: selected.name,
        type: selected.type,
        description: selected.description,
      });
      setDraftConfig(selected.config);
    } else {
      form.resetFields();
      setDraftConfig(null);
    }
  }, [selected?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = () => {
    const scheme = add({
      name: `Scheme ${state.items.length + 1}`,
      type: 'bearer',
    });
    setSelectedId(scheme.id);
  };

  const handleSave = async () => {
    if (!selected) return;
    try {
      const values = await form.validateFields();
      update(selected.id, {
        name: values.name.trim(),
        type: values.type,
        description: values.description,
        config: draftConfig ?? defaultConfigFor(values.type),
      });
      message.success('已保存');
    } catch {
      // antd validation already surfaced
    }
  };

  const handleDelete = () => {
    if (!selected) return;
    remove(selected.id);
    setSelectedId(null);
    message.success('已删除');
  };

  const handleTypeChange = (type: AuthSchemeType) => {
    setDraftConfig(defaultConfigFor(type));
  };

  const renderTypeForm = () => {
    if (!selected || !draftConfig) return null;
    const type = (form.getFieldValue('type') ?? selected.type) as AuthSchemeType;
    switch (type) {
      case 'bearer':
        return (
          <BearerForm
            value={draftConfig as BearerConfig}
            onChange={setDraftConfig}
          />
        );
      case 'basic':
        return (
          <BasicForm
            value={draftConfig as BasicConfig}
            onChange={setDraftConfig}
          />
        );
      case 'apikey':
        return (
          <ApiKeyForm
            value={draftConfig as ApiKeyConfig}
            onChange={setDraftConfig}
          />
        );
      case 'oauth2_client':
        return (
          <OAuth2ClientForm
            value={draftConfig as OAuth2ClientConfig}
            onChange={setDraftConfig}
          />
        );
    }
  };

  return (
    <Drawer
      title={
        <span>
          认证方案{' '}
          <Tag className="auth-drawer__title-tag">
            {state.items.length} 方案 / {Object.keys(state.activeBindings).length}{' '}
            服务绑定
          </Tag>
        </span>
      }
      width={880}
      open={open}
      onClose={onClose}
      destroyOnClose
      className="auth-drawer"
    >
      <div className="auth-drawer__layout">
        <div className="auth-drawer__list">
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            block
            onClick={handleCreate}
            className="auth-drawer__add-btn"
          >
            新建认证方案
          </Button>
          {state.items.length === 0 ? (
            <Empty
              description="尚未配置认证方案"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              style={{ marginTop: 32 }}
            />
          ) : (
            <List
              dataSource={state.items}
              renderItem={(s) => {
                const isSelected = s.id === selectedId;
                return (
                  <List.Item
                    className={`auth-drawer__item ${
                      isSelected ? 'auth-drawer__item--selected' : ''
                    }`}
                    onClick={() => setSelectedId(s.id)}
                  >
                    <span
                      className="auth-drawer__item-dot"
                      style={{ backgroundColor: TYPE_COLORS[s.type] }}
                    />
                    <span className="auth-drawer__item-body">
                      <span className="auth-drawer__item-name">{s.name}</span>
                      <span className="auth-drawer__item-type">
                        {TYPE_LABELS[s.type]}
                      </span>
                    </span>
                  </List.Item>
                );
              }}
            />
          )}
        </div>

        <div className="auth-drawer__form">
          {!selected ? (
            <Empty description="请选择或新建一个认证方案" />
          ) : (
            <>
              <Form layout="vertical" form={form} requiredMark={false}>
                <Form.Item
                  label="名称"
                  name="name"
                  rules={[
                    { required: true, message: '请输入方案名称' },
                    { max: 48, message: '最多 48 个字符' },
                  ]}
                >
                  <Input placeholder="例如：Dev Bearer / Staging API-Key" />
                </Form.Item>

                <Form.Item label="类型" name="type">
                  <Select onChange={handleTypeChange}>
                    {(Object.keys(TYPE_LABELS) as AuthSchemeType[]).map((t) => (
                      <Select.Option key={t} value={t}>
                        <span
                          className="auth-drawer__type-dot"
                          style={{ backgroundColor: TYPE_COLORS[t] }}
                        />
                        {TYPE_LABELS[t]}
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>

                <Form.Item label="说明（可选）" name="description">
                  <Input placeholder="给团队成员看的备注" />
                </Form.Item>
              </Form>

              <div className="auth-drawer__section">
                <div className="auth-drawer__section-title">凭证</div>
                {renderTypeForm()}
              </div>

              <div className="auth-drawer__section">
                <div className="auth-drawer__section-title">
                  <span>
                    服务绑定{' '}
                    <Tooltip title="选择哪些服务在 Try-it-out 时自动应用此方案">
                      <QuestionCircleOutlined className="auth-drawer__muted" />
                    </Tooltip>
                  </span>
                </div>
                <BindingsEditor
                  schemeId={selected.id}
                  bindings={state.activeBindings}
                  knownServices={knownServices}
                  onBind={bindToService}
                />
              </div>

              <div className="auth-drawer__actions">
                <Space>
                  <Popconfirm
                    title="确认删除该方案？相关凭证将被一并清理。"
                    onConfirm={handleDelete}
                    okText="删除"
                    cancelText="取消"
                  >
                    <Button danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                  <Button type="primary" onClick={handleSave}>
                    保存
                  </Button>
                </Space>
              </div>
            </>
          )}
        </div>
      </div>
    </Drawer>
  );
};

/* ───────────────────────── secret input ──────────────────────── */

interface SecretInputProps {
  /** Existing vault ref (or empty for unset). */
  value: string;
  /** Called with the new vault ref after persisting. */
  onChange: (newRef: string) => void;
  placeholder?: string;
}

const SecretInput: React.FC<SecretInputProps> = ({
  value,
  onChange,
  placeholder,
}) => {
  // Local plaintext mirror so the user can type freely; we sync to the vault
  // on blur to avoid spamming localStorage on every keystroke.
  const [draft, setDraft] = useState<string>(() =>
    vault.isVaultRef(value) ? vault.get(value) ?? '' : value ?? '',
  );
  const [show, setShow] = useState(false);

  useEffect(() => {
    setDraft(vault.isVaultRef(value) ? vault.get(value) ?? '' : value ?? '');
  }, [value]);

  const commit = () => {
    if (draft === '') {
      // Empty value → clear the ref entirely.
      if (value && vault.isVaultRef(value)) vault.remove(value);
      onChange('');
      return;
    }
    const newRef = vault.put(draft, value);
    onChange(newRef);
  };

  return (
    <div className="auth-drawer__secret">
      <Input.Password
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onPressEnter={commit}
        placeholder={placeholder ?? '在此粘贴 / 输入凭证'}
        visibilityToggle={{
          visible: show,
          onVisibleChange: setShow,
        }}
        iconRender={(visible) =>
          visible ? <EyeOutlined /> : <EyeInvisibleOutlined />
        }
      />
      {value && vault.isVaultRef(value) && (
        <span className="auth-drawer__muted auth-drawer__hint-line">
          已保存到本地保险库（{value.slice(0, 14)}…）。变更后会自动覆盖原条目。
        </span>
      )}
    </div>
  );
};

/* ───────────────────────── per-type forms ──────────────────────── */

const BearerForm: React.FC<{
  value: BearerConfig;
  onChange: (next: BearerConfig) => void;
}> = ({ value, onChange }) => (
  <Form layout="vertical" requiredMark={false}>
    <Form.Item label="Token">
      <SecretInput
        value={value.tokenRef}
        onChange={(ref) => onChange({ ...value, tokenRef: ref })}
        placeholder="eyJhbGciOi..."
      />
    </Form.Item>
  </Form>
);

const BasicForm: React.FC<{
  value: BasicConfig;
  onChange: (next: BasicConfig) => void;
}> = ({ value, onChange }) => (
  <Form layout="vertical" requiredMark={false}>
    <Form.Item label="Username">
      <Input
        value={value.username}
        onChange={(e) => onChange({ ...value, username: e.target.value })}
        placeholder="支持 {{var}} 变量"
      />
    </Form.Item>
    <Form.Item label="Password">
      <SecretInput
        value={value.passwordRef}
        onChange={(ref) => onChange({ ...value, passwordRef: ref })}
      />
    </Form.Item>
  </Form>
);

const ApiKeyForm: React.FC<{
  value: ApiKeyConfig;
  onChange: (next: ApiKeyConfig) => void;
}> = ({ value, onChange }) => (
  <Form layout="vertical" requiredMark={false}>
    <Form.Item label="位置">
      <Select
        value={value.in}
        onChange={(v) => onChange({ ...value, in: v })}
        options={[
          { value: 'header', label: 'Header（请求头）' },
          { value: 'query', label: 'Query（查询参数）' },
          { value: 'cookie', label: 'Cookie' },
        ]}
      />
    </Form.Item>
    <Form.Item label="参数名">
      <Input
        value={value.name}
        onChange={(e) => onChange({ ...value, name: e.target.value })}
        placeholder={
          value.in === 'header' ? '如 X-API-Key' : value.in === 'query' ? '如 api_key' : '如 session'
        }
      />
    </Form.Item>
    <Form.Item label="参数值">
      <SecretInput
        value={value.valueRef}
        onChange={(ref) => onChange({ ...value, valueRef: ref })}
      />
    </Form.Item>
  </Form>
);

const OAuth2ClientForm: React.FC<{
  value: OAuth2ClientConfig;
  onChange: (next: OAuth2ClientConfig) => void;
}> = ({ value, onChange }) => {
  const cachedTok = value.cachedTokenRef ? vault.get(value.cachedTokenRef) : null;
  const cachedExp = value.cachedExpiresAt;
  const now = Date.now();
  const cacheState = !cachedTok
    ? '尚未获取'
    : cachedExp && cachedExp - 30_000 > now
    ? `有效（${new Date(cachedExp).toLocaleString()} 过期）`
    : '已过期 / 即将过期';

  const clearCache = () => {
    if (value.cachedTokenRef) vault.remove(value.cachedTokenRef);
    onChange({ ...value, cachedTokenRef: undefined, cachedExpiresAt: undefined });
    message.success('已清空 OAuth2 access token 缓存');
  };

  return (
    <Form layout="vertical" requiredMark={false}>
      <Form.Item label="Token URL">
        <Input
          value={value.tokenUrl}
          onChange={(e) => onChange({ ...value, tokenUrl: e.target.value })}
          placeholder="https://issuer.example.com/oauth2/token"
        />
      </Form.Item>
      <Form.Item label="Client ID">
        <Input
          value={value.clientId}
          onChange={(e) => onChange({ ...value, clientId: e.target.value })}
        />
      </Form.Item>
      <Form.Item label="Client Secret">
        <SecretInput
          value={value.clientSecretRef}
          onChange={(ref) => onChange({ ...value, clientSecretRef: ref })}
        />
      </Form.Item>
      <Form.Item label="Scope（可选）">
        <Input
          value={value.scope ?? ''}
          onChange={(e) => onChange({ ...value, scope: e.target.value })}
          placeholder="例如 read:orders write:orders"
        />
      </Form.Item>
      <Form.Item label="Access Token 缓存">
        <div className="auth-drawer__cache">
          <Tag color={cachedTok && cachedExp && cachedExp > now ? 'green' : 'default'}>
            {cacheState}
          </Tag>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={clearCache}
            disabled={!cachedTok}
          >
            清空缓存
          </Button>
          <span className="auth-drawer__muted auth-drawer__hint-line">
            首次发送 Try-it-out 时会自动调用 token 端点获取并缓存。
          </span>
        </div>
      </Form.Item>
    </Form>
  );
};

/* ───────────────────────── service bindings editor ──────────────────────── */

const BindingsEditor: React.FC<{
  schemeId: string;
  bindings: Record<string, string>;
  knownServices?: Array<{ url: string; name: string }>;
  onBind: (serviceRefId: string, schemeId: string | null) => void;
}> = ({ schemeId, bindings, knownServices, onBind }) => {
  const [draftService, setDraftService] = useState<string | undefined>(
    undefined,
  );

  const boundServices = useMemo(
    () =>
      Object.entries(bindings)
        .filter(([, sId]) => sId === schemeId)
        .map(([svc]) => svc),
    [bindings, schemeId],
  );

  const candidates = useMemo(() => {
    const known = knownServices ?? [];
    // Compare by canonical key so `https://x/.../parent/` and
    // `https://x/.../parent` aren't treated as two distinct services.
    const boundCanonical = new Set(
      boundServices.map((s) => serviceRefIdFor(s) ?? s),
    );
    return known.filter(
      (s) => !boundCanonical.has(serviceRefIdFor(s.url) ?? s.url),
    );
  }, [knownServices, boundServices]);

  const handleAdd = () => {
    if (!draftService) {
      message.warning('请选择或输入服务标识');
      return;
    }
    onBind(draftService, schemeId);
    setDraftService(undefined);
  };

  const rows = boundServices.map((svc) => {
    const canonical = serviceRefIdFor(svc) ?? svc;
    return {
      key: svc,
      service: svc,
      name:
        knownServices?.find(
          (s) => (serviceRefIdFor(s.url) ?? s.url) === canonical,
        )?.name ?? null,
    };
  });

  return (
    <div className="auth-drawer__bindings">
      <div className="auth-drawer__bindings-add">
        {candidates.length > 0 || (knownServices && knownServices.length > 0) ? (
          // Surface the user-typed URL as a label but submit the canonical
          // form as the value — the store will canonicalize again, but
          // doing it here too means React-Select's `value`/`option` match
          // when comparing already-canonicalized stored bindings.
          <Select
            value={draftService}
            onChange={setDraftService}
            placeholder="选择要绑定的服务"
            allowClear
            showSearch
            style={{ width: 260 }}
            options={candidates.map((c) => ({
              value: serviceRefIdFor(c.url) ?? c.url,
              label: `${c.name} (${c.url})`,
            }))}
          />
        ) : (
          <Input
            value={draftService ?? ''}
            onChange={(e) => setDraftService(e.target.value)}
            placeholder="输入服务 URL 或标识"
            style={{ width: 320 }}
          />
        )}
        <Button icon={<PlusOutlined />} onClick={handleAdd}>
          绑定
        </Button>
      </div>
      {rows.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="尚未绑定任何服务"
          style={{ padding: '12px 0' }}
        />
      ) : (
        <Table
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={rows}
          columns={[
            {
              title: '服务',
              dataIndex: 'service',
              render: (_, row) =>
                row.name ? (
                  <span>
                    <strong>{row.name}</strong>{' '}
                    <span className="auth-drawer__muted">({row.service})</span>
                  </span>
                ) : (
                  <span>{row.service}</span>
                ),
            },
            {
              title: '',
              width: 60,
              render: (_, row) => (
                <Popconfirm
                  title="解除该绑定？"
                  okText="解除"
                  cancelText="取消"
                  onConfirm={() => onBind(row.service, null)}
                >
                  <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              ),
            },
          ]}
        />
      )}
    </div>
  );
};

export default AuthSchemesDrawer;
