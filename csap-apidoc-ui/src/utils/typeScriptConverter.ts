import { camelCase, upperFirst } from 'lodash';

/**
 * Java类型到TypeScript类型的映射
 */
const javaToTypeScriptTypeMap: Record<string, string> = {
  'java.lang.String': 'string',
  'java.lang.Integer': 'number',
  'java.lang.Long': 'number',
  'java.lang.Float': 'number',
  'java.lang.Double': 'number',
  'java.lang.Boolean': 'boolean',
  'java.util.Date': 'Date',
  'java.time.LocalDate': 'string',
  'java.time.LocalDateTime': 'string',
  'java.math.BigDecimal': 'number',
  'java.util.UUID': 'string',
  'java.time.Instant': 'string',
  'java.time.ZonedDateTime': 'string',
  'java.time.OffsetDateTime': 'string',
  'java.time.Duration': 'string',
  'java.time.Period': 'string',
  'java.util.Calendar': 'Date',
  'java.sql.Date': 'string',
  'java.sql.Timestamp': 'string',
  'int': 'number',
  'long': 'number',
  'float': 'number',
  'double': 'number',
  'boolean': 'boolean',
  'byte': 'number',
  'char': 'string',
  'short': 'number',
  'void': 'void',
  'Object': 'any',
  'java.lang.Object': 'any',
  'String': 'string',
  'Integer': 'number',
  'Long': 'number',
  'Float': 'number',
  'Double': 'number',
  'Boolean': 'boolean',
  'Date': 'Date',
  'BigDecimal': 'number',
  'UUID': 'string',
};

/**
 * 将Java类型转换为TypeScript类型
 * @param javaType Java类型
 * @returns TypeScript类型
 */
export const mapJavaToTypeScriptType = (javaType: string): string => {
  if (!javaType) return 'any';
  
  // 处理数组类型
  if (javaType.includes('[]') || javaType.includes('List<') || javaType.includes('Set<') || 
      javaType.includes('Collection<') || javaType.includes('Iterable<')) {
    let genericType = 'any';
    
    if (javaType.match(/<([^>]+)>/)) {
      const match = javaType.match(/<([^>]+)>/);
      if (match && match[1]) {
        genericType = mapJavaToTypeScriptType(match[1]);
      }
    } else if (javaType.includes('[]')) {
      genericType = mapJavaToTypeScriptType(javaType.replace('[]', ''));
    }
    
    return `${genericType}[]`;
  }
  
  // 处理Map类型
  if (javaType.includes('Map<')) {
    const match = javaType.match(/Map<([^,]+),\s*([^>]+)>/);
    if (match && match[1] && match[2]) {
      const keyType = mapJavaToTypeScriptType(match[1]);
      const valueType = mapJavaToTypeScriptType(match[2]);
      return `Record<${keyType}, ${valueType}>`;
    }
  }
  
  // 处理Page类型
  if (javaType.includes('Page<') || javaType.includes('IPage<')) {
    const match = javaType.match(/<([^>]+)>/);
    if (match && match[1]) {
      const itemType = mapJavaToTypeScriptType(match[1]);
      return `{ records: ${itemType}[], total: number, size: number, current: number }`;
    }
  }
  
  return javaToTypeScriptTypeMap[javaType] || 'any';
};

/**
 * 生成参数的TypeScript接口
 * @param params 参数列表
 * @param name 接口名称
 * @returns TypeScript接口定义
 */
