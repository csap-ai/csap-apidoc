import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Alert, Button, Card, Empty, Input, Layout, Space, Table, Tag, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { docApi } from '@/api/endpoints';
import type { DocMethod, DocParam } from '@/api/types';
import { PageTitle } from '@/components/common';
import { useLoad } from '@/utils/useLoad';
import { fmtTime } from '@/utils/format';
import { t } from '@/i18n';

const methodColor: Record<string, string> = { GET: 'green', POST: 'blue', PUT: 'orange', PATCH: 'purple', DELETE: 'red' };
const fullPath = (apiPath?: string[], paths?: string[]) => `${apiPath?.[0] ?? ''}${paths?.[0] ?? ''}`;

interface Row extends DocParam { rowKey: string; children?: any }
function toRows(params: DocParam[] | undefined, prefix = ''): Row[] {
  return (params ?? []).filter((p) => !p.hidden).map((p, i) => {
    const kids = p.parameters?.length ? p.parameters : p.children?.parameters;
    const rowKey = `${prefix}${p.name}-${i}`;
    return { ...p, rowKey, children: kids?.length ? toRows(kids, rowKey + '/') : undefined } as Row;
  });
}

function rulesOf(p: DocParam): string[] {
  const out: string[] = [];
  if (p.length) out.push(t('docs.rule.length', { n: p.length }));
  if (p.decimals) out.push(t('docs.rule.decimals', { n: p.decimals }));
  if (p.defaultValue) out.push(t('docs.rule.default', { v: p.defaultValue }));
  const extra = (p as any).validations ?? (p as any).rules;
  if (Array.isArray(extra)) extra.forEach((x) => out.push(typeof x === 'string' ? x : x?.message || JSON.stringify(x)));
  return out;
}

function ParamTable({ params }: { params?: DocParam[] }) {
  const rows = useMemo(() => toRows(params), [params]);
  if (!rows.length) return <Typography.Text type="secondary">{t('docs.noParams')}</Typography.Text>;
  return (
    <Table<Row>
      size="small"
      rowKey="rowKey"
      pagination={false}
      dataSource={rows}
      defaultExpandAllRows
      columns={[
        { title: t('docs.col.name'), dataIndex: 'name', render: (n: string) => <span className="mono">{n}</span> },
        { title: t('docs.col.type'), dataIndex: 'dataType', width: 170, render: (d?: string) => <Tag>{d}</Tag> },
        { title: t('docs.col.required'), dataIndex: 'required', width: 70, render: (r?: boolean) => (r ? <Tag color="red">{t('common.yes')}</Tag> : '') },
        { title: t('docs.col.desc'), dataIndex: 'description', render: (d, r) => d || r.value },
        { title: t('docs.col.rules'), width: 200, render: (_, r) => rulesOf(r).map((x) => <Tag key={x} color="gold">{x}</Tag>) },
        { title: t('docs.col.example'), dataIndex: 'example', width: 110, render: (e?: string) => <span className="mono">{e}</span> },
      ]}
    />
  );
}

