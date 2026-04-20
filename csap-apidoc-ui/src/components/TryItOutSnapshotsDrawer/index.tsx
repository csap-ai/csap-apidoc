/**
 * TryItOutSnapshotsDrawer — CRUD + import/export for named request templates (M9.2).
 *
 * Mounted inside {@link TryItOutPanel} the same way the M9.1 history drawer
 * is: the drawer owns nothing about the *current* form state — it takes a
 * `currentSpec` snapshot via props when opened, and hands decisions back to
 * the panel via `onLoad(snap)` callbacks. This keeps the panel as the
 * single source of truth for method/url/headers/query/body.
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Drawer,
  Empty,
  Table,
  Tag,
  Button,
  Popconfirm,
  Tooltip,
  Space,
  Modal,
  Form,
  Input,
  message,
  Upload,
  Radio,
} from 'antd';
import {
  BookOutlined,
  DeleteOutlined,
  PlaySquareOutlined,
  SaveOutlined,
  EditOutlined,
  UploadOutlined,
  DownloadOutlined,
  ClearOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import type { RcFile } from 'antd/es/upload';
import {
  TryItOutSnapshot,
  tryItOutSnapshotsStore,
  type ImportMode,
} from '@/stores/tryItOutSnapshots';
import type { HttpMethod } from '@/services/tryItOutClient';
import './index.less';

/**
 * Shape the panel hands to us when `Save current request` is clicked.
 * We do NOT close over the panel's state directly so the drawer stays
 * testable in isolation.
 */
export interface SnapshotRequestDraft {
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  query: Record<string, string>;
  body: string | null;
  bodyKind: 'json' | 'text' | 'none';
}

interface Props {
  open: boolean;
  onClose: () => void;
  /**
   * Called when the user clicks Load on a row. The panel re-seeds its
   * form from the snapshot and typically closes the drawer.
   */
  onLoad: (snap: TryItOutSnapshot) => void;
  /**
   * Captures the panel's current method/url/headers/query/body so the
   * drawer can persist it under a user-chosen name. Called lazily when
   * the user clicks `Save current request`, so the panel doesn't have
   * to recompute on every keystroke.
   */
  captureCurrent: () => SnapshotRequestDraft;
}

const METHOD_COLOR: Record<HttpMethod, string> = {
  GET: '#52c41a',
  POST: '#1677ff',
  PUT: '#fa8c16',
  PATCH: '#fa8c16',
  DELETE: '#ff4d4f',
  HEAD: '#722ed1',
  OPTIONS: '#722ed1',
};

interface SaveModalValues {
  name: string;
  description?: string;
}

