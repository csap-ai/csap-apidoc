// MSW 处理器:按契约第 7 节实现 /api/v1。仅开发 / 测试使用,不进入生产构建。
import { http, HttpResponse, type HttpHandler } from 'msw';
import type { AuditVerifyProblem, Change, ChangeInput, Project, ProjectRole, Webhook, WebhookInput } from '@/api/types';
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
        if (method !== 'get') body = await request.json().catch(() => ({}));
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
  if (f.until > Date.now()) throw new HttpErr(429, 'LOCKED', '连续失败次数过多,账号已被暂时锁定,请 15 分钟后再试');
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
  const u = { id: ++db.seq, username: body.username, displayName: body.displayName || body.username, status: 'ACTIVE' as const, orgRole: body.orgRole || 'MEMBER', createdAt: Date.now(), password: body.password };
  db.users.push(u);
  M.logAudit({ ...M.auditBy(me), action: 'user.create', reason: u.username });
  return M.publicUser(u);
});
route('patch', '/users/:id', ({ params, body }) => {
  const me = M.currentUser(); M.requireAdmin(me);
  const u = db.users.find((x) => x.id === Number(params.id));
  if (!u) throw new HttpErr(404, 'NOT_FOUND', '用户不存在');
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

// ---------- 环境(1.1 §1.2) ----------
const WRITE_ROLES: (ProjectRole | 'ADMIN')[] = ['ADMIN', 'MAINTAINER', 'DEVELOPER'];
const ALL_ROLES: (ProjectRole | 'ADMIN')[] = ['ADMIN', 'MAINTAINER', 'DEVELOPER', 'VIEWER'];
route('get', '/envs', () => {
  M.currentUser();
  return [...db.envs].sort((a, b) => a.order - b.order);
});

// ---------- 服务 ----------
type User = ReturnType<typeof M.currentUser>;
const svcVisible = (u: User, s: { projectId: number | null }) => u.orgRole === 'ADMIN' || (s.projectId != null && M.visibleProjectIds(u).includes(s.projectId));
const svc = (name: string) => { const s = db.services.find((x) => x.service === name); if (!s) throw new HttpErr(404, 'NOT_FOUND', '服务不存在'); return s; };
route('get', '/services', ({ q }) => {
  const u = M.currentUser();
  const env = q.get('env'); M.envOf(env);
  return db.services.filter((s) => svcVisible(u, s))
    .filter((s) => !q.get('projectId') || s.projectId === Number(q.get('projectId')))
    .filter((s) => q.get('unassigned') !== 'true' || s.projectId == null)
    .map((s) => M.serviceOut(s, env));
});
route('post', '/services', ({ body }) => {
  const u = M.currentUser();
  if (!body.service || !body.baseUrl) throw bad('服务名与地址必填');
  const env = M.envOf(body.env);
  const existing = db.services.find((s) => s.service === body.service);
  M.requireRole(u, existing ? existing.projectId : body.projectId ?? null, ['ADMIN', 'MAINTAINER']);
  if (existing && M.svcEnvOf(existing.service, env.key)) throw bad('该环境已有这个服务');
  const row = existing ?? { service: body.service as string, projectId: (body.projectId ?? null) as number | null, language: '-', frameworkVersion: '-' };
  if (!existing) db.services.push(row);
  db.svcEnvs.push({ service: row.service, env: env.key, baseUrl: body.baseUrl, docPath: body.docPath || '/csap/apidoc', source: 'MANUAL', status: 'OFFLINE', instances: 0, lastSeenAt: null, lastFetchedAt: null });
  M.logAudit({ ...M.auditBy(u), action: 'service.create', env: env.key, service: row.service, projectId: row.projectId });
  return M.serviceOut(row, env.key);
});
route('patch', '/services/:service', ({ params, body, q }) => {
  const u = M.currentUser(); const s = svc(params.service); const env = M.envOf(q.get('env'));
  M.requireRole(u, s.projectId, ['ADMIN', 'MAINTAINER']);
  if (body.projectId !== undefined) {
    if (u.orgRole !== 'ADMIN' && body.projectId != null) M.requireRole(u, body.projectId, ['MAINTAINER']);
    s.projectId = body.projectId;
  }
  if (body.baseUrl || body.docPath) {
    const rec = M.svcEnvOf(s.service, env.key);
    if (!rec) throw new HttpErr(404, 'NOT_FOUND', '该服务在这个环境没有记录');
    if (body.baseUrl) rec.baseUrl = body.baseUrl;
    if (body.docPath) rec.docPath = body.docPath;
  }
  M.logAudit({ ...M.auditBy(u), action: 'service.update', env: env.key, service: s.service, projectId: s.projectId });
  return M.serviceOut(s, env.key);
});
route('delete', '/services/:service', ({ params, q }) => {
  const u = M.currentUser(); const s = svc(params.service); M.requireRole(u, s.projectId, ['ADMIN', 'MAINTAINER']);
  const envKey = q.get('env');
  if (envKey) {
    M.envOf(envKey);
    db.svcEnvs = db.svcEnvs.filter((x) => !(x.service === s.service && x.env === envKey)); // 只删该环境的地址和实例
  } else {
    db.services = db.services.filter((x) => x !== s);
    db.svcEnvs = db.svcEnvs.filter((x) => x.service !== s.service);
  }
  M.logAudit({ ...M.auditBy(u), action: 'service.delete', env: envKey || null, service: s.service, projectId: s.projectId });
  return undefined;
});

// ---------- 文档中心(按环境) ----------
const docFor = (service: string, envKey: string) => {
  const doc = db.docs[service];
  if (!doc || db.scenario === 'single') return doc;
  return { ...doc, data: { ...doc.data, apiInfo: { ...doc.data.apiInfo, description: `${doc.data.apiInfo?.title ?? service} 的接口文档(${M.envOf(envKey).name}环境)` } } };
};
route('get', '/docs/search', ({ q }) => {
  const u = M.currentUser();
  const env = M.envUsable(q.get('env'));
  const kw = (q.get('q') || '').trim().toLowerCase();
  if (!kw) return [];
  const pid = num(q.get('projectId'));
  const out: any[] = [];
  for (const s of db.services.filter((x) => svcVisible(u, x) && (!pid || x.projectId === pid) && M.svcEnvOf(x.service, env.key))) {
    const doc = docFor(s.service, env.key);
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
      if (matchedIn) out.push({ env: env.key, service: s.service, method: (m.methods ?? ['GET'])[0], path, title: m.value ?? m.name, matchedIn, snippet });
    }));
  }
  return out.slice(0, 100);
});
route('get', '/docs/:service', ({ params, q }) => {
  const u = M.currentUser(); const s = svc(params.service);
  const env = M.envUsable(q.get('env'));
  if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
  const rec = M.svcEnvOf(s.service, env.key);
  const doc = rec ? docFor(s.service, env.key) : undefined;
  if (!doc || !rec) throw new HttpErr(404, 'NOT_FOUND', '该服务暂无可用文档');
  return { __res: HttpResponse.json(doc, { headers: { 'X-CSAP-Team-Fetched-At': String(rec.lastFetchedAt ?? Date.now()), 'X-CSAP-Team-Stale': String(rec.status === 'STALE') } }) };
});

