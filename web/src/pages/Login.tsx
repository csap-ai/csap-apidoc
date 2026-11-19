import { useState, type ReactNode } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useAuth } from '@/auth/AuthContext';
import { ApiError } from '@/api/client';
import { t } from '@/i18n';

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 20, padding: 16 }}>
      <div style={{ textAlign: 'center' }}>
        <Typography.Title level={2} style={{ margin: 0 }}>{t('app.name')}</Typography.Title>
        <Typography.Text type="secondary">{t('app.slogan')}</Typography.Text>
      </div>
      {children}
    </div>
  );
}

/** 只允许站内相对路径,避免开放重定向 */
export function safeRedirect(r: string | null): string {
  return r && r.startsWith('/') && !r.startsWith('//') && !r.startsWith('/login') ? r : '/docs';
}

export default function Login() {
  const { me, login, initialized } = useAuth();
  const navigate = useNavigate();
  const [sp] = useSearchParams();
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const redirect = safeRedirect(sp.get('redirect'));

  if (initialized === false) return <Navigate to="/setup" replace />;
  if (me) return <Navigate to={redirect} replace />;

  const submit = async (v: { username: string; password: string }) => {
    setBusy(true);
    setErr('');
    try {
      await login(v.username, v.password);
      navigate(redirect, { replace: true });
    } catch (e) {
      if (e instanceof ApiError) {
        setErr(e.code === 'LOCKED' ? t('login.locked') : e.body.message || t('login.failed'));
      } else setErr(t('err.network'));
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthShell>
      <Card style={{ width: 380 }}>
        <Typography.Title level={4}>{t('login.title')}</Typography.Title>
        {err && <Alert type="error" showIcon message={err} style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="username" label={t('login.username')} rules={[{ required: true, message: t('common.required') }]}><Input autoFocus autoComplete="username" /></Form.Item>
          <Form.Item name="password" label={t('login.password')} rules={[{ required: true, message: t('common.required') }]}><Input.Password autoComplete="current-password" /></Form.Item>
          <Button type="primary" htmlType="submit" block loading={busy}>{t('login.submit')}</Button>
        </Form>
      </Card>
    </AuthShell>
  );
}