export default function DocView() {
  const { service = '' } = useParams();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const [filter, setFilter] = useState('');
  const [selKey, setSelKey] = useState<string | undefined>();
  const { data, loading, error } = useLoad(() => docApi.get(service), [service], { silent: true });

  const controllers = useMemo(() => data?.doc.data.apiList ?? [], [data]);
  const all = useMemo(() => controllers.flatMap((c) => c.methodList.map((m) => ({ c, m }))), [controllers]);
  useEffect(() => {
    if (!all.length) return;
    const p = sp.get('path');
    const hit = p ? all.find((x) => fullPath(x.m.apiPath, x.m.paths) === p) : undefined;
    setSelKey((hit ?? all[0]).m.key);
  }, [all, sp]);

  const selected = all.find((x) => x.m.key === selKey);
  const kw = filter.trim().toLowerCase();

  return (
    <>
      <PageTitle
        title={<Space><Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/docs')} aria-label={t('common.back')} />{service}{data?.doc.data.apiInfo?.version && <Tag>v{data.doc.data.apiInfo.version}</Tag>}</Space>}
        extra={<Link to={`/rules/${encodeURIComponent(service)}`}>{t('docs.toRules')}</Link>}
      />
      {data?.stale && <Alert type="warning" showIcon message={t('docs.stale')} style={{ marginBottom: 12 }} />}
      {data?.fetchedAt && <Typography.Text type="secondary">{t('docs.fetchedAt')} {fmtTime(data.fetchedAt)}</Typography.Text>}
      {error && !loading && <Alert type="info" showIcon message={t('docs.noDoc')} style={{ marginTop: 12 }} />}
      {!error && (
        <Layout style={{ background: 'transparent', marginTop: 12, gap: 16, flexDirection: 'row' }}>
          <Card size="small" loading={loading} style={{ width: 300, flex: 'none', maxHeight: 'calc(100vh - 220px)', overflow: 'auto' }}>
            <Input allowClear placeholder={t('docs.filterApi')} value={filter} onChange={(e) => setFilter(e.target.value)} style={{ marginBottom: 8 }} />
            {controllers.map((c) => {
              const ms = c.methodList.filter((m) => !kw || `${m.value}${m.name}${fullPath(m.apiPath, m.paths)}`.toLowerCase().includes(kw));
              if (!ms.length) return null;
              return (
                <div key={c.name} style={{ marginBottom: 8 }}>
                  <Typography.Text strong>{c.value || c.simpleName}</Typography.Text>
                  {ms.map((m) => (
                    <div
                      key={m.key}
                      onClick={() => setSelKey(m.key)}
                      style={{ padding: '4px 8px', borderRadius: 6, cursor: 'pointer', background: m.key === selKey ? 'rgba(22,119,255,0.15)' : undefined }}
                    >
                      <Tag color={methodColor[m.methods?.[0] ?? 'GET']} style={{ marginRight: 6 }}>{m.methods?.[0]}</Tag>
                      <span>{m.value || m.name}</span>
                    </div>
                  ))}
                </div>
              );
            })}
          </Card>
          <div style={{ flex: 1, minWidth: 0 }}>
            {selected ? <MethodDetail service={service} m={selected.m} /> : !loading && <Empty description={t('common.empty')} />}
          </div>
        </Layout>
      )}
    </>
  );
}

function MethodDetail({ service, m }: { service: string; m: DocMethod }) {
  const reqParams = (m.request ?? []).flatMap((r) => r.parameters ?? []);
  const resParams = (m.response ?? []).flatMap((r) => r.parameters ?? []);
  return (
    <Card size="small" data-testid="method-detail">
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <div>
          <Typography.Title level={5} style={{ marginTop: 0 }}>{m.value || m.name}</Typography.Title>
          <Space wrap>
            {m.methods?.map((x) => <Tag key={x} color={methodColor[x]}>{x}</Tag>)}
            <span className="mono">{fullPath(m.apiPath, m.paths)}</span>
            <Link to={`/rules/${encodeURIComponent(service)}?key=${encodeURIComponent(m.key)}`}>{t('docs.toRules')}</Link>
          </Space>
          {m.description && <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>{m.description}</Typography.Paragraph>}
        </div>
        {!!m.methodHeaders?.length && (
          <div>
            <Typography.Text strong>{t('docs.headers')}</Typography.Text>
            <div>{m.methodHeaders.map((h) => <Tag key={h.key}>{h.key}: {h.value}</Tag>)}</div>
          </div>
        )}
        <div><Typography.Text strong>{t('docs.request')}</Typography.Text><div style={{ marginTop: 8 }}><ParamTable params={reqParams} /></div></div>
        <div><Typography.Text strong>{t('docs.response')}</Typography.Text><div style={{ marginTop: 8 }}><ParamTable params={resParams} /></div></div>
      </Space>
    </Card>
  );
}
