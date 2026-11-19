import { useState } from 'react';
import { Alert, Button, Card, DatePicker, Descriptions, Drawer, Dropdown, Input, Select, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { auditApi, projectApi, serviceApi, type AuditQuery } from '@/api/endpoints';
import type { AuditRecord, McpAuditRecord } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { canExportAudit, canViewAudit } from '@/auth/permissions';
import { Forbidden, PageTitle } from '@/components/common';
import DiffView, { CodeBlock } from '@/components/DiffView';
import { useLoad } from '@/utils/useLoad';
import { fmtTime, prettyJson } from '@/utils/format';
import { showError } from '@/utils/errors';
import { t, tDyn } from '@/i18n';

const SOURCES = ['CONSOLE', 'OPEN_API', 'SERVICE', 'MCP', 'MIGRATE', 'SYSTEM'];
type Range = [Dayjs | null, Dayjs | null] | null;
const toRange = (r: Range): Pick<AuditQuery, 'from' | 'to'> => ({ from: r?.[0]?.startOf('day').valueOf(), to: r?.[1]?.endOf('day').valueOf() });

export default function Audit() {
  const { me } = useAuth();
  if (!canViewAudit(me)) return <Forbidden />;
  return (
    <>
      <PageTitle title={t('audit.title')} />
      <Tabs items={[
        { key: 'records', label: t('audit.tab.records'), children: <Records /> },
        { key: 'mcp', label: t('audit.tab.mcp'), children: <Mcp /> },
      ]} />
    </>
  );
}

function Records() {
  const { me } = useAuth();
  const [range, setRange] = useState<Range>(null);
  const [f, setF] = useState<Omit<AuditQuery, 'from' | 'to'>>({});
  const [draft, setDraft] = useState<Omit<AuditQuery, 'from' | 'to'>>({});
  const [draftRange, setDraftRange] = useState<Range>(null);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [detail, setDetail] = useState<AuditRecord | null>(null);
  const projects = useLoad(() => projectApi.list(), []);
  const services = useLoad(() => serviceApi.list(), []);
  const query: AuditQuery = { ...f, ...toRange(range) };
  const list = useLoad(() => auditApi.list({ ...query, page, size }), [JSON.stringify(query), page, size]);
  const exportable = canExportAudit(me);

  const apply = () => { setF(draft); setRange(draftRange); setPage(1); };
  const reset = () => { setDraft({}); setDraftRange(null); setF({}); setRange(null); setPage(1); };
  const doExport = (format: 'csv' | 'json') => { window.location.href = auditApi.exportUrl(format, query); };

  return (
    <Card size="small">
      <Space wrap style={{ marginBottom: 12 }}>
        <DatePicker.RangePicker value={draftRange as any} onChange={(v) => setDraftRange(v as Range)} placeholder={[t('audit.range'), '']} />
        <Input style={{ width: 130 }} placeholder={t('audit.actor')} value={draft.actor} onChange={(e) => setDraft({ ...draft, actor: e.target.value })} allowClear />
        <Select allowClear style={{ width: 140 }} placeholder={t('common.project')} value={draft.projectId} onChange={(v) => setDraft({ ...draft, projectId: v })} options={(projects.data ?? []).map((p) => ({ value: p.id, label: p.name }))} />
        <Select allowClear showSearch style={{ width: 160 }} placeholder={t('common.service')} value={draft.service} onChange={(v) => setDraft({ ...draft, service: v })} options={(services.data ?? []).map((s) => ({ value: s.service, label: s.service }))} />
        <Input style={{ width: 170 }} placeholder={t('audit.ruleKey')} value={draft.ruleKey} onChange={(e) => setDraft({ ...draft, ruleKey: e.target.value })} allowClear />
        <Tooltip title={t('audit.actionHint')}><Input style={{ width: 230 }} placeholder={t('audit.actionPlaceholder')} value={draft.action} onChange={(e) => setDraft({ ...draft, action: e.target.value })} allowClear /></Tooltip>
        <Select allowClear style={{ width: 130 }} placeholder={t('audit.source')} value={draft.source} onChange={(v) => setDraft({ ...draft, source: v })} options={SOURCES.map((s) => ({ value: s, label: tDyn('audit.source', s) }))} />
        <Button type="primary" onClick={apply}>{t('common.search')}</Button>
        <Button onClick={reset}>{t('common.reset')}</Button>
        {exportable ? (
          <Dropdown menu={{ items: [{ key: 'csv', label: t('audit.exportCsv'), onClick: () => doExport('csv') }, { key: 'json', label: t('audit.exportJson'), onClick: () => doExport('json') }] }}>
            <Button icon={<DownloadOutlined />}>{t('audit.export')}</Button>
          </Dropdown>
        ) : (
          <Tooltip title={t('audit.viewerNoExport')}><span><Button icon={<DownloadOutlined />} disabled>{t('audit.export')}</Button></span></Tooltip>
        )}
      </Space>
      <Table<AuditRecord>
        scroll={{ x: 1000 }}
        rowKey="id"
        size="small"
        loading={list.loading}
        dataSource={list.data?.items ?? []}
        pagination={{ current: page, pageSize: size, total: list.data?.total ?? 0, showSizeChanger: true, onChange: (p, s) => { setPage(p); setSize(s); } }}
        onRow={(r) => ({ onClick: () => { setDetail(r); auditApi.get(r.id).then(setDetail).catch(showError); }, style: { cursor: 'pointer' } })}
        columns={[
          { title: t('common.time'), dataIndex: 'at', width: 170, render: fmtTime },
          { title: t('audit.actor'), dataIndex: 'actorName', width: 110, render: (n: string, r) => <span>{n} <Tag>{tDyn('audit.actorType', r.actorType)}</Tag></span> },
          { title: t('audit.action'), dataIndex: 'action', width: 170, render: (a: string) => <Tooltip title={a}><span>{tDyn('audit.act', a)}</span></Tooltip> },
          { title: t('common.service'), dataIndex: 'service', width: 130 },
          { title: t('audit.ruleKey'), dataIndex: 'ruleKey', ellipsis: true, render: (k?: string) => <span className="mono">{k}</span> },
          { title: t('audit.source'), dataIndex: 'source', width: 100, render: (s: string) => tDyn('audit.source', s) },
          { title: t('audit.reason'), dataIndex: 'reason', ellipsis: true },
        ]}
      />
      <Drawer open={!!detail} width={640} title={detail ? `${tDyn('audit.act', detail.action)}(${detail.action})` : ''} onClose={() => setDetail(null)}>
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label={t('common.time')}>{fmtTime(detail.at)}</Descriptions.Item>
              <Descriptions.Item label={t('audit.actor')}>{detail.actorName}({tDyn('audit.actorType', detail.actorType)})</Descriptions.Item>
              <Descriptions.Item label={t('audit.source')}>{tDyn('audit.source', detail.source)}</Descriptions.Item>
              <Descriptions.Item label={t('audit.ip')}>{detail.ip || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('common.service')}>{detail.service || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('audit.ruleKey')}>{detail.ruleKey || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('audit.reason')}>{detail.reason || '-'}</Descriptions.Item>
            </Descriptions>
            {detail.diff || detail.beforeValue || detail.afterValue ? (
              <>
                <div><b>{t('audit.diff')}</b><DiffView diff={detail.diff} defaultMode="split" /></div>
                <div><b>{t('audit.before')}</b><CodeBlock text={prettyJson(detail.beforeValue) || '-'} maxHeight={220} /></div>
                <div><b>{t('audit.after')}</b><CodeBlock text={prettyJson(detail.afterValue) || '-'} maxHeight={220} /></div>
              </>
            ) : <Alert type="info" message={t('audit.noDiff')} />}
          </Space>
        )}
      </Drawer>
    </Card>
  );
}

