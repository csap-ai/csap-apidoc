/**
 * TryItOutPanel — endpoint workbench (M4).
 *
 * Self-contained, props-driven. Takes a starting `RequestSpec` (method, url,
 * pre-filled headers / query / body) and renders:
 *
 *   ┌─ URL bar ─────────────────────────────────────────────────────┐
 *   │ [METHOD]  https://…/orders/{{id}}              [Send] [Abort] │
 *   └────────────────────────────────────────────────────────────────┘
 *   ╭─ Tabs: Headers · Query · Body ─╮
 *   │  inline-editable key/value table or JSON editor                │
 *   ╰────────────────────────────────────────────────────────────────╯
 *   ┌─ Response ────────────────────────────────────────────────────┐
 *   │ 200 OK · 124 ms · 1.4 KB · application/json                   │
 *   │ [Body] [Headers] [Raw]                                        │
 *   └────────────────────────────────────────────────────────────────┘
 *
 * Auth/env/headers wiring lives in M5 — for now this panel knows nothing
 * about those contexts; callers pass `contextBadges` to surface them
 * visually if desired.
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Input,
  Select,
  Tabs,
  Table,
  Empty,
  Tag,
  Tooltip,
  Switch,
  Alert,
  message,
} from 'antd';
import {
  SendOutlined,
  StopOutlined,
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CopyOutlined,
  HistoryOutlined,
  BookOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import CodeMirror from '@uiw/react-codemirror';
import { githubLight } from '@uiw/codemirror-theme-github';
import {
  HttpMethod,
  TryItOutResult,
  isTryItOutFailure,
  newCancelTokenSource,
  sendTryItOutRequest,
} from '@/services/tryItOutClient';
import { settingsStore } from '@/stores/settingsStore';
import type { ComposerWarning } from '@/services/requestComposer';
import {
  TryItOutHistoryEntry,
  tryItOutHistoryStore,
} from '@/stores/tryItOutHistory';
import { TryItOutSnapshot } from '@/stores/tryItOutSnapshots';
import TryItOutHistoryDrawer from '@/components/TryItOutHistoryDrawer';
import TryItOutSnapshotsDrawer, {
  type SnapshotRequestDraft,
} from '@/components/TryItOutSnapshotsDrawer';
import './index.less';

interface KvRow {
  id: string;
  enabled: boolean;
  key: string;
  value: string;
}

export interface RequestSpec {
  method: HttpMethod;
  url: string;
  headers?: Record<string, string>;
  query?: Record<string, string>;
  body?: string;
}

export interface ContextBadge {
  label: string;
  value: string;
  color?: string;
}

interface Props {
  initial: RequestSpec;
  /** Caller-supplied pills (env / auth / header count) — populated in M5. */
  contextBadges?: ContextBadge[];
  /** Optional CORS proxy URL (settings.tryItOut.proxyUrl in M6); applied by client. */
  proxyUrl?: string | null;
  /** Per-call timeout (ms); default 30s. */
  timeoutMs?: number;
  /**
   * Hook for M5 to inject env/headers/auth into the outbound request right
   * before sending. Receives the user-edited spec and returns the final
   * spec to send PLUS any non-fatal `ComposerWarning`s the panel should
   * surface to the user. Default = identity (no warnings).
   *
   * For backwards compatibility a bare `RequestSpec` return is still
   * accepted and treated as zero warnings.
   */
  enrichRequest?: (
    spec: RequestSpec,
  ) =>
    | Promise<RequestSpec | { spec: RequestSpec; warnings: ComposerWarning[] }>
    | RequestSpec
    | { spec: RequestSpec; warnings: ComposerWarning[] };
}

const METHODS: HttpMethod[] = [
  'GET',
  'POST',
  'PUT',
  'DELETE',
  'PATCH',
  'HEAD',
  'OPTIONS',
];

const METHOD_COLOR: Record<HttpMethod, string> = {
  GET: '#52c41a',
  POST: '#1677ff',
  PUT: '#fa8c16',
  PATCH: '#fa8c16',
  DELETE: '#ff4d4f',
  HEAD: '#722ed1',
  OPTIONS: '#722ed1',
};

function newRow(): KvRow {
  return {
    id: 'r_' + Math.random().toString(36).slice(2, 9),
    enabled: true,
    key: '',
    value: '',
  };
}

