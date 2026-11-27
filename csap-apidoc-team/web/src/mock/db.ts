// mock 后端的内存数据库与业务逻辑,行为对照 docs/design/team-1.0.md 与 team-1.1.md。仅开发 / 测试使用。
import { createTwoFilesPatch } from 'diff';
import type {
  AuditRecord, Change, ChangeItem, ChangeStatus, ConflictItem, DocPayload, Env, EnvDiffService, McpAuditRecord, Member, OpenChange, PresenceEntry,
  Project, ProjectRole, ServiceEnvInfo, ServiceInfo, TokenInfo, User, Webhook, WebhookDelivery,
} from '@/api/types';
import { fixtureDocs, seedRules } from './fixtures';

export class HttpErr extends Error {
  constructor(public status: number, public code: string, message: string, public extra?: Record<string, unknown>) {
    super(message);
  }
}

interface UserRow extends User { password: string }
interface RuleRow { rules: string | null; version: number; updatedBy: string; updatedAt: number }
interface HistoryRow { id: number; env: string; service: string; ruleKey: string; version: number; updatedBy: string; updatedAt: number; beforeRules: string | null; afterRules: string | null; changeId: number | null }
/** 服务(跨环境同一个,项目归属不分环境) */
export interface ServiceRow { service: string; projectId: number | null; language: string; frameworkVersion: string }
/** 服务在某环境的地址、实例与状态 */
export type SvcEnvRow = ServiceEnvInfo & { service: string };
export interface CommentRow { id: number; changeId: number; itemId: number | null; authorId: number; body: string | null; deleted: boolean; createdAt: number; updatedAt: number }
export interface PresenceRow { env: string; service: string; ruleKey: string; userId: number; since: number; lastSeen: number; sticky?: boolean }
export type AuditRow = AuditRecord & { prevHash: string; hash: string };
export type Scenario = 'multi' | 'single';

export const PRESENCE_TTL_MS = 90_000;
export const GENESIS_HASH = '0'.repeat(64);

export interface Db {
  /** 演示场景:multi = dev / test / prod 三个环境(prod 受保护);single = 只有一个环境 default,界面应与 1.0 一致 */
  scenario: Scenario;
  initialized: boolean;
  orgName: string;
  seq: number;
  users: UserRow[];
  projects: Project[];
  members: { projectId: number; userId: number; role: ProjectRole; createdAt: number }[];
  envs: Env[];
  services: ServiceRow[];
  svcEnvs: SvcEnvRow[];
  docs: Record<string, DocPayload>;
  /** env → service → ruleKey → 当前规则 */
  rules: Record<string, Record<string, Record<string, RuleRow>>>;
  history: HistoryRow[];
  changes: Change[];
  comments: CommentRow[];
  presence: PresenceRow[];
  audit: AuditRow[];
  mcp: (McpAuditRecord & { env: string })[];
  mcpAvailable: boolean;
  webhooks: (Webhook & { secret?: string })[];
  deliveries: WebhookDelivery[];
  tokens: TokenInfo[];
  sessionUserId: number | null;
}

const now = () => Date.now();
export let db: Db = emptyDb();

function emptyDb(scenario: Scenario = 'multi'): Db {
  return {
    scenario, initialized: false, orgName: '', seq: 100, users: [], projects: [], members: [], envs: [], services: [], svcEnvs: [], docs: {}, rules: {}, history: [], changes: [],
    comments: [], presence: [], audit: [], mcp: [], mcpAvailable: true, webhooks: [], deliveries: [], tokens: [],
    sessionUserId: null,
  };
}
const nid = () => ++db.seq;

/** 重置;seeded=true 时直接带演示数据与账号(仅供测试) */
export function resetDb(seeded = false, scenario: Scenario = 'multi') {
  db = emptyDb(scenario);
  if (seeded) {
    db.initialized = true;
    db.orgName = '示例科技';
    seedDemo();
  }
}
/** 初始化向导之前就要定下场景(mock 启动时按 URL / sessionStorage 决定) */
export function setScenario(s: Scenario) {
  db.scenario = s;
}

