import { lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Result } from 'antd';
import { AuthProvider } from '@/auth/AuthContext';
import RequireAuth from '@/auth/RequireAuth';
import AppLayout from '@/layout/AppLayout';
import Setup from '@/pages/Setup';
import Login from '@/pages/Login';
import { t } from '@/i18n';

const Password = lazy(() => import('@/pages/Password'));
const DocsHome = lazy(() => import('@/pages/docs/DocsHome'));
const DocView = lazy(() => import('@/pages/docs/DocView'));
const Rules = lazy(() => import('@/pages/rules/Rules'));
const ChangeList = lazy(() => import('@/pages/changes/ChangeList'));
const ChangeForm = lazy(() => import('@/pages/changes/ChangeForm'));
const ChangeDetail = lazy(() => import('@/pages/changes/ChangeDetail'));
const Audit = lazy(() => import('@/pages/audit/Audit'));
const Admin = lazy(() => import('@/pages/admin/Admin'));

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
