import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db, setEnvStatus } from '@/mock/db';
import { resolveEnv, ENV_STORAGE_KEY } from '@/env/EnvContext';
import type { Env } from '@/api/types';
import { loginAs, pickOption, renderApp } from './helpers';

const mk = (key: string, order: number, def = false): Env => ({ key, name: key, order, protected: false, readOnly: false, status: 'UP', error: null, default: def });

describe('resolveEnv:URL 参数 → localStorage → 默认环境', () => {
  const envs = [mk('dev', 0, true), mk('test', 1), mk('prod', 2)];
  it('按优先级取值,只认存在的环境', () => {
    expect(resolveEnv(envs, 'prod', 'test')).toBe('prod');
    expect(resolveEnv(envs, null, 'test')).toBe('test');
    expect(resolveEnv(envs, 'nope', 'test')).toBe('test');
    expect(resolveEnv(envs, 'nope', 'nope2')).toBe('dev');
  });
  it('单环境(或 1.0 后端没有环境)不产生 env', () => {
    expect(resolveEnv([mk('default', 0, true)], 'default', 'default')).toBeUndefined();
    expect(resolveEnv([], null, null)).toBeUndefined();
  });
});

describe('环境切换器', () => {
  it('配置了多个环境:显示切换器,默认环境写进 URL 与 localStorage', async () => {
    loginAs('admin', 'multi');
    renderApp('/rules');
    const sw = await screen.findByTestId('env-switcher');
    expect(within(sw).getByText('开发')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('env=dev'));
    expect(localStorage.getItem(ENV_STORAGE_KEY)).toBe('dev');
  });

  it('切换环境:URL 与 localStorage 同步,生产用红色标签标注', async () => {
    loginAs('admin', 'multi');
    renderApp('/rules');
    const user = userEvent.setup();
    const sw = await screen.findByTestId('env-switcher');
    await pickOption(user, within(sw).getByRole('combobox'), '生产');
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('env=prod'));
    expect(localStorage.getItem(ENV_STORAGE_KEY)).toBe('prod');
    const tag = within(await screen.findByTestId('env-switcher')).getAllByText('生产')[0];
    expect(tag.className).toContain('ant-tag-red');
  });

  it('URL 里的 env 参数优先(分享链接),无效的环境回落到 localStorage', async () => {
    loginAs('admin', 'multi');
    localStorage.setItem(ENV_STORAGE_KEY, 'test');
    renderApp('/rules?env=prod');
    await waitFor(() => expect(localStorage.getItem(ENV_STORAGE_KEY)).toBe('prod'));
    expect(within(screen.getByTestId('env-switcher')).getByText('生产')).toBeInTheDocument();
  });

  it('无效的 env 参数回落到 localStorage 里记的环境', async () => {
    loginAs('admin', 'multi');
    localStorage.setItem(ENV_STORAGE_KEY, 'test');
    renderApp('/rules?env=nope');
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('env=test'));
  });

  it('localStorage 读写抛错(隐私模式)也能正常使用', async () => {
    loginAs('admin', 'multi');
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied'); });
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied'); });
    try {
      renderApp('/rules');
      const sw = await screen.findByTestId('env-switcher');
      expect(within(sw).getByText('开发')).toBeInTheDocument();
      await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('env=dev'));
    } finally {
      get.mockRestore();
      set.mockRestore();
    }
  });

  it('页面按当前环境取数:服务只在开发环境出现过,切到测试就看不到它的文档', async () => {
    loginAs('admin', 'multi');
    renderApp('/docs/gateway-go?env=dev'); // gateway-go 只在开发环境出现过
    await screen.findByText('路由列表');
    // 同一个 SPA 里换环境:用顶栏切换器
    const user = userEvent.setup();
    await pickOption(user, within(screen.getByTestId('env-switcher')).getByRole('combobox'), '测试');
    await screen.findByText('该服务暂无可用文档(服务离线或从未拉取成功)');
  });

  it('文档中心列表显示各环境的状态', async () => {
    loginAs('admin', 'multi');
    renderApp('/docs?env=prod');
    const row = await screen.findByTestId('svc-envs-order-service');
    expect(row).toHaveTextContent('开发 · 在线');
    expect(row).toHaveTextContent('生产 · 在线');
    expect(screen.getByTestId('svc-envs-user-service')).not.toHaveTextContent('生产');
  });

  it('环境 DOWN:切换器标"不可用",页面顶部给出错误说明,文档接口返回 ENV_UNAVAILABLE', async () => {
    loginAs('admin', 'multi');
    setEnvStatus('test', 'DOWN', '连接超时');
    renderApp('/docs/order-service?env=test');
    const banner = await screen.findByTestId('env-down-banner');
    expect(banner).toHaveTextContent('环境「测试」当前不可用:连接超时');
    expect(within(screen.getByTestId('env-switcher')).getByText('不可用')).toBeInTheDocument();
    // 横幅 + 文档区域自己的错误说明(来自 ENV_UNAVAILABLE 的 message)
    await waitFor(() => expect(screen.getAllByText(/环境「测试」当前不可用/, { selector: '.ant-alert-message' }).length).toBeGreaterThanOrEqual(2));
  });

  it('只读环境:顶部提示,规则页的"修改此规则"按钮被禁用', async () => {
    loginAs('admin', 'multi');
    db.envs.find((e) => e.key === 'prod')!.readOnly = true;
    renderApp('/rules/order-service?env=prod&key=com.demo.order.OrderController.create');
    await screen.findByTestId('env-readonly-banner');
    expect(await screen.findByRole('button', { name: '修改此规则' })).toBeDisabled();
  });
});

describe('只有一个环境:界面与 1.0 一致', () => {
  it('没有切换器、没有"环境对比"菜单、URL 不带 env,请求也不带', async () => {
    loginAs('admin', 'single');
    renderApp('/docs');
    await screen.findByRole('heading', { name: '文档中心' });
    expect(screen.queryByTestId('env-switcher')).toBeNull();
    expect(screen.queryByRole('link', { name: '环境对比' })).toBeNull();
    expect(screen.getByTestId('loc')).toHaveTextContent('');
    expect(screen.queryByTestId('svc-envs-order-service')).toBeNull();
    expect(screen.queryByRole('columnheader', { name: '各环境' })).toBeNull();
  });

  it('修改单详情不显示环境', async () => {
    loginAs('admin', 'single');
    const { addChange } = await import('./helpers');
    const c = addChange({ author: 'lisi', status: 'PENDING' });
    renderApp(`/changes/${c.id}`);
    await screen.findByTestId('change-actions');
    expect(screen.queryByText('目标环境')).toBeNull();
  });
});
