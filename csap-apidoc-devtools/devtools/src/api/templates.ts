import request from '@/utils/request'
import type { ApiResponse } from '@/api/devtools'

export type ApiStatus = 'DEFAULT' | 'FINISH' | 'DEPRECATED'

export interface FieldTemplateRule {
  type?: string
  pattern?: string
  code?: string
  message?: string
  descr?: string
  level?: number
}

export interface FieldTemplate {
  name: string
  title: string
  category?: string
  description?: string
  example?: string
  required?: boolean
  validate?: FieldTemplateRule[]
  /** builtin 随 jar 的内置模板(只读)/ project 项目模板 */
  source?: 'builtin' | 'project'
}

export interface FieldTemplateList {
  projectSupported: boolean
  templates: FieldTemplate[]
}

/** 设置接口状态和备注;status 传 null 清除覆盖(回到注解 / 默认) */
export function setMethodStatus(data: { className: string; methodName: string; status: ApiStatus | null; statusNote?: string }) {
  return request<ApiResponse<boolean>>({ url: '/csap/yaml/setStatus', method: 'post', data }).then(res => res.data.data)
}

export function listFieldTemplates() {
  return request<ApiResponse<FieldTemplateList>>({ url: '/csap/yaml/fieldTemplates', method: 'get' }).then(res => res.data.data)
}

export function saveFieldTemplate(template: FieldTemplate) {
  const { source, ...body } = template
  void source
  return request<ApiResponse<boolean>>({ url: '/csap/yaml/fieldTemplates', method: 'post', data: body }).then(res => res.data.data)
}

export function deleteFieldTemplate(name: string) {
  return request<ApiResponse<boolean>>({ url: `/csap/yaml/fieldTemplates/${encodeURIComponent(name)}`, method: 'delete' })
    .then(res => res.data.data)
}