// ---------- 规则(按环境) ----------
const ruleAccess = (service: string, envKey: string | null) => {
  const u = M.currentUser(); const s = svc(service);
  const env = M.envUsable(envKey);
  if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
  return { u, s, env };
};
route('get', '/rules/:service', ({ params, q }) => {
  const { env } = ruleAccess(params.service, q.get('env'));
  const rows = db.rules[env.key]?.[params.service] ?? {};
  const items = Object.entries(rows).map(([ruleKey, r]) => ({ ruleKey, version: r.version, updatedBy: r.updatedBy, updatedAt: r.updatedAt }));
  return { service: params.service, version: Math.max(0, ...items.map((i) => i.version)), items };
});
route('get', '/rules/:service/item', ({ params, q }) => {
  const { env } = ruleAccess(params.service, q.get('env'));
  const key = q.get('key') || '';
  const r = M.curRule(env.key, params.service, key);
  return { ruleKey: key, rules: r?.rules ?? null, version: r?.version ?? 0, openChanges: M.openChangesFor(env.key, params.service, key) };
});
route('get', '/rules/:service/history', ({ params, q }) => {
  const { env } = ruleAccess(params.service, q.get('env'));
  return db.history.filter((h) => h.env === env.key && h.service === params.service && h.ruleKey === q.get('key')).slice(0, num(q.get('limit')) || 50)
    .map(({ service: _s, ruleKey: _k, env: _e, ...h }) => h);
});
route('get', '/rules/:service/diff', ({ params, q }) => {
  const { env } = ruleAccess(params.service, q.get('env'));
  const key = q.get('key') || '';
  const from = db.history.find((h) => h.id === num(q.get('from')) && h.env === env.key);
  if (!from) throw new HttpErr(404, 'NOT_FOUND', '起点版本不存在');
  const toId = num(q.get('to'));
  const to = toId ? db.history.find((h) => h.id === toId && h.env === env.key) : undefined;
  if (toId && !to) throw new HttpErr(404, 'NOT_FOUND', '终点版本不存在');
  return { from: from.id, to: to?.id ?? 0, diff: M.unified(from.afterRules, to ? to.afterRules : M.curRule(env.key, params.service, key)?.rules ?? null) };
});
route('post', '/rules/:service/rollback', ({ params, body, q }) => {
  const { u, s, env } = ruleAccess(params.service, body.env ?? q.get('env'));
  M.envWritable(env.key);
  if (s.projectId == null) throw bad('服务尚未分配到项目');
  M.requireRole(u, s.projectId, WRITE_ROLES);
  const h = db.history.find((x) => x.id === body.historyId && x.env === env.key && x.service === s.service && x.ruleKey === body.key);
  if (!h) throw new HttpErr(404, 'NOT_FOUND', '历史记录不存在');
  const c: Change = {
    id: ++db.seq, projectId: s.projectId, projectName: db.projects.find((p) => p.id === s.projectId)!.name, title: `回滚 ${body.key} 到 v${h.version}`, reason: body.reason || '', kind: 'ROLLBACK', status: 'DRAFT',
    author: M.userRef(u), createdAt: Date.now(), submittedAt: null, decidedBy: null, decidedAt: null, decisionNote: null, publishedAt: null, env: env.key, sourceEnv: null,
    items: [{ id: ++db.seq, service: s.service, ruleKey: body.key, baseVersion: M.curVersion(env.key, s.service, body.key), beforeRules: M.curRule(env.key, s.service, body.key)?.rules ?? null, afterRules: h.afterRules }],
  };
  db.changes.unshift(c);
  M.logAudit({ ...M.auditBy(u), action: 'change.create', projectId: c.projectId, env: env.key, changeId: c.id, reason: c.title });
  return M.decorate(c);
});

