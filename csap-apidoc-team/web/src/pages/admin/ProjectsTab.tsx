import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, List, Modal, Popconfirm, Row, Select, Space, Switch, Table, Typography } from 'antd';
import { message } from '@/utils/notify';
import { projectApi } from '@/api/endpoints';
import type { Member, ProjectRole } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { canManageProject, isAdmin } from '@/auth/permissions';
import { GuardedButton } from '@/components/common';
import { useLoad } from '@/utils/useLoad';
import { showError } from '@/utils/errors';
import { t, tDyn } from '@/i18n';

const ROLES: ProjectRole[] = ['MAINTAINER', 'DEVELOPER', 'VIEWER'];

export default function ProjectsTab() {
  const { me, refresh } = useAuth();
  const admin = isAdmin(me);
  const projects = useLoad(() => projectApi.list(), []);
  const [sel, setSel] = useState<number | undefined>();
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();
  useEffect(() => { if (!sel && projects.data?.length) setSel(projects.data[0].id); }, [projects.data, sel]);
  const project = projects.data?.find((p) => p.id === sel);

  const create = async (v: { name: string; description?: string; requireApproval: boolean }) => {
    try {
      const p = await projectApi.create(v);
      setCreating(false); form.resetFields(); await projects.reload(); setSel(p.id); void refresh();
    } catch (e) { showError(e); }
  };

  return (
    <Row gutter={16}>
      <Col xs={24} md={7}>
        <Card size="small" title={t('common.project')} extra={admin && <Button size="small" type="primary" onClick={() => setCreating(true)}>{t('project.new')}</Button>}>
          <List
            loading={projects.loading}
            dataSource={projects.data ?? []}
            locale={{ emptyText: t('project.noProjects') }}
            renderItem={(p) => (
              <List.Item onClick={() => setSel(p.id)} style={{ cursor: 'pointer', background: p.id === sel ? 'rgba(22,119,255,0.12)' : undefined, paddingInline: 8 }}>
                {p.name}
              </List.Item>
            )}
          />
        </Card>
      </Col>
      <Col xs={24} md={17}>
        {project ? <ProjectDetail key={project.id} id={project.id} onChanged={projects.reload} onDeleted={() => { setSel(undefined); void projects.reload(); void refresh(); }} />
          : <Typography.Text type="secondary">{t('project.noneSelected')}</Typography.Text>}
      </Col>
      <Modal open={creating} title={t('project.new')} onCancel={() => setCreating(false)} onOk={() => form.submit()} okText={t('common.create')} cancelText={t('common.cancel')}>
        <Form form={form} layout="vertical" onFinish={create} initialValues={{ requireApproval: true }}>
          <Form.Item name="name" label={t('project.name')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="description" label={t('project.desc')}><Input.TextArea rows={2} /></Form.Item>
          <Form.Item name="requireApproval" label={t('project.requireApproval')} valuePropName="checked" extra={t('project.requireApprovalHint')}><Switch /></Form.Item>
        </Form>
      </Modal>
    </Row>
  );
}

function ProjectDetail({ id, onChanged, onDeleted }: { id: number; onChanged: () => void; onDeleted: () => void }) {
  const { me } = useAuth();
  const admin = isAdmin(me);
  const manage = canManageProject(me, id);
  const project = useLoad(() => projectApi.get(id), [id]);
  const members = useLoad(() => projectApi.members(id), [id]);
  const users = useLoad(() => projectApi.candidates(id), [id], { silent: true, skip: !manage });
  const [addUser, setAddUser] = useState<number | undefined>();
  const [addRole, setAddRole] = useState<ProjectRole>('DEVELOPER');
  const [form] = Form.useForm();
  useEffect(() => { if (project.data) form.setFieldsValue(project.data); }, [project.data, form]);

  const save = async (v: { name: string; description: string; requireApproval: boolean }) => {
    try { await projectApi.update(id, v); message.success(t('common.saved')); void project.reload(); onChanged(); } catch (e) { showError(e); }
  };
  const setRole = async (userId: number, role: ProjectRole) => {
    try { await projectApi.setMember(id, userId, role); void members.reload(); } catch (e) { showError(e); }
  };
  const remove = async (userId: number) => {
    try { await projectApi.removeMember(id, userId); void members.reload(); void users.reload(); } catch (e) { showError(e); }
  };
  const candidates = users.data ?? [];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small" title={t('common.project')}>
        <Form form={form} layout="vertical" onFinish={save} disabled={!manage} style={{ maxWidth: 480 }}>
          <Form.Item name="name" label={t('project.name')} rules={[{ required: true, message: t('common.required') }]}><Input /></Form.Item>
          <Form.Item name="description" label={t('project.desc')}><Input.TextArea rows={2} /></Form.Item>
          <Form.Item name="requireApproval" label={t('project.requireApproval')} valuePropName="checked" extra={t('project.requireApprovalHint')}><Switch /></Form.Item>
          <Space>
            <GuardedButton type="primary" htmlType="submit" allowed={manage}>{t('common.save')}</GuardedButton>
            {admin && (
              <Popconfirm title={t('common.confirmDelete')} description={t('project.deleteHint')} okText={t('common.ok')} cancelText={t('common.cancel')}
                onConfirm={async () => { try { await projectApi.remove(id); onDeleted(); } catch (e) { showError(e); } }}>
                <Button danger>{t('common.delete')}</Button>
              </Popconfirm>
            )}
          </Space>
        </Form>
      </Card>
      <Card size="small" title={t('project.members')}>
        <Table<Member>
          scroll={{ x: 460 }}
          size="small"
          rowKey="userId"
          loading={members.loading}
          pagination={false}
          dataSource={members.data ?? []}
          columns={[
            { title: t('project.user'), render: (_, m) => `${m.displayName ?? ''}(${m.username ?? m.userId})` },
            { title: t('project.role'), dataIndex: 'role', width: 160, render: (r: ProjectRole, m) => (
              <Select size="small" style={{ width: 120 }} value={r} disabled={!manage} onChange={(v) => setRole(m.userId, v)} options={ROLES.map((x) => ({ value: x, label: tDyn('role', x) }))} />
            ) },
            { title: t('common.operation'), width: 100, render: (_, m) => manage && (
              <Popconfirm title={t('common.confirmDelete')} okText={t('common.ok')} cancelText={t('common.cancel')} onConfirm={() => remove(m.userId)}><a>{t('common.delete')}</a></Popconfirm>
            ) },
          ]}
        />
        {manage && (users.error ? (
          <Alert style={{ marginTop: 12 }} type="info" showIcon message="暂时无法选择用户,请联系管理员添加成员" />
        ) : (
          <Space style={{ marginTop: 12 }} wrap>
            <Select showSearch optionFilterProp="label" style={{ width: 220 }} placeholder={t('project.user')} value={addUser} onChange={setAddUser} options={candidates.map((u) => ({ value: u.id, label: `${u.displayName}(${u.username})` }))} />
            <Select style={{ width: 120 }} value={addRole} onChange={setAddRole} options={ROLES.map((x) => ({ value: x, label: tDyn('role', x) }))} />
            <Button type="primary" disabled={!addUser} onClick={async () => { await setRole(addUser!, addRole); setAddUser(undefined); void users.reload(); }}>{t('project.addMember')}</Button>
          </Space>
        ))}
      </Card>
    </Space>
  );
}
