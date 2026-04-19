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
import { useTranslation } from 'react-i18next';
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

const TYPE_I18N_KEYS: Record<AuthSchemeType, string> = {
  bearer: 'auth.type.bearer',
  basic: 'auth.type.basic',
  apikey: 'auth.type.apikey',
  oauth2_client: 'auth.type.oauth2_client',
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
  const { t } = useTranslation();
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
      message.success(t('common.saved'));
    } catch {
      // antd validation already surfaced
    }
  };

  const handleDelete = () => {
    if (!selected) return;
    remove(selected.id);
    setSelectedId(null);
    message.success(t('common.deleted'));
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
          {t('auth.drawer.title')}{' '}
          <Tag className="auth-drawer__title-tag">
            {t('auth.drawer.summary', {
              schemes: state.items.length,
              bound: Object.keys(state.activeBindings).length,
            })}
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
            {t('auth.drawer.create')}
          </Button>
          {state.items.length === 0 ? (
            <Empty
              description={t('auth.drawer.empty')}
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
                        {t(TYPE_I18N_KEYS[s.type])}
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
            <Empty description={t('auth.drawer.selectOrCreate')} />
          ) : (
            <>
              <Form layout="vertical" form={form} requiredMark={false}>
                <Form.Item
                  label={t('common.name')}
                  name="name"
                  rules={[
                    { required: true, message: t('auth.form.name.required') },
                    { max: 48, message: t('auth.form.name.maxLen') },
                  ]}
                >
                  <Input placeholder={t('auth.form.name.placeholder')} />
                </Form.Item>

                <Form.Item label={t('auth.form.type.label')} name="type">
                  <Select onChange={handleTypeChange}>
                    {(Object.keys(TYPE_I18N_KEYS) as AuthSchemeType[]).map((typeKey) => (
                      <Select.Option key={typeKey} value={typeKey}>
                        <span
                          className="auth-drawer__type-dot"
                          style={{ backgroundColor: TYPE_COLORS[typeKey] }}
                        />
                        {t(TYPE_I18N_KEYS[typeKey])}
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>

                <Form.Item label={t('auth.form.desc.label')} name="description">
                  <Input placeholder={t('auth.form.desc.placeholder')} />
                </Form.Item>
              </Form>

              <div className="auth-drawer__section">
                <div className="auth-drawer__section-title">{t('auth.section.credential')}</div>
                {renderTypeForm()}
              </div>

              <div className="auth-drawer__section">
                <div className="auth-drawer__section-title">
                  <span>
                    {t('auth.section.bindings')}{' '}
                    <Tooltip title={t('auth.section.bindings.tooltip')}>
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
                    title={t('auth.drawer.deleteConfirm')}
                    onConfirm={handleDelete}
                    okText={t('common.delete')}
                    cancelText={t('common.cancel')}
                  >
                    <Button danger icon={<DeleteOutlined />}>
                      {t('common.delete')}
                    </Button>
                  </Popconfirm>
                  <Button type="primary" onClick={handleSave}>
                    {t('common.save')}
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
  const { t } = useTranslation();
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
        placeholder={placeholder ?? t('auth.secret.placeholder')}
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
          {t('auth.secret.savedHint', { ref: value.slice(0, 14) })}
        </span>
      )}
    </div>
  );
};

/* ───────────────────────── per-type forms ──────────────────────── */

const BearerForm: React.FC<{
  value: BearerConfig;
  onChange: (next: BearerConfig) => void;
}> = ({ value, onChange }) => {
  const { t } = useTranslation();
  return (
    <Form layout="vertical" requiredMark={false}>
      <Form.Item label={t('auth.bearer.token.label')}>
        <SecretInput
          value={value.tokenRef}
          onChange={(ref) => onChange({ ...value, tokenRef: ref })}
          placeholder="eyJhbGciOi..."
        />
      </Form.Item>
    </Form>
  );
};

const BasicForm: React.FC<{
  value: BasicConfig;
  onChange: (next: BasicConfig) => void;
}> = ({ value, onChange }) => {
  const { t } = useTranslation();
  return (
    <Form layout="vertical" requiredMark={false}>
      <Form.Item label={t('auth.basic.username.label')}>
        <Input
          value={value.username}
          onChange={(e) => onChange({ ...value, username: e.target.value })}
          placeholder={t('auth.basic.username.placeholder')}
        />
      </Form.Item>
      <Form.Item label={t('auth.basic.password.label')}>
        <SecretInput
          value={value.passwordRef}
          onChange={(ref) => onChange({ ...value, passwordRef: ref })}
        />
      </Form.Item>
    </Form>
  );
};

const ApiKeyForm: React.FC<{
  value: ApiKeyConfig;
  onChange: (next: ApiKeyConfig) => void;
}> = ({ value, onChange }) => {
  const { t } = useTranslation();
  return (
    <Form layout="vertical" requiredMark={false}>
      <Form.Item label={t('auth.apikey.in.label')}>
        <Select
          value={value.in}
          onChange={(v) => onChange({ ...value, in: v })}
          options={[
            { value: 'header', label: t('auth.apikey.in.header') },
            { value: 'query', label: t('auth.apikey.in.query') },
            { value: 'cookie', label: t('auth.apikey.in.cookie') },
          ]}
        />
      </Form.Item>
      <Form.Item label={t('auth.apikey.name.label')}>
        <Input
          value={value.name}
          onChange={(e) => onChange({ ...value, name: e.target.value })}
          placeholder={
            value.in === 'header'
              ? t('auth.apikey.name.headerPlaceholder')
              : value.in === 'query'
              ? t('auth.apikey.name.queryPlaceholder')
              : t('auth.apikey.name.cookiePlaceholder')
          }
        />
      </Form.Item>
      <Form.Item label={t('auth.apikey.value.label')}>
        <SecretInput
          value={value.valueRef}
          onChange={(ref) => onChange({ ...value, valueRef: ref })}
        />
      </Form.Item>
    </Form>
  );
};

const OAuth2ClientForm: React.FC<{
  value: OAuth2ClientConfig;
  onChange: (next: OAuth2ClientConfig) => void;
}> = ({ value, onChange }) => {
  const { t } = useTranslation();
  const cachedTok = value.cachedTokenRef ? vault.get(value.cachedTokenRef) : null;
  const cachedExp = value.cachedExpiresAt;
  const now = Date.now();
  const cacheState = !cachedTok
    ? t('auth.oauth.cache.none')
    : cachedExp && cachedExp - 30_000 > now
    ? t('auth.oauth.cache.valid', { expires: new Date(cachedExp).toLocaleString() })
    : t('auth.oauth.cache.expired');

  const clearCache = () => {
    if (value.cachedTokenRef) vault.remove(value.cachedTokenRef);
    onChange({ ...value, cachedTokenRef: undefined, cachedExpiresAt: undefined });
    message.success(t('auth.oauth.cache.cleared'));
  };

  return (
    <Form layout="vertical" requiredMark={false}>
      <Form.Item label={t('auth.oauth.tokenUrl.label')}>
        <Input
          value={value.tokenUrl}
          onChange={(e) => onChange({ ...value, tokenUrl: e.target.value })}
          placeholder={t('auth.oauth.tokenUrl.placeholder')}
        />
      </Form.Item>
      <Form.Item label={t('auth.oauth.clientId.label')}>
        <Input
          value={value.clientId}
          onChange={(e) => onChange({ ...value, clientId: e.target.value })}
        />
      </Form.Item>
      <Form.Item label={t('auth.oauth.clientSecret.label')}>
        <SecretInput
          value={value.clientSecretRef}
          onChange={(ref) => onChange({ ...value, clientSecretRef: ref })}
        />
      </Form.Item>
      <Form.Item label={t('auth.oauth.scope.label')}>
        <Input
          value={value.scope ?? ''}
          onChange={(e) => onChange({ ...value, scope: e.target.value })}
          placeholder={t('auth.oauth.scope.placeholder')}
        />
      </Form.Item>
      <Form.Item label={t('auth.oauth.cache.label')}>
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
            {t('auth.oauth.cache.clear')}
          </Button>
          <span className="auth-drawer__muted auth-drawer__hint-line">
            {t('auth.oauth.cache.hint')}
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
  const { t } = useTranslation();
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
      message.warning(t('auth.bind.required'));
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
            placeholder={t('auth.bind.placeholder')}
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
            placeholder={t('auth.bind.input')}
            style={{ width: 320 }}
          />
        )}
        <Button icon={<PlusOutlined />} onClick={handleAdd}>
          {t('auth.bind.button')}
        </Button>
      </div>
      {rows.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('auth.bind.empty')}
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
              title: t('auth.bind.col.service'),
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
                  title={t('auth.bind.removeConfirm')}
                  okText={t('auth.bind.remove')}
                  cancelText={t('common.cancel')}
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