// ---------- 环境对比与推广(1.1 §3.3) ----------
function diffScope(q: URLSearchParams, u: User) {
  const from = q.get('from'), to = q.get('to');
  if (!from || !to) throw bad('from、to 必填');
  if (from === to) throw bad('来源和目标不能是同一个环境');
  const fe = M.envUsable(from), te = M.envUsable(to);
  const pid = num(q.get('projectId')), service = q.get('service');
  if (!pid && !service) throw bad('projectId 与 service 至少填一个');
  let names: string[];
  if (service) {
    const s = svc(service);
    if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
    names = [service];
  } else {
    M.requireRole(u, pid, ALL_ROLES);
    names = db.services.filter((s) => s.projectId === pid).map((s) => s.service);
  }
  return { fe, te, names };
}
route('get', '/envs/diff', ({ q }) => {
  const { fe, te, names } = diffScope(q, M.currentUser());
  return { from: fe.key, to: te.key, services: M.diffEnvs(fe.key, te.key, names) };
});
route('get', '/envs/diff/item', ({ q }) => {
  const u = M.currentUser();
  const from = M.envUsable(q.get('from')), to = M.envUsable(q.get('to'));
  if (from.key === to.key) throw bad('来源和目标不能是同一个环境');
  const service = q.get('service') || '', key = q.get('key') || '';
  const s = svc(service);
  if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
  const a = M.curRule(from.key, service, key), b = M.curRule(to.key, service, key);
  return {
    from: from.key, to: to.key, service, ruleKey: key, fromRules: a?.rules ?? null, toRules: b?.rules ?? null,
    fromVersion: a?.version ?? null, toVersion: b?.version ?? null, diff: M.unified(b?.rules ?? null, a?.rules ?? null), // to → from:推广后目标会怎么变
  };
});
route('post', '/promotions', ({ body }) => {
  const u = M.currentUser();
  const p = proj(String(body.projectId));
  M.requireRole(u, p.id, WRITE_ROLES);
  if (!body.from || !body.to || body.from === body.to) throw bad('来源和目标环境必填且不能相同');
  const from = M.envUsable(body.from), to = M.envWritable(body.to);
  const projectServices = db.services.filter((s) => s.projectId === p.id).map((s) => s.service);
  let picks: { service: string; ruleKey: string }[];
  if (body.items?.length) {
    picks = body.items;
    picks.forEach((i) => { if (!projectServices.includes(i.service)) throw bad(`服务 ${i.service} 不属于该项目`); });
  } else {
    picks = M.diffEnvs(from.key, to.key, projectServices).flatMap((d) => d.items
      .filter((i) => i.status !== 'ONLY_TO' || body.includeDeletes)
      .map((i) => ({ service: d.service, ruleKey: i.ruleKey })));
  }
  const items = picks.map((i) => {
    const a = M.curRule(from.key, i.service, i.ruleKey), b = M.curRule(to.key, i.service, i.ruleKey);
    return { service: i.service, ruleKey: i.ruleKey, after: a?.rules ?? null, before: b?.rules ?? null, base: b?.version ?? 0 };
  }).filter((i) => !M.sameJson(i.after, i.before));
  if (!items.length) throw bad('两个环境没有差异');
  const title = String(body.title || '').trim() || `推广 ${p.name}:${from.name} → ${to.name}`;
  const c: Change = {
    id: ++db.seq, projectId: p.id, projectName: p.name, title, reason: body.reason || '', kind: 'PROMOTE', status: 'DRAFT', author: M.userRef(u), createdAt: Date.now(), updatedAt: Date.now(),
    submittedAt: null, decidedBy: null, decidedAt: null, decisionNote: null, publishedAt: null, env: to.key, sourceEnv: from.key,
    items: items.map((i) => ({ id: ++db.seq, service: i.service, ruleKey: i.ruleKey, baseVersion: i.base, beforeRules: i.before, afterRules: i.after })),
  };
  db.changes.unshift(c);
  M.logAudit({ ...M.auditBy(u), action: 'change.create', projectId: c.projectId, env: to.key, changeId: c.id, reason: `${c.title}(promote ${from.key}→${to.key})` });
  if (body.submit) doSubmit(c, u);
  return M.decorate(c);
});

