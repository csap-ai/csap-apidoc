import { useNavigate, useParams } from 'react-router-dom';
import { Tabs } from 'antd';
import { useAuth } from '@/auth/AuthContext';
import { canManageProject, isAdmin } from '@/auth/permissions';
import { Forbidden, PageTitle } from '@/components/common';
import { t } from '@/i18n';
import ProjectsTab from './ProjectsTab';
import ServicesTab from './ServicesTab';
import UsersTab from './UsersTab';
import TokensTab from './TokensTab';
import WebhooksTab from './WebhooksTab';

export default function Admin() {
  const { me } = useAuth();
  const { tab } = useParams();
  const navigate = useNavigate();
  if (!canManageProject(me)) return <Forbidden />;
  const admin = isAdmin(me);
  const items = [
    { key: 'projects', label: t('admin.tab.projects'), children: <ProjectsTab /> },
    { key: 'services', label: t('admin.tab.services'), children: <ServicesTab /> },
    { key: 'webhooks', label: t('admin.tab.webhooks'), children: <WebhooksTab /> },
    ...(admin ? [
      { key: 'users', label: t('admin.tab.users'), children: <UsersTab /> },
      { key: 'tokens', label: t('admin.tab.tokens'), children: <TokensTab /> },
    ] : []),
  ];
  const active = items.some((i) => i.key === tab) ? tab! : 'projects';
  return (
    <>
      <PageTitle title={t('admin.title')} />
      <Tabs activeKey={active} onChange={(k) => navigate(`/admin/${k}`)} items={items} destroyInactiveTabPane />
    </>
  );
}
