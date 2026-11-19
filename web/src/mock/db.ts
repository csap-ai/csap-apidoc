// mock 后端的内存数据库与业务逻辑,行为对照 docs/design/team-1.0.md。仅开发 / 测试使用。
import { createTwoFilesPatch } from 'diff';
import type {
  AuditRecord, Change, ChangeItem, ChangeStatus, ConflictItem, DocPayload, License, McpAuditRecord, Member, Project, ProjectRole,
  ServiceInfo, TokenInfo, User, Webhook, WebhookDelivery,
} from '@/api/types';
import { fixtureDocs, seedRules } from './fixtures';

export class HttpErr extends Error {
  constructor(public status: number, public code: string, message: string, public extra?: Record<string, unknown>) {
    super(message);
  }
}

interface UserRow extends User { password: string }
interface RuleRow { rules: string | null; version: number; updatedBy: string; updatedAt: number }
interface HistoryRow { id: number; service: string; ruleKey: string; version: number; updatedBy: string; updatedAt: number; beforeRules: string | null; afterRules: string | null; changeId: number | null }

export interface Db {
  initialized: boolean;
  orgName: string;
  seq: number;
  users: UserRow[];
  projects: Project[];
  members: { projectId: number; userId: number; role: ProjectRole; createdAt: number }[];
  services: ServiceInfo[];
  docs: Record<string, DocPayload>;
  rules: Record<string, Record<string, RuleRow>>;
  history: HistoryRow[];
  changes: Change[];
  audit: AuditRecord[];
  mcp: McpAuditRecord[];
  mcpAvailable: boolean;
  webhooks: (Webhook & { secret?: string })[];
  deliveries: WebhookDelivery[];
  tokens: TokenInfo[];
  licenseStatus: License['status'];
  licenseMax: number;
  licensee: string | null;
  licenseExpiresAt: number | null;
  sessionUserId: number | null;
}

const now = () => Date.now();
export let db: Db = emptyDb();

function emptyDb(): Db {
  return {
    initialized: false, orgName: '', seq: 100, users: [], projects: [], members: [], services: [], docs: {}, rules: {}, history: [], changes: [],
    audit: [], mcp: [], mcpAvailable: true, webhooks: [], deliveries: [], tokens: [], licenseStatus: 'TRIAL', licenseMax: 5, licensee: null, licenseExpiresAt: null,
    sessionUserId: null,
  };
}
const nid = () => ++db.seq;

/** 重置;seeded=true 时直接带演示数据与账号(测试用) */
export function resetDb(seeded = false) {
  db = emptyDb();
  if (seeded) {
    db.initialized = true;
    db.orgName = '示例科技';
    seedDemo();
  }
}