// ---------- 修改单 ----------
function create(body: ChangeInput, me: User, fixedEnv?: string): Change {
  const p = proj(String(body.projectId));
  M.requireRole(me, p.id, WRITE_ROLES);
  const env = M.envWritable(fixedEnv ?? body.env);
  if (!body.title?.trim()) throw bad('标题必填');
  const items = M.normalizeItems(env.key, body.items);
  items.forEach((i) => { const s = svc(i.service); if (s.projectId !== p.id) throw bad(`服务 ${i.service} 不属于该项目`); });
  return { id: ++db.seq, projectId: p.id, projectName: p.name, title: body.title, reason: body.reason || '', kind: 'EDIT', status: 'DRAFT', author: M.userRef(me), createdAt: Date.now(), submittedAt: null, decidedBy: null, decidedAt: null, decisionNote: null, publishedAt: null, env: env.key, sourceEnv: null, items };
}
const readable = (u: User, c: Change) => { M.requireRole(u, c.projectId, ALL_ROLES); return c; };
route('get', '/changes', ({ q }) => {
  const u = M.currentUser(); const ids = M.visibleProjectIds(u);
  const env = q.get('env'); if (env) M.envOf(env);
  const list = db.changes.filter((c) => ids.includes(c.projectId))
    .filter((c) => !q.get('projectId') || c.projectId === Number(q.get('projectId')))
    .filter((c) => !q.get('status') || c.status === q.get('status'))
    .filter((c) => !env || M.changeEnv(c) === env)
    .filter((c) => q.get('mine') !== 'true' || c.author.id === u.id)
    .sort((a, b) => b.createdAt - a.createdAt);
  return paged(list.map((c) => M.decorate(c, false)), q);
});
route('post', '/changes', ({ body }) => {
  const u = M.currentUser(); const c = create(body, u);
  db.changes.unshift(c);
  M.logAudit({ ...M.auditBy(u), action: 'change.create', projectId: c.projectId, env: M.changeEnv(c), changeId: c.id, reason: c.title });
  return M.decorate(c);
});
route('get', '/changes/:id', ({ params }) => M.decorate(readable(M.currentUser(), M.findChange(Number(params.id)))));
route('patch', '/changes/:id', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  if (c.status !== 'DRAFT') throw bad('只有草稿可以编辑');
  if (c.author.id !== u.id) throw new HttpErr(403, 'FORBIDDEN', '只有作者可以编辑草稿');
  const next = create({ ...body, projectId: body.projectId ?? c.projectId }, u, M.changeEnv(c));
  Object.assign(c, { title: next.title, reason: next.reason, items: next.items, projectId: next.projectId, projectName: next.projectName, updatedAt: Date.now() });
  M.logAudit({ ...M.auditBy(u), action: 'change.update', projectId: c.projectId, env: M.changeEnv(c), changeId: c.id, reason: c.title });
  return M.decorate(c);
});
/** 提交:需要审批(项目要求或环境受保护)→ 待审批;否则直接发布 */
function doSubmit(c: Change, u: User) {
  M.envWritable(M.changeEnv(c));
  const env = M.changeEnv(c);
  c.submittedAt = Date.now();
  M.setStatus(c, 'PENDING', 'change.submit', u);
  M.fireEvent('change.submitted', c.projectId, env);
  if (!M.needsApproval(c)) {
    try { M.tryPublish(c, u); M.fireEvent('change.published', c.projectId, env); } catch (e) { M.fireEvent('change.conflict', c.projectId, env); throw e; }
  } else if (M.isStale(c)) {
    // 提交时先检查一次版本,避免审批人白审
    try { M.tryPublish(c, u); } catch (e) { M.fireEvent('change.conflict', c.projectId, env); throw e; }
  }
}
route('post', '/changes/:id/submit', ({ params }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  M.requireRole(u, c.projectId, WRITE_ROLES);
  if (c.status !== 'DRAFT') throw bad('只有草稿可以提交');
  doSubmit(c, u);
  return M.decorate(c);
});
route('post', '/changes/:id/approve', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  const r = M.projectRole(u, c.projectId);
  if (r !== 'ADMIN' && !(r === 'MAINTAINER' && c.author.id !== u.id)) throw new HttpErr(403, 'FORBIDDEN', r === 'MAINTAINER' ? '维护者不能审批自己提交的修改单' : '没有审批权限');
  if (c.status !== 'PENDING') throw bad('只有待审批的修改单可以审批');
  const env = M.changeEnv(c);
  M.envWritable(env);
  c.decidedBy = M.userRef(u); c.decidedAt = Date.now(); c.decisionNote = body.note ?? '';
  M.logAudit({ ...M.auditBy(u), action: 'change.approve', projectId: c.projectId, env, changeId: c.id, reason: body.note });
  try { M.tryPublish(c, u); } catch (e) { M.fireEvent('change.conflict', c.projectId, env); throw e; }
  M.fireEvent('change.approved', c.projectId, env); M.fireEvent('change.published', c.projectId, env);
  return M.decorate(c);
});
route('post', '/changes/:id/reject', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  const r = M.projectRole(u, c.projectId);
  if (r !== 'ADMIN' && !(r === 'MAINTAINER' && c.author.id !== u.id)) throw new HttpErr(403, 'FORBIDDEN', '没有审批权限');
  if (c.status !== 'PENDING') throw bad('只有待审批的修改单可以驳回');
  if (!String(body.note ?? '').trim()) throw bad('驳回必须填写意见');
  c.decidedBy = M.userRef(u); c.decidedAt = Date.now(); c.decisionNote = body.note ?? '';
  M.setStatus(c, 'REJECTED', 'change.reject', u, body.note);
  M.fireEvent('change.rejected', c.projectId, M.changeEnv(c));
  return M.decorate(c);
});
route('post', '/changes/:id/withdraw', ({ params }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  const wr = M.projectRole(u, c.projectId);
  if (c.author.id !== u.id && wr !== 'ADMIN' && wr !== 'MAINTAINER') throw new HttpErr(403, 'FORBIDDEN', '只有作者、项目维护者或管理员可以撤回');
  if (!['DRAFT', 'PENDING'].includes(c.status)) throw bad('当前状态不能撤回');
  M.setStatus(c, 'WITHDRAWN', 'change.withdraw', u);
  return M.decorate(c);
});
route('post', '/changes/:id/rebase', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  if (c.author.id !== u.id) throw new HttpErr(403, 'FORBIDDEN', '只有作者可以重新编辑');
  if (c.status !== 'CONFLICT') throw bad('只有冲突状态的修改单可以更新到最新版本');
  const env = M.changeEnv(c);
  M.envWritable(env);
  const after = new Map<string, string | null>((body.items ?? []).map((i: any) => [`${i.service}\u0000${i.ruleKey}`, i.afterRules]));
  c.items.forEach((i) => {
    const k = `${i.service}\u0000${i.ruleKey}`;
    if (after.has(k)) { const v = after.get(k)!; if (v != null) { try { JSON.parse(v); } catch { throw bad(`规则 JSON 格式错误:${i.ruleKey}`); } } i.afterRules = v; }
    i.baseVersion = M.curVersion(env, i.service, i.ruleKey);
    i.beforeRules = M.curRule(env, i.service, i.ruleKey)?.rules ?? null;
  });
  M.setStatus(c, 'DRAFT', 'change.rebase', u);
  return M.decorate(c);
});

