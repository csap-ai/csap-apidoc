import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Empty, Input, Select, Space, Table, Tag, Tooltip } from 'antd';
import { docApi, projectApi, serviceApi } from '@/api/endpoints';
import type { SearchHit, ServiceInfo } from '@/api/types';
import { PageTitle, ServiceStatusTag } from '@/components/common';
import { useLoad } from '@/utils/useLoad';
import { fmtTime } from '@/utils/format';
import { showError } from '@/utils/errors';
import { t, tDyn } from '@/i18n';

export default function DocsHome() {
  const navigate = useNavigate();
  const [projectId, setProjectId] = useState<number | undefined>();
  const [q, setQ] = useState('');
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [searching, setSearching] = useState(false);
  const projects = useLoad(() => projectApi.list(), []);
  const services = useLoad(() => serviceApi.list({ projectId }), [projectId]);

  const search = async (v: string) => {
    setQ(v);
    if (!v.trim()) return setHits(null);
    setSearching(true);
    try {
      setHits(await docApi.search(v.trim(), projectId));
    } catch (e) {
      showError(e);
    } finally {
      setSearching(false);
    }
  };

  const open = (service: string, path?: string) =>
    navigate(`/docs/${encodeURIComponent(service)}${path ? `?path=${encodeURIComponent(path)}` : ''}`);

  return (
    <>
      <PageTitle
        title={t('docs.title')}
        extra={
          <Select
            allowClear
            style={{ width: 180 }}
            placeholder={t('docs.allProjects')}
            value={projectId}
            onChange={(v) => { setProjectId(v); setHits(null); setQ(''); }}
            options={(projects.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
        }
      />
      <Input.Search
        size="large"
        allowClear
        enterButton={t('common.search')}
        placeholder={t('docs.searchPlaceholder')}
        loading={searching}
        onSearch={search}
        onChange={(e) => !e.target.value && setHits(null)}
        style={{ marginBottom: 16 }}
      />
      {hits && (
        <Card size="small" title={`${t('common.search')}:${q}(${hits.length})`} style={{ marginBottom: 16 }} data-testid="search-results">
          {hits.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('docs.searchEmpty')} />
          ) : (
            <Table
              scroll={{ x: 860 }}
              size="small"
              rowKey={(r) => `${r.service}${r.method}${r.path}`}
              pagination={false}
              dataSource={hits}
              onRow={(r) => ({ onClick: () => open(r.service, r.path), style: { cursor: 'pointer' } })}
              columns={[
                { title: t('docs.service'), dataIndex: 'service', width: 160 },
                { title: 'Method', dataIndex: 'method', width: 90, render: (m: string) => <Tag color="blue">{m}</Tag> },
                { title: 'Path', dataIndex: 'path', render: (p: string) => <span className="mono">{p}</span> },
                { title: t('changes.title.col'), dataIndex: 'title' },
                { title: '匹配位置', dataIndex: 'matchedIn', width: 90, render: (m: string) => tDyn('docs.matchedIn', m) },
                { title: '片段', dataIndex: 'snippet', ellipsis: true },
              ]}
            />
          )}
        </Card>
      )}
      <Card size="small">
        <Table<ServiceInfo>
          scroll={{ x: 900 }}
          rowKey="service"
          loading={services.loading}
          dataSource={services.data ?? []}
          pagination={false}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('docs.noService')} /> }}
          onRow={(r) => ({ onClick: () => open(r.service), style: { cursor: 'pointer' } })}
          columns={[
            { title: t('docs.service'), dataIndex: 'service', width: 190, ellipsis: { showTitle: false }, render: (s: string) => <Tooltip title={s}><b>{s}</b></Tooltip> },
            { title: t('docs.status'), dataIndex: 'status', width: 130, render: (s) => <ServiceStatusTag status={s} /> },
            { title: t('docs.language'), dataIndex: 'language', width: 90, render: (l: string) => <Tag>{l}</Tag> },
            { title: t('docs.version'), dataIndex: 'frameworkVersion', width: 110 },
            { title: t('docs.instances'), dataIndex: 'instances', width: 90 },
            { title: t('svc.owner'), dataIndex: 'projectName', width: 130, render: (n: string | null) => n ?? <Tag>{t('common.noProject')}</Tag> },
            { title: t('docs.lastSeen'), dataIndex: 'lastSeenAt', width: 180, render: fmtTime },
            { title: t('common.operation'), width: 120, render: (_, r) => <Space><a onClick={(e) => { e.stopPropagation(); open(r.service); }}>{t('docs.viewDoc')}</a></Space> },
          ]}
        />
      </Card>
    </>
  );
}
