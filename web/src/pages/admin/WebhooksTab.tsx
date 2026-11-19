import { useState } from 'react';
import { Button, Card, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag } from 'antd';
import { message } from '@/utils/notify';
import { projectApi, webhookApi } from '@/api/endpoints';
import type { Webhook, WebhookDelivery, WebhookInput, WebhookKind } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { isAdmin } from '@/auth/permissions';
import { useLoad } from '@/utils/useLoad';
import { showError } from '@/utils/errors';
import { fmtTime } from '@/utils/format';
import { t, tDyn } from '@/i18n';

const KINDS: WebhookKind[] = ['GENERIC', 'WECOM', 'DINGTALK', 'FEISHU'];
const EVENTS = ['change.submitted', 'change.approved', 'change.rejected', 'change.published', 'change.conflict', 'rule.changed_outside', 'service.offline'];

export default function WebhooksTab() {
  const { me } = useAuth();
  const hooks = useLoad(() => webhookApi.list(), []);
  const projects = useLoad(() => projectApi.list(), []);
  const [editing, setEditing] = useState<Webhook | 'new' | null>(null);
  const [logFor, setLogFor] = useState<Webhook | null>(null);
  const [form] = Form.useForm<WebhookInput>();
  const deliveries = useLoad(() => webhookApi.deliveries(logFor!.id), [logFor?.id], { skip: !logFor });

  const open = (w: Webhook | 'new') => {
    setEditing(w);
    form.resetFields();
    form.setFieldsValue(w === 'new' ? { kind: 'GENERIC', enabled: true, events: EVENTS.slice(0, 4) } : { ...w, secret: undefined });
  };
  const save = async (v: WebhookInput) => {
    try {
      if (editing === 'new') await webhookApi.create({ ...v, projectId: v.projectId ?? null });
      else await webhookApi.update((editing as Webhook).id, { ...v, projectId: v.projectId ?? null });
      setEditing(null); void hooks.reload();
    } catch (e) { showError(e); }
  };
  const test = async (w: Webhook) => {
    try { const d = await webhookApi.test(w.id); d.status === 'SUCCESS' ? message.success(t('webhook.testOk')) : message.error(d.error || t('webhook.delivery.FAILED')); } catch (e) { showError(e); }
  };

  return (
    <Card size="small">
      <Button type="primary" style={{ marginBottom: 12 }} onClick={() => open('new')}>{t('webhook.new')}</Button>
      <Table<Webhook>
        scroll={{ x: 1000 }}
        rowKey="id"
        size="small"
        loading={hooks.loading}
        pagination={false}
        dataSource={hooks.data ?? []}
        columns={[
          { title: t('common.name'), dataIndex: 'name', width: 160 },
          { title: t('webhook.kind'), dataIndex: 'kind', width: 110, render: (k: string) => <Tag>{tDyn('webhook.kind', k)}</Tag> },
          { title: t('common.project'), dataIndex: 'projectId', width: 110, render: (id: number | null) => (id ? projects.data?.find((p) => p.id === id)?.name ?? id : t('webhook.global')) },
          { title: t('webhook.events'), dataIndex: 'events', render: (e: string[]) => e.map((x) => <Tag key={x}>{tDyn('event', x)}</Tag>) },
          { title: t('webhook.secret'), dataIndex: 'hasSecret', width: 90, render: (s: boolean) => (s ? t('webhook.hasSecret') : '-') },
          { title: t('common.enabled'), dataIndex: 'enabled', width: 80, render: (en: boolean, w) => <Switch size="small" checked={en} onChange={async (v) => { try { await webhookApi.update(w.id, { enabled: v }); void hooks.reload(); } catch (e) { showError(e); } }} /> },
          { title: t('common.operation'), width: 250, render: (_, w) => (
            <Space size="small">
              <a onClick={() => open(w)}>{t('common.edit')}</a>
              <a onClick={() => test(w)}>{t('webhook.test')}</a>
              <a onClick={() => setLogFor(w)}>{t('webhook.deliveries')}</a>
              <Popconfirm title={t('common.confirmDelete')} okText={t('common.ok')} cancelText={t('common.cancel')} onConfirm={async () => { try { await webhookApi.remove(w.id); void hooks.reload(); } catch (e) { showError(e); } }}><a>{t('common.delete')}</a></Popconfirm>
            </Space>
          ) },
        ]}
      />
      <Modal open={!!editing} width={560} title={editing === 'new' ? t('webhook.new') : t('common.edit')} onCancel={() => setEditing(null)} onOk={() => form.submit()} okText={t('common.save')} cancelText={t('common.cancel')}>
        <Form form={form} layout="vertical" onFinish={save}>
          <Form.Item name="name" label={t('common.name')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="kind" label={t('webhook.kind')}><Select options={KINDS.map((k) => ({ value: k, label: tDyn('webhook.kind', k) }))} /></Form.Item>
          <Form.Item name="url" label={t('webhook.url')} rules={[{ required: true, type: 'url', message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="secret" label={t('webhook.secret')} extra={t('webhook.secretHint')}><Input.Password autoComplete="new-password" /></Form.Item>
          <Form.Item name="projectId" label={t('common.project')}><Select allowClear placeholder={t('webhook.global')} disabled={!isAdmin(me) && false} options={(projects.data ?? []).map((p) => ({ value: p.id, label: p.name }))} /></Form.Item>
          <Form.Item name="events" label={t('webhook.events')}>
            <Select mode="multiple" options={EVENTS.map((e) => ({ value: e, label: tDyn('event', e) }))} />
          </Form.Item>
          <Form.Item name="enabled" label={t('common.enabled')} valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
      <Drawer open={!!logFor} width={620} title={`${t('webhook.deliveries')}:${logFor?.name ?? ''}`} onClose={() => setLogFor(null)}>
        <Table<WebhookDelivery>
          scroll={{ x: 600 }}
          rowKey="id"
          size="small"
          loading={deliveries.loading}
          pagination={false}
          dataSource={deliveries.data ?? []}
          columns={[
            { title: t('common.time'), dataIndex: 'at', width: 170, render: fmtTime },
            { title: t('webhook.events'), dataIndex: 'event', render: (e: string) => tDyn('event', e) },
            { title: '#', dataIndex: 'attempt', width: 70, render: (n: number) => t('webhook.attempt', { n }) },
            { title: t('changes.status'), dataIndex: 'status', width: 80, render: (s: string) => <Tag color={s === 'SUCCESS' ? 'success' : 'error'}>{tDyn('webhook.delivery', s)}</Tag> },
            { title: t('webhook.httpStatus'), dataIndex: 'httpStatus', width: 90 },
            { title: t('webhook.error'), dataIndex: 'error', ellipsis: true },
          ]}
        />
      </Drawer>
    </Card>
  );
}