// ---------- 评论(1.1 §5.1) ----------
const MAX_COMMENTS = 500;
const commentOut = (c: M.CommentRow) => {
  const a = db.users.find((u) => u.id === c.authorId);
  return { id: c.id, changeId: c.changeId, itemId: c.itemId, author: { id: c.authorId, displayName: a?.displayName ?? '?' }, body: c.deleted ? null : c.body, deleted: c.deleted, createdAt: c.createdAt, updatedAt: c.updatedAt };
};
const findComment = (id: string) => { const c = db.comments.find((x) => x.id === Number(id)); if (!c) throw new HttpErr(404, 'NOT_FOUND', '评论不存在'); return c; };
const cleanBody = (v: unknown) => {
  const b = String(v ?? '').trim();
  if (b.length < 1 || b.length > 4000) throw bad('评论内容需要 1–4000 字');
  return b;
};
route('get', '/changes/:id/comments', ({ params }) => {
  const c = readable(M.currentUser(), M.findChange(Number(params.id)));
  return db.comments.filter((x) => x.changeId === c.id).sort((a, b) => a.id - b.id).map(commentOut);
});
route('post', '/changes/:id/comments', ({ params, body }) => {
  const u = M.currentUser(); const c = M.findChange(Number(params.id));
  M.requireRole(u, c.projectId, WRITE_ROLES); // 只读不能发评论
  const text = cleanBody(body.body);
  if (body.itemId != null && !c.items.some((i) => i.id === body.itemId)) throw bad('itemId 不属于该修改单');
  if (db.comments.filter((x) => x.changeId === c.id).length >= MAX_COMMENTS) throw bad('这张修改单的评论已达上限(500 条)');
  const row: M.CommentRow = { id: ++db.seq, changeId: c.id, itemId: body.itemId ?? null, authorId: u.id, body: text, deleted: false, createdAt: Date.now(), updatedAt: Date.now() };
  db.comments.push(row);
  M.logAudit({ ...M.auditBy(u), action: 'comment.create', projectId: c.projectId, env: M.changeEnv(c), changeId: c.id, after: text });
  M.fireEvent('change.commented', c.projectId, M.changeEnv(c));
  return commentOut(row);
});
route('patch', '/comments/:id', ({ params, body }) => {
  const u = M.currentUser(); const row = findComment(params.id); const c = M.findChange(row.changeId);
  if (row.authorId !== u.id) throw new HttpErr(403, 'FORBIDDEN', '只能修改自己的评论');
  if (row.deleted) throw bad('评论已删除');
  const before = row.body;
  row.body = cleanBody(body.body); row.updatedAt = Date.now();
  M.logAudit({ ...M.auditBy(u), action: 'comment.update', projectId: c.projectId, env: M.changeEnv(c), changeId: c.id, before, after: row.body });
  return commentOut(row);
});
route('delete', '/comments/:id', ({ params }) => {
  const u = M.currentUser(); const row = findComment(params.id); const c = M.findChange(row.changeId);
  const r = M.projectRole(u, c.projectId);
  if (row.authorId !== u.id && r !== 'ADMIN' && r !== 'MAINTAINER') throw new HttpErr(403, 'FORBIDDEN', '只能删除自己的评论');
  const before = row.body;
  row.deleted = true; row.body = null; row.updatedAt = Date.now(); // 软删除:列表里仍返回,保持对话顺序
  M.logAudit({ ...M.auditBy(u), action: 'comment.delete', projectId: c.projectId, env: M.changeEnv(c), changeId: c.id, before });
  return undefined;
});

