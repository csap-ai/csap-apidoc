/**
 * 导出 OpenAPI 3.0 格式
 */

interface ApiNode {
  key: string;
  title: string;
  path: string;
  method: string;
  children?: ApiNode[];
  [key: string]: any;
}

interface Parameter {
  name: string;
  dataType: string;
  required: boolean;
  description: string;
  example?: string;
  defaultValue?: string;
  children?: Parameter[];
}

interface ApiDetail {
  title: string;
  path: string;
  method: string;
  headers: Array<{
    key: string;
    value: string;
    required: boolean;
    description: string;
  }>;
  request: Parameter[];
  response: Parameter[];
  paramType?: string;
}

/**
 * 将数据类型转换为 OpenAPI 类型
 */
const convertDataType = (dataType: string): { type: string; format?: string } => {
  const typeMap: Record<string, { type: string; format?: string }> = {
    'String': { type: 'string' },
    'Integer': { type: 'integer', format: 'int32' },
    'Long': { type: 'integer', format: 'int64' },
    'Double': { type: 'number', format: 'double' },
    'Float': { type: 'number', format: 'float' },
    'Boolean': { type: 'boolean' },
    'Date': { type: 'string', format: 'date' },
    'DateTime': { type: 'string', format: 'date-time' },
    'BigDecimal': { type: 'number' },
    'Object': { type: 'object' },
    'Array': { type: 'array' },
    'List': { type: 'array' },
    'Map': { type: 'object' },
  };

  // 处理泛型类型，例如 List<String>
  if (dataType.includes('<')) {
    const baseType = dataType.split('<')[0];
    if (baseType === 'List' || baseType === 'Array') {
      return { type: 'array' };
    }
    return { type: 'object' };
  }

  return typeMap[dataType] || { type: 'string' };
};

/**
 * 转换参数为 OpenAPI Schema
 */
const convertParametersToSchema = (params: Parameter[]): any => {
  if (!params || params.length === 0) {
    return { type: 'object', properties: {} };
  }

  const properties: Record<string, any> = {};
  const required: string[] = [];

  params.forEach((param) => {
    const typeInfo = convertDataType(param.dataType);
    const schema: any = {
      ...typeInfo,
      description: param.description || param.name,
    };

    if (param.example) {
      schema.example = param.example;
    }

    if (param.defaultValue) {
      schema.default = param.defaultValue;
    }

    // 处理嵌套对象
    if (param.children && param.children.length > 0) {
      if (typeInfo.type === 'array') {
        schema.items = convertParametersToSchema(param.children);
      } else {
        const childSchema = convertParametersToSchema(param.children);
        schema.properties = childSchema.properties;
        if (childSchema.required && childSchema.required.length > 0) {
          schema.required = childSchema.required;
        }
      }
    }

    properties[param.name] = schema;

    if (param.required) {
      required.push(param.name);
    }
  });

  const result: any = {
    type: 'object',
    properties,
  };

  if (required.length > 0) {
    result.required = required;
  }

  return result;
};

/**
 * 生成 OpenAPI 路径项
 */
const generatePathItem = (api: ApiDetail): any => {
  const method = api.method.toLowerCase();
  const pathItem: any = {};

  const operation: any = {
    summary: api.title,
    operationId: `${method}${api.path.replace(/\//g, '_')}`,
    tags: [],
    parameters: [],
    responses: {
      '200': {
        description: 'Successful response',
        content: {
          'application/json': {
            schema: convertParametersToSchema(api.response),
          },
        },
      },
    },
  };

  // 添加 Headers 参数
  if (api.headers && api.headers.length > 0) {
    api.headers.forEach((header) => {
      operation.parameters.push({
        name: header.key,
        in: 'header',
        required: header.required,
        description: header.description,
        schema: {
          type: 'string',
          example: header.value,
        },
      });
    });
  }

  // 根据请求类型添加参数
  if (api.request && api.request.length > 0) {
    if (method === 'get' || method === 'delete') {
      // Query 参数
      api.request.forEach((param) => {
        const typeInfo = convertDataType(param.dataType);
        operation.parameters.push({
          name: param.name,
          in: 'query',
          required: param.required,
          description: param.description,
          schema: {
            ...typeInfo,
            example: param.example,
            default: param.defaultValue,
          },
        });
      });
    } else if (method === 'post' || method === 'put' || method === 'patch') {
      // Request Body
      operation.requestBody = {
        required: api.request.some((p) => p.required),
        content: {
          'application/json': {
            schema: convertParametersToSchema(api.request),
          },
        },
      };
    }
  }

  pathItem[method] = operation;
  return pathItem;
};

/**
 * 遍历树形结构收集所有 API
 */
const collectApis = (nodes: ApiNode[]): ApiNode[] => {
  const apis: ApiNode[] = [];

  const traverse = (nodes: ApiNode[]) => {
    nodes.forEach((node) => {
      if (node.children) {
        traverse(node.children);
      } else if (node.path && node.method) {
        // 叶子节点且有 path 和 method 的是 API
        apis.push(node);
      }
    });
  };

  traverse(nodes);
  return apis;
};

/**
 * 导出为 OpenAPI 3.0 格式
 * @param treeData API 树形数据
 * @param getApiDetail 获取 API 详情的函数
 * @param baseUrl API 基础 URL
 */
export const exportToOpenApi = async (
  treeData: ApiNode[],
  getApiDetail: (apiNode: ApiNode) => Promise<ApiDetail>,
  baseUrl: string = ''
): Promise<string> => {
  // 收集所有 API
  const apis = collectApis(treeData);

  // 创建 OpenAPI 文档结构
  const openApiDoc: any = {
    openapi: '3.0.0',
    info: {
      title: 'API Documentation',
      description: 'Generated API documentation',
      version: '1.0.0',
    },
    servers: [
      {
        url: baseUrl || 'http://localhost:8080',
        description: 'API Server',
      },
    ],
    paths: {},
    components: {
      schemas: {},
    },
  };

  // 获取每个 API 的详情并生成路径项
  for (const api of apis) {
    try {
      const detail = await getApiDetail(api);
      const path = detail.path;

      if (!openApiDoc.paths[path]) {
        openApiDoc.paths[path] = {};
      }

      const pathItem = generatePathItem(detail);
      Object.assign(openApiDoc.paths[path], pathItem);
    } catch (error) {
      console.error(`Failed to get detail for API: ${api.title}`, error);
    }
  }

  return JSON.stringify(openApiDoc, null, 2);
};

/**
 * 下载 OpenAPI 文档
 */
export const downloadOpenApiDoc = (content: string, filename: string = 'openapi.json') => {
  const blob = new Blob([content], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

