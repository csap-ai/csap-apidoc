// MSW 处理器:按契约第 7 节实现 /api/v1。仅开发 / 测试使用,不进入生产构建。
import { http, HttpResponse, type HttpHandler } from 'msw';
import type { Change, ChangeInput, Project, ProjectRole, Webhook, WebhookInput } from '@/api/types';
import * as M from './db';
import { db, HttpErr } from './db';

type Ctx = { params: Record<string, string>; q: URLSearchParams; body: any; headers: Headers };
type Fn = (c: Ctx) => unknown | Promise<unknown> | { __res: Response };

const B = '/api/v1';
const handlers: HttpHandler[] = [];
const NO_CSRF = ['/setup/status'];

function route(method: 'get' | 'post' | 'put' | 'patch' | 'delete', path: string, fn: Fn) {
  handlers.push(
    http[method](`${B}${path}`, async ({ request, params }) => {
      try {
        if (method !== 'get' && request.headers.get('X-CSAP-Team') !== '1' && !NO_CSRF.includes(path)) {
          throw new HttpErr(403, 'FORBIDDEN', '缺少 X-CSAP-Team 请求头');
        }
        let body: any;
        if (method !== 'get' && method !== 'delete') body = await request.json().catch(() => ({}));
        const q = new URL(request.url).searchParams;
        const p = Object.fromEntries(Object.entries(params).map(([k, v]) => [k, decodeURIComponent(String(v))]));
        const out = await fn({ params: p, q, body, headers: request.headers });
        if (out && typeof out === 'object' && '__res' in (out as object)) return (out as { __res: Response }).__res;
        if (out === undefined) return new HttpResponse(null, { status: 204 });
        return HttpResponse.json(out as object);
      } catch (e) {
        if (e instanceof HttpErr) return HttpResponse.json({ code: e.code, message: e.message, ...(e.extra ?? {}) }, { status: e.status });
        console.error('[mock]', e);
        return HttpResponse.json({ code: 'INTERNAL', message: '模拟后端出错' }, { status: 500 });
      }
    }),
  );
}
const num = (v: string | null) => (v ? Number(v) : undefined);
const paged = <T,>(all: T[], q: URLSearchParams) => {
  const page = num(q.get('page')) || 1, size = Math.min(num(q.get('size')) || 20, 200);
  return { items: all.slice((page - 1) * size, page * size), total: all.length };
};
const bad = (m: string) => new HttpErr(400, 'VALIDATION', m);

// ---------- 初始化与登录 ----------
route('get', '/setup/status', () => ({ initialized: db.initialized }));
route('post', '/setup', ({ body }) => {
  if (db.initialized) throw new HttpErr(403, 'FORBIDDEN', '系统已初始化');
  if (!body.username || !body.password || body.password.length < 8) throw bad('账号不能为空,密码至少 8 位');
  db.users.push({ id: ++db.seq, username: body.username, displayName: body.displayName || body.username, status: 'ACTIVE', orgRole: 'ADMIN', createdAt: Date.now(), password: body.password });
  db.orgName = body.orgName;
  db.initialized = true;
  M.seedDemo(body.displayName);
  return {};
});
const fails = new Map<string, { n: number; until: number }>();
route('post', '/auth/login', ({ body }) => {
  if (!db.initialized) throw new HttpErr(412, 'SETUP_REQUIRED', '系统尚未初始化');
  const f = fails.get(body.username) ?? { n: 0, until: 0 };
  if (f.until > Date.now()) throw new HttpErr(429, 'FORBIDDEN', '连续失败次数过多,账号已被暂时锁定,请 15 分钟后再试');
  const u = db.users.find((x) => x.username === body.username);
  if (!u || u.password !== body.password) {
    f.n++;
    if (f.n >= 5) { f.until = Date.now() + 15 * 60000; f.n = 0; }
    fails.set(body.username, f);
    M.logAudit({ actor: { type: 'USER', name: body.username || '-' }, source: 'CONSOLE', action: 'auth.login.failed' });
    throw new HttpErr(401, 'UNAUTHORIZED', '账号或密码错误');
  }
  if (u.status !== 'ACTIVE') throw new HttpErr(403, 'FORBIDDEN', '账号已停用,请联系管理员');
  fails.delete(body.username);
  db.sessionUserId = u.id;
  try { sessionStorage.setItem('mock-uid', String(u.id)); } catch { /* ignore */ }
  M.logAudit({ ...M.auditBy(u), action: 'auth.login' });
  return M.meOf(u);
});
route('post', '/auth/logout', () => {
  db.sessionUserId = null;
  try { sessionStorage.removeItem('mock-uid'); } catch { /* ignore */ }
  return undefined;
});
route('get', '/auth/me', () => M.meOf(M.currentUser()));
route('post', '/auth/password', ({ body }) => {
  const u = M.currentUser();
  if (u.password !== body.oldPassword) throw bad('当前密码不正确');
  if (!body.newPassword || body.newPassword.length < 8) throw bad('新密码至少 8 位');
  u.password = body.newPassword;
  M.logAudit({ ...M.auditBy(u), action: 'auth.password' });
  return undefined;
});