// ---------- 编辑中提示(1.1 §5.2 ①,内存) ----------
route('put', '/presence', ({ body }) => {
  const u = M.currentUser(); const s = svc(body.service);
  M.requireRole(u, s.projectId, WRITE_ROLES); // 只读用户不能 PUT
  const env = M.envOf(body.env);
  const now = Date.now();
  const ex = M.livePresence().find((p) => p.env === env.key && p.service === s.service && p.ruleKey === body.ruleKey && p.userId === u.id);
  if (ex) ex.lastSeen = now;
  else {
    db.presence.push({ env: env.key, service: s.service, ruleKey: body.ruleKey, userId: u.id, since: now, lastSeen: now });
    const mine = db.presence.filter((p) => p.userId === u.id).sort((a, b) => a.lastSeen - b.lastSeen);
    while (mine.length > 20) db.presence = db.presence.filter((p) => p !== mine.shift()); // 每人最多 20 条,挤掉最早的
  }
  return undefined;
});
route('delete', '/presence', ({ body }) => {
  const u = M.currentUser(); const env = M.envOf(body.env);
  db.presence = db.presence.filter((p) => !(p.env === env.key && p.service === body.service && p.ruleKey === body.ruleKey && p.userId === u.id));
  return undefined;
});
route('get', '/presence', ({ q }) => {
  const u = M.currentUser(); const s = svc(q.get('service') || '');
  if (!svcVisible(u, s)) throw new HttpErr(403, 'FORBIDDEN', '没有权限查看该服务');
  return M.presenceOf(M.envOf(q.get('env')).key, s.service, u.id);
});

