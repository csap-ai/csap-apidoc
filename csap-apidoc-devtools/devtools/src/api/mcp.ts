import request from '@/utils/request'
import type { ApiResponse } from '@/api/devtools'
import type { Hint } from '@/utils/hint'

export interface McpStatus {
  enabled: boolean
  path: string
  protocolVersions: string[]
  authMode?: 'token' | 'passthrough'
  defaultAuthMode?: string
  forwardHeaders?: string[]
  tokenNames?: string[]
  upstreamHeaderNames?: string[]
  exposeGetByDefault?: boolean
  storeType?: string
  storeLocation?: string
  storePersistent?: boolean
  auditEnabled?: boolean
  auditPersistent?: boolean
  toolCount?: number
  exposedCount?: number
  /** 后端给出的当前语言 / 框架的具体做法,见 docs/design/devtools-hints.md */
  hint?: Hint
  persistHint?: Hint
  tokenHint?: Hint
  securityNotes?: Hint[]
}

export interface McpToolRow {
  methodKey: string
  className: string
  methodName: string
  title?: string
  httpMethod: string
  path?: string
  name: string
  defaultName: string
  description: string
  defaultDescription: string
  exposed: boolean
  exposedByDefault: boolean
  enabledSetting?: boolean | null
  readOnly: boolean
  hidden: boolean
  unsupportedReason?: string | null
  inputSchema?: any
}

export interface McpToolUpdate {
  methodKey: string
  enabled?: boolean | null
  name?: string | null
  description?: string | null
}

export interface McpSaveResult {
  persisted: boolean
  location: string
  errors: string[]
}

export interface McpAuditEntry {
  id: number
  time: number
  caller: string
  remoteAddress?: string
  tool: string
  target?: string
  arguments?: string
  status: number
  success: boolean
  result?: string
  durationMs: number
}

export function getMcpStatus() {
  return request<ApiResponse<McpStatus>>({ url: '/devtools/mcp/status', method: 'get' }).then(res => res.data.data)
}

export function getMcpTools() {
  return request<ApiResponse<McpToolRow[]>>({ url: '/devtools/mcp/tools', method: 'get' }).then(res => res.data.data)
}

export function saveMcpTools(data: McpToolUpdate[]) {
  return request<ApiResponse<McpSaveResult>>({ url: '/devtools/mcp/tools', method: 'post', data }).then(res => res.data.data)
}

export function saveMcpAuth(data: { mode: string; forwardHeaders: string[] }) {
  return request<ApiResponse<McpSaveResult>>({ url: '/devtools/mcp/auth', method: 'post', data }).then(res => res.data.data)
}

export function getMcpAudit(params: { limit?: number; tool?: string; caller?: string }) {
  return request<ApiResponse<McpAuditEntry[]>>({ url: '/devtools/mcp/audit', method: 'get', params }).then(res => res.data.data)
}
