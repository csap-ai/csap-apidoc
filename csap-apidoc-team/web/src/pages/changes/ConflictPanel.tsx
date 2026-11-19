import { useState } from 'react';
import { Alert, Button, Card, Col, Input, Row, Space, Tag, Typography } from 'antd';
import { message } from '@/utils/notify';
import { changeApi } from '@/api/endpoints';
import type { Change, ConflictItem } from '@/api/types';
import DiffView, { CodeBlock } from '@/components/DiffView';
import { jsonError, prettyJson } from '@/utils/format';
import { makeUnified } from '@/utils/diff';
import { showError } from '@/utils/errors';
import { t } from '@/i18n';

/** 冲突处理:并列展示"我的 / 当前库里的",并支持 Rebase */
export default function ConflictPanel({ change, conflicts, isAuthor, onRebased }: {
  change: Change; conflicts: ConflictItem[]; isAuthor: boolean; onRebased: () => void;
}) {
  const mineOf = (c: ConflictItem) => change.items.find((i) => i.service === c.service && i.ruleKey === c.ruleKey)?.afterRules ?? null;
  const [edits, setEdits] = useState<Record<string, string>>(() =>
    Object.fromEntries(conflicts.map((c) => [`${c.service}|${c.ruleKey}`, mineOf(c) == null ? '' : prettyJson(mineOf(c))])),
  );
  const [busy, setBusy] = useState(false);
  const deleted = (c: ConflictItem) => mineOf(c) == null;
  const bad = conflicts.some((c) => !deleted(c) && jsonError(edits[`${c.service}|${c.ruleKey}`] ?? ''));

  const rebase = async () => {
    setBusy(true);
    try {
      await changeApi.rebase(change.id, change.items.map((i) => {
        const k = `${i.service}|${i.ruleKey}`;
        const involved = conflicts.some((c) => c.service === i.service && c.ruleKey === i.ruleKey);
        return { service: i.service, ruleKey: i.ruleKey, afterRules: involved && edits[k] !== '' ? JSON.stringify(JSON.parse(edits[k])) : i.afterRules };
      }));
      message.success(t('conflict.rebaseDone'));
      onRebased();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Alert
      type="warning"
      showIcon
      data-testid="conflict-panel"
      message={t('conflict.title')}
      description={
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Typography.Text>{t('conflict.desc')}</Typography.Text>
          {conflicts.map((c) => {
            const k = `${c.service}|${c.ruleKey}`;
            const mine = mineOf(c);
            return (
              <Card key={k} size="small" title={<span><span className="mono">{c.service} · {c.ruleKey}</span> <Tag color="orange">{t('conflict.baseNow', { base: c.baseVersion, cur: c.currentVersion })}</Tag></span>}>
                <Row gutter={12}>
                  <Col xs={24} md={12}>
                    <Typography.Text strong>{t('conflict.mine')}</Typography.Text>
                    <div data-testid="conflict-mine"><CodeBlock text={mine == null ? t('rules.deleted') : prettyJson(mine)} maxHeight={240} /></div>
                  </Col>
                  <Col xs={24} md={12}>
                    <Typography.Text strong>{t('conflict.current')}</Typography.Text>
                    <div data-testid="conflict-current"><CodeBlock text={c.currentRules == null ? t('rules.deleted') : prettyJson(c.currentRules)} maxHeight={240} /></div>
                  </Col>
                </Row>
                <Typography.Text strong style={{ display: 'block', margin: '12px 0 4px' }}>{t('conflict.diffTitle')}</Typography.Text>
                <DiffView diff={makeUnified(c.currentRules, mine, t('conflict.current'), t('conflict.mine'))} defaultMode="split" />
                {isAuthor && !deleted(c) && (
                  <div style={{ marginTop: 12 }}>
                    <Typography.Text strong>{t('conflict.rebaseTitle')}</Typography.Text>
                    <Space style={{ margin: '0 0 4px 12px' }}>
                      <Button size="small" onClick={() => setEdits((e) => ({ ...e, [k]: prettyJson(c.currentRules) }))}>{t('conflict.useCurrent')}</Button>
                      <Button size="small" onClick={() => setEdits((e) => ({ ...e, [k]: prettyJson(mine) }))}>{t('conflict.useMine')}</Button>
                    </Space>
                    <Input.TextArea className="mono" rows={8} value={edits[k]} onChange={(e) => setEdits((x) => ({ ...x, [k]: e.target.value }))} status={jsonError(edits[k] ?? '') ? 'error' : undefined} />
                  </div>
                )}
              </Card>
            );
          })}
          {isAuthor ? (
            <Button type="primary" loading={busy} disabled={bad} onClick={rebase}>{t('changes.action.rebase')}</Button>
          ) : (
            <Typography.Text type="secondary">{t('conflict.onlyAuthor')}</Typography.Text>
          )}
        </Space>
      }
    />
  );
}
