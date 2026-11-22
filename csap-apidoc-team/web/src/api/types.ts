// 类型严格对应 docs/design/team-1.0.md 第 7 节与 team-1.1.md(1.1 新增字段均为可选 / 可空,便于对接 1.0 后端)
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

/** 环境(1.1 §1.2);只读展示,环境只在后端配置文件里定义 */
export type EnvStatus = 'UP' | 'DOWN';
export interface Env {
  key: string;
  name: string;
  order: number;
  protected: boolean;
  readOnly: boolean;
  status: EnvStatus;
  error: string | null;
  default: boolean;
}

/** 服务在某个环境里的地址与状态(1.1 §2) */
export interface ServiceEnvInfo {
  env: string;
  baseUrl: string | null;
  docPath: string;
  source: 'REGISTERED' | 'MANUAL';
  status: ServiceStatus;
  instances: number;
  lastSeenAt: number | null;
  lastFetchedAt: number | null;
}
export interface ServiceInfo {
  service: string;
  projectId: number | null;
  projectName: string | null;
  /** 顶层字段取 env 参数所指环境;该环境没有记录时 baseUrl 为 null、status=OFFLINE */
  baseUrl: string | null;
  docPath: string;
  language: string;
  frameworkVersion: string;
  source: 'REGISTERED' | 'MANUAL';
  status: ServiceStatus;
  instances: number;
  lastSeenAt: number | null;
  lastFetchedAt: number | null;
  /** 1.1:该服务出现过的所有环境 */
  envs?: ServiceEnvInfo[];
}

export interface SearchHit {
  env?: string;
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
/** 同环境、同服务、同规则上未完成的修改单(1.1 §5.2 ②) */
export interface OpenChange {
  id: number;
  title: string;
  status: ChangeStatus;
  author: UserRef;
  updatedAt: number;
}
export interface RuleItem {
  ruleKey: string;
  rules: string | null;
  version: number;
  openChanges?: OpenChange[];
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
export type ChangeKind = 'EDIT' | 'ROLLBACK' | 'PROMOTE';
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
  /** 1.1:其它未完成修改单里同环境、同规则的条目 */
  overlaps?: ChangeOverlap[];
}
export interface ChangeOverlap {
  changeId: number;
  title: string;
  status: ChangeStatus;
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
  /** 1.1 */
  env?: string;
  envName?: string;
  /** 仅推广单,否则 null */
  sourceEnv?: string | null;
  /** 本单是否需要审批 = 项目需要审批 或 环境受保护 */
  requireApproval?: boolean;
  commentCount?: number;
}
export interface ChangeInput {
  projectId: number;
  env?: string;
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

/** 修改单评论(1.1 §5.1);软删除后 body 为 null、deleted=true */
export interface Comment {
  id: number;
  changeId: number;
  itemId: number | null;
  author: UserRef;
  body: string | null;
  deleted: boolean;
  createdAt: number;
  updatedAt: number;
}

/** 编辑中提示(1.1 §5.2 ①) */
export interface PresenceUser {
  id: number;
  displayName: string;
  since: number;
}
export interface PresenceEntry {
  ruleKey: string;
  users: PresenceUser[];
}
export interface PresenceBody {
  env?: string;
  service: string;
  ruleKey: string;
}

/** 环境差异与推广(1.1 §3.3) */
export type EnvDiffStatus = 'DIFFERENT' | 'ONLY_FROM' | 'ONLY_TO';
export interface EnvDiffItem {
  ruleKey: string;
  status: EnvDiffStatus;
  fromVersion: number | null;
  toVersion: number | null;
}
export interface EnvDiffService {
  service: string;
  items: EnvDiffItem[];
}
export interface EnvDiff {
  from: string;
  to: string;
  services: EnvDiffService[];
}
export interface EnvDiffDetail {
  from: string;
  to: string;
  service: string;
  ruleKey: string;
  fromRules: string | null;
  toRules: string | null;
  fromVersion: number | null;
  toVersion: number | null;
  /** to → from 的统一差异,即"推广后目标会怎么变" */
  diff: string;
}
export interface PromotionInput {
  projectId: number;
  from: string;
  to: string;
  items?: { service: string; ruleKey: string }[];
  includeDeletes: boolean;
  title: string;
  reason: string;
  submit: boolean;
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
  /** 1.1:与环境无关的动作(登录、用户管理等)为 null;升级前的旧记录也为 null */
  env?: string | null;
  service?: string | null;
  ruleKey?: string | null;
  changeId?: number | null;
  beforeValue?: string | null;
  afterValue?: string | null;
  diff?: string | null;
  reason?: string | null;
}
export type AuditVerifyProblem = 'HASH_MISMATCH' | 'PREV_MISMATCH' | 'MISSING_HASH' | 'GAP';
/** 审计哈希链校验结果(1.1 §6.5) */
export interface AuditVerifyResult {
  ok: boolean;
  checked: number;
  headId: number | null;
  headHash: string | null;
  firstBrokenId: number | null;
  problem: AuditVerifyProblem | null;
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
  /** 1.1:订阅的环境;空 / 省略 = 全部环境 */
  environments?: string[];
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
  environments?: string[];
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
  /** 1.1:REGISTER 令牌绑定的环境;null = 默认环境 */
  env?: string | null;
  createdBy?: string;
  createdAt: number;
  lastUsedAt: number | null;
  revoked: boolean;
}
export interface TokenCreated extends TokenInfo {
  token: string;
}