function Mcp() {
  const [range, setRange] = useState<Range>(null);
  const [draft, setDraft] = useState<{ service?: string; caller?: string; tool?: string }>({});
  const [f, setF] = useState<typeof draft>({});
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [expanded, setExpanded] = useState<McpAuditRecord | null>(null);
  const services = useLoad(() => serviceApi.list(), []);
  const [r, setR] = useState<Range>(null);
  const { me } = useAuth();
  const exportable = canExportAudit(me);
  const doExport = (format: 'csv' | 'json') => { window.location.href = auditApi.exportMcpUrl(format, { ...f, ...toRange(r) }); };
  const list = useLoad(() => auditApi.mcp({ ...f, ...toRange(r), page, size }), [JSON.stringify(f), JSON.stringify(toRange(r)), page, size]);

  if (list.data && !list.data.available) return <Alert type="info" showIcon message={t('audit.mcp.unavailable')} data-testid="mcp-unavailable" />;
  return (
    <Card size="small">
      <Space wrap style={{ marginBottom: 12 }}>
        <DatePicker.RangePicker value={range as any} onChange={(v) => setRange(v as Range)} />
        <Select allowClear showSearch style={{ width: 160 }} placeholder={t('common.service')} value={draft.service} onChange={(v) => setDraft({ ...draft, service: v })} options={(services.data ?? []).map((s) => ({ value: s.service, label: s.service }))} />
        <Input style={{ width: 130 }} placeholder={t('audit.mcp.caller')} value={draft.caller} onChange={(e) => setDraft({ ...draft, caller: e.target.value })} allowClear />
        <Input style={{ width: 130 }} placeholder={t('audit.mcp.tool')} value={draft.tool} onChange={(e) => setDraft({ ...draft, tool: e.target.value })} allowClear />
        <Button type="primary" onClick={() => { setF(draft); setR(range); setPage(1); }}>{t('common.search')}</Button>
        {exportable ? (
          <Dropdown menu={{ items: [{ key: 'csv', label: t('audit.exportCsv'), onClick: () => doExport('csv') }, { key: 'json', label: t('audit.exportJson'), onClick: () => doExport('json') }] }}>
            <Button icon={<DownloadOutlined />}>{t('audit.export')}</Button>
          </Dropdown>
        ) : (
          <Tooltip title={t('audit.viewerNoExport')}><span><Button icon={<DownloadOutlined />} disabled>{t('audit.export')}</Button></span></Tooltip>
        )}
      </Space>
      <Table<McpAuditRecord>
        scroll={{ x: 950 }}
        rowKey="id"
        size="small"
        loading={list.loading}
        dataSource={list.data?.items ?? []}
        pagination={{ current: page, pageSize: size, total: list.data?.total ?? 0, showSizeChanger: true, onChange: (p, s) => { setPage(p); setSize(s); } }}
        onRow={(rec) => ({ onClick: () => setExpanded(rec), style: { cursor: 'pointer' } })}
        columns={[
          { title: t('common.time'), dataIndex: 'at', width: 170, render: fmtTime },
          { title: t('common.service'), dataIndex: 'service', width: 130 },
          { title: t('audit.mcp.caller'), dataIndex: 'caller', width: 110 },
          { title: t('audit.mcp.tool'), dataIndex: 'tool', width: 130 },
          { title: t('audit.mcp.target'), dataIndex: 'target', ellipsis: true },
          { title: t('audit.mcp.success'), dataIndex: 'success', width: 80, render: (s?: boolean) => <Tag color={s ? 'success' : 'error'}>{s ? t('audit.mcp.ok') : t('audit.mcp.fail')}</Tag> },
          { title: t('audit.mcp.duration'), dataIndex: 'durationMs', width: 90, render: (d?: number) => (d != null ? `${d} ms` : '-') },
        ]}
      />
      <Drawer open={!!expanded} width={520} title={expanded?.tool} onClose={() => setExpanded(null)}>
        {expanded && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <b>{t('audit.mcp.args')}</b><CodeBlock text={prettyJson(expanded.arguments) || '-'} maxHeight={220} />
            <b>{t('audit.mcp.result')}</b><CodeBlock text={prettyJson(expanded.result) || '-'} maxHeight={220} />
          </Space>
        )}
      </Drawer>
    </Card>
  );
}
