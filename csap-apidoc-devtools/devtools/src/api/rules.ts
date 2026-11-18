import request from '@/utils/request'
import type { ApiResponse } from '@/api/devtools'

export interface RuleStatus {
  supported: boolean
  readOnly: boolean
  paramType?: string | null
  /** 后端给出的当前语言 / 框架的具体做法,见 docs/design/devtools-hints.md */
  hint?: import('@/utils/hint').Hint
}

export interface RuleChange {
  id: number
  ruleKey: string
  before?: string | null
  after?: string | null
  version: number
  updatedBy: string
  updatedAt: number
}

export interface RollbackResult {
  rolledBack: boolean
  version?: number | null
}

export function getRuleStatus() {
  return request<ApiResponse<RuleStatus>>({ url: '/devtools/rules/status', method: 'get' }).then(res => res.data.data)
}

export function getRuleHistory(params: { ruleKey?: string; limit?: number }) {
  return request<ApiResponse<RuleChange[]>>({ url: '/devtools/rules/history', method: 'get', params }).then(res => res.data.data)
}

export function rollbackRule(changeId: number) {
  return request<ApiResponse<RollbackResult>>({ url: '/devtools/rules/rollback', method: 'post', data: { changeId } })
    .then(res => res.data.data)
}
