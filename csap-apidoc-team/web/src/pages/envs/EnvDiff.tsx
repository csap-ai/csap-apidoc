// 环境对比页(1.1 §8):选来源 / 目标环境与项目,按服务分组列出差异,点开看差异,勾选后推广。
import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Drawer, Empty, Select, Space, Table, Tag, Typography } from 'antd';
import { SwapRightOutlined } from '@ant-design/icons';
import { envApi, projectApi, serviceApi } from '@/api/endpoints';
import type { EnvDiff as EnvDiffData, EnvDiffDetail, EnvDiffItem, EnvDiffStatus } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { canWrite } from '@/auth/permissions';
import { GuardedButton, PageTitle } from '@/components/common';
import DiffView, { CodeBlock } from '@/components/DiffView';
import { EnvTag } from '@/components/EnvSwitcher';
import { useEnv } from '@/env/EnvContext';
import { useLoad } from '@/utils/useLoad';
import { prettyJson } from '@/utils/format';
import { showError } from '@/utils/errors';
import { t, tDyn } from '@/i18n';
import PromoteModal, { type PromoteRow } from './PromoteModal';

const statusColor: Record<EnvDiffStatus, string> = { DIFFERENT: 'orange', ONLY_FROM: 'green', ONLY_TO: 'red' };
const sel = (service: string, ruleKey: string) => `${service}\u0000${ruleKey}`;