// ---------- 审计 ----------
function filterAudit(q: URLSearchParams, u: User) {
  const ids = M.visibleProjectIds(u);
  if (u.orgRole !== 'ADMIN' && !db.members.some((m) => m.userId === u.id && m.role !== 'VIEWER')) throw new HttpErr(403, 'FORBIDDEN', '当前角色不能查看审计');
  return db.audit.filter((a) => u.orgRole === 'ADMIN' || (a.projectId != null && ids.includes(a.projectId) && M.projectRole(u, a.projectId) !== 'VIEWER'))
    .filter((a) => !q.get('from') || a.at >= Number(q.get('from')))
    .filter((a) => !q.get('to') || a.at <= Number(q.get('to')))
    .filter((a) => !q.get('actor') || a.actorName.includes(q.get('actor')!))
    .filter((a) => !q.get('projectId') || a.projectId === Number(q.get('projectId')))
    .filter((a) => !q.get('env') || a.env === q.get('env'))
    .filter((a) => !q.get('service') || a.service === q.get('service'))
    .filter((a) => !q.get('ruleKey') || (a.ruleKey ?? '').includes(q.get('ruleKey')!))
    .filter((a) => { const f = q.get('action'); return !f || (f.endsWith('*') ? a.action.startsWith(f.slice(0, -1)) : a.action === f); })
    .filter((a) => !q.get('source') || a.source === q.get('source'));
}
const publicAudit = ({ prevHash: _p, hash: _h, ...rest }: M.AuditRow) => rest;
route('get', '/audit', ({ q }) => {
  const r = paged(filterAudit(q, M.currentUser()), q);
  return { ...r, items: r.items.map(({ beforeValue: _b, afterValue: _a, diff: _d, ...rest }) => publicAudit(rest as M.AuditRow)) }; // 列表省略大字段
});
route('get', '/audit/mcp', ({ q }) => mcpList(q));
route('get', '/audit/export', ({ q }) => exportAudit(q));
route('get', '/audit/verify', ({ q }) => {
  const u = M.currentUser(); M.requireAdmin(u);
  const fromId = num(q.get('fromId')), toId = num(q.get('toId'));
  const rows = [...db.audit].reverse().filter((a) => (!fromId || a.id >= fromId) && (!toId || a.id <= toId));
  let prev: string | null = null;
  let checked = 0;
  let broken: { id: number; problem: AuditVerifyProblem } | null = null;
  for (const a of rows) {
    checked++;
    if (!a.hash) { broken = { id: a.id, problem: 'MISSING_HASH' }; break; }
    if (prev != null ? a.prevHash !== prev : false) { broken = { id: a.id, problem: 'PREV_MISMATCH' }; break; }
    if (M.hashOf(a, a.prevHash) !== a.hash) { broken = { id: a.id, problem: 'HASH_MISMATCH' }; break; }
    prev = a.hash;
  }
  const head = rows[rows.length - 1];
  const result = { ok: !broken, checked, headId: head?.id ?? null, headHash: head?.hash ?? null, firstBrokenId: broken?.id ?? null, problem: broken?.problem ?? null };
  M.logAudit({ ...M.auditBy(u), action: 'audit.verify', reason: broken ? `异常:#${broken.id} ${broken.problem}` : `通过,${checked} 条` });
  return result;
});
route('get', '/audit/:id', ({ params }) => {
  const u = M.currentUser();
  const rec = filterAudit(new URLSearchParams(), u).find((a) => a.id === Number(params.id));
  if (!rec) throw new HttpErr(404, 'NOT_FOUND', '审计记录不存在');
  return publicAudit(rec);
});
function exportAudit(q: URLSearchParams) {
  const u = M.currentUser();
  if (u.orgRole !== 'ADMIN' && !db.members.some((m) => m.userId === u.id && m.role === 'MAINTAINER')) throw new HttpErr(403, 'FORBIDDEN', '当前角色不能导出审计');
  if (q.get('kind') === 'mcp') {
    const rows = mcpList(q).items;
    M.logAudit({ ...M.auditBy(u), action: 'audit.export', reason: `MCP ${rows.length} 条` });
    const body = q.get('format') === 'json' ? JSON.stringify(rows, null, 2) : '﻿' + ['id,service,at,caller,tool,target,success', ...(rows as any[]).map((r) => [r.id, r.service, r.at, r.caller, r.tool, r.target, r.success].join(','))].join('\n');
    return { __res: new Response(body, { headers: { 'Content-Type': q.get('format') === 'json' ? 'application/json' : 'text/csv; charset=utf-8', 'Content-Disposition': `attachment; filename="mcp-audit.${q.get('format') === 'json' ? 'json' : 'csv'}"` } }) };
  }
  const rows = filterAudit(q, u).map((r) => ({ ...publicAudit(r), envKey: r.env ?? null, prevHash: r.prevHash, hash: r.hash }));
  M.logAudit({ ...M.auditBy(u), action: 'audit.export', reason: `${rows.length} 条` });
  // 1.1:JSON 导出改为 {anchor, items},便于离线校验哈希链
  if (q.get('format') === 'json') return { __res: new Response(JSON.stringify({ anchor: null, items: rows }, null, 2), { headers: { 'Content-Type': 'application/json', 'Content-Disposition': 'attachment; filename="audit.json"' } }) };
  const cols = ['id', 'at', 'actorType', 'actorName', 'source', 'ip', 'action', 'projectId', 'envKey', 'service', 'ruleKey', 'changeId', 'reason', 'prevHash', 'hash'] as const;
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const csv = '﻿' + [cols.join(','), ...rows.map((r) => cols.map((c) => esc((r as Record<string, unknown>)[c])).join(','))].join('\n');
  return { __res: new Response(csv, { headers: { 'Content-Type': 'text/csv; charset=utf-8', 'Content-Disposition': 'attachment; filename="audit.csv"' } }) };
}
function mcpList(q: URLSearchParams): { available: boolean; items: any[]; total: number } {
  const u = M.currentUser();
  const env = M.envUsable(q.get('env'));
  if (!db.mcpAvailable) return { available: false, items: [], total: 0 };
  const ok = db.services.filter((s) => svcVisible(u, s)).map((s) => s.service);
  const rows = db.mcp.filter((r) => r.env === env.key && ok.includes(r.service))
    .filter((r) => !q.get('service') || r.service === q.get('service'))
    .filter((r) => !q.get('caller') || r.caller.includes(q.get('caller')!))
    .filter((r) => !q.get('tool') || r.tool.includes(q.get('tool')!))
    .filter((r) => !q.get('from') || r.at >= Number(q.get('from')))
    .filter((r) => !q.get('to') || r.at <= Number(q.get('to')));
  const p = paged(rows, q);
  return { available: true, ...p, items: p.items.map(({ env: _e, ...r }) => r) };
}