// ---------- 环境 ----------
export const defaultEnvKey = (): string => (db.envs.find((e) => e.default) ?? db.envs[0])?.key ?? 'default';
export function envOf(key?: string | null): Env {
  const k = key || defaultEnvKey();
  const e = db.envs.find((x) => x.key === k);
  if (!e) throw new HttpErr(400, 'VALIDATION', `环境不存在:${k}`);
  return e;
}
/** 读写该环境开源表的操作:环境 DOWN → ENV_UNAVAILABLE(503) */
export function envUsable(key?: string | null): Env {
  const e = envOf(key);
  if (e.status === 'DOWN') throw new HttpErr(503, 'ENV_UNAVAILABLE', `环境「${e.name}」当前不可用:${e.error ?? '数据库连接失败'}`);
  return e;
}
/** 向该环境发布:只读 → FORBIDDEN;DOWN → ENV_UNAVAILABLE */
export function envWritable(key?: string | null): Env {
  const e = envOf(key);
  if (e.readOnly) throw new HttpErr(403, 'FORBIDDEN', `环境「${e.name}」只读,不能向它发布`);
  return envUsable(key);
}
/** 供演示 / 测试:改环境状态 */
export function setEnvStatus(key: string, status: 'UP' | 'DOWN', error: string | null = null) {
  const e = envOf(key);
  e.status = status;
  e.error = status === 'DOWN' ? error ?? '连接超时' : null;
}

// ---------- 哈希链(mock 用的简化指纹,不是真 SHA-256;真实算法见 team-1.1.md §6) ----------
export function fakeHash(s: string): string {
  let out = '';
  for (let i = 0; out.length < 64; i++) {
    let h1 = 0xdeadbeef ^ i, h2 = 0x41c6ce57 ^ i;
    for (let k = 0; k < s.length; k++) {
      const ch = s.charCodeAt(k);
      h1 = Math.imul(h1 ^ ch, 2654435761);
      h2 = Math.imul(h2 ^ ch, 1597334677);
    }
    h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
    h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
    out += (h2 >>> 0).toString(16).padStart(8, '0') + (h1 >>> 0).toString(16).padStart(8, '0');
  }
  return out.slice(0, 64);
}
const canonical = (a: AuditRecord, prev: string) =>
  JSON.stringify([a.id, a.at, a.actorType, a.actorId ?? null, a.actorName, a.source, a.ip ?? null, a.action, a.projectId ?? null, a.env ?? null, a.service ?? null, a.ruleKey ?? null, a.changeId ?? null, a.beforeValue ?? null, a.afterValue ?? null, a.diff ?? null, a.reason ?? null, null, prev]);
export const hashOf = (a: AuditRecord, prev: string) => fakeHash(prev + '\n' + canonical(a, prev));
/** 演示:绕过团队版直接改一条审计记录的内容(不更新哈希),之后「校验哈希链」应报 HASH_MISMATCH */
export function tamperAudit(id: number, reason = '被改过的原因') {
  const a = db.audit.find((x) => x.id === id);
  if (a) a.reason = reason;
}

