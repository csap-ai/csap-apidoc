import { Navigate, Route, Routes } from 'react-router-dom';
import { Result } from 'antd';
import { AuthProvider } from '@/auth/AuthContext';
import RequireAuth from '@/auth/RequireAuth';
import AppLayout from '@/layout/AppLayout';
import Setup from '@/pages/Setup';
import Login from '@/pages/Login';
import Password from '@/pages/Password';
import DocsHome from '@/pages/docs/DocsHome';
import DocView from '@/pages/docs/DocView';
import Rules from '@/pages/rules/Rules';
import ChangeList from '@/pages/changes/ChangeList';
import ChangeForm from '@/pages/changes/ChangeForm';
import ChangeDetail from '@/pages/changes/ChangeDetail';
import Audit from '@/pages/audit/Audit';
import Admin from '@/pages/admin/Admin';
import { t } from '@/i18n';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/setup" element={<Setup />} />
        <Route path="/login" element={<Login />} />
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/" element={<Navigate to="/docs" replace />} />
          <Route path="/docs" element={<DocsHome />} />
          <Route path="/docs/:service" element={<DocView />} />
          <Route path="/rules" element={<Rules />} />
          <Route path="/rules/:service" element={<Rules />} />
          <Route path="/changes" element={<ChangeList />} />
          <Route path="/changes/new" element={<ChangeForm />} />
          <Route path="/changes/:id" element={<ChangeDetail />} />
          <Route path="/changes/:id/edit" element={<ChangeForm />} />
          <Route path="/audit" element={<Audit />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="/admin/:tab" element={<Admin />} />
          <Route path="/password" element={<Password />} />
          <Route path="*" element={<Result status="404" title={t('notfound.title')} />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