// ---------- Webhook ----------
const hook = (id: string) => { const w = db.webhooks.find((x) => x.id === Number(id)); if (!w) throw new HttpErr(404, 'NOT_FOUND', 'Webhook 不存在'); return w; };
const hookAccess = (u: User, pid: number | null) => { if (pid == null) M.requireAdmin(u); else M.requireRole(u, pid, ['ADMIN', 'MAINTAINER']); };
const maskUrl = (u: string) => { try { const x = new URL(u); return `${x.origin}/***`; } catch { return '***'; } };
const outHook = ({ secret: _s, ...w }: Webhook & { secret?: string }) => ({ ...w, environments: w.environments ?? [], url: maskUrl(w.url) });
const checkEnvs = (v: unknown): string[] => {
  const list = Array.isArray(v) ? (v as string[]) : [];
  list.forEach((k) => M.envOf(k));
  return list;
};
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
  const w = { id: ++db.seq, projectId: body.projectId ?? null, name: body.name, kind: body.kind, url: body.url, secret: body.secret, hasSecret: !!body.secret, events: body.events, enabled: body.enabled ?? true, environments: checkEnvs(body.environments) };
  db.webhooks.push(w);
  M.logAudit({ ...M.auditBy(u), action: 'webhook.create', projectId: w.projectId, reason: w.name });
  return outHook(w);
});
route('patch', '/webhooks/:id', ({ params, body }) => {
  const u = M.currentUser(); const w = hook(params.id); hookAccess(u, w.projectId);
  (['name', 'kind', 'url', 'events', 'enabled'] as const).forEach((k) => { if (body[k] !== undefined) (w as any)[k] = body[k]; });
  if (body.environments !== undefined) w.environments = checkEnvs(body.environments);
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
  if (body.env) M.envOf(body.env);
  const token = 'csapt_' + Array.from({ length: 40 }, () => b62[Math.floor(Math.random() * 62)]).join('');
  const t = { id: ++db.seq, name: body.name, prefix: token.slice(0, 8), scope: body.scope, projectId: body.projectId ?? null, env: body.scope === 'REGISTER' ? body.env ?? null : null, createdBy: u.displayName, createdAt: Date.now(), lastUsedAt: null, revoked: false };
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

export { handlers };