export function seedDemo(adminDisplay = '管理员') {
  const t0 = now();
  const multi = db.scenario === 'multi';
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

  // 环境:multi = 开发 / 测试 / 生产(生产受保护);single = 默认
  db.envs = multi
    ? [
      { key: 'dev', name: '开发', order: 0, protected: false, readOnly: false, status: 'UP', error: null, default: true },
      { key: 'test', name: '测试', order: 1, protected: false, readOnly: false, status: 'UP', error: null, default: false },
      { key: 'prod', name: '生产', order: 2, protected: true, readOnly: false, status: 'UP', error: null, default: false },
    ]
    : [{ key: 'default', name: '默认', order: 0, protected: false, readOnly: false, status: 'UP', error: null, default: true }];

  // 服务与各环境的地址 / 状态
  db.docs = fixtureDocs();
  db.services.push(
    { service: 'order-service', projectId: pOrder.id, language: 'java', frameworkVersion: '1.5.0' },
    { service: 'user-service', projectId: pUser.id, language: 'java', frameworkVersion: '1.5.0' },
    { service: 'gateway-go', projectId: null, language: 'go', frameworkVersion: '0.3.0' },
    { service: 'legacy-report', projectId: null, language: 'java', frameworkVersion: '1.4.0' },
  );
  const se = (env: string, service: string, status: ServiceInfo['status'], instances: number, source: 'REGISTERED' | 'MANUAL' = 'REGISTERED') => {
    const idx = Math.max(0, db.envs.findIndex((e) => e.key === env));
    db.svcEnvs.push({ service, env, baseUrl: `http://10.0.${idx}.${db.svcEnvs.length + 10}:8080`, docPath: '/csap/apidoc', source, status, instances, lastSeenAt: t0 - 5000, lastFetchedAt: t0 - 20000 });
  };
  if (multi) {
    se('dev', 'order-service', 'ONLINE', 2); se('test', 'order-service', 'ONLINE', 1); se('prod', 'order-service', 'ONLINE', 3);
    se('dev', 'user-service', 'ONLINE', 1); se('test', 'user-service', 'ONLINE', 1);
    se('dev', 'gateway-go', 'STALE', 1);
    se('test', 'legacy-report', 'OFFLINE', 0, 'MANUAL');
  } else {
    se('default', 'order-service', 'ONLINE', 2); se('default', 'user-service', 'ONLINE', 1); se('default', 'gateway-go', 'STALE', 1); se('default', 'legacy-report', 'OFFLINE', 0, 'MANUAL');
  }

  // 规则:每个环境每条规则按版本序列写入(并写 history)
  const J = JSON.stringify;
  const create1 = seedRules['order-service']['com.demo.order.OrderController.create'];
  const create2 = J({ amount: { required: true, min: 0.01, message: '订单金额必须大于 0' }, remark: { maxLength: 300 } });
  const create3 = J({ amount: { required: true, min: 0.01, max: 100000, message: '订单金额必须在 0.01 ~ 100000 之间' }, remark: { maxLength: 300 } });
  const cancel = seedRules['order-service']['com.demo.order.OrderController.cancel'];
  const register = seedRules['user-service']['com.demo.user.UserController.register'];
  const refund = J({ orderId: { required: true }, reason: { required: true, maxLength: 200 } });
  const exportRule = J({ from: { required: true, pattern: '^\\d{4}-\\d{2}-\\d{2}$' }, to: { required: true } });
  const K = 'com.demo.order.OrderController.';
  const seedKey = (env: string, service: string, key: string, versions: string[]) => {
    versions.forEach((rules, i) => {
      const at = t0 - (versions.length - i) * 86400000;
      const by = i === 0 ? '系统初始导入' : i === 1 ? '张三' : '李四';
      writeRule(env, service, key, rules, by, null, at);
    });
  };
  if (multi) {
    seedKey('dev', 'order-service', K + 'create', [create1, create2, create3]);
    seedKey('test', 'order-service', K + 'create', [create1, create2]);
    seedKey('prod', 'order-service', K + 'create', [create1]);
    (['dev', 'test', 'prod'] as const).forEach((e) => seedKey(e, 'order-service', K + 'cancel', [cancel]));
    seedKey('dev', 'order-service', K + 'refund', [refund]); // 仅开发有 → 推广时是「仅来源有」
    seedKey('test', 'order-service', K + 'export', [exportRule]); // 仅测试有 → dev→test 时是「仅目标有」
    (['dev', 'test'] as const).forEach((e) => seedKey(e, 'user-service', 'com.demo.user.UserController.register', [register]));
  } else {
    seedKey('default', 'order-service', K + 'create', [create1]);
    seedKey('default', 'order-service', K + 'cancel', [cancel]);
    seedKey('default', 'user-service', 'com.demo.user.UserController.register', [register]);
  }

  db.tokens.push({ id: nid(), name: '订单服务注册', prefix: 'csapt_Ab', scope: 'REGISTER', projectId: pOrder.id, env: multi ? 'dev' : null, createdBy: '管理员', createdAt: t0 - 86400000, lastUsedAt: t0 - 30000, revoked: false });
  db.webhooks.push({ id: nid(), projectId: pOrder.id, name: '订单群通知', kind: 'WECOM', url: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=***', hasSecret: false, events: ['change.submitted', 'change.published'], enabled: true, environments: multi ? ['prod'] : [] });
  db.mcp.push(
    { id: nid(), env: defaultEnvKey(), service: 'order-service', at: t0 - 600000, caller: 'ide-agent', remoteAddress: '10.0.0.8', tool: 'search_api', target: '/api/orders/create', arguments: '{"q":"订单"}', status: 200, success: true, result: '命中 3 条', durationMs: 42 },
    { id: nid(), env: defaultEnvKey(), service: 'user-service', at: t0 - 300000, caller: 'ide-agent', remoteAddress: '10.0.0.8', tool: 'try_api', target: '/api/users/profile', arguments: '{}', status: 401, success: false, result: '未登录', durationMs: 18 },
  );
  logAudit({ actor: { type: 'SYSTEM', name: '系统' }, source: 'SYSTEM', action: 'system.seed', reason: '演示数据初始化' });

  if (multi) {
    // 演示"未完成的修改单涉及同一条规则"与评论:李四在开发环境提交了一张待审批的单子
    const key = K + 'create';
    const c: Change = {
      id: nid(), projectId: pOrder.id, projectName: pOrder.name, title: '放宽订单金额上限', reason: '运营活动需要更大的订单金额', kind: 'EDIT', status: 'PENDING',
      author: userRef(ls), createdAt: t0 - 3600000, updatedAt: t0 - 3000000, submittedAt: t0 - 3000000, decidedBy: null, decidedAt: null, decisionNote: null, publishedAt: null,
      env: 'dev', sourceEnv: null,
      items: [{ id: nid(), service: 'order-service', ruleKey: key, baseVersion: curVersion('dev', 'order-service', key), beforeRules: curRule('dev', 'order-service', key)?.rules ?? null, afterRules: J({ amount: { required: true, min: 0.01, max: 500000 }, remark: { maxLength: 300 } }) }],
    };
    db.changes.unshift(c);
    const itemId = c.items[0].id!;
    db.comments.push(
      { id: nid(), changeId: c.id, itemId, authorId: zs.id, body: '上限 50 万会不会太大?建议先和风控确认。', deleted: false, createdAt: t0 - 2400000, updatedAt: t0 - 2400000 },
      { id: nid(), changeId: c.id, itemId: null, authorId: ls.id, body: '已经和风控确认过,活动期间可以放宽。', deleted: false, createdAt: t0 - 1800000, updatedAt: t0 - 1800000 },
    );
    logAudit({ actor: { type: 'USER', id: ls.id, name: ls.displayName }, source: 'CONSOLE', action: 'change.create', projectId: pOrder.id, env: 'dev', changeId: c.id, reason: c.title });
    // 演示"有人正在编辑":李四(常驻,不过期)正在开发环境编辑这条规则
    db.presence.push({ env: 'dev', service: 'order-service', ruleKey: key, userId: ls.id, since: t0 - 120000, lastSeen: t0, sticky: true });
  }
}

// ---------- 会话 ----------
export function currentUser(): UserRow {
  if (!db.initialized) throw new HttpErr(412, 'SETUP_REQUIRED', '系统尚未初始化');
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
  return {
    id: u.id, username: u.username, displayName: u.displayName, orgRole: u.orgRole,
    projects: db.members.filter((m) => m.userId === u.id).map((m) => ({ id: m.projectId, name: db.projects.find((p) => p.id === m.projectId)!.name, role: m.role })),
  };
}
export const publicUser = ({ password: _p, ...u }: UserRow): User => u;

// ---------- 审计 ----------
export function logAudit(a: {
  actor: { type: AuditRecord['actorType']; id?: number; name: string }; source: string; action: string; projectId?: number | null; env?: string | null; service?: string | null;
  ruleKey?: string | null; changeId?: number | null; before?: string | null; after?: string | null; reason?: string | null;
}) {
  const diff = a.before != null || a.after != null ? unified(a.before ?? null, a.after ?? null) : null;
  const rec: AuditRecord = {
    id: nid(), at: now(), actorType: a.actor.type, actorId: a.actor.id ?? null, actorName: a.actor.name, source: a.source, ip: '127.0.0.1', action: a.action,
    projectId: a.projectId ?? null, env: a.env ?? null, service: a.service ?? null, ruleKey: a.ruleKey ?? null, changeId: a.changeId ?? null,
    beforeValue: a.before ?? null, afterValue: a.after ?? null, diff, reason: a.reason ?? null,
  };
  const prev = db.audit[0]?.hash ?? GENESIS_HASH;
  db.audit.unshift({ ...rec, prevHash: prev, hash: hashOf(rec, prev) });
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

// ---------- 规则(按环境) ----------
export const curRule = (env: string, service: string, key: string): RuleRow | undefined => db.rules[env]?.[service]?.[key];
export const curVersion = (env: string, service: string, key: string) => curRule(env, service, key)?.version ?? 0;
export function writeRule(env: string, service: string, key: string, rules: string | null, by: string, changeId: number | null, at = now()): number {
  const before = curRule(env, service, key);
  const version = (before?.version ?? 0) + 1;
  db.rules[env] ??= {};
  db.rules[env][service] ??= {};
  db.rules[env][service][key] = { rules, version, updatedBy: by, updatedAt: at };
  const id = nid();
  db.history.unshift({ id, env, service, ruleKey: key, version, updatedBy: by, updatedAt: at, beforeRules: before?.rules ?? null, afterRules: rules, changeId });
  return id;
}
/** 演示用:模拟服务侧 DevTools 直接改规则(制造冲突) */
export function editOutside(service: string, key: string, rules: string, by = '服务 DevTools', env = defaultEnvKey()) {
  const before = curRule(env, service, key)?.rules ?? null;
  writeRule(env, service, key, rules, by, null);
  logAudit({ actor: { type: 'SERVICE', name: by }, source: 'SERVICE', action: 'rule.changed_outside', env, service, ruleKey: key, before, after: rules, projectId: db.services.find((s) => s.service === service)?.projectId });
}

// ---------- 服务(按环境) ----------
export function serviceOut(row: ServiceRow, envKey?: string | null): ServiceInfo {
  const k = envKey || defaultEnvKey();
  const mine = db.svcEnvs.filter((x) => x.service === row.service).sort((a, b) => db.envs.findIndex((e) => e.key === a.env) - db.envs.findIndex((e) => e.key === b.env));
  const cur = mine.find((x) => x.env === k);
  return {
    service: row.service, projectId: row.projectId, projectName: row.projectId ? db.projects.find((p) => p.id === row.projectId)?.name ?? null : null,
    baseUrl: cur?.baseUrl ?? null, docPath: cur?.docPath ?? '/csap/apidoc', language: row.language, frameworkVersion: row.frameworkVersion,
    source: cur?.source ?? mine[0]?.source ?? 'MANUAL', status: cur?.status ?? 'OFFLINE', instances: cur?.instances ?? 0, lastSeenAt: cur?.lastSeenAt ?? null, lastFetchedAt: cur?.lastFetchedAt ?? null,
    envs: mine.map(({ service: _s, ...e }) => e),
  };
}
export const svcEnvOf = (service: string, envKey?: string | null) => db.svcEnvs.find((x) => x.service === service && x.env === (envKey || defaultEnvKey()));

// ---------- 修改单 ----------
export const findChange = (id: number): Change => {
  const c = db.changes.find((x) => x.id === id);
  if (!c) throw new HttpErr(404, 'NOT_FOUND', '修改单不存在');
  return c;
};
export const userRef = (u: Pick<UserRow, 'id' | 'displayName'>) => ({ id: u.id, displayName: u.displayName });
export const changeEnv = (c: Change) => c.env ?? defaultEnvKey();
const isOpen = (c: Change) => c.status === 'DRAFT' || c.status === 'PENDING' || c.status === 'CONFLICT';

/** 本单是否需要审批 = 项目需要审批 或 环境受保护 */
export function needsApproval(c: Change): boolean {
  return !!db.projects.find((p) => p.id === c.projectId)?.requireApproval || !!db.envs.find((e) => e.key === changeEnv(c))?.protected;
}

export function openChangesFor(env: string, service: string, key: string, exceptId?: number): OpenChange[] {
  return db.changes
    .filter((c) => c.id !== exceptId && isOpen(c) && changeEnv(c) === env && c.items.some((i) => i.service === service && i.ruleKey === key))
    .map((c) => ({ id: c.id, title: c.title, status: c.status, author: c.author, updatedAt: c.updatedAt ?? c.createdAt }));
}

export function decorate(c: Change, withDiff = true): Change {
  const env = changeEnv(c);
  const items = c.items.map((i) => {
    const cur = curVersion(env, i.service, i.ruleKey);
    const conflict = isOpen(c) && cur !== i.baseVersion && !sameJson(curRule(env, i.service, i.ruleKey)?.rules ?? null, i.afterRules);
    return {
      ...i, currentVersion: cur, conflict, diff: withDiff ? unified(i.beforeRules ?? null, i.afterRules) : undefined,
      overlaps: withDiff ? openChangesFor(env, i.service, i.ruleKey, c.id).map((o) => ({ changeId: o.id, title: o.title, status: o.status })) : undefined,
    };
  });
  return {
    ...c, env, envName: envOf(env).name, sourceEnv: c.sourceEnv ?? null, requireApproval: needsApproval(c),
    commentCount: db.comments.filter((x) => x.changeId === c.id && !x.deleted).length, items,
  };
}

export function normalizeItems(env: string, input: { service: string; ruleKey: string; baseVersion: number; afterRules: string | null }[]): ChangeItem[] {
  if (!input?.length) throw new HttpErr(400, 'VALIDATION', '至少需要一条规则条目');
  return input.map((i) => {
    if (!i.service || !i.ruleKey) throw new HttpErr(400, 'VALIDATION', '服务与规则 Key 不能为空');
    if (i.afterRules != null) { try { JSON.parse(i.afterRules); } catch { throw new HttpErr(400, 'VALIDATION', `规则 JSON 格式错误:${i.ruleKey}`); } }
    return { id: nid(), service: i.service, ruleKey: i.ruleKey, baseVersion: i.baseVersion, beforeRules: curRule(env, i.service, i.ruleKey)?.rules ?? null, afterRules: i.afterRules };
  });
}

function conflictsOf(c: Change): ConflictItem[] {
  const env = changeEnv(c);
  return c.items
    .filter((i) => curVersion(env, i.service, i.ruleKey) !== i.baseVersion && !sameJson(curRule(env, i.service, i.ruleKey)?.rules ?? null, i.afterRules))
    .map((i) => ({ service: i.service, ruleKey: i.ruleKey, baseVersion: i.baseVersion, currentVersion: curVersion(env, i.service, i.ruleKey), currentRules: curRule(env, i.service, i.ruleKey)?.rules ?? null }));
}
export const isStale = (c: Change) => conflictsOf(c).length > 0;

/** 检查 + 发布;冲突 → 状态置 CONFLICT 并抛 409 */
export function tryPublish(c: Change, by: UserRow): void {
  const env = changeEnv(c);
  envWritable(env);
  const conflicts = conflictsOf(c);
  if (conflicts.length) {
    c.status = 'CONFLICT';
    c.updatedAt = now();
    logAudit({ ...auditBy(by), action: 'change.conflict', projectId: c.projectId, env, changeId: c.id, reason: '发布时版本不符' });
    throw new HttpErr(409, 'CONFLICT', '规则已被他人修改,请先同步最新内容', { items: conflicts });
  }
  c.items.forEach((i) => {
    if (sameJson(curRule(env, i.service, i.ruleKey)?.rules ?? null, i.afterRules)) return; // 内容相同跳过
    writeRule(env, i.service, i.ruleKey, i.afterRules, by.displayName, c.id);
    i.publishedVersion = curVersion(env, i.service, i.ruleKey);
    logAudit({ ...auditBy(by), action: 'change.publish', projectId: c.projectId, env, service: i.service, ruleKey: i.ruleKey, changeId: c.id, before: i.beforeRules ?? null, after: i.afterRules, reason: c.title });
  });
  c.status = 'PUBLISHED';
  c.publishedAt = now();
  c.updatedAt = now();
}

export function setStatus(c: Change, s: ChangeStatus, action: string, u: UserRow, reason?: string) {
  c.status = s;
  c.updatedAt = now();
  logAudit({ ...auditBy(u), action, projectId: c.projectId, env: changeEnv(c), changeId: c.id, reason: reason ?? c.title });
}

// ---------- 环境差异 ----------
const hasRule = (r: RuleRow | undefined): r is RuleRow => !!r && r.rules != null;
export function diffEnvs(from: string, to: string, services: string[]): EnvDiffService[] {
  const out: EnvDiffService[] = [];
  services.forEach((service) => {
    const a = db.rules[from]?.[service] ?? {}, b = db.rules[to]?.[service] ?? {};
    const keys = Array.from(new Set([...Object.keys(a), ...Object.keys(b)])).sort();
    const items: EnvDiffService['items'] = [];
    keys.forEach((ruleKey) => {
      const x = a[ruleKey], y = b[ruleKey];
      const fx = hasRule(x), fy = hasRule(y);
      if (fx && fy) { if (!sameJson(x.rules, y.rules)) items.push({ ruleKey, status: 'DIFFERENT', fromVersion: x.version, toVersion: y.version }); }
      else if (fx) items.push({ ruleKey, status: 'ONLY_FROM', fromVersion: x.version, toVersion: (y as RuleRow | undefined)?.version ?? null });
      else if (fy) items.push({ ruleKey, status: 'ONLY_TO', fromVersion: (x as RuleRow | undefined)?.version ?? null, toVersion: y.version });
    });
    if (items.length) out.push({ service, items });
  });
  return out;
}

// ---------- 编辑中提示(内存,不入库) ----------
export function livePresence(): PresenceRow[] {
  const cut = now() - PRESENCE_TTL_MS;
  db.presence = db.presence.filter((p) => p.sticky || p.lastSeen >= cut);
  return db.presence;
}
export function presenceOf(env: string, service: string, exceptUserId: number): PresenceEntry[] {
  const map = new Map<string, PresenceEntry>();
  livePresence().filter((p) => p.env === env && p.service === service && p.userId !== exceptUserId).forEach((p) => {
    const u = db.users.find((x) => x.id === p.userId);
    if (!u) return;
    const e = map.get(p.ruleKey) ?? { ruleKey: p.ruleKey, users: [] };
    e.users.push({ id: u.id, displayName: u.displayName, since: p.since });
    map.set(p.ruleKey, e);
  });
  return [...map.values()];
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
/** 按项目与环境触发事件;Webhook 订阅了环境(environments 非空)时只在这些环境触发 */
export function fireEvent(event: string, projectId: number, env?: string | null) {
  db.webhooks
    .filter((w) => w.enabled && (w.projectId == null || w.projectId === projectId) && w.events.includes(event)
      && (!w.environments?.length || !env || w.environments.includes(env)))
    .forEach((w) => deliver(w, event));
}
