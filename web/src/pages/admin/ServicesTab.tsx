import { useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag } from 'antd';
import { projectApi, serviceApi } from '@/api/endpoints';
import type { ServiceInfo } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { isAdmin } from '@/auth/permissions';
import { ServiceStatusTag } from '@/components/common';
import { useLoad } from '@/utils/useLoad';
import { showError } from '@/utils/errors';
import { fmtTime } from '@/utils/format';
import { t, tDyn } from '@/i18n';

export default function ServicesTab() {
  const { me } = useAuth();
  const [unassigned, setUnassigned] = useState(false);
  const [adding, setAdding] = useState(false);
  const [form] = Form.useForm();
  const projects = useLoad(() => projectApi.list(), []);
  const services = useLoad(() => serviceApi.list({ unassigned: unassigned || undefined }), [unassigned]);
  const opts = (projects.data ?? []).map((p) => ({ value: p.id, label: p.name }));

  const assign = async (s: ServiceInfo, projectId: number | null) => {
    try { await serviceApi.update(s.service, { projectId }); void services.reload(); } catch (e) { showError(e); }
  };
  const add = async (v: { service: string; baseUrl: string; docPath?: string; projectId?: number }) => {
    try { await serviceApi.create(v); setAdding(false); form.resetFields(); void services.reload(); } catch (e) { showError(e); }
  };

  return (
    <Card size="small">
      <Space style={{ marginBottom: 12 }}>
        <span><Switch checked={unassigned} onChange={setUnassigned} /> {t('svc.unassigned')}</span>
        <Button type="primary" onClick={() => setAdding(true)}>{t('svc.add')}</Button>
      </Space>
      <Table<ServiceInfo>
        scroll={{ x: 1000 }}
        rowKey="service"
        size="small"
        loading={services.loading}
        pagination={false}
        dataSource={services.data ?? []}
        locale={{ emptyText: t('svc.empty') }}
        columns={[
          { title: t('common.service'), dataIndex: 'service', render: (s: string) => <b>{s}</b> },
          { title: t('docs.status'), dataIndex: 'status', width: 120, render: (s) => <ServiceStatusTag status={s} /> },
          { title: t('docs.language'), dataIndex: 'language', width: 80, render: (l: string) => <Tag>{l}</Tag> },
          { title: t('svc.baseUrl'), dataIndex: 'baseUrl', ellipsis: true },
          { title: '来源', dataIndex: 'source', width: 100, render: (s: string) => tDyn('svc.source', s) },
          { title: t('docs.lastSeen'), dataIndex: 'lastSeenAt', width: 170, render: fmtTime },
          { title: t('svc.owner'), dataIndex: 'projectId', width: 180, render: (pid: number | null, s) => (
            <Select size="small" style={{ width: 150 }} allowClear={isAdmin(me)} placeholder={t('common.noProject')} value={pid ?? undefined} options={opts}
              onChange={(v) => assign(s, v ?? null)} />
          ) },
          { title: t('common.operation'), width: 80, render: (_, s) => (
            <Popconfirm title={t('common.confirmDelete')} okText={t('common.ok')} cancelText={t('common.cancel')} onConfirm={async () => { try { await serviceApi.remove(s.service); void services.reload(); } catch (e) { showError(e); } }}><a>{t('common.delete')}</a></Popconfirm>
          ) },
        ]}
      />
      <Modal open={adding} title={t('svc.add')} onCancel={() => setAdding(false)} onOk={() => form.submit()} okText={t('common.create')} cancelText={t('common.cancel')}>
        <Form form={form} layout="vertical" onFinish={add} initialValues={{ docPath: '/csap/apidoc' }}>
          <Form.Item name="service" label={t('common.service')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="baseUrl" label={t('svc.baseUrl')} rules={[{ required: true, message: t('common.required') }]}><Input placeholder="http://10.0.0.5:8080" /></Form.Item>
          <Form.Item name="docPath" label={t('svc.docPath')}><Input /></Form.Item>
          <Form.Item name="projectId" label={t('svc.owner')}><Select allowClear options={opts} /></Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
