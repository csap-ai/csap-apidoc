import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mock/db';
import { promotableItems } from '@/pages/envs/PromoteModal';
import { loginAs, pickOption, renderApp } from './helpers';

const KEY = 'com.demo.order.OrderController.';

describe('promotableItems', () => {
  const rows = [
    { service: 's', ruleKey: 'a', status: 'DIFFERENT' as const },
    { service: 's', ruleKey: 'b', status: 'ONLY_FROM' as const },
    { service: 's', ruleKey: 'c', status: 'ONLY_TO' as const },
  ];
  it('「仅目标有」只有勾了"包含删除"才进推广单', () => {
    expect(promotableItems(rows, false).map((r) => r.ruleKey)).toEqual(['a', 'b']);
    expect(promotableItems(rows, true).map((r) => r.ruleKey)).toEqual(['a', 'b', 'c']);
  });
});

async function openDiff(user: ReturnType<typeof userEvent.setup>, project = '订单') {
  await pickOption(user, await screen.findByRole('combobox', { name: '项目' }), project);
  await user.click(screen.getByRole('button', { name: /对\s*比/ }));
}

describe('环境对比页', () => {
  it('默认方向是当前环境 → 下一个环境;按服务分组列出差异并给出状态标签', async () => {
    loginAs('admin', 'multi');
    renderApp('/envs?env=dev');
    const user = userEvent.setup();
    await openDiff(user);
    const card = await screen.findByTestId('envdiff-order-service');
    expect(within(card).getByText(KEY + 'create')).toBeInTheDocument();
    expect(within(card).getByText('内容不同')).toBeInTheDocument();
    expect(within(card).getByText('仅来源有')).toBeInTheDocument(); // refund 只在开发有
    expect(within(card).getByText('仅目标有')).toBeInTheDocument(); // export 只在测试有
    expect(within(card).getByText('推广后将在目标环境删除')).toBeInTheDocument();
    // 默认勾上"内容不同"和"仅来源有"
    expect(screen.getByText('已选 2 条规则')).toBeInTheDocument();
    expect(within(card).queryByText('v3')).not.toBeNull(); // 来源 dev 的版本
  });

  it('点开看差异(目标 → 来源)', async () => {
    loginAs('admin', 'multi');
    renderApp('/envs?env=dev');
    const user = userEvent.setup();
    await openDiff(user);
    const card = await screen.findByTestId('envdiff-order-service');
    const row = within(card).getByText(KEY + 'create').closest('tr')!;
    await user.click(within(row).getByText('查看差异'));
    const detail = await screen.findByTestId('envdiff-detail');
    expect(detail).toHaveTextContent('"max": 100000'); // 开发(来源)多出来的上限
  });

  it('没有差异时给出提示', async () => {
    loginAs('admin', 'multi');
    renderApp('/envs?env=dev');
    const user = userEvent.setup();
    await openDiff(user, '用户中心');
    await screen.findByTestId('envdiff-none');
    expect(screen.getByText('两个环境没有差异')).toBeInTheDocument();
  });

  it('推广弹窗:默认标题、勾选包含删除会带上"仅目标有"的规则,创建推广单(不提交)', async () => {
    loginAs('admin', 'multi');
    renderApp('/envs?env=dev');
    const user = userEvent.setup();
    await openDiff(user);
    const card = await screen.findByTestId('envdiff-order-service');
    // 再勾上"仅目标有"那条(export)
    const exportRow = within(card).getByText(KEY + 'export').closest('tr')!;
    await user.click(within(exportRow).getByRole('checkbox'));
    await screen.findByText('已选 3 条规则');
    await user.click(screen.getByRole('button', { name: /推广所选\(3\)/ }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).getByLabelText('修改单标题')).toHaveValue('推广 订单:开发 → 测试');
    // 没勾"包含删除":删除项被忽略
    expect(within(dlg).getByTestId('promote-summary')).toHaveTextContent('将把 2 条规则从「开发」推广到「测试」(其中删除 0 条)');
    await user.click(within(dlg).getByRole('checkbox', { name: '包含删除' }));
    expect(within(dlg).getByTestId('promote-summary')).toHaveTextContent('将把 3 条规则从「开发」推广到「测试」(其中删除 1 条)');
    await user.type(within(dlg).getByLabelText('推广原因'), '测试环境验证通过');
    await user.click(within(dlg).getByRole('button', { name: /推广所选\(3\)/ }));

    await waitFor(() => expect(screen.getByTestId('loc')).toHaveAttribute('data-path', expect.stringMatching(/^\/changes\/\d+$/)));
    const c = db.changes[0];
    expect(c.kind).toBe('PROMOTE');
    expect(c.env).toBe('test');
    expect(c.sourceEnv).toBe('dev');
    expect(c.status).toBe('DRAFT');
    expect(c.items).toHaveLength(3);
    expect(c.items.find((i) => i.ruleKey === KEY + 'export')!.afterRules).toBeNull(); // 推广后在目标删除
    expect(c.items.find((i) => i.ruleKey === KEY + 'refund')!.baseVersion).toBe(0); // 目标环境没有 → 0
    expect(c.reason).toBe('测试环境验证通过');
    // 详情页显示目标环境与推广来源
    await screen.findByText('推广来源');
  });

  it('一键推广到受保护的环境(生产):创建后立即提交进入待审批,详情页显示"需要审批"', async () => {
    loginAs('admin', 'multi');
    renderApp('/envs?env=test');
    const user = userEvent.setup();
    await openDiff(user);
    await screen.findByTestId('envdiff-order-service');
    await user.click(screen.getByRole('button', { name: /推广所选/ }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).getByText('目标环境受保护,提交后需要审批。')).toBeInTheDocument();
    await user.click(within(dlg).getByRole('checkbox', { name: '创建后立即提交' }));
    await user.click(within(dlg).getByRole('button', { name: /推广所选/ }));
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveAttribute('data-path', expect.stringMatching(/^\/changes\/\d+$/)));
    expect(db.changes[0].env).toBe('prod');
    expect(db.changes[0].status).toBe('PENDING');
    await waitFor(() => expect(screen.getByTestId('change-status')).toHaveTextContent('待审批'));
    expect(screen.getByText('需要审批')).toBeInTheDocument();
  });

  it('目标环境只读:推广按钮被禁用', async () => {
    loginAs('admin', 'multi');
    db.envs.find((e) => e.key === 'test')!.readOnly = true;
    renderApp('/envs?env=dev');
    const user = userEvent.setup();
    await openDiff(user);
    await screen.findByTestId('envdiff-order-service');
    expect(screen.getByRole('button', { name: /推广所选/ })).toBeDisabled();
  });

  it('只读成员没有向目标项目建修改单的权限:推广按钮被禁用', async () => {
    loginAs('wangwu', 'multi'); // 订单项目的只读成员
    renderApp('/envs?env=dev');
    const user = userEvent.setup();
    await openDiff(user);
    await screen.findByTestId('envdiff-order-service');
    expect(screen.getByRole('button', { name: /推广所选/ })).toBeDisabled();
  });
});
