import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Input, Space, Tag, Upload, message } from 'antd';
import { licenseApi } from '@/api/endpoints';
import { useLoad } from '@/utils/useLoad';
import { showError } from '@/utils/errors';
import { fmtDate } from '@/utils/format';
import { t, tDyn } from '@/i18n';

export default function LicenseTab() {
  const lic = useLoad(() => licenseApi.get(), []);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const l = lic.data;

  const submit = async () => {
    if (!text.trim()) return void message.warning(t('lic.empty'));
    setBusy(true);
    try { lic.setData(await licenseApi.upload(text.trim())); setText(''); message.success(t('lic.updated')); } catch (e) { showError(e); } finally { setBusy(false); }
  };
  const color = l?.status === 'VALID' ? 'success' : l?.status === 'TRIAL' ? 'processing' : 'warning';

  return (
    <Space direction="vertical" style={{ width: '100%', maxWidth: 760 }} size="middle">
      <Card size="small" title={t('lic.title')} loading={lic.loading}>
        {l && (
          <Descriptions size="small" column={2}>
            <Descriptions.Item label={t('changes.status')}><Tag color={color}>{tDyn('lic.status', l.status)}</Tag></Descriptions.Item>
            <Descriptions.Item label={t('lic.licensee')}>{l.licensee || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('lic.maxMembers')}>{l.maxMembers}</Descriptions.Item>
            <Descriptions.Item label={t('lic.activeMembers')}>{l.activeMembers}</Descriptions.Item>
            <Descriptions.Item label={t('lic.expiresAt')}>{fmtDate(l.expiresAt)}</Descriptions.Item>
          </Descriptions>
        )}
        {l && l.status !== 'VALID' && <Alert style={{ marginTop: 12 }} type={l.status === 'TRIAL' ? 'info' : 'warning'} showIcon message={t(`license.${l.status}` as 'license.TRIAL')} />}
      </Card>
      <Card size="small" title={t('lic.upload')}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input.TextArea rows={5} className="mono" value={text} onChange={(e) => setText(e.target.value)} placeholder={t('lic.paste')} />
          <Space>
            <Upload accept=".lic,.txt,.license" showUploadList={false} beforeUpload={async (f) => { setText((await f.text()).trim()); return false; }}><Button>{t('lic.pickFile')}</Button></Upload>
            <Button type="primary" loading={busy} onClick={submit}>{t('lic.submit')}</Button>
          </Space>
        </Space>
      </Card>
    </Space>
  );
}
