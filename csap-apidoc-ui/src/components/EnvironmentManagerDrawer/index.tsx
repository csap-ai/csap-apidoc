/**
 * EnvironmentManagerDrawer — left list + right form CRUD for environments.
 *
 * Layout follows the convention used elsewhere in csap-apidoc-ui: an Antd
 * Drawer whose body is a 2-column layout. Variables are edited as a tag-row
 * key/value table to keep the UI light without pulling in extra deps.
 */

import React, { useEffect, useMemo, useState } from 'react';
import {
  Drawer,
  List,
  Button,
  Form,
  Input,
  Popconfirm,
  Space,
  Table,
  Empty,
  Tag,
  message,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  CheckCircleFilled,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useEnvironments } from '@/contexts/EnvironmentContext';
import {
  Environment,
  ENV_PRESET_COLORS,
} from '@/stores/environmentStore';
import './index.less';

interface Props {
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  name: string;
  baseUrl: string;
  color: string;
}

interface VariableRow {
  key: string;
  name: string;
  value: string;
}

function toRows(env: Environment | null): VariableRow[] {
  if (!env) return [];
  return Object.entries(env.variables).map(([name, value], i) => ({
    key: `${i}-${name}`,
    name,
    value,
  }));
}

function rowsToMap(rows: VariableRow[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const r of rows) {
    const k = r.name.trim();
    if (k) out[k] = r.value;
  }
  return out;
}