export const generateTypeScriptInterface = (
  params: any[], 
  name: string,
  isRequest: boolean = true,
  parentPath: string = ''
): { code: string, interfaces: Record<string, string> } => {
  // 移除特殊字符并生成接口名称
  let sanitizedName = name.replace(/[^a-zA-Z0-9_$]/g, '') || 'ApiType';
  
  // 如果名称以数字开头，添加前缀
  if (/^\d/.test(sanitizedName)) {
    sanitizedName = 'Api' + sanitizedName;
  }
  
  const interfaceName = upperFirst(camelCase(sanitizedName)) + (isRequest ? 'Request' : '');
  const interfaces: Record<string, string> = {};
  let code = `interface ${interfaceName} {\n`;
  
  // 收集需要创建的子接口
  const nestedInterfaces: Record<string, { params: any[], path: string }> = {};
  
  params.forEach((param, index) => {
    if (!param.name || typeof param.name !== 'string') {
      console.warn(`参数索引 ${index} 缺少name字段:`, param);
      return;
    }
    
    const currentPath = parentPath ? `${parentPath}.${param.name}` : param.name;
    
    // 检查是否是数组类型
    const isArray = param.dataType?.includes('[]') || 
                   param.dataType?.includes('List<') || 
                   param.dataType?.includes('Set<') ||
                   param.dataType?.includes('Collection<') ||
                   param.dataType?.includes('Iterable<');
    
    let tsType = mapJavaToTypeScriptType(param.dataType);
    
    // 处理嵌套对象
    if (param.children) {
      console.log(`处理嵌套字段: ${currentPath}, children类型:`, typeof param.children, 'isArray:', Array.isArray(param.children));
      
      // 处理children字段 - 可能是数组或对象
      let childrenArray: any[] = [];
      
      if (Array.isArray(param.children)) {
        childrenArray = param.children;
      } else if (param.children && typeof param.children === 'object' && param.children.parameters) {
        // 如果children是对象且有parameters属性
        childrenArray = param.children.parameters;
        console.log(`${currentPath} 使用 children.parameters:`, childrenArray.length, '个参数');
      } else if (param.children && typeof param.children === 'object') {
        // 如果children是单个对象，将其转换为数组
        console.warn(`${currentPath} 的 children 是对象而非数组，尝试转换`);
        childrenArray = [param.children];
      }
      
      if (childrenArray.length > 0) {
        // 清理字段名，移除特殊字符
        const cleanFieldName = param.name.replace(/[^a-zA-Z0-9_$]/g, '');
        const fieldNameCapitalized = upperFirst(camelCase(cleanFieldName || 'Nested'));
        
        // 生成嵌套接口名：基础名 + 字段名（首字母大写）
        const nestedName = sanitizedName + fieldNameCapitalized;
        
        // 使用完整路径作为key，避免命名冲突
        const uniqueKey = `${nestedName}_${currentPath.replace(/\./g, '_')}`;
        nestedInterfaces[uniqueKey] = { 
          params: childrenArray,
          path: currentPath
        };
        
        // 如果是数组类型，添加[]后缀
        if (isArray) {
          tsType = `${nestedName}[]`;
        } else {
          tsType = nestedName;
        }
        
        console.log(`生成嵌套接口: ${nestedName} (基础名: ${sanitizedName}, 字段: ${fieldNameCapitalized}), ${childrenArray.length} 个字段, 数组类型: ${isArray}`);
      } else {
        console.warn(`${currentPath} 的 children 为空或无法解析`);
      }
    }
    
    // 生成字段注释，包含备注、描述、示例和枚举
    const comments: string[] = [];
    
    // 优先使用value字段（备注），其次是description（描述）
    const mainDesc = param.value || param.description || param.desc;
    if (mainDesc && typeof mainDesc === 'string' && mainDesc.trim()) {
      comments.push(mainDesc.trim());
    }
    
    // 添加枚举信息（固定取 code 和 message）
    if (param.extendDescr && Array.isArray(param.extendDescr) && param.extendDescr.length > 0) {
      const enumValues = param.extendDescr.map((item: any) => {
        // 优先级：message > description
        const label = item.message || item.description;
        if (item.code && label) {
          return `${item.code}=${label}`;
        }
        return '';
      }).filter(v => v);
      
      if (enumValues.length > 0) {
        comments.push(`@enum {${enumValues.join(' | ')}}`);
      }
    }
    
    // 添加验证信息
    if (param.validate && Array.isArray(param.validate) && param.validate.length > 0) {
      param.validate.forEach((validation: any, index: number) => {
        const parts: string[] = [];
        
        // 错误码（放在最前面）
        if (validation.code) {
          parts.push(`code: ${validation.code}`);
        }
        
        // 优先级
        if (validation.level !== undefined) {
          parts.push(`level: ${validation.level}`);
        }
        
        // 验证类型
        if (validation.type) {
          parts.push(validation.type);
        }
        
        // 验证描述
        if (validation.descr && validation.descr !== validation.type) {
          parts.push(validation.descr);
        }
        
        // 验证规则（正则表达式或规则名）
        if (validation.pattern) {
          // 如果是正则表达式，显示完整内容
          if (validation.pattern.startsWith('^') || validation.pattern.includes('\\d')) {
            parts.push(`pattern: ${validation.pattern}`);
          } else {
            // 否则只显示规则名（如 NotNull, NotEmpty）
            parts.push(validation.pattern);
          }
        }
        
        // 错误消息
        if (validation.message) {
          parts.push(`message: "${validation.message}"`);
        }
        
        if (parts.length > 0) {
          const validationStr = parts.join(', ');
          if (param.validate.length === 1) {
            comments.push(`@validate ${validationStr}`);
          } else {
            comments.push(`@validate[${index + 1}] ${validationStr}`);
          }
        }
      });
    }
    
    // 添加示例值
    if (param.example && typeof param.example === 'string' && param.example.trim()) {
      comments.push(`@example ${param.example.trim()}`);
    }
    
    // 添加默认值
    if (param.defaultValue && typeof param.defaultValue === 'string' && param.defaultValue.trim()) {
      comments.push(`@default ${param.defaultValue.trim()}`);
    }
    
    // 生成注释
    if (comments.length > 0) {
      if (comments.length === 1) {
        code += `  /** ${comments[0]} */\n`;
      } else {
        code += `  /**\n`;
        code += `   * ${comments[0]}\n`;
        for (let i = 1; i < comments.length; i++) {
          code += `   * ${comments[i]}\n`;
        }
        code += `   */\n`;
      }
    }
    
    code += `  ${param.name}${param.required ? '' : '?'}: ${tsType};\n`;
  });
  
  code += `}\n`;
  interfaces[interfaceName] = code;
  
  // 递归生成嵌套接口
  Object.entries(nestedInterfaces).forEach(([uniqueKey, { params: nestedParams, path }]) => {
    // 从uniqueKey中提取实际的接口名
    const nestedName = uniqueKey.split('_')[0];
    console.log(`递归生成嵌套接口: ${nestedName}, 路径: ${path}, 参数数量: ${nestedParams.length}`);
    
    const result = generateTypeScriptInterface(nestedParams, nestedName, false, path);
    
    // 合并接口，避免重复
    Object.entries(result.interfaces).forEach(([key, value]) => {
      if (!interfaces[key]) {
        interfaces[key] = value;
      } else {
        console.warn(`接口名冲突: ${key}, 跳过重复定义`);
      }
    });
  });
  
  console.log(`接口 ${interfaceName} 生成完成，共生成 ${Object.keys(interfaces).length} 个接口`);
  return { code, interfaces };
};