export default function EnvDiff() {
  const { me } = useAuth();
  const { envs, env, next, find } = useEnv();
  const idx = envs.findIndex((e) => e.key === env);
  // 默认方向:当前环境 → 下一个环境;当前是最后一个时,上一个 → 当前
  const [from, setFrom] = useState<string | undefined>(next ? env : envs[Math.max(0, idx - 1)]?.key);
  const [to, setTo] = useState<string | undefined>(next ? next.key : env);
  const [projectId, setProjectId] = useState<number | undefined>();
  const [service, setService] = useState<string | undefined>();
  const [data, setData] = useState<EnvDiffData | null>(null);
  const [loading, setLoading] = useState(false);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [detail, setDetail] = useState<{ service: string; ruleKey: string; item?: EnvDiffDetail; loading: boolean } | null>(null);
  const [promoting, setPromoting] = useState(false);

  const projects = useLoad(() => projectApi.list(), []);
  const services = useLoad(() => serviceApi.list({ projectId, env: from }), [projectId, from], { skip: !projectId });
  const fromEnv = find(from), toEnv = find(to);
  const project = (projects.data ?? []).find((p) => p.id === projectId);
  const sameEnv = !!from && from === to;

  // 改了选择,旧结果作废
  useEffect(() => { setData(null); setPicked(new Set()); }, [from, to, projectId, service]);

  const run = async () => {
    if (!from || !to || !projectId || sameEnv) return;
    setLoading(true);
    try {
      const d = await envApi.diff({ from, to, projectId, service });
      setData(d);
      // 默认勾上"内容不同"和"仅来源有";"仅目标有"(推广后会在目标删除)需要手工勾
      setPicked(new Set(d.services.flatMap((s) => s.items.filter((i) => i.status !== 'ONLY_TO').map((i) => sel(s.service, i.ruleKey)))));
    } catch (e) {
      showError(e);
    } finally {
      setLoading(false);
    }
  };

  const openDetail = async (svc: string, ruleKey: string) => {
    if (!from || !to) return;
    setDetail({ service: svc, ruleKey, loading: true });
    try {
      const item = await envApi.diffItem({ from, to, service: svc, key: ruleKey });
      setDetail({ service: svc, ruleKey, item, loading: false });
    } catch (e) {
      setDetail(null);
      showError(e);
    }
  };

  const rows: PromoteRow[] = useMemo(
    () => (data?.services ?? []).flatMap((s) => s.items.filter((i) => picked.has(sel(s.service, i.ruleKey))).map((i) => ({ service: s.service, ruleKey: i.ruleKey, status: i.status }))),
    [data, picked],
  );

  const writeOk = !!projectId && canWrite(me, projectId);
  const blockReason = !writeOk ? t('envdiff.noPermission')
    : toEnv?.readOnly ? t('env.readOnlyReason')
      : toEnv?.status === 'DOWN' || fromEnv?.status === 'DOWN' ? t('env.downReason') : undefined;
  const envOptions = envs.map((e) => ({ value: e.key, label: <EnvTag env={e} /> }));

  return (
    <>
      <PageTitle title={t('envdiff.title')} />
      <Card size="small">
        <Typography.Paragraph type="secondary">{t('envdiff.hint')}</Typography.Paragraph>
        <Space wrap align="center">
          <Select style={{ width: 170 }} aria-label={t('envdiff.from')} placeholder={t('envdiff.from')} value={from} onChange={setFrom} options={envOptions} />
          <SwapRightOutlined />
          <Select style={{ width: 170 }} aria-label={t('envdiff.to')} placeholder={t('envdiff.to')} value={to} onChange={setTo} options={envOptions} />
          <Select
            style={{ width: 180 }}
            aria-label={t('envdiff.project')}
            placeholder={t('envdiff.pickProject')}
            value={projectId}
            onChange={(v) => { setProjectId(v); setService(undefined); }}
            options={(projects.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
          <Select
            allowClear
            showSearch
            style={{ width: 200 }}
            aria-label={t('envdiff.service')}
            placeholder={t('envdiff.service')}
            disabled={!projectId}
            value={service}
            onChange={setService}
            options={(services.data ?? []).map((s) => ({ value: s.service, label: s.service }))}
          />
          <Button type="primary" loading={loading} disabled={!projectId || !from || !to || sameEnv} onClick={run}>{t('envdiff.load')}</Button>
        </Space>
        {sameEnv && <Alert type="warning" showIcon style={{ marginTop: 12 }} message={t('envdiff.sameEnv')} />}
      </Card>

      <div style={{ marginTop: 16 }}>
        {!data ? (
          <Empty description={projectId ? t('envdiff.loadMore') : t('envdiff.needProject')} />
        ) : data.services.length === 0 ? (
          <Alert type="success" showIcon message={t('envdiff.none')} data-testid="envdiff-none" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space wrap>
              <Typography.Text>{t('envdiff.selected', { n: rows.length })}</Typography.Text>
              <GuardedButton type="primary" allowed={!blockReason && rows.length > 0} reason={blockReason ?? t('envdiff.nothingSelected')} onClick={() => setPromoting(true)}>
                {t('envdiff.promote', { n: rows.length })}
              </GuardedButton>
            </Space>
            {data.services.map((s) => (
              <Card key={s.service} size="small" title={<span className="mono">{s.service}</span>} data-testid={`envdiff-${s.service}`}>
                <Table<EnvDiffItem>
                  size="small"
                  rowKey="ruleKey"
                  pagination={false}
                  dataSource={s.items}
                  rowSelection={{
                    selectedRowKeys: s.items.filter((i) => picked.has(sel(s.service, i.ruleKey))).map((i) => i.ruleKey),
                    onChange: (keys) => setPicked((prev) => {
                      const n = new Set(prev);
                      s.items.forEach((i) => n.delete(sel(s.service, i.ruleKey)));
                      keys.forEach((k) => n.add(sel(s.service, String(k))));
                      return n;
                    }),
                  }}
                  columns={[
                    { title: t('envdiff.ruleKey'), dataIndex: 'ruleKey', ellipsis: true, render: (k: string) => <span className="mono">{k}</span> },
                    {
                      title: t('envdiff.status'), dataIndex: 'status', width: 190,
                      render: (st: EnvDiffStatus) => (
                        <span><Tag color={statusColor[st]}>{tDyn('envdiff.status', st)}</Tag>{st === 'ONLY_TO' && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t('envdiff.onlyToHint')}</Typography.Text>}</span>
                      ),
                    },
                    { title: t('envdiff.fromVersion'), dataIndex: 'fromVersion', width: 100, render: (v: number | null) => (v == null ? '-' : <Tag>v{v}</Tag>) },
                    { title: t('envdiff.toVersion'), dataIndex: 'toVersion', width: 100, render: (v: number | null) => (v == null ? '-' : <Tag>v{v}</Tag>) },
                    { title: t('common.operation'), width: 100, render: (_, r) => <a onClick={() => openDetail(s.service, r.ruleKey)}>{t('envdiff.viewDiff')}</a> },
                  ]}
                />
              </Card>
            ))}
          </Space>
        )}
      </div>

      <Drawer
        open={!!detail}
        width={720}
        title={detail ? t('envdiff.diffTitle', { service: detail.service, to: toEnv?.name ?? to ?? '' }) : ''}
        onClose={() => setDetail(null)}
        destroyOnClose
      >
        {detail?.item && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }} data-testid="envdiff-detail">
            <Typography.Text type="secondary">{t('envdiff.diffDesc', { from: fromEnv?.name ?? '', to: toEnv?.name ?? '' })} · <span className="mono">{detail.ruleKey}</span></Typography.Text>
            <DiffView diff={detail.item.diff} defaultMode="split" />
            <div><b>{fromEnv?.name} v{detail.item.fromVersion ?? '-'}</b><CodeBlock text={prettyJson(detail.item.fromRules) || t('rules.deleted')} maxHeight={220} /></div>
            <div><b>{toEnv?.name} v{detail.item.toVersion ?? '-'}</b><CodeBlock text={prettyJson(detail.item.toRules) || t('rules.deleted')} maxHeight={220} /></div>
          </Space>
        )}
      </Drawer>

      {fromEnv && toEnv && projectId && project && (
        <PromoteModal open={promoting} from={fromEnv} to={toEnv} projectId={projectId} projectName={project.name} rows={rows} onClose={() => setPromoting(false)} />
      )}
    </>
  );
}