function recordToRows(r: Record<string, string> | undefined): KvRow[] {
  if (!r || Object.keys(r).length === 0) return [];
  return Object.entries(r).map(([k, v]) => ({
    id: 'r_' + Math.random().toString(36).slice(2, 9),
    enabled: true,
    key: k,
    value: v,
  }));
}

function rowsToRecord(rows: KvRow[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const r of rows) {
    if (!r.enabled) continue;
    const k = r.key.trim();
    if (!k) continue;
    out[k] = r.value;
  }
  return out;
}

function statusColor(status: number): string {
  if (status >= 200 && status < 300) return 'success';
  if (status >= 300 && status < 400) return 'cyan';
  if (status >= 400 && status < 500) return 'warning';
  if (status >= 500) return 'error';
  return 'default';
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

const TryItOutPanel: React.FC<Props> = ({
  initial,
  contextBadges,
  proxyUrl,
  timeoutMs,
  enrichRequest,
}) => {
  const { t } = useTranslation();
  const [method, setMethod] = useState<HttpMethod>(initial.method);
  const [url, setUrl] = useState<string>(initial.url);
  const [headerRows, setHeaderRows] = useState<KvRow[]>(() =>
    recordToRows(initial.headers),
  );
  const [queryRows, setQueryRows] = useState<KvRow[]>(() =>
    recordToRows(initial.query),
  );
  const [body, setBody] = useState<string>(initial.body ?? '');
  const [bodyKind, setBodyKind] = useState<'json' | 'text' | 'none'>(() =>
    initial.body ? 'json' : 'none',
  );

  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<TryItOutResult | null>(null);
  const [responseTab, setResponseTab] = useState<'body' | 'headers' | 'raw'>(
    'body',
  );
  // M9.1 — drawer visibility for the request-history browser.
  const [historyOpen, setHistoryOpen] = useState(false);
  // M9.2 — drawer visibility for the named-snapshots browser.
  const [snapshotsOpen, setSnapshotsOpen] = useState(false);
  // C2 — non-fatal warnings emitted by enrichRequest (e.g. cookies the auth
  // scheme tried to set that the browser would refuse). Cleared at the start
  // of each send so users can dismiss without affecting the next attempt.
  const [composerWarnings, setComposerWarnings] = useState<ComposerWarning[]>(
    [],
  );
  const cancelRef = useRef<ReturnType<typeof newCancelTokenSource> | null>(null);

  // C4 — read tryItOutWithCredentials live from settingsStore so flipping
  // the SettingsDrawer switch takes effect on the very next send without
  // remounting the panel. Subscribe with a tracked snapshot rather than
  // calling getState() inline so React stays in sync with the store.
  const [withCredentials, setWithCredentials] = useState<boolean>(
    () => settingsStore.getState().tryItOutWithCredentials,
  );
  useEffect(() => {
    return settingsStore.subscribe((s) =>
      setWithCredentials(s.tryItOutWithCredentials),
    );
  }, []);

  // Re-seed when caller swaps to a new endpoint.
  useEffect(() => {
    setMethod(initial.method);
    setUrl(initial.url);
    setHeaderRows(recordToRows(initial.headers));
    setQueryRows(recordToRows(initial.query));
    setBody(initial.body ?? '');
    setBodyKind(initial.body ? 'json' : 'none');
    setResult(null);
    setResponseTab('body');
  }, [initial.method, initial.url]); // eslint-disable-line react-hooks/exhaustive-deps

  const supportsBody = useMemo(
    () => !['GET', 'HEAD'].includes(method),
    [method],
  );

  const handleSend = async () => {
    if (!url.trim()) {
      message.warning(t('tryout.error.urlRequired'));
      return;
    }
    let parsedBody: string | object | null | undefined = undefined;
    if (supportsBody && bodyKind !== 'none' && body.trim()) {
      if (bodyKind === 'json') {
        try {
          parsedBody = JSON.parse(body);
        } catch (err) {
          message.error(t('tryout.error.bodyJson'));
          return;
        }
      } else {
        parsedBody = body;
      }
    }

    let spec: RequestSpec = {
      method,
      url: url.trim(),
      headers: rowsToRecord(headerRows),
      query: rowsToRecord(queryRows),
      body:
        parsedBody == null
          ? undefined
          : typeof parsedBody === 'string'
          ? parsedBody
          : JSON.stringify(parsedBody),
    };

    let nextWarnings: ComposerWarning[] = [];
    if (enrichRequest) {
      try {
        const enriched = await enrichRequest(spec);
        if (enriched && typeof enriched === 'object' && 'spec' in enriched) {
          spec = (enriched as { spec: RequestSpec }).spec;
          nextWarnings =
            (enriched as { warnings?: ComposerWarning[] }).warnings ?? [];
        } else {
          spec = enriched as RequestSpec;
        }
      } catch (err) {
        message.error(t('tryout.error.enrich', { message: (err as Error).message }));
        return;
      }
    }

    cancelRef.current = newCancelTokenSource();
    setSending(true);
    setResult(null);
    setComposerWarnings(nextWarnings);
    try {
      const r = await sendTryItOutRequest(
        {
          method: spec.method,
          url: spec.url,
          headers: spec.headers,
          query: spec.query,
          body:
            spec.body == null
              ? null
              : bodyKind === 'json'
              ? safeParse(spec.body)
              : spec.body,
          timeoutMs,
          proxyUrl,
          withCredentials,
        },
        { cancelToken: cancelRef.current.token },
      );
      setResult(r);
      setResponseTab('body');
      // M9.1 — record the send in the local history ring buffer. We
      // intentionally stash the *user-edited* spec (headers/query/body) rather
      // than the enriched outbound form so Replay re-applies env/auth/global-
      // header enrichment freshly — otherwise a replay done after the vault
      // was rotated or the env switched would leak a stale credential.
      try {
        tryItOutHistoryStore.push({
          method,
          url: url.trim(),
          headers: rowsToRecord(headerRows),
          query: rowsToRecord(queryRows),
          body: supportsBody && bodyKind !== 'none' && body.trim() ? body : null,
          bodyKind,
          response: isTryItOutFailure(r)
            ? {
                ok: false,
                latencyMs: r.latencyMs,
                failureReason: r.reason,
                failureMessage: r.message,
              }
            : {
                ok: r.ok,
                status: r.status,
                statusText: r.statusText,
                latencyMs: r.latencyMs,
                byteLength: r.byteLength,
              },
        });
      } catch (err) {
        // History is a nice-to-have; never let a persistence error surface
        // to the user — they already have their response rendered above.
        console.warn('[csap-apidoc] failed to record try-it-out history', err);
      }
    } finally {
      setSending(false);
      cancelRef.current = null;
    }
  };

  /**
   * M9.1 — Replay seeds the form from a history entry and closes the drawer.
   * We bypass the `useEffect([initial.method, initial.url])` reseed hook on
   * purpose: that one is for *caller-driven* endpoint swaps, while replay is
   * a user action that should preserve whatever env/auth context the panel
   * is currently inside.
   */
  const handleReplay = (entry: TryItOutHistoryEntry) => {
    setMethod(entry.method);
    setUrl(entry.url);
    setHeaderRows(recordToRows(entry.headers));
    setQueryRows(recordToRows(entry.query));
    setBody(entry.body ?? '');
    setBodyKind(entry.bodyKind);
    setResult(null);
    setResponseTab('body');
    setComposerWarnings([]);
    setHistoryOpen(false);
  };

  /**
   * M9.2 — same reseed semantics as Replay but sourced from a named snapshot.
   * Kept as a separate handler so future divergence (e.g. mock-server mode
   * would skip the send entirely) stays trivial to add without entangling
   * the two flows.
   */
  const handleLoadSnapshot = (snap: TryItOutSnapshot) => {
    setMethod(snap.method);
    setUrl(snap.url);
    setHeaderRows(recordToRows(snap.headers));
    setQueryRows(recordToRows(snap.query));
    setBody(snap.body ?? '');
    setBodyKind(snap.bodyKind);
    setResult(null);
    setResponseTab('body');
    setComposerWarnings([]);
    setSnapshotsOpen(false);
  };

  /**
   * M9.2 — hand the drawer a fresh capture of the current panel state when
   * the user clicks `Save current request`. We compute it lazily (rather
   * than propping it through on every keystroke) so the drawer doesn't
   * force unnecessary re-renders while closed.
   */
  const captureCurrentDraft = (): SnapshotRequestDraft => ({
    method,
    url: url.trim(),
    headers: rowsToRecord(headerRows),
    query: rowsToRecord(queryRows),
    body: supportsBody && bodyKind !== 'none' && body.trim() ? body : null,
    bodyKind,
  });

  const handleAbort = () => {
    cancelRef.current?.cancel(t('tryout.cancel.userMessage'));
  };

  const renderKvTable = (
    rows: KvRow[],
    setRows: React.Dispatch<React.SetStateAction<KvRow[]>>,
    placeholders: { key: string; value: string },
  ) => {
    const update = (id: string, patch: Partial<KvRow>) =>
      setRows((rs) => rs.map((r) => (r.id === id ? { ...r, ...patch } : r)));
    const remove = (id: string) =>
      setRows((rs) => rs.filter((r) => r.id !== id));
    return (
      <div className="tryout__kv">
        <div className="tryout__kv-toolbar">
          <Button
            size="small"
            type="dashed"
            icon={<PlusOutlined />}
            onClick={() => setRows((rs) => [...rs, newRow()])}
          >
            {t('common.add')}
          </Button>
          <span className="tryout__muted">
            {t('tryout.kv.count', {
              total: rows.length,
              enabled: rows.filter((r) => r.enabled).length,
            })}
          </span>
        </div>
        {rows.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={t('tryout.kv.empty')}
            style={{ padding: '20px 0' }}
          />
        ) : (
          <Table<KvRow>
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={rows}
            columns={[
              {
                title: t('tryout.col.enabled'),
                dataIndex: 'enabled',
                width: 60,
                render: (_: boolean, row) => (
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
                width: '40%',
                render: (_: string, row) => (
                  <Input
                    size="small"
                    value={row.key}
                    placeholder={placeholders.key}
                    onChange={(e) => update(row.id, { key: e.target.value })}
                  />
                ),
              },
              {
                title: 'Value',
                dataIndex: 'value',
                render: (_: string, row) => (
                  <Input
                    size="small"
                    value={row.value}
                    placeholder={placeholders.value}
                    onChange={(e) => update(row.id, { value: e.target.value })}
                  />
                ),
              },
              {
                title: '',
                width: 44,
                render: (_, row) => (
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => remove(row.id)}
                  />
                ),
              },
            ]}
          />
        )}
      </div>
    );
  };

  const renderResponse = () => {
    if (sending) {
      return (
        <Alert
          showIcon
          type="info"
          message={t('tryout.sending.title')}
          description={t('tryout.sending.desc')}
        />
      );
    }
    if (!result) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('tryout.empty')}
        />
      );
    }
    if (isTryItOutFailure(result)) {
      const guidance =
        result.reason === 'network'
          ? t('tryout.failed.network')
          : result.reason === 'timeout'
          ? t('tryout.failed.timeout')
          : result.reason === 'cancelled'
          ? t('tryout.failed.cancelled')
          : result.reason === 'config'
          ? t('tryout.failed.config')
          : t('tryout.failed.unknown');
      return (
        <Alert
          showIcon
          type="error"
          message={t('tryout.failed.message', { message: result.message })}
          description={
            <span>
              {t('tryout.failed.elapsed', { ms: result.latencyMs })}
              <span className="tryout__muted">{guidance}</span>
            </span>
          }
        />
      );
    }
    return (
      <div className="tryout__response">
        <div className="tryout__response-meta">
          <Tag color={statusColor(result.status)} className="tryout__status-tag">
            {result.status} {result.statusText || ''}
          </Tag>
          <span className="tryout__muted">{result.latencyMs} ms</span>
          <span className="tryout__muted">·</span>
          <span className="tryout__muted">{formatBytes(result.byteLength)}</span>
          {result.contentType && (
            <>
              <span className="tryout__muted">·</span>
              <span className="tryout__muted">{result.contentType}</span>
            </>
          )}
          <span className="tryout__response-meta-spacer" />
          <Tooltip title={t('tryout.copy.tooltip')}>
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={() => {
                if (result.rawText != null) {
                  navigator.clipboard
                    ?.writeText(result.rawText)
                    .then(() => message.success(t('tryout.copy.success')))
                    .catch(() => message.error(t('tryout.copy.failed')));
                }
              }}
            />
          </Tooltip>
        </div>
        <Tabs
          activeKey={responseTab}
          onChange={(k) => setResponseTab(k as typeof responseTab)}
          items={[
            {
              key: 'body',
              label: 'Body',
              children: renderResponseBody(result, t),
            },
            {
              key: 'headers',
              label: `Headers (${Object.keys(result.headers).length})`,
              children: renderResponseHeaders(result.headers, t),
            },
            {
              key: 'raw',
              label: 'Raw',
              children: renderRawDump(result),
            },
          ]}
        />
      </div>
    );
  };

  return (
    <div className="tryout">
      {contextBadges && contextBadges.length > 0 && (
        <div className="tryout__badges">
          {contextBadges.map((b, i) => (
            <Tag key={i} color={b.color}>
              <strong>{b.label}</strong> {b.value}
            </Tag>
          ))}
        </div>
      )}

      <div className="tryout__urlbar">
        <Select
          value={method}
          onChange={(v) => setMethod(v as HttpMethod)}
          style={{ width: 110 }}
          dropdownMatchSelectWidth={false}
          options={METHODS.map((m) => ({
            value: m,
            label: (
              <span className="tryout__method-opt">
                <span
                  className="tryout__method-dot"
                  style={{ backgroundColor: METHOD_COLOR[m] }}
                />
                {m}
              </span>
            ),
          }))}
        />
        <Input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder={t('tryout.urlbar.placeholder')}
          className="tryout__url-input"
          onPressEnter={handleSend}
        />
        {sending ? (
          <Button danger icon={<StopOutlined />} onClick={handleAbort}>
            {t('tryout.urlbar.abort')}
          </Button>
        ) : (
          <Button type="primary" icon={<SendOutlined />} onClick={handleSend}>
            {t('tryout.urlbar.send')}
          </Button>
        )}
        <Tooltip title={t('tryout.urlbar.clearTooltip')}>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => setResult(null)}
            disabled={!result || sending}
          />
        </Tooltip>
        <Tooltip title={t('tryoutHistory.open.tooltip')}>
          <Button
            icon={<HistoryOutlined />}
            onClick={() => setHistoryOpen(true)}
            aria-label={t('tryoutHistory.open.tooltip')}
          />
        </Tooltip>
        <Tooltip title={t('tryoutSnapshots.open.tooltip')}>
          <Button
            icon={<BookOutlined />}
            onClick={() => setSnapshotsOpen(true)}
            aria-label={t('tryoutSnapshots.open.tooltip')}
          />
        </Tooltip>
      </div>

      <Tabs
        defaultActiveKey="headers"
        items={[
          {
            key: 'headers',
            label: `Headers (${headerRows.filter((r) => r.enabled && r.key).length})`,
            children: renderKvTable(headerRows, setHeaderRows, {
              key: t('tryout.headers.placeholderKey'),
              value: t('tryout.headers.placeholderValue'),
            }),
          },
          {
            key: 'query',
            label: `Query (${queryRows.filter((r) => r.enabled && r.key).length})`,
            children: renderKvTable(queryRows, setQueryRows, {
              key: t('tryout.query.placeholderKey'),
              value: t('tryout.query.placeholderValue'),
            }),
          },
          {
            key: 'body',
            label: 'Body',
            disabled: !supportsBody,
            children: (
              <div className="tryout__body">
                <div className="tryout__body-toolbar">
                  <Select
                    size="small"
                    value={bodyKind}
                    onChange={setBodyKind}
                    style={{ width: 130 }}
                    options={[
                      { value: 'none', label: t('tryout.tab.bodyKind.none') },
                      { value: 'json', label: t('tryout.tab.bodyKind.json') },
                      { value: 'text', label: t('tryout.tab.bodyKind.text') },
                    ]}
                  />
                </div>
                {bodyKind === 'none' ? (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={t('tryout.tab.body.noBody')}
                    style={{ padding: '24px 0' }}
                  />
                ) : (
                  <CodeMirror
                    theme={githubLight}
                    height="240px"
                    value={body}
                    onChange={(v) => setBody(v)}
                    placeholder={
                      bodyKind === 'json'
                        ? '{\n  "field": "value"\n}'
                        : t('tryout.tab.body.text.placeholder')
                    }
                  />
                )}
              </div>
            ),
          },
        ]}
      />

      {composerWarnings.length > 0 && (
        <Alert
          className="tryout__composer-warning"
          type="warning"
          showIcon
          closable
          onClose={() => setComposerWarnings([])}
          message={t('tryout.composer.warning.title')}
          description={
            <ul style={{ paddingLeft: 20, margin: 0 }}>
              {composerWarnings.map((w, i) => (
                <li key={i}>{describeComposerWarning(w, t)}</li>
              ))}
            </ul>
          }
        />
      )}

      <div className="tryout__divider">{t('tryout.divider.response')}</div>
      {renderResponse()}

      <TryItOutHistoryDrawer
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        onReplay={handleReplay}
      />

      <TryItOutSnapshotsDrawer
        open={snapshotsOpen}
        onClose={() => setSnapshotsOpen(false)}
        onLoad={handleLoadSnapshot}
        captureCurrent={captureCurrentDraft}
      />
    </div>
  );
};