/**
 * 生成完整的TypeScript类型定义代码
 * @param apiDetail API详情
 * @param apiName API名称
 * @returns 完整的TypeScript类型定义代码
 */
/**
 * 检测是否为分页结构
 * @param params 参数列表
 * @returns 是否为分页结构
 */
const isPageStructure = (params: any[]): boolean => {
  // 检查是否包含分页相关的字段
  const pageFields = ['total', 'size', 'current', 'pages', 'records'];
  const paramNames = params.map(p => p.name);
  
  // 如果包含大部分分页字段，则认为是分页结构
  const matchCount = pageFields.filter(field => paramNames.includes(field)).length;
  return matchCount >= 3;
};

/**
 * 查找记录列表字段
 * @param params 参数列表
 * @returns 记录列表字段
 */
const findRecordsField = (params: any[]): any | null => {
  // 首先查找名为records的字段
  const recordsField = params.find(p => p.name === 'records');
  if (recordsField) return recordsField;
  
  // 其次查找名为rows的字段
  const rowsField = params.find(p => p.name === 'rows');
  if (rowsField) return rowsField;
  
  // 最后查找名为list的字段
  const listField = params.find(p => p.name === 'list');
  if (listField) return listField;
  
  // 查找任何数组类型的字段
  return params.find(p => 
    p.dataType?.includes('[]') || 
    p.dataType?.includes('List<') || 
    p.dataType?.includes('Set<') ||
    p.dataType?.includes('Collection<')
  );
};

