import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Alert, AutoComplete, Button, Card, Checkbox, Form, Input, Select, Space, Typography } from 'antd';
import { message } from '@/utils/notify';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { changeApi, projectApi, ruleApi, serviceApi } from '@/api/endpoints';
import type { ServiceInfo } from '@/api/types';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthContext';
import { canWrite } from '@/auth/permissions';
import { Forbidden, PageTitle } from '@/components/common';
import { jsonError, prettyJson } from '@/utils/format';
import { showError } from '@/utils/errors';
import { useLoad } from '@/utils/useLoad';
import { t } from '@/i18n';

interface ItemState {
  uid: number;
  service?: string;
  ruleKey?: string;
  baseVersion: number;
  text: string;
  del: boolean;
}
let uidSeq = 0;
const blank = (): ItemState => ({ uid: ++uidSeq, baseVersion: 0, text: '{}', del: false });

export default function ChangeForm() {
  const { id } = useParams();
  const editId = id ? Number(id) : undefined;
  const [sp] = useSearchParams();
  const { me } = useAuth();
  const navigate = useNavigate();
  const projects = useLoad(() => projectApi.list(), []);
  const [projectId, setProjectId] = useState<number | undefined>();
  const [title, setTitle] = useState('');
  const [reason, setReason] = useState('');
  const [items, setItems] = useState<ItemState[]>([blank()]);
  const [busy, setBusy] = useState(false);
  const [touched, setTouched] = useState(false);
  const services = useLoad(() => serviceApi.list({ projectId }), [projectId], { skip: !projectId });
  const prefilled = useRef(false);

  // 编辑草稿:载入
  useEffect(() => {
    if (!editId) return;
    changeApi.get(editId).then((c) => {
      setProjectId(c.projectId);
      setTitle(c.title);
      setReason(c.reason);
      setItems(c.items.map((i) => ({ uid: ++uidSeq, service: i.service, ruleKey: i.ruleKey, baseVersion: i.baseVersion, text: prettyJson(i.afterRules), del: i.afterRules == null })));
    }).catch(showError);
  }, [editId]);

  // 从规则页带着服务 + key 进来:预填
  useEffect(() => {
    if (editId || prefilled.current) return;
    const svc = sp.get('service'), key = sp.get('key');
    if (!svc || !projects.data) return;
    prefilled.current = true;
    serviceApi.list().then(async (all) => {
      const s = all.find((x) => x.service === svc);
      if (!s?.projectId) return;
      setProjectId(s.projectId);
      if (key) {
        const cur = await ruleApi.item(svc, key);
        setItems([{ uid: ++uidSeq, service: svc, ruleKey: key, baseVersion: cur.version, text: prettyJson(cur.rules) || '{}', del: false }]);
        setTitle(`调整 ${key.split('.').slice(-1)[0]} 的规则`);
      }
    }).catch(showError);
  }, [editId, sp, projects.data]);

  const writableProjects = (projects.data ?? []).filter((p) => canWrite(me, p.id));
  if (!canWrite(me)) return <Forbidden />;

  const patch = (uid: number, v: Partial<ItemState>) => setItems((xs) => xs.map((x) => (x.uid === uid ? { ...x, ...v } : x)));
  const loadCurrent = async (it: ItemState, service = it.service, key = it.ruleKey) => {
    if (!service || !key) return;
    try {
      const cur = await ruleApi.item(service, key);
      patch(it.uid, { service, ruleKey: key, baseVersion: cur.version, text: prettyJson(cur.rules) || '{}', del: false });
    } catch (e) {
      showError(e);
    }
  };

  const errorOf = (it: ItemState) => (it.del ? null : jsonError(it.text));
  const valid = !!projectId && !!title.trim() && items.length > 0 && items.every((i) => i.service && i.ruleKey && !errorOf(i));

  const save = async (submit: boolean) => {
    setTouched(true);
    if (!valid) return;
    setBusy(true);
    const body = {
      projectId: projectId!, title: title.trim(), reason,
      items: items.map((i) => ({ service: i.service!, ruleKey: i.ruleKey!, baseVersion: i.baseVersion, afterRules: i.del ? null : JSON.stringify(JSON.parse(i.text)) })),
    };
    try {
      const c = editId ? await changeApi.update(editId, body) : await changeApi.create(body);
      if (submit) {
        try {
          await changeApi.submit(c.id);
          message.success(t('changes.submitted'));
        } catch (e) {
          if (!(e instanceof ApiError && e.code === 'CONFLICT')) throw e; // 冲突:到详情页处理
        }
      } else message.success(t('changes.form.saved'));
      navigate(`/changes/${c.id}`);
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  const svcOptions = (services.data ?? []).map((s: ServiceInfo) => ({ value: s.service, label: s.service }));
  const proj = (projects.data ?? []).find((p) => p.id === projectId);

  return (
    <>
      <PageTitle title={editId ? t('changes.action.edit') : t('changes.new')} />
      <Card>
        <Form layout="vertical" requiredMark>
          <Form.Item label={t('changes.form.pickProject')} required validateStatus={touched && !projectId ? 'error' : ''}>
            <Select style={{ maxWidth: 320 }} disabled={!!editId} value={projectId} onChange={(v) => { setProjectId(v); setItems([blank()]); }} options={writableProjects.map((p) => ({ value: p.id, label: p.name }))} />
          </Form.Item>
          {proj && !proj.requireApproval && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t('changes.requireApprovalOff')} />}
          <Form.Item label={t('changes.form.title')} required validateStatus={touched && !title.trim() ? 'error' : ''} help={touched && !title.trim() ? t('common.required') : undefined}>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} />
          </Form.Item>
          <Form.Item label={t('changes.form.reason')}><Input.TextArea rows={2} value={reason} onChange={(e) => setReason(e.target.value)} /></Form.Item>
          <Typography.Title level={5}>{t('changes.form.items')}</Typography.Title>
          {items.map((it) => (
            <RuleItemEditor
              key={it.uid}
              it={it}
              svcOptions={svcOptions}
              disabledService={!projectId}
              error={touched ? errorOf(it) : null}
              onPatch={(v) => patch(it.uid, v)}
              onLoad={(service, key) => loadCurrent(it, service, key)}
              onRemove={items.length > 1 ? () => setItems((xs) => xs.filter((x) => x.uid !== it.uid)) : undefined}
            />
          ))}
          <Button icon={<PlusOutlined />} onClick={() => setItems((xs) => [...xs, blank()])} disabled={!projectId}>{t('changes.form.addItem')}</Button>
          {touched && items.length === 0 && <Alert type="error" message={t('changes.form.noItems')} style={{ marginTop: 8 }} />}
          <div style={{ marginTop: 24 }}>
            <Space>
              <Button loading={busy} onClick={() => save(false)}>{t('common.save')}</Button>
              <Button type="primary" loading={busy} onClick={() => save(true)}>{t('changes.action.submit')}</Button>
              <Button onClick={() => navigate(-1)}>{t('common.cancel')}</Button>
            </Space>
          </div>
        </Form>
      </Card>
    </>
  );
}

