import { getApiDocList } from '@/api/devtools'

/**
 * 添加参数keyName
 */
export function addParam(paramsField: any[], name: string): any[] {
  for (let i = 0; i < paramsField.length; i++) {
    const paramsFieldKey = paramsField[i]
    paramsFieldKey.keyName = name + paramsFieldKey.name
    if (paramsFieldKey.parameters != null && paramsFieldKey.parameters.length > 0) {
      addParam(paramsFieldKey.parameters, name + paramsFieldKey.name + '.')
    }
  }
  return paramsField
}

/**
 * 测试验证正则表达式
 */
export function patternValidate(pattern: string, value: string): boolean {
  try {
    return new RegExp(pattern).test(value)
  } catch (e) {
    console.error(e)
  }
  return false
}

/**
 * 获取参数列表
 */
export function getParamList(name: string, methodName: string) {
  return getApiDocList(name, true, methodName).then((res: any) => {
    const method = res.apiList[0].methodList
      .map((i: any) => {
        i.className = name
        return i
      })
      .filter((i: any) => i.name === methodName)[0]
    return method
  })
}

