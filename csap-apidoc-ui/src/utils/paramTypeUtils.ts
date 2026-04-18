/**
 * 请求参数类型工具函数
 */

export type ParamType = 'PATH' | 'QUERY' | 'BODY' | 'FORM_DATA' | 'HEADER' | 'DEFAULT';

export interface Parameter {
  name: string;
  dataType: string;
  required: boolean;
  value?: string;
  defaultValue?: string;
  example?: string;
  description?: string;
  paramType?: ParamType;
  children?: Parameter[];
  [key: string]: any;
}

export interface GroupedParams {
  PATH: Parameter[];
  QUERY: Parameter[];
  BODY: Parameter[];
  FORM_DATA: Parameter[];
  HEADER: Parameter[];
  DEFAULT: Parameter[];
}

/**
 * 获取参数类型的显示标签
 */
export const getParamTypeLabel = (paramType: string): string => {
  const labels: Record<string, string> = {
    PATH: 'Path 参数',
    QUERY: 'Query 参数',
    BODY: 'Body 参数',
    FORM_DATA: 'Form Data 参数',
    HEADER: 'Header 参数',
    DEFAULT: 'Query 参数' // DEFAULT 默认当作 QUERY
  };
  return labels[paramType] || paramType;
};

/**
 * 获取参数类型的标签颜色
 */
export const getParamTypeColor = (paramType: string): string => {
  const colors: Record<string, string> = {
    PATH: 'blue',
    QUERY: 'cyan',
    BODY: 'green',
    FORM_DATA: 'orange',
    HEADER: 'purple',
    DEFAULT: 'default'
  };
  return colors[paramType] || 'default';
};

/**
 * 按照 paramType 分组请求参数
 */
export const groupParametersByType = (params: Parameter[]): GroupedParams => {
  const grouped: GroupedParams = {
    PATH: [],
    QUERY: [],
    BODY: [],
    FORM_DATA: [],
    HEADER: [],
    DEFAULT: []
  };

  if (!params || !Array.isArray(params)) {
    return grouped;
  }

  params.forEach(param => {
    const type = (param.paramType || 'DEFAULT') as ParamType;
    if (grouped[type]) {
      grouped[type].push(param);
    } else {
      // 如果是未知类型，放到 DEFAULT 中
      grouped.DEFAULT.push(param);
    }
  });

  return grouped;
};

/**
 * 获取非空的参数类型列表
 */
export const getNonEmptyParamTypes = (grouped: GroupedParams): ParamType[] => {
  return (Object.keys(grouped) as ParamType[]).filter(
    type => grouped[type] && grouped[type].length > 0
  );
};

/**
 * 构建请求对象（用于在线测试）
 */
export const buildRequestData = (
  params: Parameter[],
  jsonData: any
): {
  pathParams: Record<string, any>;
  queryParams: Record<string, any>;
  bodyData: any;
  formData: Record<string, any>;
  headers: Record<string, any>;
} => {
  const grouped = groupParametersByType(params);
  
  const result = {
    pathParams: {} as Record<string, any>,
    queryParams: {} as Record<string, any>,
    bodyData: {} as any,
    formData: {} as Record<string, any>,
    headers: {} as Record<string, any>
  };

  // 如果提供了 jsonData，优先使用
  if (jsonData && typeof jsonData === 'object') {
    // PATH 参数
    grouped.PATH.forEach(param => {
      if (jsonData[param.name] !== undefined) {
        result.pathParams[param.name] = jsonData[param.name];
      }
    });

    // QUERY 参数 (包括 DEFAULT)
    [...grouped.QUERY, ...grouped.DEFAULT].forEach(param => {
      if (jsonData[param.name] !== undefined) {
        result.queryParams[param.name] = jsonData[param.name];
      }
    });

    // BODY 参数
    if (grouped.BODY.length > 0) {
      result.bodyData = jsonData;
    }

    // FORM_DATA 参数
    grouped.FORM_DATA.forEach(param => {
      if (jsonData[param.name] !== undefined) {
        result.formData[param.name] = jsonData[param.name];
      }
    });

    // HEADER 参数
    grouped.HEADER.forEach(param => {
      if (jsonData[param.name] !== undefined) {
        result.headers[param.name] = jsonData[param.name];
      }
    });
  }

  return result;
};

/**
 * 替换 URL 中的 Path 参数
 * 例如: /api/user/{id} => /api/user/123
 */
export const replacePathParams = (url: string, pathParams: Record<string, any>): string => {
  let result = url;
  Object.keys(pathParams).forEach(key => {
    // 支持两种格式: {id} 和 :id
    result = result.replace(`{${key}}`, String(pathParams[key]));
    result = result.replace(`:${key}`, String(pathParams[key]));
  });
  return result;
};