const EnvironmentManagerDrawer: React.FC<Props> = ({ open, onClose }) => {
  const { t } = useTranslation();
  const { state, active, add, update, remove, setActive, suggestColor } =
    useEnvironments();

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form] = Form.useForm<FormValues>();
  const [rows, setRows] = useState<VariableRow[]>([]);

  const selected = useMemo<Environment | null>(
    () => state.items.find((e) => e.id === selectedId) ?? null,
    [state.items, selectedId],
  );

  useEffect(() => {
    if (!open) return;
    const fallback = active?.id ?? state.items[0]?.id ?? null;
    setSelectedId((prev) =>
      prev && state.items.some((e) => e.id === prev) ? prev : fallback,
    );
  }, [open, active?.id, state.items]);

  useEffect(() => {
    if (selected) {
      form.setFieldsValue({
        name: selected.name,
        baseUrl: selected.baseUrl,
        color: selected.color,
      });
      setRows(toRows(selected));
    } else {
      form.resetFields();
      setRows([]);
    }
  }, [selected?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = () => {
    const env = add({
      name: `Env ${state.items.length + 1}`,
      baseUrl: '',
      color: suggestColor(),
      variables: {},
    });
    setSelectedId(env.id);
  };

  const handleSave = async () => {
    if (!selected) return;
    try {
      const values = await form.validateFields();
      const variables = rowsToMap(rows);
      update(selected.id, {
        name: values.name.trim(),
        baseUrl: values.baseUrl.trim(),
        color: values.color || selected.color,
        variables,
      });
      message.success(t('common.saved'));
    } catch {
      // antd form validation error already surfaced
    }
  };

  const handleDelete = () => {
    if (!selected) return;
    remove(selected.id);
    setSelectedId(null);
    message.success(t('common.deleted'));
  };

  const handleSetActive = () => {
    if (!selected) return;
    setActive(selected.id);
    message.success(t('env.drawer.activated', { name: selected.name }));
  };

  const handleAddRow = () =>
    setRows((r) => [...r, { key: `new-${Date.now()}`, name: '', value: '' }]);

  const handleRowChange = (
    key: string,
    field: 'name' | 'value',
    val: string,
  ) =>
    setRows((rs) =>
      rs.map((r) => (r.key === key ? { ...r, [field]: val } : r)),
    );

  const handleRowDelete = (key: string) =>
    setRows((rs) => rs.filter((r) => r.key !== key));

  return (
    <Drawer
      title={t('env.drawer.title')}
      width={760}
      open={open}
      onClose={onClose}
      destroyOnClose
      className="env-drawer"
    >
      <div className="env-drawer__layout">
        <div className="env-drawer__list">
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            block
            onClick={handleCreate}
            className="env-drawer__add-btn"
          >
            {t('env.drawer.create')}
          </Button>
          {state.items.length === 0 ? (
            <Empty
              description={t('env.drawer.empty')}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              style={{ marginTop: 32 }}
            />
          ) : (
            <List
              dataSource={state.items}
              renderItem={(env) => {
                const isSelected = env.id === selectedId;
                const isActive = env.id === active?.id;
                return (
                  <List.Item
                    className={`env-drawer__item ${
                      isSelected ? 'env-drawer__item--selected' : ''
                    }`}
                    onClick={() => setSelectedId(env.id)}
                  >
                    <span
                      className="env-drawer__item-dot"
                      style={{ backgroundColor: env.color }}
                    />
                    <span className="env-drawer__item-name">{env.name}</span>
                    {isActive && (
                      <CheckCircleFilled className="env-drawer__item-active" />
                    )}
                  </List.Item>
                );
              }}
            />
          )}
        </div>

        <div className="env-drawer__form">
          {!selected ? (
            <Empty description={t('env.drawer.selectOrCreate')} />
          ) : (
            <>
              <Form layout="vertical" form={form} requiredMark={false}>
                <Form.Item
                  label={t('common.name')}
                  name="name"
                  rules={[
                    { required: true, message: t('env.form.name.required') },
                    { max: 32, message: t('env.form.name.maxLen') },
                  ]}
                >
                  <Input placeholder={t('env.form.name.placeholder')} />
                </Form.Item>
                <Form.Item
                  label={t('env.form.baseUrl.label')}
                  name="baseUrl"
                  extra={t('env.form.baseUrl.help')}
                >
                  <Input placeholder={t('env.form.baseUrl.placeholder')} />
                </Form.Item>
                <Form.Item label={t('env.form.color')} name="color">
                  <ColorPicker />
                </Form.Item>
              </Form>

              <div className="env-drawer__variables">
                <div className="env-drawer__section-title">
                  <span>{t('env.variables.title')}</span>
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={handleAddRow}
                  >
                    {t('common.add')}
                  </Button>
                </div>
                {rows.length === 0 ? (
                  <Empty
                    description={t('env.variables.empty')}
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    style={{ padding: '16px 0' }}
                  />
                ) : (
                  <Table<VariableRow>
                    rowKey="key"
                    size="small"
                    pagination={false}
                    dataSource={rows}
                    columns={[
                      {
                        title: t('env.variables.colName'),
                        dataIndex: 'name',
                        width: '40%',
                        render: (_, row) => (
                          <Input
                            size="small"
                            value={row.name}
                            placeholder={t('env.variables.name.placeholder')}
                            onChange={(e) =>
                              handleRowChange(row.key, 'name', e.target.value)
                            }
                          />
                        ),
                      },
                      {
                        title: t('env.variables.colValue'),
                        dataIndex: 'value',
                        render: (_, row) => (
                          <Input
                            size="small"
                            value={row.value}
                            placeholder={t('env.variables.value.placeholder')}
                            onChange={(e) =>
                              handleRowChange(row.key, 'value', e.target.value)
                            }
                          />
                        ),
                      },
                      {
                        title: '',
                        width: 50,
                        render: (_, row) => (
                          <Button
                            size="small"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => handleRowDelete(row.key)}
                          />
                        ),
                      },
                    ]}
                  />
                )}
                <div className="env-drawer__hint">
                  {t('env.variables.hint.before')}<Tag>{`{{name}}`}</Tag>
                  {t('env.variables.hint.middle')}<Tag>{`{{baseUrl}}`}</Tag>
                  {t('env.variables.hint.after')}
                </div>
              </div>

              <div className="env-drawer__actions">
                <Space>
                  <Popconfirm
                    title={t('env.drawer.deleteConfirm')}
                    onConfirm={handleDelete}
                    okText={t('common.delete')}
                    cancelText={t('common.cancel')}
                  >
                    <Button danger icon={<DeleteOutlined />}>
                      {t('common.delete')}
                    </Button>
                  </Popconfirm>
                  <Button
                    onClick={handleSetActive}
                    disabled={selected.id === active?.id}
                  >
                    {t('env.drawer.setActive')}
                  </Button>
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

interface ColorPickerProps {
  value?: string;
  onChange?: (val: string) => void;
}

const ColorPicker: React.FC<ColorPickerProps> = ({ value, onChange }) => {
  return (
    <Space wrap size={6}>
      {ENV_PRESET_COLORS.map((c) => (
        <span
          key={c}
          className={`env-drawer__color-chip ${
            c === value ? 'env-drawer__color-chip--active' : ''
          }`}
          style={{ backgroundColor: c }}
          onClick={() => onChange?.(c)}
        />
      ))}
    </Space>
  );
};

export default EnvironmentManagerDrawer;
