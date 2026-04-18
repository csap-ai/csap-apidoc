/**
 * 将中文字符串转换为拼音首字母或移除中文
 * @param str 包含中文的字符串
 * @returns 不包含中文的字符串
 */
export const sanitizeForTypeName = (str: string): string => {
  if (!str) return '';
  
  // 移除所有中文字符，只保留英文、数字和特殊字符
  const sanitized = str.replace(/[\u4e00-\u9fa5]/g, '');
  
  // 如果移除中文后为空，则返回默认名称
  if (!sanitized.trim()) {
    return 'ApiType';
  }
  
  // 移除前后空格并返回
  return sanitized.trim();
};