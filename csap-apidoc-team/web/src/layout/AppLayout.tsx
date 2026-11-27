import { Suspense, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Spin, Button, Dropdown, Layout, Menu, Space, Tooltip } from 'antd';
import { AuditOutlined, BulbFilled, BulbOutlined, FileTextOutlined, FormOutlined, SettingOutlined, UserOutlined, ApiOutlined, SwapOutlined } from '@ant-design/icons';
import { useAuth } from '@/auth/AuthContext';
import { canManageProject, canViewAudit } from '@/auth/permissions';
import { useThemeMode } from '@/theme';
import { EnvProvider, useEnv } from '@/env/EnvContext';
import EnvSwitcher, { EnvBanner } from '@/components/EnvSwitcher';
import { t } from '@/i18n';

const { Header, Sider, Content } = Layout;

export default function AppLayout() {
  return (
    <EnvProvider>
      <Shell />
    </EnvProvider>
  );
}

function Shell() {
  const { me, logout } = useAuth();
  const { multi, loading: envLoading } = useEnv();
  const { mode, toggle } = useThemeMode();
  const loc = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const items = [
    { key: '/docs', icon: <ApiOutlined />, label: <Link to="/docs">{t('nav.docs')}</Link> },
    { key: '/rules', icon: <FileTextOutlined />, label: <Link to="/rules">{t('nav.rules')}</Link> },
    { key: '/changes', icon: <FormOutlined />, label: <Link to="/changes">{t('nav.changes')}</Link> },
    ...(multi ? [{ key: '/envs', icon: <SwapOutlined />, label: <Link to="/envs">{t('nav.envs')}</Link> }] : []),
    ...(canViewAudit(me) ? [{ key: '/audit', icon: <AuditOutlined />, label: <Link to="/audit">{t('nav.audit')}</Link> }] : []),
    ...(canManageProject(me) ? [{ key: '/admin', icon: <SettingOutlined />, label: <Link to="/admin">{t('nav.admin')}</Link> }] : []),
  ];
  const selected = '/' + (loc.pathname.split('/')[1] || 'docs');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} breakpoint="lg" width={208}>
        <div style={{ height: 56, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 600, fontSize: collapsed ? 14 : 15, whiteSpace: 'nowrap', overflow: 'hidden' }}>
          {collapsed ? 'CSAP' : t('app.name')}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selected]} items={items} />
      </Sider>
      <Layout>
        <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', padding: '0 20px', background: 'var(--hdr-bg, transparent)', borderBottom: '1px solid rgba(128,128,128,0.2)' }}>
          <Space size="middle">
            <EnvSwitcher />
            <Tooltip title={mode === 'dark' ? t('common.themeLight') : t('common.themeDark')}>
              <Button type="text" aria-label="theme" icon={mode === 'dark' ? <BulbFilled /> : <BulbOutlined />} onClick={toggle} />
            </Tooltip>
            <Dropdown
              menu={{
                items: [
                  { key: 'pwd', label: t('nav.password'), onClick: () => navigate('/password') },
                  { key: 'out', label: t('nav.logout'), onClick: () => void logout() },
                ],
              }}
            >
              <Button type="text" icon={<UserOutlined />} data-testid="user-menu">
                {me?.displayName} · {me?.orgRole === 'ADMIN' ? t('role.ADMIN') : t('role.MEMBER')}
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <EnvBanner />
          <Suspense fallback={<Spin style={{ display: 'block', margin: '20vh auto' }} />}>
            {/* 环境列表加载完再渲染页面,避免先按错误的环境发一轮请求 */}
            {envLoading ? <Spin style={{ display: 'block', margin: '20vh auto' }} /> : <Outlet />}
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
}
