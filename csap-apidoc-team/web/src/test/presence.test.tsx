import { describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db, livePresence, PRESENCE_TTL_MS } from '@/mock/db';
import { PresenceNotice } from '@/components/PresenceNotice';
import { addChange, loginAs, renderApp } from './helpers';

const KEY = 'com.demo.order.OrderController.create';
const uid = (name: string) => db.users.find((u) => u.username === name)!.id;
const mineRows = (name: string) => db.presence.filter((p) => p.userId === uid(name));

describe('PresenceNotice(编辑中提示)', () => {
  it('显示"别人"正在编辑这条规则,不含自己', async () => {
    loginAs('zhangsan', 'multi'); // 种子:李四(常驻)在开发环境编辑 create 规则
    render(<PresenceNotice env="dev" service="order-service" ruleKey={KEY} heartbeat intervalMs={60_000} />);
    expect(await screen.findByTestId('presence-notice')).toHaveTextContent('李四 正在编辑这条规则');
  });

  it('别的环境 / 别的规则不提示', async () => {
    loginAs('zhangsan', 'multi');
    render(<PresenceNotice env="test" service="order-service" ruleKey={KEY} heartbeat={false} intervalMs={60_000} />);
    await waitFor(() => expect(screen.queryByTestId('presence-notice')).toBeNull());
  });

  it('heartbeat:打开后立即 PUT,之后按间隔续期,卸载时 DELETE', async () => {
    loginAs('zhangsan', 'multi');
    const { unmount } = render(<PresenceNotice env="dev" service="order-service" ruleKey={KEY} heartbeat intervalMs={80} />);
    await waitFor(() => expect(mineRows('zhangsan')).toHaveLength(1));
    const first = mineRows('zhangsan')[0].lastSeen;
    await waitFor(() => expect(mineRows('zhangsan')[0].lastSeen).toBeGreaterThan(first)); // 续期
    expect(mineRows('zhangsan')[0].since).toBeLessThanOrEqual(first); // since 不变
    unmount();
    await waitFor(() => expect(mineRows('zhangsan')).toHaveLength(0));
  });

  it('只读用户不发心跳,只看提示', async () => {
    loginAs('wangwu', 'multi');
    render(<PresenceNotice env="dev" service="order-service" ruleKey={KEY} heartbeat={false} intervalMs={60_000} />);
    expect(await screen.findByTestId('presence-notice')).toHaveTextContent('李四');
    expect(mineRows('wangwu')).toHaveLength(0);
  });

  it('90 秒没续上视为离开(后端语义,mock 对照)', () => {
    loginAs('zhangsan', 'multi');
    db.presence.push({ env: 'dev', service: 'order-service', ruleKey: 'k.old', userId: uid('wangwu'), since: 0, lastSeen: Date.now() - PRESENCE_TTL_MS - 1000 });
    expect(livePresence().some((p) => p.ruleKey === 'k.old')).toBe(false);
  });
});

describe('规则编辑页:编辑中提示与未完成修改单', () => {
  it('新建修改单带着规则进来:显示李四正在编辑 + 1 张未完成的修改单(可点开),并上报自己的心跳', async () => {
    loginAs('zhangsan', 'multi');
    renderApp(`/changes/new?service=order-service&key=${encodeURIComponent(KEY)}&env=dev`);
    const user = userEvent.setup();
    expect(await screen.findByTestId('presence-notice')).toHaveTextContent('李四 正在编辑这条规则');
    const open = await screen.findByTestId('open-changes-notice');
    expect(open).toHaveTextContent('有 1 张未完成的修改单涉及这条规则');
    expect(within(open).queryByTestId('open-changes-list')).toBeNull();
    await user.click(within(open).getByText('点开查看'));
    expect(await screen.findByTestId('open-changes-list')).toHaveTextContent('放宽订单金额上限');
    // 自己的心跳(规则 Key 稳定 600ms 后才上报)
    await waitFor(() => expect(mineRows('zhangsan').map((p) => p.ruleKey)).toContain(KEY));
    expect(mineRows('zhangsan')[0].env).toBe('dev');
  });

  it('规则查看页:同样给出提示,但不发心跳', async () => {
    loginAs('wangwu', 'multi');
    renderApp(`/rules/order-service?env=dev&key=${encodeURIComponent(KEY)}`);
    expect(await screen.findByTestId('presence-notice')).toHaveTextContent('李四');
    expect(await screen.findByTestId('open-changes-notice')).toHaveTextContent('有 1 张未完成的修改单');
    expect(mineRows('wangwu')).toHaveLength(0);
  });

  it('编辑自己的草稿时,不把这张草稿自己算进"未完成的修改单"', async () => {
    loginAs('zhangsan', 'multi');
    const d = addChange({ author: 'zhangsan', status: 'DRAFT', env: 'dev', title: '我的草稿' });
    renderApp(`/changes/${d.id}/edit`);
    const open = await screen.findByTestId('open-changes-notice'); // 只剩种子里李四那一张
    expect(open).toHaveTextContent('有 1 张');
  });
});

describe('修改单详情:overlaps', () => {
  it('同环境、同规则上其它未完成的修改单会列出来;不同环境的不算', async () => {
    loginAs('zhangsan', 'multi');
    const mine = addChange({ author: 'zhangsan', status: 'DRAFT', env: 'dev', title: '我也想改金额校验' });
    addChange({ author: 'lisi', status: 'PENDING', env: 'test', title: '测试环境上的同一条规则' });
    renderApp(`/changes/${mine.id}`);
    const box = await screen.findByTestId('overlaps');
    expect(box).toHaveTextContent('另有 1 张未完成的修改单涉及这条规则');
    expect(box).toHaveTextContent('放宽订单金额上限');
    expect(box).not.toHaveTextContent('测试环境上的同一条规则');
  });
});