const TryItOutSnapshotsDrawer: React.FC<Props> = ({
  open,
  onClose,
  onLoad,
  captureCurrent,
}) => {
  const { t } = useTranslation();
  const [snapshots, setSnapshots] = useState<TryItOutSnapshot[]>(
    () => tryItOutSnapshotsStore.list(),
  );

  // Modals: save-current / rename-existing.
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [savingDraft, setSavingDraft] = useState<SnapshotRequestDraft | null>(
    null,
  );
  const [saveForm] = Form.useForm<SaveModalValues>();

  const [renameTarget, setRenameTarget] = useState<TryItOutSnapshot | null>(
    null,
  );
  const [renameForm] = Form.useForm<SaveModalValues>();

  // Import flow: radio chooser between merge / replace.
  const [importMode, setImportMode] = useState<ImportMode>('merge');

  useEffect(() => {
    const unsub = tryItOutSnapshotsStore.subscribe((s) =>
      setSnapshots(s.snapshots),
    );
    setSnapshots(tryItOutSnapshotsStore.list());
    return unsub;
  }, []);

  // Wipe draft state when drawer closes so a reopen starts clean.
  useEffect(() => {
    if (!open) {
      setSaveModalOpen(false);
      setSavingDraft(null);
      saveForm.resetFields();
      setRenameTarget(null);
      renameForm.resetFields();
    }
  }, [open, saveForm, renameForm]);

  const handleOpenSaveModal = () => {
    const draft = captureCurrent();
    if (!draft.url.trim()) {
      message.warning(t('tryoutSnapshots.save.emptyUrl'));
      return;
    }
    setSavingDraft(draft);
    saveForm.resetFields();
    setSaveModalOpen(true);
  };

  const handleConfirmSave = async () => {
    const values = await saveForm.validateFields();
    if (!savingDraft) return;
    try {
      tryItOutSnapshotsStore.create({
        name: values.name,
        description: values.description,
        ...savingDraft,
      });
      message.success(t('tryoutSnapshots.save.success'));
      setSaveModalOpen(false);
      setSavingDraft(null);
    } catch (err) {
      message.error(
        t('tryoutSnapshots.save.failed', { message: (err as Error).message }),
      );
    }
  };

  const handleRename = async () => {
    if (!renameTarget) return;
    const values = await renameForm.validateFields();
    tryItOutSnapshotsStore.update(renameTarget.id, {
      name: values.name,
      description: values.description,
    });
    message.success(t('tryoutSnapshots.rename.success'));
    setRenameTarget(null);
  };

  const handleExport = () => {
    const payload = tryItOutSnapshotsStore.exportJson();
    const blob = new Blob([payload], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const stamp = new Date().toISOString().slice(0, 10);
    a.href = url;
    a.download = `csap-apidoc-snapshots-${stamp}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    message.success(
      t('tryoutSnapshots.export.success', { count: snapshots.length }),
    );
  };

  const beforeImportUpload = async (file: RcFile): Promise<boolean> => {
    try {
      const text = await file.text();
      const res = tryItOutSnapshotsStore.importJson(text, importMode);
      if (res.error) {
        message.error(t('tryoutSnapshots.import.failed', { message: res.error }));
      } else {
        message.success(
          t('tryoutSnapshots.import.success', {
            added: res.added,
            skipped: res.skipped,
          }),
        );
      }
    } catch (err) {
      message.error(
        t('tryoutSnapshots.import.failed', { message: (err as Error).message }),
      );
    }
    // Returning `false` prevents AntD from actually uploading anywhere —
    // we've already handled the file entirely client-side.
    return false;
  };

  const columns: ColumnsType<TryItOutSnapshot> = useMemo(
    () => [
      {
        title: t('tryoutSnapshots.col.name'),
        dataIndex: 'name',
        width: 200,
        render: (_name: string, row: TryItOutSnapshot) => (
          <div className="tryoutSnapshots__name">
            <div className="tryoutSnapshots__name-primary">{row.name}</div>
            {row.description && (
              <div className="tryoutSnapshots__name-desc">{row.description}</div>
            )}
          </div>
        ),
      },
      {
        title: t('tryoutSnapshots.col.method'),
        dataIndex: 'method',
        width: 90,
        render: (m: HttpMethod) => (
          <Tag color={METHOD_COLOR[m]} style={{ fontWeight: 600, marginRight: 0 }}>
            {m}
          </Tag>
        ),
      },
      {
        title: t('tryoutSnapshots.col.url'),
        dataIndex: 'url',
        ellipsis: { showTitle: false },
        render: (url: string) => (
          <Tooltip title={url} placement="topLeft">
            <span className="tryoutSnapshots__url">{url}</span>
          </Tooltip>
        ),
      },
      {
        title: t('tryoutSnapshots.col.updatedAt'),
        dataIndex: 'updatedAt',
        width: 160,
        render: (ts: number) => (
          <span className="tryoutSnapshots__muted">
            {new Date(ts).toLocaleString()}
          </span>
        ),
      },
      {
        title: t('tryoutSnapshots.col.actions'),
        width: 200,
        render: (_: unknown, row: TryItOutSnapshot) => (
          <Space size="small">
            <Button
              type="primary"
              size="small"
              icon={<PlaySquareOutlined />}
              onClick={() => onLoad(row)}
            >
              {t('tryoutSnapshots.load')}
            </Button>
            <Tooltip title={t('tryoutSnapshots.rename')}>
              <Button
                size="small"
                icon={<EditOutlined />}
                onClick={() => {
                  setRenameTarget(row);
                  renameForm.setFieldsValue({
                    name: row.name,
                    description: row.description ?? '',
                  });
                }}
              />
            </Tooltip>
            <Popconfirm
              title={t('tryoutSnapshots.remove.confirm')}
              okText={t('common.yes')}
              cancelText={t('common.no')}
              onConfirm={() => {
                tryItOutSnapshotsStore.remove(row.id);
                message.success(t('tryoutSnapshots.remove.success'));
              }}
            >
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [t, onLoad, renameForm],
  );

  return (
    <Drawer
      title={
        <Space>
          <BookOutlined />
          {t('tryoutSnapshots.title')}
          <span className="tryoutSnapshots__muted">
            {t('tryoutSnapshots.count', { count: snapshots.length })}
          </span>
        </Space>
      }
      open={open}
      onClose={onClose}
      width={1040}
      extra={
        <Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            onClick={handleOpenSaveModal}
          >
            {t('tryoutSnapshots.saveCurrent')}
          </Button>
          <Radio.Group
            size="small"
            value={importMode}
            onChange={(e) => setImportMode(e.target.value)}
          >
            <Radio.Button value="merge">
              {t('tryoutSnapshots.import.mode.merge')}
            </Radio.Button>
            <Radio.Button value="replace">
              {t('tryoutSnapshots.import.mode.replace')}
            </Radio.Button>
          </Radio.Group>
          <Upload
            accept="application/json,.json"
            showUploadList={false}
            beforeUpload={beforeImportUpload}
          >
            <Button icon={<UploadOutlined />}>
              {t('tryoutSnapshots.import')}
            </Button>
          </Upload>
          <Button
            icon={<DownloadOutlined />}
            onClick={handleExport}
            disabled={snapshots.length === 0}
          >
            {t('tryoutSnapshots.export')}
          </Button>
          {snapshots.length > 0 && (
            <Popconfirm
              title={t('tryoutSnapshots.clear.confirm')}
              okText={t('common.yes')}
              cancelText={t('common.no')}
              onConfirm={() => {
                tryItOutSnapshotsStore.clear();
                message.success(t('tryoutSnapshots.clear.success'));
              }}
            >
              <Button icon={<ClearOutlined />} danger>
                {t('tryoutSnapshots.clear')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      }
    >
      {snapshots.length === 0 ? (
        <Empty description={t('tryoutSnapshots.empty')} />
      ) : (
        <Table<TryItOutSnapshot>
          rowKey="id"
          size="small"
          pagination={{ pageSize: 25, hideOnSinglePage: true }}
          dataSource={snapshots}
          columns={columns}
        />
      )}

      <Modal
        title={t('tryoutSnapshots.save.modal.title')}
        open={saveModalOpen}
        onOk={handleConfirmSave}
        onCancel={() => setSaveModalOpen(false)}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnClose
      >
        <Form form={saveForm} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label={t('tryoutSnapshots.field.name')}
            rules={[{ required: true, message: t('tryoutSnapshots.field.name.required') }]}
          >
            <Input autoFocus placeholder={t('tryoutSnapshots.field.name.placeholder')} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('tryoutSnapshots.field.description')}
          >
            <Input.TextArea
              rows={2}
              placeholder={t('tryoutSnapshots.field.description.placeholder')}
            />
          </Form.Item>
          {savingDraft && (
            <div className="tryoutSnapshots__draftSummary">
              <Tag color={METHOD_COLOR[savingDraft.method]}>{savingDraft.method}</Tag>
              <span className="tryoutSnapshots__url">{savingDraft.url}</span>
            </div>
          )}
        </Form>
      </Modal>

      <Modal
        title={t('tryoutSnapshots.rename.modal.title')}
        open={renameTarget !== null}
        onOk={handleRename}
        onCancel={() => setRenameTarget(null)}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        destroyOnClose
      >
        <Form form={renameForm} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label={t('tryoutSnapshots.field.name')}
            rules={[{ required: true, message: t('tryoutSnapshots.field.name.required') }]}
          >
            <Input autoFocus />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('tryoutSnapshots.field.description')}
          >
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  );
};

export default TryItOutSnapshotsDrawer;
