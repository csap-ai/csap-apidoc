// 类型严格对应 docs/design/team-1.0.md 第 7 节
export type OrgRole = 'ADMIN' | 'MEMBER';
export type ProjectRole = 'MAINTAINER' | 'DEVELOPER' | 'VIEWER';
export type UserStatus = 'ACTIVE' | 'DISABLED';

export interface Me {
  id: number;
  username: string;
  displayName: string;
  orgRole: OrgRole;
  projects: { id: number; name: string; role: ProjectRole }[];
}

export interface User {
  id: number;
  username: string;
  displayName: string;
  status: UserStatus;
  orgRole: OrgRole;
  createdAt?: number;
}

export interface Project {
  id: number;
  name: string;
  description?: string;
  requireApproval: boolean;
  createdAt?: number;
  updatedAt?: number;
}

export interface Member {
  userId: number;
  username?: string;
  displayName?: string;
  role: ProjectRole;
  createdAt?: number;
}

export type ServiceStatus = 'ONLINE' | 'OFFLINE' | 'STALE';
export interface ServiceInfo {
  service: string;
  projectId: number | null;
  projectName: string | null;
  baseUrl: string;
  docPath: string;
  language: string;
  frameworkVersion: string;
  source: 'REGISTERED' | 'MANUAL';
  status: ServiceStatus;
  instances: number;
  lastSeenAt: number | null;
  lastFetchedAt: number | null;
}

export interface SearchHit {
  service: string;
  method: string;
  path: string;
  title: string;
  matchedIn: 'path' | 'title' | 'description' | 'field';
  snippet: string;
}

/** /csap/apidoc 原样 JSON(只列渲染用到的字段) */
export interface DocParam {
  name: string;
  value?: string;
  description?: string;
  example?: string;
  defaultValue?: string;
  required?: boolean;
  dataType?: string;
  length?: number;
  decimals?: number;
  key?: string;
  hidden?: boolean;
  children?: { name?: string; parameters?: DocParam[] } | null;
  parameters?: DocParam[];
  [extra: string]: unknown;
}
export interface DocModel {
  name?: string;
  value?: string;
  description?: string;
  modelType?: string;
  methodParamName?: string;
  parameters?: DocParam[];
}
export interface DocMethod {
  key: string;
  value?: string;
  name: string;
  paths?: string[];
  apiPath?: string[];
  description?: string;
  methods?: string[];
  request?: DocModel[];
  response?: DocModel[];
  methodHeaders?: { key: string; value?: string; required?: boolean; description?: string }[];
  hidden?: boolean;
  status?: string;
}
export interface DocController {
  value?: string;
  name: string;
  simpleName?: string;
  description?: string;
  path?: string[];
  methodList: DocMethod[];
}
export interface DocPayload {
  code?: string;
  message?: string;
  data: {
    apiInfo?: { title?: string; version?: string; description?: string };
    apiList: DocController[];
  };
}
export interface DocResult {
  doc: DocPayload;
  fetchedAt: number | null;
  stale: boolean;
}

export interface RuleListItem {
  ruleKey: string;
  version: number;
  updatedBy: string;
  updatedAt: number;
}
export interface RuleList {
  service: string;
  version: number;
  items: RuleListItem[];
}
export interface RuleItem {
  ruleKey: string;
  rules: string | null;
  version: number;
}
export interface RuleHistory {
  id: number;
  version: number;
  updatedBy: string;
  updatedAt: number;
  beforeRules: string | null;
  afterRules: string | null;
  changeId?: number | null;
}
export interface RuleDiff {
  from: number;
  to: number;
  diff: string;
}

export type ChangeStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'WITHDRAWN' | 'CONFLICT';
export type ChangeKind = 'EDIT' | 'ROLLBACK';
export interface UserRef {
  id: number;
  displayName: string;
}
export interface ChangeItem {
  id?: number;
  service: string;
  ruleKey: string;
  baseVersion: number;
  beforeRules?: string | null;
  afterRules: string | null;
  currentVersion?: number;
  conflict?: boolean;
  diff?: string;
  publishedVersion?: number | null;
}
export interface Change {
  id: number;
  projectId: number;
  projectName: string;
  title: string;
  reason: string;
  kind: ChangeKind;
  status: ChangeStatus;
  author: UserRef;
  createdAt: number;
  updatedAt?: number;
  submittedAt: number | null;
  decidedBy: UserRef | null;
  decidedAt: number | null;
  decisionNote: string | null;
  publishedAt: number | null;
  items: ChangeItem[];
}
export interface ChangeInput {
  projectId: number;
  title: string;
  reason: string;
  items: { service: string; ruleKey: string; baseVersion: number; afterRules: string | null }[];
}
export interface ConflictItem {
  service: string;
  ruleKey: string;
  baseVersion: number;
  currentVersion: number;
  currentRules: string | null;
}

export interface Candidate {
  id: number;
  username: string;
  displayName: string;
}

export interface Paged<T> {
  items: T[];
  total: number;
}

export interface AuditRecord {
  id: number;
  at: number;
  actorType: 'USER' | 'TOKEN' | 'SERVICE' | 'SYSTEM';
  actorId?: number | null;
  actorName: string;
  source: string;
  ip?: string;
  action: string;
  projectId?: number | null;
  service?: string | null;
  ruleKey?: string | null;
  changeId?: number | null;
  beforeValue?: string | null;
  afterValue?: string | null;
  diff?: string | null;
  reason?: string | null;
}
export interface McpAuditRecord {
  id: number;
  service: string;
  at: number;
  caller: string;
  remoteAddress?: string;
  tool: string;
  target?: string;
  arguments?: string;
  status?: number;
  success?: boolean;
  result?: string;
  durationMs?: number;
}
export interface McpAuditPage {
  available: boolean;
  items: McpAuditRecord[];
  total: number;
}

export type WebhookKind = 'GENERIC' | 'WECOM' | 'DINGTALK' | 'FEISHU';
export interface Webhook {
  id: number;
  projectId: number | null;
  name: string;
  kind: WebhookKind;
  url: string;
  hasSecret: boolean;
  events: string[];
  enabled: boolean;
  createdAt?: number;
  updatedAt?: number;
}
export interface WebhookInput {
  projectId?: number | null;
  name: string;
  kind: WebhookKind;
  url: string;
  secret?: string;
  events: string[];
  enabled: boolean;
}
export interface WebhookDelivery {
  id: number;
  webhookId: number;
  event: string;
  at: number;
  attempt: number;
  status: 'SUCCESS' | 'FAILED';
  httpStatus?: number | null;
  error?: string | null;
}

export type TokenScope = 'REGISTER' | 'OPEN_API';
export interface TokenInfo {
  id: number;
  name: string;
  prefix: string;
  scope: TokenScope;
  projectId: number | null;
  createdBy?: string;
  createdAt: number;
  lastUsedAt: number | null;
  revoked: boolean;
}
export interface TokenCreated extends TokenInfo {
  token: string;
}
