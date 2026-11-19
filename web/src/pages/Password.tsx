import { useState } from 'react';
import { Button, Card, Form, Input } from 'antd';
import { message } from '@/utils/notify';
import { authApi } from '@/api/endpoints';
import { PageTitle } from '@/components/common';
import { showError } from '@/utils/errors';
import { t } from '@/i18n';

export default function Password() {
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();
  const submit = async (v: { oldPassword: string; newPassword: string }) => {
    setBusy(true);
    try {
      await authApi.password({ oldPassword: v.oldPassword, newPassword: v.newPassword });
      message.success(t('pwd.success'));
      form.resetFields();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <PageTitle title={t('pwd.title')} />
      <Card style={{ maxWidth: 420 }}>
        <Form form={form} layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="oldPassword" label={t('pwd.old')} rules={[{ required: true, message: t('common.required') }]}><Input.Password autoComplete="current-password" /></Form.Item>
          <Form.Item name="newPassword" label={t('pwd.new')} rules={[{ required: true, min: 8, message: t('setup.passwordHint') }]}><Input.Password autoComplete="new-password" /></Form.Item>
          <Form.Item name="n2" label={t('pwd.new2')} dependencies={['newPassword']} rules={[{ required: true, message: t('common.required') }, ({ getFieldValue }) => ({ validator: (_, v) => (!v || getFieldValue('newPassword') === v ? Promise.resolve() : Promise.reject(new Error(t('setup.passwordMismatch')))) })]}><Input.Password autoComplete="new-password" /></Form.Item>
          <Button type="primary" htmlType="submit" loading={busy}>{t('common.save')}</Button>
        </Form>
      </Card>
    </>
  );
}