/**
 * 检测是否为标准响应包装结构（包含code, message, data字段）
 * @param params 参数列表
 * @returns 是否为标准响应包装结构
 */
const isStandardResponseWrapper = (params: any[]): boolean => {
  const paramNames = params.map(p => p.name);
  // 检查是否同时包含 code, message, data 这三个字段
  return paramNames.includes('code') && 
         paramNames.includes('message') && 
         paramNames.includes('data');
};

/**
 * 查找data字段
 * @param params 参数列表
 * @returns data字段
 */
const findDataField = (params: any[]): any | null => {
  return params.find(p => p.name === 'data');
};

/**
 * 从完整的类名中提取业务名称（去掉 Controller 后缀）
 * 例如：com.csap.framework.example.mybatisplus.web.ProductController.queryListPage1 -> Product
 */
const extractControllerName = (fullKey: string): string => {
  if (!fullKey || typeof fullKey !== 'string') {
    return '';
  }
  
  // 按 . 分割
  const parts = fullKey.split('.');
  
  // 找到包含 Controller 的部分
  for (let i = parts.length - 1; i >= 0; i--) {
    if (parts[i].includes('Controller')) {
      // 去掉 Controller 后缀
      return parts[i].replace(/Controller$/, '');
    }
  }
  
  // 如果没找到 Controller，返回倒数第二个部分（类名）
  if (parts.length >= 2) {
    return parts[parts.length - 2];
  }
  
  return '';
};

