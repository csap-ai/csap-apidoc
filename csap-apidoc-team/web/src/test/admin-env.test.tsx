import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '@/mock/db';
import { loginAs, pickOption, renderApp } from './helpers';

describe('管理:服务归属按环境', () => {
  it('多环境:列出各环境状态;只移除当前环境的地址,服务本身和其它环境保留', async () => {
    loginAs('admin', 'multi');
    renderApp('/admin/services?env=dev');
    const user = userEvent.setup();
    const envs = await screen.findByTestId('svc-envs-order-service');
    expect(envs).toHaveTextContent('开发 · 在线');
    const row = screen.getByText('gateway-go').closest('tr')!;
    await user.click(within(row).getByText('移除此环境'));
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }));
    await waitFor(() => expect(db.svcEnvs.some((x) => x.service === 'gateway-go' && x.env === 'dev')).toBe(false));
    expect(db.services.some((s) => s.service === 'gateway-go')).toBe(true); // 服务本身还在
    expect(db.svcEnvs.some((x) => x.service === 'order-service' && x.env === 'prod')).toBe(true);
  });

  it('手工添加服务可选环境;同一服务在另一个环境再加一次 = 给该环境加地址', async () => {
    loginAs('admin', 'multi');
    renderApp('/admin/services?env=dev');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /手工添加服务/ }));
    const dlg = await screen.findByRole('dialog');
    await user.type(within(dlg).getByLabelText('服务'), 'user-service');
    await user.type(within(dlg).getByLabelText('服务地址 baseUrl'), 'http://10.9.0.1:8080');
    await pickOption(user, within(dlg).getByRole('combobox', { name: '环境' }), '生产');
    await user.click(within(dlg).getByRole('button', { name: /新\s*建/ }));
    await waitFor(() => expect(db.svcEnvs.some((x) => x.service === 'user-service' && x.env === 'prod' && x.baseUrl === 'http://10.9.0.1:8080')).toBe(true));
    expect(db.services.filter((s) => s.service === 'user-service')).toHaveLength(1);
  });

  it('单环境:没有环境列,手工添加没有环境选项,删除是整行删除', async () => {
    loginAs('admin', 'single');
    renderApp('/admin/services');
    const user = userEvent.setup();
    await screen.findByText('gateway-go');
    expect(screen.queryByRole('columnheader', { name: '各环境' })).toBeNull();
    await user.click(screen.getByRole('button', { name: /手工添加服务/ }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).queryByRole('combobox', { name: '环境' })).toBeNull();
    expect(screen.queryByText('移除此环境')).toBeNull();
  });
});

describe('管理:令牌绑定环境', () => {
  it('创建 REGISTER 令牌可选绑定环境;列表显示绑定的环境,未绑定 = 默认环境', async () => {
    loginAs('admin', 'multi');
    renderApp('/admin/tokens?env=dev');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /新建令牌/ }, { timeout: 10000 }));
    const dlg = await screen.findByRole('dialog');
    await user.type(within(dlg).getByLabelText('令牌名称'), '测试环境注册');
    await pickOption(user, await within(dlg).findByRole('combobox', { name: '绑定环境' }), '测试');
    await user.click(within(dlg).getByRole('button', { name: /新\s*建$/ }));
    await screen.findByTestId('token-plain');
    expect(db.tokens[db.tokens.length - 1]).toMatchObject({ name: '测试环境注册', scope: 'REGISTER', env: 'test' });
    await user.click(screen.getByRole('button', { name: '我已保存,关闭' }));
    const row = (await screen.findByText('测试环境注册')).closest('tr')!;
    expect(within(row).getByText('测试')).toBeInTheDocument();
  });

  it('单环境:创建令牌没有环境选项', async () => {
    loginAs('admin', 'single');
    renderApp('/admin/tokens');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /新建令牌/ }, { timeout: 10000 }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).queryByLabelText('绑定环境')).toBeNull();
  });
});

describe('管理:Webhook 订阅环境与新事件', () => {
  it('列表显示订阅的环境;新建时可选环境,事件里有 change.commented / audit.checkpoint', async () => {
    loginAs('admin', 'multi');
    renderApp('/admin/webhooks?env=dev');
    const user = userEvent.setup();
    const row = (await screen.findByText('订单群通知')).closest('tr')!;
    expect(within(row).getByText('生产')).toBeInTheDocument(); // 种子:只订阅生产
    await user.click(screen.getByRole('button', { name: /新建 Webhook/ }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).getByText('订阅环境')).toBeInTheDocument();
    const events = within(dlg).getAllByRole('combobox').find((el) => el.closest('.ant-form-item')?.textContent?.includes('订阅事件'))!;
    await user.click(events);
    expect(await screen.findAllByText('修改单新评论')).not.toHaveLength(0);
    expect(await screen.findAllByText('审计链头检查点')).not.toHaveLength(0);
  });

  it('单环境:Webhook 没有订阅环境', async () => {
    loginAs('admin', 'single');
    renderApp('/admin/webhooks');
    const user = userEvent.setup();
    await screen.findByText('订单群通知');
    await user.click(screen.getByRole('button', { name: /新建 Webhook/ }));
    const dlg = await screen.findByRole('dialog');
    expect(within(dlg).queryByText('订阅环境')).toBeNull();
  });
});
