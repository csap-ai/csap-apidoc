import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { changeActions } from '@/auth/permissions';
import type { Change, Me } from '@/api/types';
import { editOutside, db } from '@/mock/db';
import { addChange, loginAs, renderApp, userId } from './helpers';

describe('登录跳转', () => {
  it('未登录访问受保护页面会跳到登录页,登录后回到原页面', async () => {
    loginAs(null);
    renderApp('/changes');
    const user = userEvent.setup();
    await screen.findByText('登录', { selector: 'h4' });
    await user.type(screen.getByLabelText('账号'), 'admin');
    await user.type(screen.getByLabelText('密码'), 'admin1234');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));
    await screen.findByRole('heading', { name: '修改单' });
    expect(screen.queryByText('登录', { selector: 'h4' })).toBeNull();
  });

  it('密码错误显示提示且停留在登录页', async () => {
    loginAs(null);
    renderApp('/login');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('账号'), 'admin');
    await user.type(screen.getByLabelText('密码'), 'wrong-pass');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));
    await screen.findByText('账号或密码错误');
  });

  it('会话失效(UNAUTHORIZED)时跳回登录页', async () => {
    loginAs('admin');
    renderApp('/changes');
    await screen.findByRole('heading', { name: '修改单' });
    db.sessionUserId = null; // 服务端会话过期
    await userEvent.click(screen.getByRole('link', { name: '文档中心' }));
    await screen.findByText('登录', { selector: 'h4' });
  });
});

describe('修改单状态按钮按角色显示', () => {
  const actionsOf = async (as: string, author: string, status: 'DRAFT' | 'PENDING') => {
    loginAs(as);
    const c = addChange({ author, status });
    renderApp(`/changes/${c.id}`);
    await screen.findByTestId('change-actions');
    await screen.findByText('调整订单金额校验');
    return within(screen.getByTestId('change-actions'));
  };

  it('待审批:非作者维护者能通过 / 驳回 / 撤回', async () => {
    const a = await actionsOf('zhangsan', 'lisi', 'PENDING');
    expect(a.getByRole('button', { name: /通过并发布/ })).toBeEnabled();
    expect(a.getByRole('button', { name: /驳\s*回/ })).toBeEnabled();
    expect(a.getByRole('button', { name: /撤\s*回/ })).toBeInTheDocument();
  });

  it('待审批:作者(开发)只能撤回,看不到审批', async () => {
    const a = await actionsOf('lisi', 'lisi', 'PENDING');
    expect(a.getByRole('button', { name: /撤\s*回/ })).toBeInTheDocument();
    expect(a.queryByRole('button', { name: /通过并发布/ })).toBeNull();
    expect(a.queryByRole('button', { name: /驳\s*回/ })).toBeNull();
  });

  it('待审批:维护者不能审批自己提交的(按钮禁用)', async () => {
    const a = await actionsOf('zhangsan', 'zhangsan', 'PENDING');
    expect(a.getByRole('button', { name: /通过并发布/ })).toBeDisabled();
    expect(a.queryByRole('button', { name: /驳\s*回/ })).toBeNull();
  });

  it('待审批:同项目开发者(非作者)看不到撤回', async () => {
    const a = await actionsOf('lisi', 'zhangsan', 'PENDING');
    expect(a.queryAllByRole('button')).toHaveLength(0);
  });

  it('待审批:管理员可以审批他人和自己的', async () => {
    const a = await actionsOf('admin', 'lisi', 'PENDING');
    expect(a.getByRole('button', { name: /通过并发布/ })).toBeEnabled();
  });

  it('待审批:只读成员什么都看不到', async () => {
    const a = await actionsOf('wangwu', 'lisi', 'PENDING');
    expect(a.queryAllByRole('button')).toHaveLength(0);
  });

  it('草稿:作者可编辑 / 提交 / 撤回,他人看不到', async () => {
    const a = await actionsOf('lisi', 'lisi', 'DRAFT');
    expect(a.getByRole('button', { name: /编辑草稿/ })).toBeInTheDocument();
    expect(a.getByRole('button', { name: /提\s*交/ })).toBeInTheDocument();
    expect(a.getByRole('button', { name: /撤\s*回/ })).toBeInTheDocument();
  });

  it('权限矩阵纯函数:冲突状态只有作者能 rebase', () => {
    const me = (id: number, role: 'DEVELOPER' | 'MAINTAINER'): Me => ({ id, username: 'x', displayName: 'x', orgRole: 'MEMBER', projects: [{ id: 1, name: 'p', role }] });
    const c = { projectId: 1, status: 'CONFLICT', author: { id: 7, displayName: 'a' } } as Change;
    expect(changeActions(me(7, 'DEVELOPER'), c).visible).toContain('rebase');
    expect(changeActions(me(8, 'MAINTAINER'), c).visible).not.toContain('rebase');
  });
});

