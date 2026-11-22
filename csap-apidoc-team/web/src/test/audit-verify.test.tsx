import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db, tamperAudit } from '@/mock/db';
import { loginAs, renderApp } from './helpers';

describe('审计:校验哈希链', () => {
  it('管理员点"校验哈希链":链完整时显示通过与链头', async () => {
    loginAs('admin', 'multi');
    renderApp('/audit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /校验哈希链/ }));
    const dlg = await screen.findByRole('dialog');
    await user.click(within(dlg).getByRole('button', { name: /开始校验/ }));
    const res = await within(dlg).findByTestId('verify-result');
    expect(res).toHaveTextContent('哈希链完整,未发现篡改');
    expect(res).toHaveTextContent(`共校验 ${db.audit.length - 1} 条记录`); // 校验本身又记了一条 audit.verify
    expect(res).toHaveTextContent(db.audit[1].hash); // 链头 = 校验前的最后一条
    expect(db.audit[0].action).toBe('audit.verify');
  });

  it('有人绕过团队版改了一条审计:报"哈希链被破坏",指出第一处异常的记录', async () => {
    loginAs('admin', 'multi');
    const target = db.audit.find((a) => a.action === 'change.create')!;
    tamperAudit(target.id, '偷偷改过的原因');
    renderApp('/audit');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /校验哈希链/ }));
    const dlg = await screen.findByRole('dialog');
    await user.click(within(dlg).getByRole('button', { name: /开始校验/ }));
    const res = await within(dlg).findByTestId('verify-result');
    expect(res).toHaveTextContent('哈希链被破坏');
    expect(res).toHaveTextContent(`#${target.id}`);
    expect(res).toHaveTextContent('记录内容与哈希不一致');
  });

  it('可以只校验一段 ID 范围', async () => {
    loginAs('admin', 'multi');
    renderApp('/audit');
    const user = userEvent.setup();
    const last = db.audit[0];
    await user.click(await screen.findByRole('button', { name: /校验哈希链/ }));
    const dlg = await screen.findByRole('dialog');
    await user.type(within(dlg).getByLabelText('起始 ID(可选)'), String(last.id));
    await user.click(within(dlg).getByRole('button', { name: /开始校验/ }));
    expect(await within(dlg).findByTestId('verify-result')).toHaveTextContent('共校验 1 条记录');
  });

  it('非管理员(维护者)看得到审计但没有校验按钮', async () => {
    loginAs('zhangsan', 'multi');
    renderApp('/audit');
    await screen.findByRole('heading', { name: '审计' });
    expect(screen.queryByRole('button', { name: /校验哈希链/ })).toBeNull();
  });
});

describe('审计:环境筛选与列', () => {
  it('多环境:有环境列,默认只看当前环境(与环境无关的动作没有环境,显示 —)', async () => {
    loginAs('admin', 'multi');
    renderApp('/audit?env=dev');
    await screen.findByText('创建修改单');
    expect(screen.getByRole('columnheader', { name: '环境' })).toBeInTheDocument();
    expect(screen.queryByText('初始化演示数据')).toBeNull(); // 种子审计没有环境,被当前环境筛选挡掉
    expect(screen.getAllByText('开发').length).toBeGreaterThan(0);
  });

  it('单环境:没有环境列也没有环境筛选', async () => {
    loginAs('admin', 'single');
    renderApp('/audit');
    await screen.findByText('初始化演示数据');
    expect(screen.queryByRole('columnheader', { name: '环境' })).toBeNull();
  });
});
