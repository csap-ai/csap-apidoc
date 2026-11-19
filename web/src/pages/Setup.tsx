import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { setupApi } from '@/api/endpoints';
import { useAuth } from '@/auth/AuthContext';
import { showError } from '@/utils/errors';
import { t } from '@/i18n';
import { AuthShell } from './Login';

export default function Setup() {
  const navigate = useNavigate();
  const { initialized, loading } = useAuth();
  const [busy, setBusy] = useState(false);
  if (!loading && initialized === true) return <Navigate to="/login" replace />;
  const submit = async (v: { orgName: string; displayName: string; username: string; password: string }) => {
    setBusy(true);
    try {
      await setupApi.init({ orgName: v.orgName, displayName: v.displayName, username: v.username, password: v.password });
      message.success(t('setup.success'));
      window.location.assign('/login'); // 重新读取初始化状态
      void navigate;
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthShell>
      <Card style={{ width: 440 }}>
        <Typography.Title level={4}>{t('setup.title')}</Typography.Title>
        <Typography.Paragraph type="secondary">{t('setup.sub')}</Typography.Paragraph>
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="orgName" label={t('setup.orgName')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="displayName" label={t('setup.displayName')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="username" label={t('setup.username')} rules={[{ required: true, message: t('common.required') }]}><Input autoComplete="username" /></Form.Item>
          <Form.Item name="password" label={t('setup.password')} extra={t('setup.passwordHint')} rules={[{ required: true, min: 8, message: t('setup.passwordHint') }]}><Input.Password autoComplete="new-password" /></Form.Item>
          <Form.Item
            name="password2"
            label={t('setup.password2')}
            dependencies={['password']}
            rules={[
              { required: true, message: t('common.required') },
              ({ getFieldValue }) => ({
                validator: (_, v) => (!v || getFieldValue('password') === v ? Promise.resolve() : Promise.reject(new Error(t('setup.passwordMismatch')))),
              }),
            ]}
          ><Input.Password autoComplete="new-password" /></Form.Item>
          <Button type="primary" htmlType="submit" block loading={busy}>{t('setup.submit')}</Button>
        </Form>
      </Card>
    </AuthShell>
  );
}