// ---------- 用户 ----------
route('get', '/users', () => { const u = M.currentUser(); M.requireAdmin(u); return db.users.map(M.publicUser); });
route('post', '/users', ({ body }) => {
  const me = M.currentUser(); M.requireAdmin(me);
  if (!body.username || !body.password) throw bad('账号与密码必填');
  if (db.users.some((x) => x.username === body.username)) throw bad('账号已存在');
  if (M.activeUsers() >= db.licenseMax) throw new HttpErr(403, 'LICENSE_LIMIT', '已达授权人数上限');
  const u = { id: ++db.seq, username: body.username, displayName: body.displayName || body.username, status: 'ACTIVE' as const, orgRole: body.orgRole || 'MEMBER', createdAt: Date.now(), password: body.password };
  db.users.push(u);
  M.logAudit({ ...M.auditBy(me), action: 'user.create', reason: u.username });
  return M.publicUser(u);
});
route('patch', '/users/:id', ({ params, body }) => {
  const me = M.currentUser(); M.requireAdmin(me);
  const u = db.users.find((x) => x.id === Number(params.id));
  if (!u) throw new HttpErr(404, 'NOT_FOUND', '用户不存在');
  if (body.status === 'ACTIVE' && u.status !== 'ACTIVE' && M.activeUsers() >= db.licenseMax) throw new HttpErr(403, 'LICENSE_LIMIT', '已达授权人数上限');
  if (u.id === me.id && (body.status === 'DISABLED' || body.orgRole === 'MEMBER')) throw bad('不能停用或降级自己');
  Object.assign(u, ['displayName', 'status', 'orgRole', 'password'].reduce((o, k) => (body[k] !== undefined ? { ...o, [k]: body[k] } : o), {}));
  M.logAudit({ ...M.auditBy(me), action: 'user.update', reason: u.username });
  return M.publicUser(u);
});

