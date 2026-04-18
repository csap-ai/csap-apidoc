/**
 * 修复格式错误的JSON字符串
 * 主要处理缺少逗号分隔符的问题
 */
export const fixJsonFormat = (jsonString: string): string => {
  try {
    // 首先尝试直接解析，如果成功则直接返回
    JSON.parse(jsonString);
    return jsonString;
  } catch (e) {
    // JSON格式有问题，进行简单修复
    console.log('检测到JSON格式问题，开始修复...');
    
    let fixed = jsonString;
    
    // 简单修复：在引号后添加逗号
    fixed = fixed
      .replace(/"([^"]+)"([^{}\\[\\]]*)([^{}\\[\\]]+)"([^"]+)"/g, '"$1"$2,$3"$4"')
      .replace(/([}\\]])([^{}\\[\\]]+)([{\\[])/g, '$1,$2$3');
    
    // 尝试解析修复后的JSON
    try {
      JSON.parse(fixed);
      console.log('JSON修复成功');
      return fixed;
    } catch (parseError) {
      console.error('JSON修复失败，返回原始字符串');
      return jsonString;
    }
  }
};

/**
 * 从格式错误的JSON中提取apiList数据
 */
export const extractApiListFromBrokenJson = (text: string): any[] => {
  try {
    // 尝试修复JSON格式
    const fixedJson = fixJsonFormat(text);
    const data = JSON.parse(fixedJson);
    return data.data?.apiList || data.apiList || [];
  } catch (e) {
    console.error('无法解析JSON，返回空数组');
    return [];
  }
};