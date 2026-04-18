/**
 * 调试工具函数
 */

/**
 * 打印API详情结构
 * @param apiDetail API详情
 */
export const logApiStructure = (apiDetail: any): void => {
  console.log('API详情结构:', {
    title: apiDetail.title,
    method: apiDetail.method,
    path: apiDetail.patch,
    paramType: apiDetail.paramType,
    requestParamsCount: apiDetail.request?.length || 0,
    responseParamsCount: apiDetail.response?.length || 0,
  });

  // 打印请求参数结构
  if (apiDetail.request && apiDetail.request.length > 0) {
    console.log('请求参数示例:', apiDetail.request[0]);
    
    // 检查字段名称
    const fieldNames = new Set<string>();
    apiDetail.request.forEach((param: any) => {
      Object.keys(param).forEach(key => fieldNames.add(key));
    });
    console.log('请求参数字段名称:', Array.from(fieldNames));
  }

  // 打印响应参数结构
  if (apiDetail.response && apiDetail.response.length > 0) {
    console.log('响应参数示例:', apiDetail.response[0]);
    
    // 检查字段名称
    const fieldNames = new Set<string>();
    apiDetail.response.forEach((param: any) => {
      Object.keys(param).forEach(key => fieldNames.add(key));
    });
    console.log('响应参数字段名称:', Array.from(fieldNames));
  }
};