// ---------- 项目与成员 ----------
route('get', '/projects', () => { const u = M.currentUser(); const ids = M.visibleProjectIds(u); return db.projects.filter((p) => ids.includes(p.id)); });
route('post', '/projects', ({ body }) => {
  const u = M.currentUser(); M.requireAdmin(u);
  if (!body.name) throw bad('项目名称必填');
  if (db.projects.some((p) => p.name === body.name)) throw bad('项目名称已存在');
  const p: Project = { id: ++db.seq, name: body.name, description: body.description ?? '', requireApproval: body.requireApproval ?? true, createdAt: Date.now(), updatedAt: Date.now() };
  db.projects.push(p);
  M.logAudit({ ...M.auditBy(u), action: 'project.create', projectId: p.id, reason: p.name });
  return p;
});
const proj = (id: string) => { const p = db.projects.find((x) => x.id === Number(id)); if (!p) throw new HttpErr(404, 'NOT_FOUND', '项目不存在'); return p; };
route('get', '/projects/:id', ({ params }) => { const u = M.currentUser(); const p = proj(params.id); M.requireRole(u, p.id, ['ADMIN', 'MAINTAINER', 'DEVELOPER', 'VIEWER']); return p; });
route('patch', '/projects/:id', ({ params, body }) => {
  const u = M.currentUser(); const p = proj(params.id); M.requireRole(u, p.id, ['ADMIN', 'MAINTAINER']);
  ['name', 'description', 'requireApproval'].forEach((k) => { if (body[k] !== undefined) (p as any)[k] = body[k]; });
  p.updatedAt = Date.now();
  db.services.forEach((s) => { if (s.projectId === p.id) s.projectName = p.name; });
  M.logAudit({ ...M.auditBy(u), action: 'project.update', projectId: p.id, reason: p.name });
  return p;
});
route('delete', '/projects/:id', ({ params }) => {
  const u = M.currentUser(); M.requireAdmin(u); const p = proj(params.id);
  if (db.services.some((s) => s.projectId === p.id)) throw bad('项目下仍有服务,请先转移或移除服务');
  db.projects = db.projects.filter((x) => x !== p);
  db.members = db.members.filter((m) => m.projectId !== p.id);
  M.logAudit({ ...M.auditBy(u), action: 'project.delete', reason: p.name });
  return undefined;
});
route('get', '/projects/:id/members', ({ params }) => { const u = M.currentUser(); const p = proj(params.id); M.requireRole(u, p.id, ['ADMIN', 'MAINTAINER', 'DEVELOPER', 'VIEWER']); return M.members(p.id); });
route('get', '/projects/:id/candidates', ({ params }) => {
  const u = M.currentUser(); const p = proj(params.id); M.requireRole(u, p.id, ['ADMIN', 'MAINTAINER']);
  const inProject = db.members.filter((m) => m.projectId === p.id).map((m) => m.userId);
  return db.users.filter((x) => x.status === 'ACTIVE' && !inProject.includes(x.id)).map(({ id, username, displayName }) => ({ id, username, displayName }));
});
route('put', '/projects/:id/members/:userId', ({ params, body }) => {
  const u = M.currentUser(); const p = proj(params.id); M.requireRole(u, p.id, ['ADMIN', 'MAINTAINER']);
  const target = db.users.find((x) => x.id === Number(params.userId));
  if (!target) throw new HttpErr(404, 'NOT_FOUND', '用户不存在');
  const role = body.role as ProjectRole;
  if (!['MAINTAINER', 'DEVELOPER', 'VIEWER'].includes(role)) throw bad('角色无效');
  const ex = db.members.find((m) => m.projectId === p.id && m.userId === target.id);
  if (ex) ex.role = role; else db.members.push({ projectId: p.id, userId: target.id, role, createdAt: Date.now() });
  M.logAudit({ ...M.auditBy(u), action: 'member.update', projectId: p.id, reason: `${target.displayName} → ${role}` });
  return M.members(p.id).find((m) => m.userId === target.id);
});
route('delete', '/projects/:id/members/:userId', ({ params }) => {
  const u = M.currentUser(); const p = proj(params.id); M.requireRole(u, p.id, ['ADMIN', 'MAINTAINER']);
  db.members = db.members.filter((m) => !(m.projectId === p.id && m.userId === Number(params.userId)));
  M.logAudit({ ...M.auditBy(u), action: 'member.remove', projectId: p.id, reason: String(params.userId) });
  return undefined;
});

// ---------- 服务 ----------
const svcVisible = (u: ReturnType<typeof M.currentUser>, s: { projectId: number | null }) => u.orgRole === 'ADMIN' || (s.projectId != null && M.visibleProjectIds(u).includes(s.projectId));
const svc = (name: string) => { const s = db.services.find((x) => x.service === name); if (!s) throw new HttpErr(404, 'NOT_FOUND', '服务不存在'); return s; };
route('get', '/services', ({ q }) => {
  const u = M.currentUser();
  return db.services.filter((s) => svcVisible(u, s))
    .filter((s) => !q.get('projectId') || s.projectId === Number(q.get('projectId')))
    .filter((s) => q.get('unassigned') !== 'true' || s.projectId == null);
});
route('post', '/services', ({ body }) => {
  const u = M.currentUser(); M.requireRole(u, body.projectId ?? null, ['ADMIN', 'MAINTAINER']);
  if (!body.service || !body.baseUrl) throw bad('服务名与地址必填');
  if (db.services.some((s) => s.service === body.service)) throw bad('服务名已存在');
  const s = { service: body.service, projectId: body.projectId ?? null, projectName: db.projects.find((p) => p.id === body.projectId)?.name ?? null, baseUrl: body.baseUrl, docPath: body.docPath || '/csap/apidoc', language: '-', frameworkVersion: '-', source: 'MANUAL' as const, status: 'OFFLINE' as const, instances: 0, lastSeenAt: null, lastFetchedAt: null };
  db.services.push(s);
  M.logAudit({ ...M.auditBy(u), action: 'service.create', service: s.service, projectId: s.projectId });
  return s;
});
route('patch', '/services/:service', ({ params, body }) => {
  const u = M.currentUser(); const s = svc(params.service);
  M.requireRole(u, s.projectId, ['ADMIN', 'MAINTAINER']);
  if (body.projectId !== undefined) {
    if (u.orgRole !== 'ADMIN' && body.projectId != null) M.requireRole(u, body.projectId, ['MAINTAINER']);
    s.projectId = body.projectId; s.projectName = db.projects.find((p) => p.id === body.projectId)?.name ?? null;
  }
  if (body.baseUrl) s.baseUrl = body.baseUrl;
  if (body.docPath) s.docPath = body.docPath;
  M.logAudit({ ...M.auditBy(u), action: 'service.update', service: s.service, projectId: s.projectId });
  return s;
});
route('delete', '/services/:service', ({ params }) => {
  const u = M.currentUser(); const s = svc(params.service); M.requireRole(u, s.projectId, ['ADMIN', 'MAINTAINER']);
  db.services = db.services.filter((x) => x !== s);
  M.logAudit({ ...M.auditBy(u), action: 'service.delete', service: s.service, projectId: s.projectId });
  return undefined;
});

