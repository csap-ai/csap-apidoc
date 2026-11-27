// 推广弹窗(1.1 §3.3 / §8):填标题、原因,选项"包含删除""创建后立即提交"。
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Checkbox, Form, Input, Modal, Space, Typography } from 'antd';
import { message } from '@/utils/notify';
import { promotionApi } from '@/api/endpoints';
import { ApiError } from '@/api/client';
import type { Env, EnvDiffStatus } from '@/api/types';
import { showError } from '@/utils/errors';
import { t } from '@/i18n';

export interface PromoteRow {
  service: string;
  ruleKey: string;
  status: EnvDiffStatus;
}

/** 勾选的规则里哪些会真正进入推广单:「仅目标有」(推广后在目标删除)只有勾了"包含删除"才带上 */
export function promotableItems(rows: PromoteRow[], includeDeletes: boolean): PromoteRow[] {
  return rows.filter((r) => r.status !== 'ONLY_TO' || includeDeletes);
}

export default function PromoteModal({ open, from, to, projectId, projectName, rows, onClose }: {
  open: boolean; from: Env; to: Env; projectId: number; projectName: string; rows: PromoteRow[]; onClose: () => void;
}) {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [reason, setReason] = useState('');
  const [includeDeletes, setIncludeDeletes] = useState(false);
  const [submit, setSubmit] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTitle(t('envdiff.defaultTitle', { project: projectName, from: from.name, to: to.name }));
    setReason('');
    setIncludeDeletes(false);
    setSubmit(false);
  }, [open, projectName, from.name, to.name]);

  const items = promotableItems(rows, includeDeletes);
  const deletes = items.filter((r) => r.status === 'ONLY_TO').length;
  const hasOnlyTo = rows.some((r) => r.status === 'ONLY_TO');

  const ok = async () => {
    if (!title.trim() || !items.length) return;
    setBusy(true);
    try {
      const c = await promotionApi.create({
        projectId, from: from.key, to: to.key,
        items: items.map((r) => ({ service: r.service, ruleKey: r.ruleKey })),
        includeDeletes, title: title.trim(), reason, submit,
      });
      message.success(t('envdiff.created'));
      onClose();
      navigate(`/changes/${c.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'CONFLICT') {
        // 一键推广时提交发现版本已变化:单子已创建(状态 CONFLICT),后端在 409 里带 changeId
        message.warning(t('envdiff.submittedConflict'));
        onClose();
        const id = (e.body as unknown as { changeId?: number }).changeId;
        navigate(id ? `/changes/${id}` : '/changes');
      } else showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      title={t('envdiff.promoteTitle', { from: from.name, to: to.name })}
      onCancel={onClose}
      onOk={ok}
      okText={t('envdiff.promote', { n: items.length })}
      cancelText={t('common.cancel')}
      okButtonProps={{ disabled: !title.trim() || !items.length, loading: busy }}
      destroyOnClose
      width={560}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Text data-testid="promote-summary">{t('envdiff.promoteSummary', { n: items.length, from: from.name, to: to.name, m: deletes })}</Typography.Text>
        {to.protected && <Alert type="warning" showIcon message={t('envdiff.protectedNote')} />}
        {!items.length && <Alert type="info" showIcon message={t('envdiff.nothingSelected')} />}
        <Form layout="vertical">
          <Form.Item label={t('envdiff.title.field')} required>
            <Input value={title} maxLength={100} onChange={(e) => setTitle(e.target.value)} aria-label={t('envdiff.title.field')} />
          </Form.Item>
          <Form.Item label={t('envdiff.reason.field')}>
            <Input.TextArea rows={2} value={reason} onChange={(e) => setReason(e.target.value)} aria-label={t('envdiff.reason.field')} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 4 }} extra={hasOnlyTo ? t('envdiff.includeDeletesHint') : undefined}>
            <Checkbox checked={includeDeletes} onChange={(e) => setIncludeDeletes(e.target.checked)}>{t('envdiff.includeDeletes')}</Checkbox>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }} extra={t('envdiff.submitNowHint')}>
            <Checkbox checked={submit} onChange={(e) => setSubmit(e.target.checked)}>{t('envdiff.submitNow')}</Checkbox>
          </Form.Item>
        </Form>
      </Space>
    </Modal>
  );
}