function RuleItemEditor({ it, svcOptions, disabledService, error, onPatch, onLoad, onRemove }: {
  it: ItemState; svcOptions: { value: string; label: string }[]; disabledService: boolean; error: string | null;
  onPatch: (v: Partial<ItemState>) => void; onLoad: (service?: string, key?: string) => void; onRemove?: () => void;
}) {
  const keys = useLoad(() => ruleApi.list(it.service!), [it.service], { skip: !it.service, silent: true });
  const options = (keys.data?.items ?? []).map((k) => ({ value: k.ruleKey }));
  return (
    <Card size="small" style={{ marginBottom: 12 }} extra={onRemove && <Button type="text" danger icon={<DeleteOutlined />} onClick={onRemove} aria-label={t('common.delete')} />}>
      <Space wrap style={{ marginBottom: 8 }}>
        <Select style={{ width: 200 }} placeholder={t('changes.form.service')} disabled={disabledService} value={it.service} options={svcOptions}
          onChange={(v) => onPatch({ service: v, ruleKey: undefined, baseVersion: 0, text: '{}' })} />
        <AutoComplete style={{ width: 420 }} placeholder={t('changes.form.ruleKey')} value={it.ruleKey} options={options}
          onChange={(v) => onPatch({ ruleKey: v })} onSelect={(v) => onLoad(it.service, v)} onBlur={() => it.service && it.ruleKey && it.baseVersion === 0 && onLoad()} />
        <Button size="small" onClick={() => onLoad()} disabled={!it.service || !it.ruleKey}>{t('changes.form.loadCurrent')}</Button>
        <Typography.Text type="secondary">{t('changes.baseVersion')} v{it.baseVersion}</Typography.Text>
      </Space>
      <Checkbox checked={it.del} onChange={(e) => onPatch({ del: e.target.checked })} style={{ marginBottom: 8 }}>{t('changes.form.deleteRule')}</Checkbox>
      {!it.del && (
        <>
          <Input.TextArea
            className="mono"
            rows={9}
            value={it.text}
            status={error ? 'error' : jsonError(it.text) ? 'warning' : undefined}
            onChange={(e) => onPatch({ text: e.target.value })}
            aria-label={t('changes.form.afterRules')}
          />
          <div style={{ marginTop: 4 }}>
            {(error || jsonError(it.text)) ? <Typography.Text type="danger">{t('changes.form.jsonInvalid', { msg: jsonError(it.text) ?? '' })}</Typography.Text> : null}
            <Button size="small" type="link" onClick={() => !jsonError(it.text) && onPatch({ text: prettyJson(it.text) })}>{t('changes.form.format')}</Button>
          </div>
        </>
      )}
    </Card>
  );
}