// ---------- 文档中心 ----------
route('get', '/docs/search', ({ q }) => {
  const u = M.currentUser();
  const kw = (q.get('q') || '').trim().toLowerCase();
  if (!kw) return [];
  const pid = num(q.get('projectId'));
  const out: any[] = [];
  for (const s of db.services.filter((x) => svcVisible(u, x) && (!pid || x.projectId === pid))) {
    const doc = db.docs[s.service];
    doc?.data.apiList.forEach((c) => c.methodList.forEach((m) => {
      const path = `${c.path?.[0] ?? ''}${m.paths?.[0] ?? ''}`;
      const has = (x?: string) => !!x && x.toLowerCase().includes(kw);
      let matchedIn: string | null = null, snippet = '';
      if (has(path)) { matchedIn = 'path'; snippet = path; }
      else if (has(m.value)) { matchedIn = 'title'; snippet = m.value!; }
      else if (has(m.description)) { matchedIn = 'description'; snippet = m.description!; }
      else {
        const flat = [...(m.request ?? []), ...(m.response ?? [])].flatMap((r) => r.parameters ?? []);
        const hit = flat.find((f) => has(f.name) || has(f.description));
        if (hit) { matchedIn = 'field'; snippet = `${hit.name}:${hit.description ?? ''}`; }
      }
      if (matchedIn) out.push({ service: s.service, method: (m.methods ?? ['GET'])[0], path, title: m.value ?? m.name, matchedIn, snippet });
    }));
  }
  return out.slice(0, 100);
});
route('get', '/docs/:service', ({ params }) => {
  const u = M.currentUser(); const s = svc(params.service);
  if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
  const doc = db.docs[s.service];
  if (!doc) throw new HttpErr(404, 'NOT_FOUND', '该服务暂无可用文档');
  return { __res: HttpResponse.json(doc, { headers: { 'X-CSAP-Team-Fetched-At': String(s.lastFetchedAt ?? Date.now()), 'X-CSAP-Team-Stale': String(s.status === 'STALE') } }) };
});

// ---------- 规则 ----------
const ruleAccess = (service: string) => {
  const u = M.currentUser(); const s = svc(service);
  if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
  return { u, s };
};
route('get', '/rules/:service', ({ params }) => {
  ruleAccess(params.service);
  const rows = db.rules[params.service] ?? {};
  const items = Object.entries(rows).map(([ruleKey, r]) => ({ ruleKey, version: r.version, updatedBy: r.updatedBy, updatedAt: r.updatedAt }));
  return { service: params.service, version: Math.max(0, ...items.map((i) => i.version)), items };
});
route('get', '/rules/:service/item', ({ params, q }) => {
  ruleAccess(params.service);
  const r = M.curRule(params.service, q.get('key') || '');
  return { ruleKey: q.get('key'), rules: r?.rules ?? null, version: r?.version ?? 0 };
});
route('get', '/rules/:service/history', ({ params, q }) => {
  ruleAccess(params.service);
  return db.history.filter((h) => h.service === params.service && h.ruleKey === q.get('key')).slice(0, num(q.get('limit')) || 50)
    .map(({ service: _s, ruleKey: _k, ...h }) => h);
});
route('get', '/rules/:service/diff', ({ params, q }) => {
  ruleAccess(params.service);
  const key = q.get('key') || '';
  const from = db.history.find((h) => h.id === num(q.get('from')));
  if (!from) throw new HttpErr(404, 'NOT_FOUND', '起点版本不存在');
  const toId = num(q.get('to'));
  const to = toId ? db.history.find((h) => h.id === toId) : undefined;
  if (toId && !to) throw new HttpErr(404, 'NOT_FOUND', '终点版本不存在');
  return { from: from.id, to: to?.id ?? 0, diff: M.unified(from.afterRules, to ? to.afterRules : M.curRule(params.service, key)?.rules ?? null) };
});
route('post', '/rules/:service/rollback', ({ params, body }) => {
  const { u, s } = ruleAccess(params.service);
  if (s.projectId == null) throw bad('服务尚未分配到项目');
  M.requireRole(u, s.projectId, ['ADMIN', 'MAINTAINER', 'DEVELOPER']);
  const h = db.history.find((x) => x.id === body.historyId && x.service === s.service && x.ruleKey === body.key);
  if (!h) throw new HttpErr(404, 'NOT_FOUND', '历史记录不存在');
  const c: Change = {
    id: ++db.seq, projectId: s.projectId, projectName: s.projectName!, title: `回滚 ${body.key} 到 v${h.version}`, reason: body.reason || '', kind: 'ROLLBACK', status: 'DRAFT',
    author: M.userRef(u), createdAt: Date.now(), submittedAt: null, decidedBy: null, decidedAt: null, decisionNote: null, publishedAt: null,
    items: [{ id: ++db.seq, service: s.service, ruleKey: body.key, baseVersion: M.curVersion(s.service, body.key), beforeRules: M.curRule(s.service, body.key)?.rules ?? null, afterRules: h.afterRules }],
  };
  db.changes.unshift(c);
  M.logAudit({ ...M.auditBy(u), action: 'change.create', projectId: c.projectId, changeId: c.id, reason: c.title });
  return M.decorate(c);
});