function describeComposerWarning(w: ComposerWarning, t: TFunction): string {
  switch (w.kind) {
    case 'cookies-dropped':
      return t('tryout.composer.warning.cookies', { names: w.names.join(', ') });
    default:
      return JSON.stringify(w);
  }
}

function safeParse(s: string): object | string {
  try {
    return JSON.parse(s);
  } catch {
    return s;
  }
}

function renderResponseBody(
  result: Exclude<TryItOutResult, { failed: true }>,
  t: TFunction,
) {
  if (result.body == null) {
    return (
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('tryout.body.empty')} />
    );
  }
  if (result.bodyKind === 'image') {
    return (
      <div className="tryout__image-wrap">
        <img src={result.url} alt="response" className="tryout__image" />
      </div>
    );
  }
  if (result.bodyKind === 'binary') {
    return (
      <Alert
        showIcon
        type="info"
        message={t('tryout.body.binary.title', {
          contentType: result.contentType || 'unknown',
          bytes: result.byteLength,
        })}
        description={t('tryout.body.binary.desc')}
      />
    );
  }
  const text =
    result.bodyKind === 'json' && typeof result.body === 'object'
      ? JSON.stringify(result.body, null, 2)
      : (result.body as string);
  return (
    <CodeMirror
      theme={githubLight}
      height="320px"
      value={text}
      readOnly
      basicSetup={{ lineNumbers: true, foldGutter: true }}
    />
  );
}

