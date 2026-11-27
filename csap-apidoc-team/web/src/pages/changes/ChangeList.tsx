import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Card, Select, Space, Switch, Table, Tooltip } from 'antd';
import { changeApi, projectApi } from '@/api/endpoints';
import type { Change, ChangeStatus } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { canWrite } from '@/auth/permissions';
import { ChangeStatusTag, GuardedButton, PageTitle } from '@/components/common';
import { EnvTagByKey } from '@/components/EnvSwitcher';
import { useEnv } from '@/env/EnvContext';
import { useLoad } from '@/utils/useLoad';
import { fmtTime } from '@/utils/format';
import { t, tDyn } from '@/i18n';

const STATUSES: ChangeStatus[] = ['DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED', 'WITHDRAWN', 'CONFLICT'];

export default function ChangeList() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const [projectId, setProjectId] = useState<number | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [mine, setMine] = useState(false);
  const { env, envs, multi } = useEnv();
  // 默认只看当前环境;清掉筛选 = 看全部环境
  const [envFilter, setEnvFilter] = useState<string | undefined>(env);
  useEffect(() => setEnvFilter(env), [env]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const projects = useLoad(() => projectApi.list(), []);
  const list = useLoad(() => changeApi.list({ projectId, status, mine: mine || undefined, env: envFilter, page, size }), [projectId, status, mine, envFilter, page, size]);

  return (
    <>
      <PageTitle
        title={t('changes.title')}
        extra={<GuardedButton type="primary" allowed={canWrite(me)} reason={t('changes.form.noWrite')} onClick={() => navigate('/changes/new')}>{t('changes.new')}</GuardedButton>}
      />
      <Card size="small">
        <Space wrap style={{ marginBottom: 12 }}>
          <Select allowClear style={{ width: 160 }} placeholder={t('common.project')} value={projectId} onChange={(v) => { setProjectId(v); setPage(1); }} options={(projects.data ?? []).map((p) => ({ value: p.id, label: p.name }))} />
          <Select allowClear style={{ width: 140 }} placeholder={t('changes.status')} value={status} onChange={(v) => { setStatus(v); setPage(1); }} options={STATUSES.map((s) => ({ value: s, label: tDyn('changes.status', s) }))} />
          {multi && <Select allowClear style={{ width: 150 }} placeholder={t('env.allEnvs')} aria-label={t('env.label')} value={envFilter} onChange={(v) => { setEnvFilter(v); setPage(1); }} options={envs.map((e) => ({ value: e.key, label: e.name }))} />}
          <span><Switch checked={mine} onChange={(v) => { setMine(v); setPage(1); }} /> {t('changes.mine')}</span>
        </Space>
        <Table<Change>
          scroll={{ x: 860 }}
          rowKey="id"
          loading={list.loading}
          dataSource={list.data?.items ?? []}
          pagination={{ current: page, pageSize: size, total: list.data?.total ?? 0, showSizeChanger: true, onChange: (p, s) => { setPage(p); setSize(s); } }}
          columns={[
            { title: '#', dataIndex: 'id', width: 70 },
            { title: t('changes.title.col'), dataIndex: 'title', width: 260, ellipsis: { showTitle: false }, render: (v: string, r) => <Tooltip title={v}><Link to={`/changes/${r.id}`}>{v}</Link></Tooltip> },
            { title: t('common.project'), dataIndex: 'projectName', width: 120 },
            { title: t('changes.status'), dataIndex: 'status', width: 100, render: (s: ChangeStatus) => <ChangeStatusTag status={s} /> },
            ...(multi ? [{ title: t('env.label'), dataIndex: 'env', width: 170, render: (_: unknown, r: Change) => (
              <span>{r.sourceEnv && <><EnvTagByKey envKey={r.sourceEnv} /> → </>}<EnvTagByKey envKey={r.env} name={r.envName} /></span>
            ) }] : []),
            { title: '类型', dataIndex: 'kind', width: 70, render: (k: string) => tDyn('changes.kind', k) },
            { title: t('changes.author'), dataIndex: ['author', 'displayName'], width: 110 },
            { title: t('changes.createdAt'), dataIndex: 'createdAt', width: 180, render: fmtTime },
          ]}
        />
      </Card>
    </>
  );
}