// ---------- 修改单 ----------
function create(body: ChangeInput, me: ReturnType<typeof M.currentUser>): Change {
  const p = proj(String(body.projectId));
  M.requireRole(me, p.id, ['ADMIN', 'MAINTAINER', 'DEVELOPER']);
  if (!body.title?.trim()) throw bad('标题必填');
  const items = M.normalizeItems(body.items);
  items.forEach((i) => { const s = svc(i.service); if (s.projectId !== p.id) throw bad(`服务 ${i.service} 不属于该项目`); });
  const c: Change = { id: ++db.seq, projectId: p.id, projectName: p.name, title: body.title, reason: body.reason || '', kind: 'EDIT', status: 'DRAFT', author: M.userRef(me), createdAt: Date.now(), submittedAt: null, decidedBy: null, decidedAt: null, decisionNote: null, publishedAt: null, items };
  return c;
}
const readable = (u: ReturnType<typeof M.currentUser>, c: Change) => { M.requireRole(u, c.projectId, ['ADMIN', 'MAINTAINER', 'DEVELOPER', 'VIEWER']); return c; };
route('get', '/changes', ({ q }) => {
  const u = M.currentUser(); const ids = M.visibleProjectIds(u);
  const list = db.changes.filter((c) => ids.includes(c.projectId))
    .filter((c) => !q.get('projectId') || c.projectId === Number(q.get('projectId')))
    .filter((c) => !q.get('status') || c.status === q.get('status'))
    .filter((c) => q.get('mine') !== 'true' || c.author.id === u.id)
    .sort((a, b) => b.createdAt - a.createdAt);
  return paged(list.map((c) => M.decorate(c, false)), q);
});
route('post', '/changes', ({ body }) => {
  const u = M.currentUser(); const c = create(body, u);
  db.changes.unshift(c);
  M.logAudit({ ...M.auditBy(u), action: 'change.create', projectId: c.projectId, changeId: c.id, reason: c.title });
  return M.decorate(c);
});
route('get', '/changes/:id', ({ params }) => M.decorate(readable(M.currentUser(), M.findChange(Number(params.id)))));
route('patch', '/changes/:id', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  if (c.status !== 'DRAFT') throw bad('只有草稿可以编辑');
  if (c.author.id !== u.id) throw new HttpErr(403, 'FORBIDDEN', '只有作者可以编辑草稿');
  const next = create({ ...body, projectId: body.projectId ?? c.projectId }, u);
  Object.assign(c, { title: next.title, reason: next.reason, items: next.items, projectId: next.projectId, projectName: next.projectName, updatedAt: Date.now() });
  M.logAudit({ ...M.auditBy(u), action: 'change.update', projectId: c.projectId, changeId: c.id, reason: c.title });
  return M.decorate(c);
});
route('post', '/changes/:id/submit', ({ params }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  M.requireRole(u, c.projectId, ['ADMIN', 'MAINTAINER', 'DEVELOPER']);
  if (c.status !== 'DRAFT') throw bad('只有草稿可以提交');
  c.submittedAt = Date.now();
  M.setStatus(c, 'PENDING', 'change.submit', u);
  M.fireEvent('change.submitted', c.projectId);
  const needApproval = db.projects.find((p) => p.id === c.projectId)!.requireApproval;
  if (!needApproval) {
    try { M.tryPublish(c, u); M.fireEvent('change.published', c.projectId); } catch (e) { M.fireEvent('change.conflict', c.projectId); throw e; }
  } else {
    // 提交时先检查一次版本,避免审批人白审
    const stale = c.items.some((i) => M.curVersion(i.service, i.ruleKey) !== i.baseVersion && !M.sameJson(M.curRule(i.service, i.ruleKey)?.rules ?? null, i.afterRules));
    if (stale) { try { M.tryPublish(c, u); } catch (e) { M.fireEvent('change.conflict', c.projectId); throw e; } }
  }
  return M.decorate(c);
});
route('post', '/changes/:id/approve', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  const r = M.projectRole(u, c.projectId);
  if (r !== 'ADMIN' && !(r === 'MAINTAINER' && c.author.id !== u.id)) throw new HttpErr(403, 'FORBIDDEN', r === 'MAINTAINER' ? '维护者不能审批自己提交的修改单' : '没有审批权限');
  if (c.status !== 'PENDING') throw bad('只有待审批的修改单可以审批');
  c.decidedBy = M.userRef(u); c.decidedAt = Date.now(); c.decisionNote = body.note ?? '';
  M.logAudit({ ...M.auditBy(u), action: 'change.approve', projectId: c.projectId, changeId: c.id, reason: body.note });
  try { M.tryPublish(c, u); } catch (e) { M.fireEvent('change.conflict', c.projectId); throw e; }
  M.fireEvent('change.approved', c.projectId); M.fireEvent('change.published', c.projectId);
  return M.decorate(c);
});
route('post', '/changes/:id/reject', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  const r = M.projectRole(u, c.projectId);
  if (r !== 'ADMIN' && !(r === 'MAINTAINER' && c.author.id !== u.id)) throw new HttpErr(403, 'FORBIDDEN', '没有审批权限');
  if (c.status !== 'PENDING') throw bad('只有待审批的修改单可以驳回');
  c.decidedBy = M.userRef(u); c.decidedAt = Date.now(); c.decisionNote = body.note ?? '';
  M.setStatus(c, 'REJECTED', 'change.reject', u, body.note);
  M.fireEvent('change.rejected', c.projectId);
  return M.decorate(c);
});
route('post', '/changes/:id/withdraw', ({ params }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  if (c.author.id !== u.id) throw new HttpErr(403, 'FORBIDDEN', '只有作者可以撤回');
  if (!['DRAFT', 'PENDING', 'CONFLICT'].includes(c.status)) throw bad('当前状态不能撤回');
  M.setStatus(c, 'WITHDRAWN', 'change.withdraw', u);
  return M.decorate(c);
});
route('post', '/changes/:id/rebase', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  if (c.author.id !== u.id) throw new HttpErr(403, 'FORBIDDEN', '只有作者可以 Rebase');
  if (c.status !== 'CONFLICT') throw bad('只有冲突状态的修改单可以 Rebase');
  const after = new Map<string, string | null>((body.items ?? []).map((i: any) => [`${i.service}\u0000${i.ruleKey}`, i.afterRules]));
  c.items.forEach((i) => {
    const k = `${i.service}\u0000${i.ruleKey}`;
    if (after.has(k)) { const v = after.get(k)!; if (v != null) { try { JSON.parse(v); } catch { throw bad(`规则 JSON 格式错误:${i.ruleKey}`); } } i.afterRules = v; }
    i.baseVersion = M.curVersion(i.service, i.ruleKey);
    i.beforeRules = M.curRule(i.service, i.ruleKey)?.rules ?? null;
  });
  M.setStatus(c, 'DRAFT', 'change.rebase', u);
  return M.decorate(c);
});

