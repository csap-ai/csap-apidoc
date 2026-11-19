import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Table, Tag, Typography, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { projectApi, tokenApi } from '@/api/endpoints';
import type { TokenCreated, TokenInfo, TokenScope } from '@/api/types';
import { useLoad } from '@/utils/useLoad';
import { showError } from '@/utils/errors';
import { copyText, fmtTime } from '@/utils/format';
import { t, tDyn } from '@/i18n';

export default function TokensTab() {
  const tokens = useLoad(() => tokenApi.list(), []);
  const projects = useLoad(() => projectApi.list(), []);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<TokenCreated | null>(null); // 明文只存在这里,关闭即丢弃
  const [form] = Form.useForm();

  const create = async (v: { name: string; scope: TokenScope; projectId?: number }) => {
    try {
      const tk = await tokenApi.create(v);
      setCreating(false);
      form.resetFields();
      setCreated(tk);
      void tokens.reload();
    } catch (e) { showError(e); }
  };
  const copy = async () => (await copyText(created!.token)) ? message.success(t('common.copied')) : message.error(t('err.INTERNAL'));

  return (
    <Card size="small">
      <Button type="primary" style={{ marginBottom: 12 }} onClick={() => setCreating(true)}>{t('token.new')}</Button>
      <Table<TokenInfo>
        rowKey="id"
        size="small"
        loading={tokens.loading}
        pagination={false}
        dataSource={tokens.data ?? []}
        columns={[
          { title: t('token.name'), dataIndex: 'name' },
          { title: t('token.prefix'), dataIndex: 'prefix', width: 110, render: (p: string) => <span className="mono">{p}…</span> },
          { title: t('token.scope'), dataIndex: 'scope', width: 110, render: (s: string) => <Tag>{s}</Tag> },
          { title: t('common.project'), dataIndex: 'projectId', width: 110, render: (id: number | null) => (id ? projects.data?.find((p) => p.id === id)?.name ?? id : t('webhook.global')) },
          { title: t('changes.createdAt'), dataIndex: 'createdAt', width: 170, render: fmtTime },
          { title: t('token.lastUsed'), dataIndex: 'lastUsedAt', width: 170, render: (v: number | null) => (v ? fmtTime(v) : t('common.never')) },
          { title: t('common.operation'), width: 100, render: (_, tk) => tk.revoked ? <Tag>{t('token.revoked')}</Tag> : (
            <Popconfirm title={t('token.revokeConfirm')} okText={t('common.ok')} cancelText={t('common.cancel')} onConfirm={async () => { try { await tokenApi.revoke(tk.id); void tokens.reload(); } catch (e) { showError(e); } }}><a>{t('token.revoke')}</a></Popconfirm>
          ) },
        ]}
      />
      <Modal open={creating} title={t('token.new')} onCancel={() => setCreating(false)} onOk={() => form.submit()} okText={t('common.create')} cancelText={t('common.cancel')}>
        <Form form={form} layout="vertical" onFinish={create} initialValues={{ scope: 'REGISTER' }}>
          <Form.Item name="name" label={t('token.name')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="scope" label={t('token.scope')}><Select options={(['REGISTER', 'OPEN_API'] as const).map((s) => ({ value: s, label: tDyn('token.scope', s) }))} /></Form.Item>
          <Form.Item name="projectId" label={t('token.projectLimit')}><Select allowClear options={(projects.data ?? []).map((p) => ({ value: p.id, label: p.name }))} /></Form.Item>
        </Form>
      </Modal>
      <Modal
        open={!!created}
        title={t('token.created')}
        closable={false}
        maskClosable={false}
        keyboard={false}
        destroyOnClose
        footer={<Button type="primary" onClick={() => setCreated(null)}>{t('token.copyAndClose')}</Button>}
      >
        {created && (
          <>
            <Alert type="warning" showIcon message={t('token.onceWarn')} style={{ marginBottom: 12 }} />
            <Typography.Paragraph data-testid="token-plain" className="mono" style={{ wordBreak: 'break-all', padding: 10, border: '1px solid rgba(128,128,128,0.3)', borderRadius: 6 }}>
              {created.token}
            </Typography.Paragraph>
            <Button icon={<CopyOutlined />} onClick={copy}>{t('common.copy')}</Button>
          </>
        )}
      </Modal>
    </Card>
  );
}
