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
      message.success('已保存');
    } catch {
      // antd form validation error already surfaced
    }
  };

  const handleDelete = () => {
    if (!selected) return;
    remove(selected.id);
    setSelectedId(null);
    message.success('已删除');
  };

  const handleSetActive = () => {
    if (!selected) return;
    setActive(selected.id);
    message.success(`已切换到「${selected.name}」`);
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
      title="环境管理"
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
            新建环境
          </Button>
          {state.items.length === 0 ? (
            <Empty
              description="尚未配置环境"
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
            <Empty description="请选择或新建一个环境" />
          ) : (
            <>
              <Form layout="vertical" form={form} requiredMark={false}>
                <Form.Item
                  label="名称"
                  name="name"
                  rules={[
                    { required: true, message: '请输入环境名称' },
                    { max: 32, message: '最多 32 个字符' },
                  ]}
                >
                  <Input placeholder="例如：Dev / Staging / Prod" />
                </Form.Item>
                <Form.Item
                  label="Base URL"
                  name="baseUrl"
                  extra="留空表示沿用当前页面来源（适合开发代理场景）"
                >
                  <Input placeholder="https://api-staging.example.com" />
                </Form.Item>
                <Form.Item label="颜色标记" name="color">
                  <ColorPicker />
                </Form.Item>
              </Form>

              <div className="env-drawer__variables">
                <div className="env-drawer__section-title">
                  <span>变量</span>
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={handleAddRow}
                  >
                    新增
                  </Button>
                </div>
                {rows.length === 0 ? (
                  <Empty
                    description="暂无变量"
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
                        title: '名称',
                        dataIndex: 'name',
                        width: '40%',
                        render: (_, row) => (
                          <Input
                            size="small"
                            value={row.name}
                            placeholder="如 tenantId"
                            onChange={(e) =>
                              handleRowChange(row.key, 'name', e.target.value)
                            }
                          />
                        ),
                      },
                      {
                        title: '值',
                        dataIndex: 'value',
                        render: (_, row) => (
                          <Input
                            size="small"
                            value={row.value}
                            placeholder="如 42"
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
                  在 Base URL、请求头等位置使用 <Tag>{`{{name}}`}</Tag>{' '}
                  引用变量；保留字 <Tag>{`{{baseUrl}}`}</Tag>{' '}
                  始终解析为当前 Base URL。
                </div>
              </div>

              <div className="env-drawer__actions">
                <Space>
                  <Popconfirm
                    title="确认删除该环境？"
                    onConfirm={handleDelete}
                    okText="删除"
                    cancelText="取消"
                  >
                    <Button danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                  <Button
                    onClick={handleSetActive}
                    disabled={selected.id === active?.id}
                  >
                    设为当前
                  </Button>
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
