import request from '@/utils/request'

// 返回空字符串，让请求走Vite代理 /api -> http://localhost:8085
const getUrl = () => ''

/**
 * API响应类型
 */
export interface ApiResponse<T = any> {
  code: string
  data: T
  language: string
  message: string
  success: boolean
}

/**
 * Controller模型
 */
export interface ControllerModel {
  name: string
  simpleName: string
  value: string
  description: string
  path: string[]
  hidden: boolean
  hiddenMethod: string[]
  methodList: MethodModel[]
  group: string[]
  version: string[]
  position: number
  protocols: string
  status: string
  tags: string[]
  search: string[]
}

/**
 * 方法模型
 */
export interface MethodModel {
  name: string
  paths: string
  methods: string
  paramTypes: string[]
  value: string
  description: string
  hidden: boolean
  request: ParamModel[]
  response: ParamModel[]
  className?: string
}

/**
 * 参数模型
 */
export interface ParamModel {
  name: string
  methodParamName: string
  modelType: string
  unitName?: string
  money?: string
  $ref?: string
}

/**
 * 字段模型
 */
export interface FieldModel {
  name: string
  type: string
  columnType: string
  notNull: boolean
  defaultValue: string
  comment: string
  keyFlag: boolean
  keyIdentityFlag: boolean
}

/**
 * 获取扫描的API列表
 */
export function scannerApi(className?: string) {
  return request<ApiResponse<ControllerModel[]>>({
    url: `${getUrl()}/csap/yaml/scannerApi`,
    method: 'get',
    params: { className: className || '' }
  }).then(res => res.data.data)
}

/**
 * 获取指定类下的所有方法
 */
export function getAllMethod(className: string) {
  return request<ApiResponse<MethodModel[]>>({
    url: `${getUrl()}/csap/yaml/getAllMethod`,
    method: 'get',
    params: { className }
  }).then(res => res.data.data)
}

/**
 * 隐藏/显示某个接口
 */
export function hiddenMethod(className: string, methodName: string, hidden: boolean) {
  return request<ApiResponse<boolean>>({
    url: `${getUrl()}/devtools/hiddenMethod`,
    method: 'post',
    data: { className, methodName, hidden }
  }).then(res => res.data.data)
}

/**
 * 添加/修改API请求参数
 */
export function addRequestParam(data: any) {
  return request<ApiResponse<boolean>>({
    url: `${getUrl()}/csap/yaml/addRequestParam`,
    method: 'post',
    data
  }).then(res => res.data.data)
}

/**
 * 添加/修改API返回参数
 */
export function addResponseParam(data: any) {
  return request<ApiResponse<boolean>>({
    url: `${getUrl()}/csap/yaml/addResponseParam`,
    method: 'post',
    data
  }).then(res => res.data.data)
}

/**
 * 获取指定类所有字段
 * 支持三种方式：
 * 1. 直接传 className - 获取类的字段（无法获取泛型信息）
 * 2. 传 controllerClassName + methodName + parameterIndex - 从方法参数中获取泛型类型
 * 3. 传 controllerClassName + methodName + parameterIndex=-1 - 从返回值中获取泛型类型
 */
export function getFields(
  className: string,
  excludeField?: string[],
  controllerClassName?: string,
  methodName?: string,
  parameterIndex?: number
) {
  return request<ApiResponse<FieldModel[]>>({
    url: `${getUrl()}/devtools/getFields`,
    method: 'post',
    data: {
      className,
      excledFields: excludeField,
      controllerClassName,
      methodName,
      parameterIndex
    }
  }).then(res => res.data.data)
}

/**
 * 获取API文档列表
 */
export function getApiDocList(tableName?: string, className?: boolean, methodName?: string) {
  const key = methodName ? `${tableName}.${methodName}` : ''
  const url = tableName && tableName.length > 0
    ? `${getUrl()}/csap/apidoc/${tableName}?className=${className}&key=${key}`
    : `${getUrl()}/csap/apidoc`

  return request({
    url,
    method: 'get'
  }).then(res => res.data.data)
}

/**
 * 获取正则验证列表
 */
export function validatePatternTypes() {
  return request({
    url: `${getUrl()}/csap/yaml/validatePatternTypes`,
    method: 'get'
  }).then(res => res.data.data)
}

/**
 * 获取方法的验证字段
 */
export function getMethodValidateFields(className: string, methodName: string, fieldName: string) {
  return request({
    url: `${getUrl()}/csap/yaml/getMethodValidateFields`,
    method: 'get',
    params: { className, methodName, fieldName }
  }).then(res => res.data.data)
}

/**
 * 写入文档
 */
export function write(data: any) {
  return request({
    url: `${getUrl()}/csap/yaml/write`,
    method: 'post',
    data
  }).then(res => res.data.data)
}

/**
 * 写入选择的类文档
 */
export function writeSelect(data: string[]) {
  return request({
    url: `${getUrl()}/csap/yaml/writeSelect`,
    method: 'post',
    data
  })
}

