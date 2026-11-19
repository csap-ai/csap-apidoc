import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button, Card, Col, Empty, Form, Input, Modal, Row, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { message } from '@/utils/notify';
import { ruleApi, serviceApi } from '@/api/endpoints';
import type { RuleHistory, RuleListItem } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { canWrite } from '@/auth/permissions';
import { GuardedButton, PageTitle } from '@/components/common';
import DiffView, { CodeBlock } from '@/components/DiffView';
import { useLoad } from '@/utils/useLoad';
import { fmtTime, prettyJson } from '@/utils/format';
import { makeUnified } from '@/utils/diff';
import { showError } from '@/utils/errors';
import { t } from '@/i18n';

export default function Rules() {
  const { service } = useParams();
  const [sp, setSp] = useSearchParams();
  const navigate = useNavigate();
  const key = sp.get('key') || undefined;
  const services = useLoad(() => serviceApi.list(), []);
  const list = useLoad(() => ruleApi.list(service!), [service], { skip: !service });
  const project = services.data?.find((s) => s.service === service)?.projectId ?? null;

  return (
    <>
      <PageTitle
        title={t('rules.title')}
        extra={
          <Select
            showSearch
            style={{ width: 240 }}
            placeholder={t('rules.pickService')}
            value={service}
            onChange={(v) => navigate(`/rules/${encodeURIComponent(v)}`)}
            options={(services.data ?? []).map((s) => ({ value: s.service, label: s.service }))}
          />
        }
      />
      {!service ? (
        <Empty description={t('rules.pickService')} />
      ) : (
        <Row gutter={16}>
          <Col xs={24} lg={9}>
            <Card size="small" title={t('rules.keys')}>
              <Table<RuleListItem>
                size="small"
                rowKey="ruleKey"
                loading={list.loading}
                pagination={false}
                dataSource={list.data?.items ?? []}
                locale={{ emptyText: t('rules.noRules') }}
                rowClassName={(r) => (r.ruleKey === key ? 'ant-table-row-selected' : '')}
                onRow={(r) => ({ onClick: () => setSp({ key: r.ruleKey }), style: { cursor: 'pointer' } })}
                columns={[
                  { title: t('rules.keys'), dataIndex: 'ruleKey', ellipsis: true, render: (k: string) => <span className="mono">{k.split('.').slice(-2).join('.')}</span> },
                  { title: t('rules.version'), dataIndex: 'version', width: 70, render: (v: number) => <Tag>v{v}</Tag> },
                ]}
              />
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            {key ? <RuleDetail key={`${service}|${key}`} service={service} ruleKey={key} projectId={project} onChanged={list.reload} /> : <Empty description={t('rules.keys')} />}
          </Col>
        </Row>
      )}
    </>
  );
}

