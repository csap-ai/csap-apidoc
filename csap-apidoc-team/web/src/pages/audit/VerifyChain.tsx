// 审计哈希链校验(1.1 §6.5):管理员点击"校验哈希链",显示结果。
import { useState } from 'react';
import { Alert, Button, Descriptions, InputNumber, Modal, Space, Typography } from 'antd';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import { auditApi } from '@/api/endpoints';
import type { AuditVerifyResult } from '@/api/types';
import { showError } from '@/utils/errors';
import { t, tDyn } from '@/i18n';

export default function VerifyChain() {
  const [open, setOpen] = useState(false);
  const [fromId, setFromId] = useState<number | null>(null);
  const [toId, setToId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<AuditVerifyResult | null>(null);

  const run = async () => {
    setBusy(true);
    setResult(null);
    try {
      setResult(await auditApi.verify({ fromId: fromId ?? undefined, toId: toId ?? undefined }));
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button icon={<SafetyCertificateOutlined />} onClick={() => { setOpen(true); setResult(null); }}>{t('audit.verify')}</Button>
      <Modal open={open} title={t('audit.verifyTitle')} onCancel={() => setOpen(false)} footer={<Button onClick={() => setOpen(false)}>{t('common.close')}</Button>} destroyOnClose width={560}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ margin: 0 }}>{t('audit.verifyDesc')}</Typography.Paragraph>
          <Space wrap>
            <InputNumber min={1} placeholder={t('audit.verifyFromId')} value={fromId} onChange={setFromId} aria-label={t('audit.verifyFromId')} style={{ width: 160 }} />
            <InputNumber min={1} placeholder={t('audit.verifyToId')} value={toId} onChange={setToId} aria-label={t('audit.verifyToId')} style={{ width: 160 }} />
            <Button type="primary" loading={busy} onClick={run}>{t('audit.verifyRun')}</Button>
          </Space>
          {result && (
            <div data-testid="verify-result">
              {result.ok ? (
                <Alert type="success" showIcon message={t('audit.verifyOk')} description={t('audit.verifyOkDesc', { n: result.checked })} />
              ) : (
                <Alert
                  type="error"
                  showIcon
                  message={t('audit.verifyFail')}
                  description={`${t('audit.verifyFailDesc', { id: result.firstBrokenId ?? '-', problem: tDyn('audit.verifyProblem', result.problem), n: result.checked })}`}
                />
              )}
              <Descriptions size="small" column={1} style={{ marginTop: 12 }}>
                <Descriptions.Item label={t('audit.verifyHead')}>
                  <span className="mono" style={{ wordBreak: 'break-all' }}>#{result.headId ?? '-'} {result.headHash ?? ''}</span>
                </Descriptions.Item>
              </Descriptions>
              <Typography.Text type="secondary">{t('audit.verifyHeadHint')}</Typography.Text>
            </div>
          )}
        </Space>
      </Modal>
    </>
  );
}
