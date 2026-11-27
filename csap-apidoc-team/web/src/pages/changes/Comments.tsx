// 修改单评论(1.1 §5.1):列表 + 发表框,可选关联到某条规则;自己的可编辑 / 删除,管理员和项目维护者可删任何人的。
import { useState } from 'react';
import { Button, Card, Empty, Input, List, Popconfirm, Select, Space, Tag, Typography } from 'antd';
import { message } from '@/utils/notify';
import { commentApi } from '@/api/endpoints';
import type { Change, Comment } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { canManageProject, canWrite } from '@/auth/permissions';
import { useLoad } from '@/utils/useLoad';
import { fmtTime } from '@/utils/format';
import { showError } from '@/utils/errors';
import { t } from '@/i18n';

const MAX = 4000;

export default function Comments({ change, onChanged }: { change: Change; onChanged?: () => void }) {
  const { me } = useAuth();
  const list = useLoad(() => commentApi.list(change.id), [change.id]);
  const [text, setText] = useState('');
  const [itemId, setItemId] = useState<number | undefined>();
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<{ id: number; text: string } | null>(null);
  const canPost = canWrite(me, change.projectId); // 管理员 / 维护者 / 开发;只读不能发
  const moderator = canManageProject(me, change.projectId); // 管理员和该项目维护者可以删任何评论
  const itemLabel = (id: number | null) => {
    const i = change.items.find((x) => x.id === id);
    return i ? `${i.service} · ${i.ruleKey.split('.').slice(-2).join('.')}` : null;
  };

  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    setBusy(true);
    try {
      await fn();
      if (ok) message.success(ok);
      await list.reload();
      onChanged?.();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  const post = () => {
    const body = text.trim();
    if (!body) return void message.warning(t('comment.emptyBody'));
    if (body.length > MAX) return void message.warning(t('comment.tooLong'));
    void run(async () => {
      await commentApi.create(change.id, { body, itemId });
      setText('');
      setItemId(undefined);
    }, t('comment.posted'));
  };

  const comments = list.data ?? [];
  return (
    <Card size="small" title={`${t('comment.title')}(${change.commentCount ?? comments.filter((c) => !c.deleted).length})`} data-testid="comments">
      {comments.length === 0 && !list.loading ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('comment.empty')} />
      ) : (
        <List<Comment>
          loading={list.loading}
          dataSource={comments}
          renderItem={(c) => {
            const mine = c.author.id === me?.id;
            const label = itemLabel(c.itemId);
            return (
              <List.Item key={c.id} data-testid={`comment-${c.id}`} style={{ display: 'block' }}>
                <Space size="small" wrap style={{ marginBottom: 4 }}>
                  <b>{c.author.displayName}</b>
                  <Typography.Text type="secondary">{fmtTime(c.createdAt)}</Typography.Text>
                  {!c.deleted && c.updatedAt > c.createdAt && <Tag bordered={false}>{t('comment.edited')}</Tag>}
                  {label && <Tag className="mono">{label}</Tag>}
                </Space>
                {c.deleted ? (
                  <Typography.Text type="secondary" italic>{t('comment.deleted')}</Typography.Text>
                ) : editing?.id === c.id ? (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Input.TextArea rows={3} value={editing.text} onChange={(e) => setEditing({ id: c.id, text: e.target.value })} aria-label={t('comment.editAction')} />
                    <Space>
                      <Button size="small" type="primary" loading={busy} disabled={!editing.text.trim() || editing.text.trim().length > MAX}
                        onClick={() => run(async () => { await commentApi.update(c.id, editing.text.trim()); setEditing(null); })}>{t('comment.saveEdit')}</Button>
                      <Button size="small" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
                    </Space>
                  </Space>
                ) : (
                  <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{c.body}</Typography.Paragraph>
                )}
                {!c.deleted && editing?.id !== c.id && (mine || moderator) && (
                  <Space size="small" style={{ marginTop: 4 }}>
                    {mine && <a onClick={() => setEditing({ id: c.id, text: c.body ?? '' })}>{t('comment.editAction')}</a>}
                    <Popconfirm title={t('comment.deleteConfirm')} okText={t('common.ok')} cancelText={t('common.cancel')} onConfirm={() => run(() => commentApi.remove(c.id))}>
                      <a>{t('comment.deleteAction')}</a>
                    </Popconfirm>
                  </Space>
                )}
              </List.Item>
            );
          }}
        />
      )}
      {canPost ? (
        <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
          <Input.TextArea rows={3} value={text} onChange={(e) => setText(e.target.value)} placeholder={t('comment.placeholder')} aria-label={t('comment.placeholder')} />
          <Space wrap>
            <Select
              allowClear
              style={{ minWidth: 260 }}
              placeholder={t('comment.relate')}
              aria-label={t('comment.relate')}
              value={itemId}
              onChange={setItemId}
              options={change.items.filter((i) => i.id != null).map((i) => ({ value: i.id as number, label: `${i.service} · ${i.ruleKey}` }))}
            />
            <Button type="primary" loading={busy} onClick={post}>{t('comment.post')}</Button>
          </Space>
        </Space>
      ) : (
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>{t('comment.readOnly')}</Typography.Text>
      )}
    </Card>
  );
}