function renderResponseHeaders(headers: Record<string, string>, t: TFunction) {
  const rows = Object.entries(headers).map(([k, v], i) => ({
    key: `${i}-${k}`,
    name: k,
    value: v,
  }));
  if (rows.length === 0) {
    return (
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('tryout.headers.empty')} />
    );
  }
  return (
    <Table
      rowKey="key"
      size="small"
      pagination={false}
      dataSource={rows}
      columns={[
        { title: 'Name', dataIndex: 'name', width: '36%' },
        { title: 'Value', dataIndex: 'value' },
      ]}
    />
  );
}

function renderRawDump(result: Exclude<TryItOutResult, { failed: true }>) {
  const reqLines: string[] = [
    `${result.request.method} ${result.request.url}`,
    ...Object.entries(result.request.headers).map(([k, v]) => `${k}: ${v}`),
  ];
  if (result.request.bodyPreview) {
    reqLines.push('', result.request.bodyPreview);
  }
  const resLines: string[] = [
    `HTTP ${result.status} ${result.statusText}`,
    ...Object.entries(result.headers).map(([k, v]) => `${k}: ${v}`),
  ];
  if (result.rawText) {
    resLines.push('', result.rawText);
  }
  return (
    <div className="tryout__raw">
      <div className="tryout__raw-block">
        <div className="tryout__raw-title">→ Request</div>
        <pre>{reqLines.join('\n')}</pre>
      </div>
      <div className="tryout__raw-block">
        <div className="tryout__raw-title">← Response</div>
        <pre>{resLines.join('\n')}</pre>
      </div>
    </div>
  );
}

export default TryItOutPanel;
