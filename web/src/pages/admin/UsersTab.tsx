import { useState } from 'react';
import { Button, Card, Form, Input, Modal, Select, Table, Tag } from 'antd';
import { userApi } from '@/api/endpoints';
import type { OrgRole, User } from '@/api/types';
import { useLoad } from '@/utils/useLoad';
import { showError } from '@/utils/errors';
import { fmtTime } from '@/utils/format';
import { t, tDyn } from '@/i18n';

export default function UsersTab() {
  const users = useLoad(() => userApi.list(), []);
  const [creating, setCreating] = useState(false);
  const [resetFor, setResetFor] = useState<User | null>(null);
  const [form] = Form.useForm();
  const [pwd, setPwd] = useState('');

  const create = async (v: { username: string; displayName: string; password: string; orgRole: OrgRole }) => {
    try { await userApi.create(v); setCreating(false); form.resetFields(); void users.reload(); } catch (e) { showError(e); }
  };
  const patch = async (u: User, b: Parameters<typeof userApi.update>[1]) => {
    try { await userApi.update(u.id, b); void users.reload(); } catch (e) { showError(e); }
  };

  return (
    <Card size="small">
      <Button type="primary" style={{ marginBottom: 12 }} onClick={() => setCreating(true)}>{t('user.new')}</Button>
      <Table<User>
        rowKey="id"
        size="small"
        loading={users.loading}
        pagination={false}
        dataSource={users.data ?? []}
        columns={[
          { title: t('user.username'), dataIndex: 'username' },
          { title: t('user.displayName'), dataIndex: 'displayName' },
          { title: t('user.orgRole'), dataIndex: 'orgRole', width: 130, render: (r: OrgRole, u) => (
            <Select size="small" style={{ width: 110 }} value={r} onChange={(v) => patch(u, { orgRole: v })} options={[{ value: 'ADMIN', label: t('role.ADMIN') }, { value: 'MEMBER', label: t('role.MEMBER') }]} />
          ) },
          { title: t('user.status'), dataIndex: 'status', width: 90, render: (s: string) => <Tag color={s === 'ACTIVE' ? 'success' : 'default'}>{tDyn('user.status', s)}</Tag> },
          { title: t('changes.createdAt'), dataIndex: 'createdAt', width: 170, render: fmtTime },
          { title: t('common.operation'), width: 170, render: (_, u) => (
            <span>
              <a onClick={() => patch(u, { status: u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>{u.status === 'ACTIVE' ? t('user.disable') : t('user.enable')}</a>
              {' · '}
              <a onClick={() => { setResetFor(u); setPwd(''); }}>{t('user.resetPassword')}</a>
            </span>
          ) },
        ]}
      />
      <Modal open={creating} title={t('user.new')} onCancel={() => setCreating(false)} onOk={() => form.submit()} okText={t('common.create')} cancelText={t('common.cancel')}>
        <Form form={form} layout="vertical" onFinish={create} initialValues={{ orgRole: 'MEMBER' }}>
          <Form.Item name="username" label={t('user.username')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="displayName" label={t('user.displayName')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="password" label={t('user.password')} rules={[{ required: true, min: 8, message: t('setup.passwordHint') }]}><Input.Password autoComplete="new-password" /></Form.Item>
          <Form.Item name="orgRole" label={t('user.orgRole')}><Select options={[{ value: 'MEMBER', label: t('role.MEMBER') }, { value: 'ADMIN', label: t('role.ADMIN') }]} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={!!resetFor} title={`${t('user.resetPassword')}:${resetFor?.displayName}`} onCancel={() => setResetFor(null)} okText={t('common.ok')} cancelText={t('common.cancel')} okButtonProps={{ disabled: pwd.length < 8 }}
        onOk={async () => { await patch(resetFor!, { password: pwd }); setResetFor(null); }}>
        <Input.Password value={pwd} onChange={(e) => setPwd(e.target.value)} placeholder={t('setup.passwordHint')} autoComplete="new-password" />
      </Modal>
    </Card>
  );
}