export const generateTypeScriptTypes = (apiDetail: any, apiName: string): string => {
  if (!apiDetail) {
    throw new Error('apiDetail参数不能为空');
  }
  
  if (typeof apiDetail !== 'object') {
    throw new Error(`apiDetail必须是对象，当前类型: ${typeof apiDetail}`);
  }

  // 尝试从 key 中提取业务名称（去掉 Controller 后缀）
  let baseName = apiName;
  if (apiDetail.key) {
    const businessName = extractControllerName(apiDetail.key);
    if (businessName) {
      baseName = businessName;
      console.log('从 key 提取的业务名称:', businessName);
    }
  }

  console.log('生成TypeScript类型 - 基础名称:', baseName);
  console.log('生成TypeScript类型 - API名称:', apiName);
  console.log('生成TypeScript类型 - 请求参数数量:', apiDetail.request?.length || 0);
  console.log('生成TypeScript类型 - 响应参数数量:', apiDetail.response?.length || 0);
  
  let code = '';
  const allInterfaces: Record<string, string> = {};
  
  // 生成请求参数接口（使用 Controller 名称）
  if (apiDetail.request && apiDetail.request.length > 0) {
    const requestResult = generateTypeScriptInterface(apiDetail.request, baseName, true);
    Object.assign(allInterfaces, requestResult.interfaces);
  }
  
  // 生成响应数据接口
  if (apiDetail.response && apiDetail.response.length > 0) {
    // 检查是否已经是标准响应包装结构
    const isWrapper = isStandardResponseWrapper(apiDetail.response);
    
    if (isWrapper) {
      // 如果已经是包装结构，直接使用该结构，不再额外包装
      const dataField = findDataField(apiDetail.response);
      
      if (dataField && dataField.children && dataField.children.length > 0) {
        // 如果data字段有children，生成data内部的接口
        let sanitizedApiName = baseName.replace(/[^a-zA-Z0-9_$]/g, '');
        
        // 如果以数字开头，添加前缀
        if (/^\d/.test(sanitizedApiName)) {
          sanitizedApiName = 'Api' + sanitizedApiName;
        }
        
        // 检查data字段是否为数组类型
        const isDataArray = dataField.dataType?.includes('[]') || 
                           dataField.dataType?.includes('List<') || 
                           dataField.dataType?.includes('Set<') ||
                           dataField.dataType?.includes('Collection<') ||
                           dataField.dataType?.includes('Iterable<');
        
        console.log('data字段类型:', dataField.dataType, '是否为数组:', isDataArray);
        
        // 检查data内部是否为分页结构
        if (isPageStructure(dataField.children)) {
          const recordsField = findRecordsField(dataField.children);
          
          if (recordsField && recordsField.children && recordsField.children.length > 0) {
            // 生成记录项接口
            const recordItemName = upperFirst(camelCase(sanitizedApiName)) + 'Item';
            const recordItemResult = generateTypeScriptInterface(recordsField.children, recordItemName, false);
            Object.assign(allInterfaces, recordItemResult.interfaces);
            
            // 生成分页数据接口
            let pageDataName = upperFirst(camelCase(sanitizedApiName)) + 'PageData';
            
            // 如果pageDataName以数字开头，添加前缀
            if (/^\d/.test(pageDataName)) {
              pageDataName = 'Api' + pageDataName;
            }
            
            const recordItemType = Object.keys(recordItemResult.interfaces)[0];
            
            allInterfaces[pageDataName] = `interface ${pageDataName} {
  /** 总记录数 */
  total: number;
  /** 每页大小 */
  size: number;
  /** 当前页码 */
  current: number;
  /** 总页数 */
  pages?: number;
  /** 记录列表 */
  ${recordsField.name}: ${recordItemType}[];
}\n`;
            
            // 生成完整响应接口
            let responseName = upperFirst(camelCase(sanitizedApiName)) + 'Response';
            
            // 如果responseName以数字开头，添加前缀
            if (/^\d/.test(responseName)) {
              responseName = 'Api' + responseName;
            }
            
            allInterfaces[responseName] = `interface ${responseName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${pageDataName};
}\n`;
          } else {
            // data内部不是分页，直接生成data的接口
            const dataTypeName = upperFirst(camelCase(sanitizedApiName)) + 'Data';
            const dataResult = generateTypeScriptInterface(dataField.children, dataTypeName, false);
            Object.assign(allInterfaces, dataResult.interfaces);
            
            const responseName = upperFirst(camelCase(sanitizedApiName)) + 'Response';
            const dataType = Object.keys(dataResult.interfaces)[0];
            
            // 根据data字段的类型决定是否添加[]
            const finalDataType = isDataArray ? `${dataType}[]` : dataType;
            
            allInterfaces[responseName] = `interface ${responseName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${finalDataType};
}\n`;
          }
        } else {
          // data内部不是分页，直接生成data的接口
          let dataTypeName = upperFirst(camelCase(sanitizedApiName)) + 'Data';
          
          // 如果dataTypeName以数字开头，添加前缀
          if (/^\d/.test(dataTypeName)) {
            dataTypeName = 'Api' + dataTypeName;
          }
          
          const dataResult = generateTypeScriptInterface(dataField.children, dataTypeName, false);
          Object.assign(allInterfaces, dataResult.interfaces);
          
          let responseName = upperFirst(camelCase(sanitizedApiName)) + 'Response';
          
          // 如果responseName以数字开头，添加前缀
          if (/^\d/.test(responseName)) {
            responseName = 'Api' + responseName;
          }
          
          const dataType = Object.keys(dataResult.interfaces)[0];
          
          // 根据data字段的类型决定是否添加[]
          const finalDataType = isDataArray ? `${dataType}[]` : dataType;
          
          allInterfaces[responseName] = `interface ${responseName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${finalDataType};
}\n`;
        }
      } else {
        // data字段没有children或data字段是基本类型
        const responseResult = generateTypeScriptInterface(apiDetail.response, baseName, false);
        Object.assign(allInterfaces, responseResult.interfaces);
        
        // 如果data字段没有children但是是数组基本类型，需要特殊处理
        if (dataField) {
          let sanitizedApiName = baseName.replace(/[^a-zA-Z0-9_$]/g, '');
          
          // 如果以数字开头，添加前缀
          if (/^\d/.test(sanitizedApiName)) {
            sanitizedApiName = 'Api' + sanitizedApiName;
          }
          
          let responseName = upperFirst(camelCase(sanitizedApiName)) + 'Response';
          
          // 如果responseName以数字开头，添加前缀
          if (/^\d/.test(responseName)) {
            responseName = 'Api' + responseName;
          }
          
          // 检查data字段是否为数组类型
          const isDataArray = dataField.dataType?.includes('[]') || 
                             dataField.dataType?.includes('List<') || 
                             dataField.dataType?.includes('Set<') ||
                             dataField.dataType?.includes('Collection<') ||
                             dataField.dataType?.includes('Iterable<');
          
          // 获取data的基础类型
          let dataType = mapJavaToTypeScriptType(dataField.dataType);
          
          // 如果已经是数组类型，mapJavaToTypeScriptType会自动处理
          // 但如果没有正确处理，我们手动添加
          if (isDataArray && !dataType.includes('[]')) {
            dataType = `${dataType}[]`;
          }
          
          allInterfaces[responseName] = `interface ${responseName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${dataType};
}\n`;
        }
      }
    } else if (isPageStructure(apiDetail.response)) {
      // 不是包装结构，但是分页结构
      // 查找记录列表字段
      const recordsField = findRecordsField(apiDetail.response);
      
      if (recordsField && recordsField.children && recordsField.children.length > 0) {
        // 生成记录项的接口
        let sanitizedApiName = baseName.replace(/[^a-zA-Z0-9_$]/g, '');
        
        // 如果以数字开头，添加前缀
        if (/^\d/.test(sanitizedApiName)) {
          sanitizedApiName = 'Api' + sanitizedApiName;
        }
        
        const recordItemName = upperFirst(camelCase(sanitizedApiName)) + 'Item';
        const recordItemResult = generateTypeScriptInterface(recordsField.children, recordItemName, false);
        Object.assign(allInterfaces, recordItemResult.interfaces);
        
        // 生成分页接口
        const pageTypeName = upperFirst(camelCase(sanitizedApiName)) + 'PageResult';
        const recordItemType = Object.keys(recordItemResult.interfaces)[0];
        
        allInterfaces[pageTypeName] = `interface ${pageTypeName} {
  /** 总记录数 */
  total: number;
  /** 每页大小 */
  size: number;
  /** 当前页码 */
  current: number;
  /** 总页数 */
  pages?: number;
  /** 记录列表 */
  ${recordsField.name}: ${recordItemType}[];
}\n`;
        
        // 添加标准响应包装接口
        const responseWrapperName = `${upperFirst(camelCase(sanitizedApiName))}Response`;
        
        allInterfaces[responseWrapperName] = `interface ${responseWrapperName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${pageTypeName};
}\n`;
      } else {
        // 常规处理 - 不是包装也不是分页
        const responseResult = generateTypeScriptInterface(apiDetail.response, baseName, false);
        Object.assign(allInterfaces, responseResult.interfaces);
        
        // 添加标准响应包装接口
        let sanitizedApiName = baseName.replace(/[^a-zA-Z0-9_$]/g, '');
        
        // 如果以数字开头，添加前缀
        if (/^\d/.test(sanitizedApiName)) {
          sanitizedApiName = 'Api' + sanitizedApiName;
        }
        
        const responseWrapperName = `${upperFirst(camelCase(sanitizedApiName))}Response`;
        const dataType = Object.keys(responseResult.interfaces)[0] || 'any';
        
        allInterfaces[responseWrapperName] = `interface ${responseWrapperName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${dataType};
}\n`;
      }
    } else {
      // 常规处理 - 不是包装也不是分页
      const responseResult = generateTypeScriptInterface(apiDetail.response, baseName, false);
      Object.assign(allInterfaces, responseResult.interfaces);
      
      // 添加标准响应包装接口
      let sanitizedApiName = baseName.replace(/[^a-zA-Z0-9_$]/g, '');
      
      // 如果以数字开头，添加前缀
      if (/^\d/.test(sanitizedApiName)) {
        sanitizedApiName = 'Api' + sanitizedApiName;
      }
      
      const responseWrapperName = `${upperFirst(camelCase(sanitizedApiName))}Response`;
      const dataType = Object.keys(responseResult.interfaces)[0] || 'any';
      
      allInterfaces[responseWrapperName] = `interface ${responseWrapperName} {
  /** 响应代码 */
  code: string;
  /** 响应消息 */
  message: string;
  /** 响应数据 */
  data: ${dataType};
}\n`;
    }
  }
  
  // 按照依赖关系排序接口
  const sortedInterfaces = Object.values(allInterfaces);
  
  // 组合所有接口
  code = sortedInterfaces.join('\n');
  
  return code;
};