function RuleDetail({ service, ruleKey, projectId }: { service: string; ruleKey: string; projectId: number | null; onChanged: () => void }) {
  const { me } = useAuth();
  const navigate = useNavigate();
  const item = useLoad(() => ruleApi.item(service, ruleKey), [service, ruleKey]);
  const history = useLoad(() => ruleApi.history(service, ruleKey), [service, ruleKey]);
  const [rb, setRb] = useState<RuleHistory | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const write = canWrite(me, projectId);

  const doRollback = async () => {
    if (!rb) return;
    setBusy(true);
    try {
      const c = await ruleApi.rollback(service, { key: ruleKey, historyId: rb.id, reason });
      message.success(t('rules.rollbackCreated'));
      navigate(`/changes/${c.id}`);
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card size="small" title={<span className="mono">{ruleKey}</span>} extra={
      <Space>
        <Link to={`/docs/${encodeURIComponent(service)}`}>{t('docs.viewDoc')}</Link>
        <GuardedButton size="small" type="primary" allowed={write} onClick={() => navigate(`/changes/new?service=${encodeURIComponent(service)}&key=${encodeURIComponent(ruleKey)}`)}>{t('rules.edit')}</GuardedButton>
      </Space>
    }>
      <Tabs
        items={[
          {
            key: 'json', label: t('rules.json'),
            children: item.data?.rules == null ? <Typography.Text type="secondary">{t('rules.deleted')}</Typography.Text> : (
              <>
                <Tag>v{item.data.version}</Tag>
                <CodeBlock text={prettyJson(item.data.rules)} />
              </>
            ),
          },
          {
            key: 'history', label: t('rules.history'),
            children: (
              <Table<RuleHistory>
                scroll={{ x: 680 }}
                size="small"
                rowKey="id"
                loading={history.loading}
                pagination={false}
                dataSource={history.data ?? []}
                locale={{ emptyText: t('rules.historyEmpty') }}
                expandable={{ expandedRowRender: (h) => <DiffView diff={makeUnified(h.beforeRules, h.afterRules, 'before', 'after')} defaultMode="unified" /> }}
                columns={[
                  { title: t('rules.version'), dataIndex: 'version', width: 80, render: (v: number) => <Tag>v{v}</Tag> },
                  { title: t('rules.updatedBy'), dataIndex: 'updatedBy' },
                  { title: t('rules.updatedAt'), dataIndex: 'updatedAt', render: fmtTime },
                  { title: t('changes.title'), dataIndex: 'changeId', render: (c?: number | null) => (c ? <Link to={`/changes/${c}`}>{t('rules.byChange', { id: c })}</Link> : <Tag color="orange">{t('rules.outside')}</Tag>) },
                  { title: t('common.operation'), width: 130, render: (_, h) => <GuardedButton size="small" allowed={write} onClick={() => { setRb(h); setReason(''); }}>{t('rules.rollback')}</GuardedButton> },
                ]}
              />
            ),
          },
          { key: 'compare', label: t('rules.compare'), children: <Compare service={service} ruleKey={ruleKey} history={history.data ?? []} /> },
        ]}
      />
      <Modal open={!!rb} title={t('rules.rollbackTitle')} onCancel={() => setRb(null)} onOk={doRollback} confirmLoading={busy} okText={t('common.ok')} cancelText={t('common.cancel')} okButtonProps={{ disabled: !reason.trim() }}>
        <Typography.Paragraph type="secondary">{t('rules.rollbackHint')}</Typography.Paragraph>
        {rb && <Typography.Paragraph>v{rb.version} · {rb.updatedBy} · {fmtTime(rb.updatedAt)}</Typography.Paragraph>}
        <Form layout="vertical"><Form.Item label={t('rules.rollbackReason')} required><Input.TextArea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} /></Form.Item></Form>
      </Modal>
    </Card>
  );
}

function Compare({ service, ruleKey, history }: { service: string; ruleKey: string; history: RuleHistory[] }) {
  const [from, setFrom] = useState<number | undefined>();
  const [to, setTo] = useState<number | undefined>(); // undefined = 当前
  const [diff, setDiff] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const opts = history.map((h) => ({ value: h.id, label: `v${h.version} · ${h.updatedBy} · ${fmtTime(h.updatedAt)}` }));
  const run = async () => {
    if (!from) return;
    setLoading(true);
    try {
      setDiff((await ruleApi.diff(service, ruleKey, from, to)).diff);
    } catch (e) {
      showError(e);
    } finally {
      setLoading(false);
    }
  };
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Space wrap>
        <Select style={{ width: 280 }} placeholder={t('rules.from')} options={opts} value={from} onChange={setFrom} />
        →
        <Select style={{ width: 280 }} allowClear placeholder={t('rules.toCurrent')} options={opts} value={to} onChange={setTo} />
        <Button type="primary" disabled={!from} loading={loading} onClick={run}>{t('rules.compare')}</Button>
      </Space>
      {diff !== null && <DiffView diff={diff} defaultMode="split" />}
    </Space>
  );
}