// ---------- 审计 ----------
function filterAudit(q: URLSearchParams, u: ReturnType<typeof M.currentUser>) {
  const ids = M.visibleProjectIds(u);
  if (u.orgRole !== 'ADMIN' && !db.members.some((m) => m.userId === u.id && m.role !== 'VIEWER')) throw new HttpErr(403, 'FORBIDDEN', '当前角色不能查看审计');
  return db.audit.filter((a) => u.orgRole === 'ADMIN' || (a.projectId != null && ids.includes(a.projectId) && M.projectRole(u, a.projectId) !== 'VIEWER'))
    .filter((a) => !q.get('from') || a.at >= Number(q.get('from')))
    .filter((a) => !q.get('to') || a.at <= Number(q.get('to')))
    .filter((a) => !q.get('actor') || a.actorName.includes(q.get('actor')!))
    .filter((a) => !q.get('projectId') || a.projectId === Number(q.get('projectId')))
    .filter((a) => !q.get('service') || a.service === q.get('service'))
    .filter((a) => !q.get('ruleKey') || (a.ruleKey ?? '').includes(q.get('ruleKey')!))
    .filter((a) => !q.get('action') || a.action.startsWith(q.get('action')!))
    .filter((a) => !q.get('source') || a.source === q.get('source'));
}
route('get', '/audit', ({ q }) => {
  const r = paged(filterAudit(q, M.currentUser()), q);
  return { ...r, items: r.items.map(({ beforeValue: _b, afterValue: _a, diff: _d, ...rest }) => rest) }; // 列表省略大字段
});
route('get', '/audit/mcp', ({ q }) => mcpList(q));
route('get', '/audit/export', ({ q }) => exportAudit(q));
route('get', '/audit/:id', ({ params }) => {
  const u = M.currentUser();
  const rec = filterAudit(new URLSearchParams(), u).find((a) => a.id === Number(params.id));
  if (!rec) throw new HttpErr(404, 'NOT_FOUND', '审计记录不存在');
  return rec;
});
function exportAudit(q: URLSearchParams) {
  const u = M.currentUser();
  if (u.orgRole !== 'ADMIN' && !db.members.some((m) => m.userId === u.id && m.role === 'MAINTAINER')) throw new HttpErr(403, 'FORBIDDEN', '当前角色不能导出审计');
  const rows = filterAudit(q, u);
  M.logAudit({ ...M.auditBy(u), action: 'audit.export', reason: `${rows.length} 条` });
  if (q.get('format') === 'json') return { __res: new Response(JSON.stringify(rows, null, 2), { headers: { 'Content-Type': 'application/json', 'Content-Disposition': 'attachment; filename="audit.json"' } }) };
  const cols = ['id', 'at', 'actorType', 'actorName', 'source', 'ip', 'action', 'projectId', 'service', 'ruleKey', 'changeId', 'reason'] as const;
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const csv = '﻿' + [cols.join(','), ...rows.map((r) => cols.map((c) => esc(r[c])).join(','))].join('\n');
  return { __res: new Response(csv, { headers: { 'Content-Type': 'text/csv; charset=utf-8', 'Content-Disposition': 'attachment; filename="audit.csv"' } }) };
}
function mcpList(q: URLSearchParams) {
  const u = M.currentUser();
  if (!db.mcpAvailable) return { available: false, items: [], total: 0 };
  const ok = db.services.filter((s) => svcVisible(u, s)).map((s) => s.service);
  const rows = db.mcp.filter((r) => ok.includes(r.service))
    .filter((r) => !q.get('service') || r.service === q.get('service'))
    .filter((r) => !q.get('caller') || r.caller.includes(q.get('caller')!))
    .filter((r) => !q.get('tool') || r.tool.includes(q.get('tool')!))
    .filter((r) => !q.get('from') || r.at >= Number(q.get('from')))
    .filter((r) => !q.get('to') || r.at <= Number(q.get('to')));
  return { available: true, ...paged(rows, q) };
}

