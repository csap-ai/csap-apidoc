import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mock/db';
import { addChange, loginAs, renderApp } from './helpers';

/** 种子里那张开发环境的待审批单(李四提交),带两条评论:张三一条(关联规则)、李四一条 */
const seeded = () => db.changes.find((c) => c.title === '放宽订单金额上限')!;
const commentsOf = (id: number) => db.comments.filter((c) => c.changeId === id);
const byAuthor = (id: number, name: string) => {
  const uid = db.users.find((u) => u.displayName === name)!.id;
  return commentsOf(id).find((c) => c.authorId === uid)!;
};

describe('修改单评论', () => {
  it('列表按时间显示,关联到规则的评论带规则标签;评论数进标题', async () => {
    loginAs('zhangsan', 'multi');
    const c = seeded();
    renderApp(`/changes/${c.id}`);
    const box = await screen.findByTestId('comments');
    expect(await within(box).findByText('上限 50 万会不会太大?建议先和风控确认。')).toBeInTheDocument();
    expect(within(box).getByText('已经和风控确认过,活动期间可以放宽。')).toBeInTheDocument();
    expect(within(box).getByText(/order-service · OrderController\.create/)).toBeInTheDocument();
    expect(within(box).getByText('评论(2)')).toBeInTheDocument();
  });

  it('开发 / 维护者可以发表评论,内容首尾空白会被去掉;发表后条数增加', async () => {
    loginAs('lisi', 'multi');
    const c = seeded();
    renderApp(`/changes/${c.id}`);
    const user = userEvent.setup();
    const box = await screen.findByTestId('comments');
    await user.type(within(box).getByLabelText('写下你的意见(最多 4000 字)'), '  同意,上线前通知下游  ');
    await user.click(within(box).getByRole('button', { name: /发表评论/ }));
    await within(await screen.findByTestId('comments')).findByText('同意,上线前通知下游');
    expect(commentsOf(c.id)).toHaveLength(3);
    expect(commentsOf(c.id)[2].body).toBe('同意,上线前通知下游');
    await screen.findByText('评论(3)');
  });

  it('空内容不能发表', async () => {
    loginAs('lisi', 'multi');
    renderApp(`/changes/${seeded().id}`);
    const user = userEvent.setup();
    const box = await screen.findByTestId('comments');
    await user.click(within(box).getByRole('button', { name: /发表评论/ }));
    await screen.findByText('请先写点内容');
    expect(commentsOf(seeded().id)).toHaveLength(2);
  });

  it('只读成员看得到评论,但没有发表框', async () => {
    loginAs('wangwu', 'multi'); // 订单项目只读
    renderApp(`/changes/${seeded().id}`);
    const box = await screen.findByTestId('comments');
    await within(box).findByText('已经和风控确认过,活动期间可以放宽。');
    expect(within(box).queryByRole('button', { name: /发表评论/ })).toBeNull();
    expect(within(box).getByText('只读成员不能发表评论')).toBeInTheDocument();
  });

  it('只能改、删自己的评论:别人的评论没有操作入口', async () => {
    loginAs('lisi', 'multi'); // 开发,不是维护者
    const c = seeded();
    renderApp(`/changes/${c.id}`);
    const box = await screen.findByTestId('comments');
    const zs = await within(box).findByTestId(`comment-${byAuthor(c.id, '张三').id}`);
    expect(within(zs).queryByText('编辑')).toBeNull();
    expect(within(zs).queryByText('删除')).toBeNull();
    const mine = within(box).getByTestId(`comment-${byAuthor(c.id, '李四').id}`);
    expect(within(mine).getByText('编辑')).toBeInTheDocument();
    expect(within(mine).getByText('删除')).toBeInTheDocument();
  });

  it('修改自己的评论后标"已编辑"', async () => {
    loginAs('lisi', 'multi');
    const c = seeded();
    renderApp(`/changes/${c.id}`);
    const user = userEvent.setup();
    const mine = await screen.findByTestId(`comment-${byAuthor(c.id, '李四').id}`);
    await user.click(within(mine).getByText('编辑'));
    const ta = within(mine).getByLabelText('编辑');
    await user.clear(ta);
    await user.type(ta, '已确认,活动期间放宽');
    await user.click(within(mine).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(byAuthor(c.id, '李四').body).toBe('已确认,活动期间放宽'));
    await within(await screen.findByTestId(`comment-${byAuthor(c.id, '李四').id}`)).findByText('已编辑');
  });

  it('项目维护者可以删别人的评论:软删除,仍占位,内容不再显示', async () => {
    loginAs('zhangsan', 'multi'); // 订单项目维护者
    const c = seeded();
    renderApp(`/changes/${c.id}`);
    const user = userEvent.setup();
    const target = byAuthor(c.id, '李四');
    const row = await screen.findByTestId(`comment-${target.id}`);
    await user.click(within(row).getByText('删除'));
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }));
    await waitFor(() => expect(db.comments.find((x) => x.id === target.id)!.deleted).toBe(true));
    const after = await screen.findByTestId(`comment-${target.id}`);
    expect(within(after).getByText('该评论已删除')).toBeInTheDocument();
    expect(screen.queryByText('已经和风控确认过,活动期间可以放宽。')).toBeNull();
    await screen.findByText('评论(1)'); // 评论数只算未删除的
  });

  it('已发布的修改单也能评论(事后讨论);创建评论触发 change.commented 事件', async () => {
    loginAs('zhangsan', 'multi');
    const c = addChange({ author: 'lisi', status: 'DRAFT', env: 'dev' });
    (c as { status: string }).status = 'PUBLISHED';
    db.webhooks.push({ id: ++db.seq, projectId: c.projectId, name: '评论通知', kind: 'GENERIC', url: 'https://x.example/hook', hasSecret: false, events: ['change.commented'], enabled: true, environments: ['dev'] });
    const before = db.deliveries.filter((d) => d.event === 'change.commented').length;
    renderApp(`/changes/${c.id}`);
    const user = userEvent.setup();
    const box = await screen.findByTestId('comments');
    await user.type(within(box).getByLabelText('写下你的意见(最多 4000 字)'), '上线后观察一天');
    await user.click(within(box).getByRole('button', { name: /发表评论/ }));
    await waitFor(() => expect(db.deliveries.filter((d) => d.event === 'change.commented')).toHaveLength(before + 1));
  });
});
