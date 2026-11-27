// 编辑中提示(1.1 §5.2):① 有人正在编辑(内存心跳);② 有未完成的修改单涉及这条规则(查库,随 /rules/{service}/item 返回)。
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Space, Tag } from 'antd';
import { presenceApi } from '@/api/endpoints';
import type { OpenChange, PresenceUser } from '@/api/types';
import { fmtTime } from '@/utils/format';
import { t, tDyn } from '@/i18n';

/** 心跳间隔:前端每 30 秒续一次,后端 90 秒没续上视为离开 */
export const PRESENCE_INTERVAL_MS = 30_000;

/** 值稳定一段时间后再更新(规则 Key 一个字符一个字符地输入时,不要每个字符都上报) */
export function useDebounced<T>(value: T, ms = 600): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

/**
 * 打开规则编辑器后:heartbeat=true 时每 30 秒 PUT 一次、离开时 DELETE(失败也无妨);
 * 同时轮询 GET 拿到"别人"正在编辑这条规则的列表。只读用户传 heartbeat=false,只看不报。
 */
export function usePresence(opts: { env?: string; service?: string; ruleKey?: string; heartbeat: boolean; intervalMs?: number }): PresenceUser[] {
  const { env, service, ruleKey, heartbeat, intervalMs = PRESENCE_INTERVAL_MS } = opts;
  const [users, setUsers] = useState<PresenceUser[]>([]);
  useEffect(() => {
    setUsers([]);
    if (!service || !ruleKey) return;
    let alive = true;
    const body = { env, service, ruleKey };
    const tick = async () => {
      if (heartbeat) {
        try { await presenceApi.put(body); } catch { /* 心跳失败不打扰用户,下一轮再试 */ }
      }
      try {
        const list = await presenceApi.list({ env, service });
        if (alive) setUsers(list.find((e) => e.ruleKey === ruleKey)?.users ?? []);
      } catch { /* 提示是锦上添花,失败就不显示 */ }
    };
    void tick();
    const id = setInterval(() => void tick(), intervalMs);
    return () => {
      alive = false;
      clearInterval(id);
      if (heartbeat) presenceApi.remove(body).catch(() => undefined);
    };
  }, [env, service, ruleKey, heartbeat, intervalMs]);
  return users;
}

export function PresenceNotice({ env, service, ruleKey, heartbeat, intervalMs }: {
  env?: string; service?: string; ruleKey?: string; heartbeat: boolean; intervalMs?: number;
}) {
  const users = usePresence({ env, service, ruleKey, heartbeat, intervalMs });
  if (!users.length) return null;
  const names = users.slice(0, 3).map((u) => u.displayName).join('、');
  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 8 }}
      data-testid="presence-notice"
      message={users.length > 3 ? t('presence.editingMany', { names, n: users.length }) : t('presence.editing', { names })}
    />
  );
}

/** "有 N 张未完成的修改单涉及这条规则"(可点开) */
export function OpenChangesNotice({ changes, excludeId }: { changes?: OpenChange[]; excludeId?: number }) {
  const [open, setOpen] = useState(false);
  const list = (changes ?? []).filter((c) => c.id !== excludeId);
  if (!list.length) return null;
  return (
    <Alert
      type="warning"
      showIcon
      style={{ marginBottom: 8 }}
      data-testid="open-changes-notice"
      message={
        <Space wrap>
          <span>{t('presence.openChanges', { n: list.length })}</span>
          <a onClick={() => setOpen((v) => !v)}>{t('presence.view')}</a>
        </Space>
      }
      description={open ? (
        <div data-testid="open-changes-list">
          <ul style={{ margin: 0, paddingInlineStart: 18 }}>
            {list.map((c) => (
              <li key={c.id}>
                <Link to={`/changes/${c.id}`}>#{c.id} {c.title}</Link> <Tag>{tDyn('changes.status', c.status)}</Tag>
                <span style={{ opacity: 0.65 }}>{c.author.displayName} · {fmtTime(c.updatedAt)}</span>
              </li>
            ))}
          </ul>
          <span style={{ opacity: 0.65 }}>{t('presence.openChangesHint')}</span>
        </div>
      ) : undefined}
    />
  );
}