// ---------- Webhook ----------
const hook = (id: string) => { const w = db.webhooks.find((x) => x.id === Number(id)); if (!w) throw new HttpErr(404, 'NOT_FOUND', 'Webhook 不存在'); return w; };
const hookAccess = (u: ReturnType<typeof M.currentUser>, pid: number | null) => { if (pid == null) M.requireAdmin(u); else M.requireRole(u, pid, ['ADMIN', 'MAINTAINER']); };
const outHook = ({ secret: _s, ...w }: Webhook & { secret?: string }) => w;
route('get', '/webhooks', ({ q }) => {
  const u = M.currentUser();
  if (u.orgRole !== 'ADMIN' && !db.members.some((m) => m.userId === u.id && m.role === 'MAINTAINER')) throw new HttpErr(403, 'FORBIDDEN', '当前角色不能管理 Webhook');
  const ids = M.visibleProjectIds(u);
  return db.webhooks.filter((w) => (w.projectId == null ? u.orgRole === 'ADMIN' : ids.includes(w.projectId) && M.projectRole(u, w.projectId) !== 'VIEWER' && M.projectRole(u, w.projectId) !== 'DEVELOPER'))
    .filter((w) => !q.get('projectId') || w.projectId === Number(q.get('projectId'))).map(outHook);
});
route('post', '/webhooks', ({ body }: { body: WebhookInput }) => {
  const u = M.currentUser(); hookAccess(u, body.projectId ?? null);
  if (!body.name || !body.url) throw bad('名称与地址必填');
  const w = { id: ++db.seq, projectId: body.projectId ?? null, name: body.name, kind: body.kind, url: body.url, secret: body.secret, hasSecret: !!body.secret, events: body.events, enabled: body.enabled ?? true };
  db.webhooks.push(w);
  M.logAudit({ ...M.auditBy(u), action: 'webhook.create', projectId: w.projectId, reason: w.name });
  return outHook(w);
});
route('patch', '/webhooks/:id', ({ params, body }) => {
  const u = M.currentUser(); const w = hook(params.id); hookAccess(u, w.projectId);
  (['name', 'kind', 'url', 'events', 'enabled'] as const).forEach((k) => { if (body[k] !== undefined) (w as any)[k] = body[k]; });
  if (body.secret) { w.secret = body.secret; w.hasSecret = true; }
  M.logAudit({ ...M.auditBy(u), action: 'webhook.update', projectId: w.projectId, reason: w.name });
  return outHook(w);
});
route('delete', '/webhooks/:id', ({ params }) => {
  const u = M.currentUser(); const w = hook(params.id); hookAccess(u, w.projectId);
  db.webhooks = db.webhooks.filter((x) => x !== w);
  M.logAudit({ ...M.auditBy(u), action: 'webhook.delete', projectId: w.projectId, reason: w.name });
  return undefined;
});
route('post', '/webhooks/:id/test', ({ params }) => { const u = M.currentUser(); const w = hook(params.id); hookAccess(u, w.projectId); return M.deliver(w, 'test'); });
route('get', '/webhooks/:id/deliveries', ({ params }) => { const u = M.currentUser(); const w = hook(params.id); hookAccess(u, w.projectId); return db.deliveries.filter((d) => d.webhookId === w.id).slice(0, 100); });

