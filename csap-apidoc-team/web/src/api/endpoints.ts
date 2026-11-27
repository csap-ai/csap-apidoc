import { request, requestRaw, url } from './client';
import type * as T from './types';

type Q = Record<string, string | number | boolean | undefined | null>;
const post = <R>(path: string, body?: unknown, silent = false) =>
  request<R>(path, { method: 'POST', body: body ?? {}, silent });

export const setupApi = {
  status: () => request<{ initialized: boolean }>('/setup/status', { silent: true }),
  init: (b: { orgName: string; username: string; password: string; displayName: string }) =>
    post<unknown>('/setup', b, true),
};

export const authApi = {
  login: (b: { username: string; password: string }) => post<T.Me>('/auth/login', b, true),
  logout: () => post<void>('/auth/logout'),
  me: () => request<T.Me>('/auth/me', { silent: true }),
  password: (b: { oldPassword: string; newPassword: string }) => post<void>('/auth/password', b),
};

export const userApi = {
  list: () => request<T.User[]>('/users'),
  create: (b: { username: string; displayName: string; password: string; orgRole: T.OrgRole }) =>
    post<T.User>('/users', b),
  update: (id: number, b: Partial<{ displayName: string; status: T.UserStatus; orgRole: T.OrgRole; password: string }>) =>
    request<T.User>(`/users/${id}`, { method: 'PATCH', body: b }),
};

export const projectApi = {
  list: () => request<T.Project[]>('/projects'),
  create: (b: { name: string; description?: string; requireApproval?: boolean }) => post<T.Project>('/projects', b),
  get: (id: number) => request<T.Project>(`/projects/${id}`),
  update: (id: number, b: Partial<{ name: string; description: string; requireApproval: boolean }>) =>
    request<T.Project>(`/projects/${id}`, { method: 'PATCH', body: b }),
  remove: (id: number) => request<void>(`/projects/${id}`, { method: 'DELETE' }),
  candidates: (id: number) => request<T.Candidate[]>(`/projects/${id}/candidates`),
  members: (id: number) => request<T.Member[]>(`/projects/${id}/members`),
  setMember: (id: number, userId: number, role: T.ProjectRole) =>
    request<T.Member>(`/projects/${id}/members/${userId}`, { method: 'PUT', body: { role } }),
  removeMember: (id: number, userId: number) =>
    request<void>(`/projects/${id}/members/${userId}`, { method: 'DELETE' }),
};

export const envApi = {
  list: () => request<T.Env[]>('/envs', { silent: true }),
  diff: (q: { from: string; to: string; projectId?: number; service?: string }) => request<T.EnvDiff>('/envs/diff', { query: q as Q }),
  diffItem: (q: { from: string; to: string; service: string; key: string }) => request<T.EnvDiffDetail>('/envs/diff/item', { query: q as Q }),
};

export const promotionApi = {
  create: (b: T.PromotionInput) => post<T.Change>('/promotions', b),
};

export const serviceApi = {
  list: (q?: { projectId?: number; unassigned?: boolean; env?: string }) => request<T.ServiceInfo[]>('/services', { query: q as Q }),
  create: (b: { service: string; baseUrl: string; docPath?: string; projectId?: number; env?: string }) =>
    post<T.ServiceInfo>('/services', b),
  update: (service: string, b: Partial<{ projectId: number | null; baseUrl: string; docPath: string }>, env?: string) =>
    request<T.ServiceInfo>(`/services/${encodeURIComponent(service)}`, { method: 'PATCH', body: b, query: { env } }),
  /** 带 env:只删该环境的地址和实例;不带:删整个服务 */
  remove: (service: string, env?: string) => request<void>(`/services/${encodeURIComponent(service)}`, { method: 'DELETE', query: { env } }),
};

export const docApi = {
  get: async (service: string, env?: string): Promise<T.DocResult> => {
    const res = await requestRaw(`/docs/${encodeURIComponent(service)}`, { query: { env } });
    const fetched = res.headers.get('X-CSAP-Team-Fetched-At');
    return {
      doc: await res.json(),
      fetchedAt: fetched ? Number(fetched) : null,
      stale: res.headers.get('X-CSAP-Team-Stale') === 'true',
    };
  },
  search: (q: string, projectId?: number, env?: string) => request<T.SearchHit[]>('/docs/search', { query: { q, projectId, env } }),
};

