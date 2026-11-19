import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Descriptions, Input, Modal, Popconfirm, Space, Tag, Timeline, Tooltip, Typography } from 'antd';
import { message } from '@/utils/notify';
import { changeApi, ruleApi } from '@/api/endpoints';
import { ApiError } from '@/api/client';
import type { ConflictItem } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { changeActions, isAuthor } from '@/auth/permissions';
import { ChangeStatusTag, PageTitle } from '@/components/common';
import DiffView from '@/components/DiffView';
import { useLoad } from '@/utils/useLoad';
import { fmtTime } from '@/utils/format';
import { showError } from '@/utils/errors';
import { t, tDyn } from '@/i18n';
import ConflictPanel from './ConflictPanel';

export default function ChangeDetail() {
  const id = Number(useParams().id);
  const { me } = useAuth();
  const navigate = useNavigate();
  const { data: c, loading, reload } = useLoad(() => changeApi.get(id), [id]);
  const [conflicts, setConflicts] = useState<ConflictItem[] | null>(null);
  const [decide, setDecide] = useState<'approve' | 'reject' | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  // 状态为 CONFLICT 但还没有冲突明细(刷新页面后):按条目取当前库内容
  useEffect(() => {
    if (!c || c.status !== 'CONFLICT' || conflicts) return;
    Promise.all(c.items.filter((i) => i.conflict).map(async (i) => {
      const cur = await ruleApi.item(i.service, i.ruleKey);
      return { service: i.service, ruleKey: i.ruleKey, baseVersion: i.baseVersion, currentVersion: cur.version, currentRules: cur.rules } as ConflictItem;
    })).then(setConflicts).catch(showError);
  }, [c, conflicts]);
  useEffect(() => { if (c && c.status !== 'CONFLICT') setConflicts(null); }, [c?.status]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!c) return <PageTitle title={t('changes.detail', { id })} extra={loading ? t('common.loading') : null} />;
  const { visible, approveBlockedSelf } = changeActions(me, c);
  const author = isAuthor(me, c);

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      message.success(ok);
      await reload();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409 && e.code === 'CONFLICT') {
        setConflicts((e.body.items as ConflictItem[]) ?? []);
        message.warning(e.message);
        await reload();
      } else showError(e);
    } finally {
      setBusy(false);
      setDecide(null);
    }
  };

  const doDecide = () => {
    if (decide === 'reject' && !note.trim()) return void message.warning(t('changes.noteRequired'));
    void run(() => (decide === 'approve' ? changeApi.approve(id, note) : changeApi.reject(id, note)), decide === 'approve' ? t('changes.approved') : t('changes.rejected'));
    setNote('');
  };

  return (
    <>
      <PageTitle
        title={<Space>{c.title}<ChangeStatusTag status={c.status} /><Tag>{tDyn('changes.kind', c.kind)}</Tag></Space>}
        extra={
          <Space data-testid="change-actions">
            {visible.includes('edit') && <Button onClick={() => navigate(`/changes/${id}/edit`)}>{t('changes.action.edit')}</Button>}
            {visible.includes('submit') && <Button type="primary" loading={busy} onClick={() => run(() => changeApi.submit(id), t('changes.submitted'))}>{t('changes.action.submit')}</Button>}
            {visible.includes('approve') && <Button type="primary" onClick={() => setDecide('approve')}>{t('changes.action.approve')}</Button>}
            {approveBlockedSelf && <Tooltip title={t('changes.action.selfApproveBlocked')}><span><Button type="primary" disabled>{t('changes.action.approve')}</Button></span></Tooltip>}
            {visible.includes('reject') && <Button danger onClick={() => setDecide('reject')}>{t('changes.action.reject')}</Button>}
            {visible.includes('withdraw') && (
              <Popconfirm title={t('changes.action.withdraw') + '?'} okText={t('common.ok')} cancelText={t('common.cancel')} onConfirm={() => run(() => changeApi.withdraw(id), t('changes.withdrawn'))}>
                <Button>{t('changes.action.withdraw')}</Button>
              </Popconfirm>
            )}
          </Space>
        }
      />
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {c.status === 'CONFLICT' && conflicts && conflicts.length > 0 && (
          <ConflictPanel key={conflicts.map((x) => x.currentVersion).join(',')} change={c} conflicts={conflicts} isAuthor={author} onRebased={() => { setConflicts(null); void reload(); }} />
        )}
        <Card size="small">
          <Descriptions size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label={t('common.project')}>{c.projectName}</Descriptions.Item>
            <Descriptions.Item label={t('changes.author')}>{c.author.displayName}</Descriptions.Item>
            <Descriptions.Item label={t('changes.createdAt')}>{fmtTime(c.createdAt)}</Descriptions.Item>
            <Descriptions.Item label={t('changes.submittedAt')}>{fmtTime(c.submittedAt)}</Descriptions.Item>
            <Descriptions.Item label={t('changes.decidedBy')}>{c.decidedBy?.displayName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('changes.publishedAt')}>{fmtTime(c.publishedAt)}</Descriptions.Item>
            <Descriptions.Item label={t('changes.reason')} span={2}>{c.reason || '-'}</Descriptions.Item>
            {c.decisionNote && <Descriptions.Item label={t('changes.note')} span={2}>{c.decisionNote}</Descriptions.Item>}
          </Descriptions>
        </Card>
        {c.items.map((i) => (
          <Card key={`${i.service}${i.ruleKey}`} size="small" title={<span className="mono">{i.service} · {i.ruleKey}</span>}
            extra={<Space>
              <Tag>{t('changes.baseVersion')} v{i.baseVersion}</Tag>
              {i.conflict && <Tag color="orange">{t('changes.itemConflict')}(v{i.currentVersion})</Tag>}
              <Link to={`/rules/${encodeURIComponent(i.service)}?key=${encodeURIComponent(i.ruleKey)}`}>{t('nav.rules')}</Link>
            </Space>}>
            <Typography.Text strong>{t('changes.diff')}</Typography.Text>
            <DiffView diff={i.diff} defaultMode="unified" />
          </Card>
        ))}
        <Card size="small" title={t('changes.timeline')}>
          <Timeline items={[
            { children: `${fmtTime(c.createdAt)} ${c.author.displayName} 创建` },
            ...(c.submittedAt ? [{ children: `${fmtTime(c.submittedAt)} 提交` }] : []),
            ...(c.decidedAt ? [{ children: `${fmtTime(c.decidedAt)} ${c.decidedBy?.displayName ?? ''} ${c.status === 'REJECTED' ? '驳回' : '通过'}${c.decisionNote ? `:${c.decisionNote}` : ''}`, color: c.status === 'REJECTED' ? 'red' : 'green' }] : []),
            ...(c.publishedAt ? [{ children: `${fmtTime(c.publishedAt)} 已发布`, color: 'green' }] : []),
          ]} />
        </Card>
      </Space>
      <Modal
        open={!!decide}
        title={decide === 'approve' ? t('changes.action.approve') : t('changes.action.reject')}
        okText={t('common.ok')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: decide === 'reject', loading: busy }}
        onOk={doDecide}
        onCancel={() => setDecide(null)}
      >
        <Input.TextArea rows={3} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('changes.note')} aria-label={t('changes.note')} />
      </Modal>
    </>
  );
}