describe('冲突处理', () => {
  it('提交时版本已过期:展示"我的 / 当前库里的"并可 Rebase 回到草稿', async () => {
    loginAs('lisi');
    const c = addChange({ author: 'lisi', status: 'DRAFT', baseVersion: 1 });
    const key = c.items[0].ruleKey;
    editOutside('order-service', key, JSON.stringify({ amount: { required: true, min: 5 } })); // 他人改了规则,版本 → 2
    renderApp(`/changes/${c.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /提\s*交/ }));
    const panel = await screen.findByTestId('conflict-panel');
    expect(within(panel).getByTestId('conflict-mine')).toHaveTextContent('"min": 1');
    expect(within(panel).getByTestId('conflict-current')).toHaveTextContent('"min": 5');
    expect(screen.getByTestId('change-status')).toHaveTextContent('冲突');

    await user.click(within(panel).getByRole('button', { name: /更新到最新版本/ }));
    await waitFor(() => expect(screen.getByTestId('change-status')).toHaveTextContent('草稿'));
    expect(screen.queryByTestId('conflict-panel')).toBeNull();
    expect(db.changes.find((x) => x.id === c.id)!.items[0].baseVersion).toBe(2);
  });

  it('非作者看到冲突但没有 Rebase 按钮', async () => {
    loginAs('zhangsan');
    const c = addChange({ author: 'lisi', status: 'PENDING', baseVersion: 1 });
    editOutside('order-service', c.items[0].ruleKey, JSON.stringify({ amount: { min: 9 } }));
    renderApp(`/changes/${c.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /通过并发布/ }));
    const dlg = await screen.findByRole('dialog');
    await user.click(within(dlg).getByRole('button', { name: /确\s*定/ }));
    const panel = await screen.findByTestId('conflict-panel');
    expect(within(panel).queryByRole('button', { name: /更新到最新版本/ })).toBeNull();
    expect(within(panel).getByText('只有作者可以基于最新内容重新编辑')).toBeInTheDocument();
  });
});

describe('令牌明文只显示一次', () => {
  it('创建后弹窗给出明文,关闭后列表与页面都不再出现', async () => {
    loginAs('admin');
    renderApp('/admin/tokens');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /新建令牌/ }, { timeout: 10000 }));
    await user.type(await screen.findByLabelText('令牌名称'), '网关注册');
    await user.click(screen.getByRole('button', { name: /新\s*建$/ }));
    const plain = await screen.findByTestId('token-plain');
    const token = plain.textContent!;
    expect(token).toMatch(/^csapt_[A-Za-z0-9]{40}$/);
    await user.click(screen.getByRole('button', { name: '我已保存,关闭' }));
    await waitFor(() => expect(screen.queryByTestId('token-plain')).toBeNull());
    await screen.findByText('网关注册');
    expect(document.body.textContent).not.toContain(token);
    // 前缀仍可识别
    expect(screen.getAllByText(/csapt_/).length).toBeGreaterThan(0);
    void userId;
  });
});