export function seedDemo(adminDisplay = '管理员') {
  const t0 = now();
  const mkUser = (username: string, displayName: string, orgRole: 'ADMIN' | 'MEMBER', password: string): UserRow => {
    const u: UserRow = { id: nid(), username, displayName, status: 'ACTIVE', orgRole, createdAt: t0, password };
    db.users.push(u);
    return u;
  };
  if (!db.users.length) mkUser('admin', adminDisplay, 'ADMIN', 'admin1234');
  const zs = mkUser('zhangsan', '张三', 'MEMBER', 'demo1234');
  const ls = mkUser('lisi', '李四', 'MEMBER', 'demo1234');
  const ww = mkUser('wangwu', '王五', 'MEMBER', 'demo1234');
  const pOrder: Project = { id: nid(), name: '订单', description: '订单与支付相关服务', requireApproval: true, createdAt: t0, updatedAt: t0 };
  const pUser: Project = { id: nid(), name: '用户中心', description: '账号与资料', requireApproval: false, createdAt: t0, updatedAt: t0 };
  db.projects.push(pOrder, pUser);
  const m = (projectId: number, u: UserRow, role: ProjectRole) => db.members.push({ projectId, userId: u.id, role, createdAt: t0 });
  m(pOrder.id, zs, 'MAINTAINER'); m(pOrder.id, ls, 'DEVELOPER'); m(pOrder.id, ww, 'VIEWER'); m(pUser.id, zs, 'DEVELOPER'); m(pUser.id, ls, 'MAINTAINER');
  db.docs = fixtureDocs();
  const svc = (service: string, projectId: number | null, language: string, fw: string, status: ServiceInfo['status'], instances: number, source: ServiceInfo['source'] = 'REGISTERED'): ServiceInfo => ({
    service, projectId, projectName: projectId ? db.projects.find((p) => p.id === projectId)!.name : null, baseUrl: `http://10.0.0.${db.seq % 200}:8080`, docPath: '/csap/apidoc', language,
    frameworkVersion: fw, source, status, instances, lastSeenAt: t0 - 5000, lastFetchedAt: t0 - 20000,
  });
  db.services.push(svc('order-service', pOrder.id, 'java', '1.5.0', 'ONLINE', 2), svc('user-service', pUser.id, 'java', '1.5.0', 'ONLINE', 1), svc('gateway-go', null, 'go', '0.3.0', 'STALE', 1), svc('legacy-report', null, 'java', '1.4.0', 'OFFLINE', 0, 'MANUAL'));
  Object.entries(seedRules).forEach(([service, rs]) => {
    db.rules[service] = {};
    Object.entries(rs).forEach(([key, rules]) => {
      const row = { rules, version: 1, updatedBy: '系统初始导入', updatedAt: t0 - 86400000 };
      db.rules[service][key] = row;
      db.history.push({ id: nid(), service, ruleKey: key, version: 1, updatedBy: row.updatedBy, updatedAt: row.updatedAt, beforeRules: null, afterRules: rules, changeId: null });
    });
  });
  db.tokens.push({ id: nid(), name: '订单服务注册', prefix: 'csapt_Ab', scope: 'REGISTER', projectId: pOrder.id, createdBy: '管理员', createdAt: t0 - 86400000, lastUsedAt: t0 - 30000, revoked: false });
  db.webhooks.push({ id: nid(), projectId: pOrder.id, name: '订单群通知', kind: 'WECOM', url: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=***', hasSecret: false, events: ['change.submitted', 'change.published'], enabled: true });
  db.mcp.push(
    { id: nid(), service: 'order-service', at: t0 - 600000, caller: 'ide-agent', remoteAddress: '10.0.0.8', tool: 'search_api', target: '/api/orders/create', arguments: '{"q":"订单"}', status: 200, success: true, result: '命中 3 条', durationMs: 42 },
    { id: nid(), service: 'user-service', at: t0 - 300000, caller: 'ide-agent', remoteAddress: '10.0.0.8', tool: 'try_api', target: '/api/users/profile', arguments: '{}', status: 401, success: false, result: '未登录', durationMs: 18 },
  );
  logAudit({ actor: { type: 'SYSTEM', name: '系统' }, source: 'SYSTEM', action: 'system.seed', reason: '演示数据初始化' });
}

// ---------- 会话 ----------
export function currentUser(): UserRow {
  if (!db.initialized) throw new HttpErr(409, 'SETUP_REQUIRED', '系统尚未初始化');
  const u = db.users.find((x) => x.id === db.sessionUserId && x.status === 'ACTIVE');
  if (!u) throw new HttpErr(401, 'UNAUTHORIZED', '请先登录');
  return u;
}
export const projectRole = (u: UserRow, projectId: number | null | undefined): ProjectRole | 'ADMIN' | null =>
  u.orgRole === 'ADMIN' ? 'ADMIN' : db.members.find((m) => m.projectId === projectId && m.userId === u.id)?.role ?? null;
export function requireAdmin(u: UserRow) {
  if (u.orgRole !== 'ADMIN') throw new HttpErr(403, 'FORBIDDEN', '仅管理员可以执行该操作');
}
export function requireRole(u: UserRow, projectId: number | null | undefined, roles: (ProjectRole | 'ADMIN')[]) {
  const r = projectRole(u, projectId);
  if (!r || !roles.includes(r)) throw new HttpErr(403, 'FORBIDDEN', '当前角色没有权限执行该操作');
}
export const visibleProjectIds = (u: UserRow): number[] =>
  u.orgRole === 'ADMIN' ? db.projects.map((p) => p.id) : db.members.filter((m) => m.userId === u.id).map((m) => m.projectId);

export function meOf(u: UserRow) {
  const active = db.users.filter((x) => x.status === 'ACTIVE').length;
  return {
    id: u.id, username: u.username, displayName: u.displayName, orgRole: u.orgRole,
    projects: db.members.filter((m) => m.userId === u.id).map((m) => ({ id: m.projectId, name: db.projects.find((p) => p.id === m.projectId)!.name, role: m.role })),
    license: { status: db.licenseStatus, maxMembers: db.licenseMax, activeMembers: active, expiresAt: db.licenseExpiresAt },
  };
}
export const publicUser = ({ password: _p, ...u }: UserRow): User => u;

// ---------- 审计 ----------
export function logAudit(a: {
  actor: { type: AuditRecord['actorType']; id?: number; name: string }; source: string; action: string; projectId?: number | null; service?: string | null;
  ruleKey?: string | null; changeId?: number | null; before?: string | null; after?: string | null; reason?: string | null;
}) {
  const diff = a.before != null || a.after != null ? unified(a.before ?? null, a.after ?? null) : null;
  db.audit.unshift({
    id: nid(), at: now(), actorType: a.actor.type, actorId: a.actor.id ?? null, actorName: a.actor.name, source: a.source, ip: '127.0.0.1', action: a.action,
    projectId: a.projectId ?? null, service: a.service ?? null, ruleKey: a.ruleKey ?? null, changeId: a.changeId ?? null,
    beforeValue: a.before ?? null, afterValue: a.after ?? null, diff, reason: a.reason ?? null,
  });
}
export const auditBy = (u: UserRow, source = 'CONSOLE') => ({ actor: { type: 'USER' as const, id: u.id, name: u.displayName }, source });

export const pretty = (s: string | null | undefined) => {
  if (s == null) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
};
export function unified(a: string | null, b: string | null): string {
  const x = pretty(a), y = pretty(b);
  if (x === y) return '';
  return createTwoFilesPatch('before', 'after', x ? x + '\n' : '', y ? y + '\n' : '', '', '', { context: 3 });
}
export const sameJson = (a: string | null, b: string | null) => {
  if (a == null || b == null) return a == b;
  try { return JSON.stringify(JSON.parse(a)) === JSON.stringify(JSON.parse(b)); } catch { return a === b; }
};

// ---------- 规则 ----------
export const curRule = (service: string, key: string): RuleRow | undefined => db.rules[service]?.[key];
export const curVersion = (service: string, key: string) => curRule(service, key)?.version ?? 0;
export function writeRule(service: string, key: string, rules: string | null, by: string, changeId: number | null): number {
  const before = curRule(service, key);
  const version = (before?.version ?? 0) + 1;
  db.rules[service] ??= {};
  db.rules[service][key] = { rules, version, updatedBy: by, updatedAt: now() };
  const id = nid();
  db.history.unshift({ id, service, ruleKey: key, version, updatedBy: by, updatedAt: now(), beforeRules: before?.rules ?? null, afterRules: rules, changeId });
  return id;
}
/** 演示用:模拟服务侧 DevTools 直接改规则(制造冲突) */
export function editOutside(service: string, key: string, rules: string, by = '服务 DevTools') {
  const before = curRule(service, key)?.rules ?? null;
  writeRule(service, key, rules, by, null);
  logAudit({ actor: { type: 'SERVICE', name: by }, source: 'SERVICE', action: 'rule.changed_outside', service, ruleKey: key, before, after: rules, projectId: db.services.find((s) => s.service === service)?.projectId });
}

// ---------- 修改单 ----------
export const findChange = (id: number): Change => {
  const c = db.changes.find((x) => x.id === id);
  if (!c) throw new HttpErr(404, 'NOT_FOUND', '修改单不存在');
  return c;
};
export const userRef = (u: UserRow) => ({ id: u.id, displayName: u.displayName });

export function decorate(c: Change, withDiff = true): Change {
  const items = c.items.map((i) => {
    const cur = curVersion(i.service, i.ruleKey);
    const open = c.status === 'DRAFT' || c.status === 'PENDING' || c.status === 'CONFLICT';
    const conflict = open && cur !== i.baseVersion && !sameJson(curRule(i.service, i.ruleKey)?.rules ?? null, i.afterRules);
    return { ...i, currentVersion: cur, conflict, diff: withDiff ? unified(i.beforeRules ?? null, i.afterRules) : undefined };
  });
  return { ...c, items };
}

export function normalizeItems(input: { service: string; ruleKey: string; baseVersion: number; afterRules: string | null }[]): ChangeItem[] {
  if (!input?.length) throw new HttpErr(400, 'VALIDATION', '至少需要一条规则条目');
  return input.map((i) => {
    if (!i.service || !i.ruleKey) throw new HttpErr(400, 'VALIDATION', '服务与规则 Key 不能为空');
    if (i.afterRules != null) { try { JSON.parse(i.afterRules); } catch { throw new HttpErr(400, 'VALIDATION', `规则 JSON 格式错误:${i.ruleKey}`); } }
    return { id: nid(), service: i.service, ruleKey: i.ruleKey, baseVersion: i.baseVersion, beforeRules: curRule(i.service, i.ruleKey)?.rules ?? null, afterRules: i.afterRules };
  });
}

function conflictsOf(c: Change): ConflictItem[] {
  return c.items
    .filter((i) => curVersion(i.service, i.ruleKey) !== i.baseVersion && !sameJson(curRule(i.service, i.ruleKey)?.rules ?? null, i.afterRules))
    .map((i) => ({ service: i.service, ruleKey: i.ruleKey, baseVersion: i.baseVersion, currentVersion: curVersion(i.service, i.ruleKey), currentRules: curRule(i.service, i.ruleKey)?.rules ?? null }));
}

/** 检查 + 发布;冲突 → 状态置 CONFLICT 并抛 409 */
export function tryPublish(c: Change, by: UserRow): void {
  const conflicts = conflictsOf(c);
  if (conflicts.length) {
    c.status = 'CONFLICT';
    c.updatedAt = now();
    logAudit({ ...auditBy(by), action: 'change.conflict', projectId: c.projectId, changeId: c.id, reason: '发布时版本不符' });
    throw new HttpErr(409, 'CONFLICT', '规则已被他人修改,请 Rebase 后再提交', { items: conflicts });
  }
  c.items.forEach((i) => {
    if (sameJson(curRule(i.service, i.ruleKey)?.rules ?? null, i.afterRules)) return; // 内容相同跳过
    const hid = writeRule(i.service, i.ruleKey, i.afterRules, by.displayName, c.id);
    i.publishedVersion = curVersion(i.service, i.ruleKey);
    void hid;
    logAudit({ ...auditBy(by), action: 'change.publish', projectId: c.projectId, service: i.service, ruleKey: i.ruleKey, changeId: c.id, before: i.beforeRules ?? null, after: i.afterRules, reason: c.title });
  });
  c.status = 'PUBLISHED';
  c.publishedAt = now();
  c.updatedAt = now();
}

export function setStatus(c: Change, s: ChangeStatus, action: string, u: UserRow, reason?: string) {
  c.status = s;
  c.updatedAt = now();
  logAudit({ ...auditBy(u), action, projectId: c.projectId, changeId: c.id, reason: reason ?? c.title });
}

export function members(projectId: number): Member[] {
  return db.members.filter((m) => m.projectId === projectId).map((m) => {
    const u = db.users.find((x) => x.id === m.userId)!;
    return { userId: u.id, username: u.username, displayName: u.displayName, role: m.role, createdAt: m.createdAt };
  });
}
export function activeUsers() { return db.users.filter((u) => u.status === 'ACTIVE').length; }
export function deliver(w: Webhook, event: string): WebhookDelivery {
  const d: WebhookDelivery = { id: nid(), webhookId: w.id, event, at: now(), attempt: 1, status: 'SUCCESS', httpStatus: 200, error: null };
  db.deliveries.unshift(d);
  return d;
}
export function fireEvent(event: string, projectId: number) {
  db.webhooks.filter((w) => w.enabled && (w.projectId == null || w.projectId === projectId) && w.events.includes(event)).forEach((w) => deliver(w, event));
}
