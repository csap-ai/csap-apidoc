// 按契约第 3 节的角色矩阵,统一判断"能不能做"。后端仍是最终裁决,前端只负责隐藏 / 禁用。
import type { Change, Me, ProjectRole } from '@/api/types';

export function isAdmin(me: Me | null): boolean {
  return me?.orgRole === 'ADMIN';
}

export function roleIn(me: Me | null, projectId: number | null | undefined): ProjectRole | 'ADMIN' | null {
  if (!me) return null;
  if (me.orgRole === 'ADMIN') return 'ADMIN';
  if (projectId == null) return null;
  return me.projects.find((p) => p.id === projectId)?.role ?? null;
}

const hasAny = (me: Me | null, roles: ProjectRole[]) =>
  isAdmin(me) || !!me?.projects.some((p) => roles.includes(p.role));

/** 建草稿、提交、发起回滚:管理员 / 维护者 / 开发 */
export function canWrite(me: Me | null, projectId?: number | null): boolean {
  if (projectId == null) return hasAny(me, ['MAINTAINER', 'DEVELOPER']);
  const r = roleIn(me, projectId);
  return r === 'ADMIN' || r === 'MAINTAINER' || r === 'DEVELOPER';
}

/** 审批:管理员(含自己的);维护者不能审批自己提交的 */
export function canApprove(me: Me | null, change: Pick<Change, 'projectId' | 'author'>): boolean {
  const r = roleIn(me, change.projectId);
  if (r === 'ADMIN') return true;
  return r === 'MAINTAINER' && change.author.id !== me?.id;
}

/** 维护者审批自己提交的单子被禁用时给出原因 */
export function approveBlockedBySelf(me: Me | null, change: Pick<Change, 'projectId' | 'author'>): boolean {
  return roleIn(me, change.projectId) === 'MAINTAINER' && change.author.id === me?.id;
}

export function isAuthor(me: Me | null, change: Pick<Change, 'author'>): boolean {
  return !!me && change.author.id === me.id;
}

/** 查看审计:管理员 / 维护者 / 开发(只读不可) */
export function canViewAudit(me: Me | null, projectId?: number | null): boolean {
  if (projectId == null) return hasAny(me, ['MAINTAINER', 'DEVELOPER']);
  const r = roleIn(me, projectId);
  return r === 'ADMIN' || r === 'MAINTAINER' || r === 'DEVELOPER';
}

/** 导出审计:管理员 / 维护者(开发只查不导出) */
export function canExportAudit(me: Me | null): boolean {
  return hasAny(me, ['MAINTAINER']);
}

/** 项目成员、项目设置、服务归属、Webhook:管理员 / 所在项目维护者 */
export function canManageProject(me: Me | null, projectId?: number | null): boolean {
  if (projectId == null) return hasAny(me, ['MAINTAINER']);
  const r = roleIn(me, projectId);
  return r === 'ADMIN' || r === 'MAINTAINER';
}

export type ChangeAction = 'edit' | 'submit' | 'approve' | 'reject' | 'withdraw' | 'rebase';

/** 一张修改单当前用户能看到哪些操作按钮 */
export function changeActions(me: Me | null, c: Change): { visible: ChangeAction[]; approveBlockedSelf: boolean } {
  const visible: ChangeAction[] = [];
  const author = isAuthor(me, c);
  const write = canWrite(me, c.projectId);
  // 编辑 / 提交 / 更新到最新版本:仅作者;撤回:作者、项目维护者、管理员(仅草稿 / 待审批)
  if (c.status === 'DRAFT' && author && write) visible.push('edit', 'submit');
  if (c.status === 'PENDING' && canApprove(me, c)) visible.push('approve', 'reject');
  if ((c.status === 'DRAFT' || c.status === 'PENDING') && (author || canManageProject(me, c.projectId))) visible.push('withdraw');
  if (c.status === 'CONFLICT' && author && write) visible.push('rebase');
  return { visible, approveBlockedSelf: c.status === 'PENDING' && approveBlockedBySelf(me, c) };
}
