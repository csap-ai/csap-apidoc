/**
 * TryItOutHistoryDrawer — browse + replay the last N Try-it-out requests (M9.1).
 *
 * This drawer is mounted *inside* {@link TryItOutPanel} so the panel owns the
 * "Replay" seam — on click we hand the parent a {@link TryItOutHistoryEntry}
 * and let it reseed its own form state. Keeping the drawer passive avoids
 * duplicating the method/url/headers/query/body state in yet another store
 * and preserves the panel's existing prop-driven `initial` contract.
 */
import React, { useEffect, useState } from 'react';
import {
  Drawer,
  Empty,
  Table,
  Tag,
  Button,
  Popconfirm,
  Tooltip,
  Space,
  message,
} from 'antd';
import {
  HistoryOutlined,
  DeleteOutlined,
  PlaySquareOutlined,
  ClearOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import {
  TryItOutHistoryEntry,
  tryItOutHistoryStore,
} from '@/stores/tryItOutHistory';
import type { HttpMethod } from '@/services/tryItOutClient';
import './index.less';

interface Props {
  open: boolean;
  onClose: () => void;
  /**
   * Called when the user clicks Replay on an entry. The panel is expected
   * to reseed its form fields and then auto-close the drawer (we don't
   * close on the drawer's side so the caller can e.g. keep it open on
   * error).
   */
  onReplay: (entry: TryItOutHistoryEntry) => void;
}

// Mirrors TryItOutPanel's METHOD_COLOR so colour stays consistent between
// the panel's URL bar and the history drawer.
const METHOD_COLOR: Record<HttpMethod, string> = {
  GET: '#52c41a',
  POST: '#1677ff',
  PUT: '#fa8c16',
  PATCH: '#fa8c16',
  DELETE: '#ff4d4f',
  HEAD: '#722ed1',
  OPTIONS: '#722ed1',
};

function statusColor(status: number | undefined): string {
  if (status == null) return 'default';
  if (status >= 200 && status < 300) return 'success';
  if (status >= 300 && status < 400) return 'cyan';
  if (status >= 400 && status < 500) return 'warning';
  if (status >= 500) return 'error';
  return 'default';
}

function formatBytes(n: number | undefined): string {
  if (n == null) return '—';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

/**
 * Light-weight relative time formatter — avoids pulling in dayjs for one
 * string. Buckets: "just now", "Xm ago", "Xh ago", or a YYYY-MM-DD HH:mm
 * absolute timestamp for anything older than 24h.
 */
function formatRelativeTime(ts: number, now: number = Date.now()): string {
  const diffSec = Math.max(0, Math.floor((now - ts) / 1000));
  if (diffSec < 10) return 'just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const d = new Date(ts);
  const p = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(
    d.getHours(),
  )}:${p(d.getMinutes())}`;
}

const TryItOutHistoryDrawer: React.FC<Props> = ({ open, onClose, onReplay }) => {
  const { t } = useTranslation();
  const [entries, setEntries] = useState<TryItOutHistoryEntry[]>(
    () => tryItOutHistoryStore.list(),
  );

  // Subscribe once the drawer mounts so pushes from the parent's `handleSend`
  // are reflected live even while the drawer is open.
  useEffect(() => {
    const unsub = tryItOutHistoryStore.subscribe((state) => {
      setEntries(state.entries);
    });
    // Re-sync on open/close in case entries were added while unsubscribed
    // (e.g. during the first render before the effect runs).
    setEntries(tryItOutHistoryStore.list());
    return unsub;
  }, []);

  const columns: ColumnsType<TryItOutHistoryEntry> = [
    {
      title: t('tryoutHistory.col.method'),
      dataIndex: 'method',
      width: 90,
      render: (m: HttpMethod) => (
        <Tag color={METHOD_COLOR[m]} style={{ fontWeight: 600, marginRight: 0 }}>
          {m}
        </Tag>
      ),
    },
    {
      title: t('tryoutHistory.col.url'),
      dataIndex: 'url',
      ellipsis: { showTitle: false },
      render: (url: string) => (
        <Tooltip title={url} placement="topLeft">
          <span className="tryoutHistory__url">{url}</span>
        </Tooltip>
      ),
    },
    {
      title: t('tryoutHistory.col.status'),
      width: 120,
      render: (_: unknown, row: TryItOutHistoryEntry) => {
        if (row.response.ok && row.response.status != null) {
          return (
            <Tag color={statusColor(row.response.status)}>
              {row.response.status} {row.response.statusText ?? ''}
            </Tag>
          );
        }
        const reason = row.response.failureReason ?? 'unknown';
        return (
          <Tooltip title={row.response.failureMessage ?? reason}>
            <Tag color="error">
              {t('tryoutHistory.status.failed', { reason })}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: t('tryoutHistory.col.latency'),
      width: 90,
      render: (_: unknown, row: TryItOutHistoryEntry) => (
        <span className="tryoutHistory__muted">{row.response.latencyMs} ms</span>
      ),
    },
    {
      title: t('tryoutHistory.col.size'),
      width: 90,
      render: (_: unknown, row: TryItOutHistoryEntry) => (
        <span className="tryoutHistory__muted">
          {formatBytes(row.response.byteLength)}
        </span>
      ),
    },
    {
      title: t('tryoutHistory.col.time'),
      width: 130,
      render: (_: unknown, row: TryItOutHistoryEntry) => (
        <Tooltip title={new Date(row.timestamp).toISOString()}>
          <span className="tryoutHistory__muted">
            {formatRelativeTime(row.timestamp)}
          </span>
        </Tooltip>
      ),
    },
    {
      title: t('tryoutHistory.col.actions'),
      width: 140,
      render: (_: unknown, row: TryItOutHistoryEntry) => (
        <Space size="small">
          <Button
            type="primary"
            size="small"
            icon={<PlaySquareOutlined />}
            onClick={() => onReplay(row)}
          >
            {t('tryoutHistory.replay')}
          </Button>
          <Popconfirm
            title={t('tryoutHistory.remove.confirm')}
            okText={t('common.yes')}
            cancelText={t('common.no')}
            onConfirm={() => {
              tryItOutHistoryStore.remove(row.id);
              message.success(t('tryoutHistory.remove.success'));
            }}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title={
        <Space>
          <HistoryOutlined />
          {t('tryoutHistory.title')}
          <span className="tryoutHistory__muted">
            {t('tryoutHistory.count', { count: entries.length })}
          </span>
        </Space>
      }
      open={open}
      onClose={onClose}
      width={980}
      extra={
        entries.length > 0 ? (
          <Popconfirm
            title={t('tryoutHistory.clear.confirm')}
            okText={t('common.yes')}
            cancelText={t('common.no')}
            onConfirm={() => {
              tryItOutHistoryStore.clear();
              message.success(t('tryoutHistory.clear.success'));
            }}
          >
            <Button icon={<ClearOutlined />} danger>
              {t('tryoutHistory.clear')}
            </Button>
          </Popconfirm>
        ) : null
      }
    >
      {entries.length === 0 ? (
        <Empty description={t('tryoutHistory.empty')} />
      ) : (
        <Table<TryItOutHistoryEntry>
          rowKey="id"
          size="small"
          pagination={{ pageSize: 25, hideOnSinglePage: true }}
          dataSource={entries}
          columns={columns}
        />
      )}
    </Drawer>
  );
};

export default TryItOutHistoryDrawer;