// 规则相关接口的 env 省略 = 默认环境(单环境部署时前端不传)
export const ruleApi = {
  list: (service: string, env?: string) => request<T.RuleList>(`/rules/${encodeURIComponent(service)}`, { query: { env } }),
  item: (service: string, key: string, env?: string) => request<T.RuleItem>(`/rules/${encodeURIComponent(service)}/item`, { query: { key, env } }),
  history: (service: string, key: string, limit = 50, env?: string) =>
    request<T.RuleHistory[]>(`/rules/${encodeURIComponent(service)}/history`, { query: { key, limit, env } }),
  diff: (service: string, key: string, from: number, to?: number, env?: string) =>
    request<T.RuleDiff>(`/rules/${encodeURIComponent(service)}/diff`, { query: { key, from, to, env } }),
  rollback: (service: string, b: { key: string; historyId: number; reason: string; env?: string }) =>
    post<T.Change>(`/rules/${encodeURIComponent(service)}/rollback`, b),
};

export const presenceApi = {
  /** 打开规则编辑器后每 30 秒调用一次;90 秒没续上视为离开 */
  put: (b: T.PresenceBody) => request<void>('/presence', { method: 'PUT', body: b }),
  /** 关闭编辑器时调用,失败也无妨 */
  remove: (b: T.PresenceBody) => request<void>('/presence', { method: 'DELETE', body: b }),
  list: (q: { env?: string; service: string }) => request<T.PresenceEntry[]>('/presence', { query: q as Q }),
};

export const commentApi = {
  list: (changeId: number) => request<T.Comment[]>(`/changes/${changeId}/comments`),
  create: (changeId: number, b: { body: string; itemId?: number }) => post<T.Comment>(`/changes/${changeId}/comments`, b),
  update: (id: number, body: string) => request<T.Comment>(`/comments/${id}`, { method: 'PATCH', body: { body } }),
  remove: (id: number) => request<void>(`/comments/${id}`, { method: 'DELETE' }),
};

export const changeApi = {
  list: (q: { projectId?: number; status?: string; mine?: boolean; env?: string; page?: number; size?: number }) =>
    request<T.Paged<T.Change>>('/changes', { query: q as Q }),
  get: (id: number) => request<T.Change>(`/changes/${id}`),
  create: (b: T.ChangeInput) => post<T.Change>('/changes', b),
  update: (id: number, b: T.ChangeInput) => request<T.Change>(`/changes/${id}`, { method: 'PATCH', body: b }),
  submit: (id: number) => post<T.Change>(`/changes/${id}/submit`),
  approve: (id: number, note: string) => post<T.Change>(`/changes/${id}/approve`, { note }),
  reject: (id: number, note: string) => post<T.Change>(`/changes/${id}/reject`, { note }),
  withdraw: (id: number) => post<T.Change>(`/changes/${id}/withdraw`),
  rebase: (id: number, items: { service: string; ruleKey: string; afterRules: string | null }[]) =>
    post<T.Change>(`/changes/${id}/rebase`, { items }),
};

export interface AuditQuery {
  from?: number;
  to?: number;
  actor?: string;
  projectId?: number;
  service?: string;
  ruleKey?: string;
  action?: string;
  source?: string;
  env?: string;
}
export const auditApi = {
  list: (q: AuditQuery & { page?: number; size?: number }) => request<T.Paged<T.AuditRecord>>('/audit', { query: q as Q }),
  get: (id: number) => request<T.AuditRecord>(`/audit/${id}`),
  exportUrl: (format: 'csv' | 'json', q: AuditQuery) => url('/audit/export', { ...(q as Q), format }),
  exportMcpUrl: (format: 'csv' | 'json', q: { from?: number; to?: number; service?: string; caller?: string; tool?: string; env?: string }) =>
    url('/audit/export', { ...(q as Q), kind: 'mcp', format }),
  mcp: (q: { from?: number; to?: number; service?: string; caller?: string; tool?: string; env?: string; page?: number; size?: number }) =>
    request<T.McpAuditPage>('/audit/mcp', { query: q as Q }),
  /** 校验哈希链(仅管理员);不传范围 = 全部 */
  verify: (q?: { fromId?: number; toId?: number }) => request<T.AuditVerifyResult>('/audit/verify', { query: q as Q }),
};

export const webhookApi = {
  list: (projectId?: number) => request<T.Webhook[]>('/webhooks', { query: { projectId } }),
  create: (b: T.WebhookInput) => post<T.Webhook>('/webhooks', b),
  update: (id: number, b: Partial<T.WebhookInput>) => request<T.Webhook>(`/webhooks/${id}`, { method: 'PATCH', body: b }),
  remove: (id: number) => request<void>(`/webhooks/${id}`, { method: 'DELETE' }),
  test: (id: number) => post<T.WebhookDelivery>(`/webhooks/${id}/test`),
  deliveries: (id: number) => request<T.WebhookDelivery[]>(`/webhooks/${id}/deliveries`),
};

export const tokenApi = {
  list: () => request<T.TokenInfo[]>('/tokens'),
  create: (b: { name: string; scope: T.TokenScope; projectId?: number; env?: string }) => post<T.TokenCreated>('/tokens', b),
  revoke: (id: number) => request<void>(`/tokens/${id}`, { method: 'DELETE' }),
};