// ---------- 令牌 ----------
const b62 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
route('get', '/tokens', () => { M.requireAdmin(M.currentUser()); return db.tokens; });
route('post', '/tokens', ({ body }) => {
  const u = M.currentUser(); M.requireAdmin(u);
  if (!body.name) throw bad('令牌名称必填');
  const token = 'csapt_' + Array.from({ length: 40 }, () => b62[Math.floor(Math.random() * 62)]).join('');
  const t = { id: ++db.seq, name: body.name, prefix: token.slice(0, 8), scope: body.scope, projectId: body.projectId ?? null, createdBy: u.displayName, createdAt: Date.now(), lastUsedAt: null, revoked: false };
  db.tokens.push(t);
  M.logAudit({ ...M.auditBy(u), action: 'token.create', projectId: t.projectId, reason: t.name });
  return { ...t, token }; // 明文仅此一次
});
route('delete', '/tokens/:id', ({ params }) => {
  const u = M.currentUser(); M.requireAdmin(u);
  const t = db.tokens.find((x) => x.id === Number(params.id));
  if (!t) throw new HttpErr(404, 'NOT_FOUND', '令牌不存在');
  t.revoked = true;
  M.logAudit({ ...M.auditBy(u), action: 'token.revoke', reason: t.name });
  return undefined;
});

// ---------- 授权 ----------
const lic = () => ({ status: db.licenseStatus, licensee: db.licensee, maxMembers: db.licenseMax, expiresAt: db.licenseExpiresAt, activeMembers: M.activeUsers() });
route('get', '/license', () => { M.requireAdmin(M.currentUser()); return lic(); });
route('post', '/license', ({ body }) => {
  const u = M.currentUser(); M.requireAdmin(u);
  const text = String(body.license || '').trim();
  if (!text) throw bad('授权文本为空');
  try {
    const j = JSON.parse(atob(text));
    db.licenseStatus = j.expiresAt && j.expiresAt < Date.now() ? 'EXPIRED' : 'VALID';
    db.licensee = j.licensee ?? null; db.licenseMax = j.maxMembers ?? 5; db.licenseExpiresAt = j.expiresAt ?? null;
  } catch { db.licenseStatus = 'INVALID'; throw bad('授权文件无效或签名校验失败'); }
  M.logAudit({ ...M.auditBy(u), action: 'license.update' });
  return lic();
});

export { handlers };
