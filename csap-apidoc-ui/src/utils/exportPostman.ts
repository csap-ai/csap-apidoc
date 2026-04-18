/**
 * 导出 Postman Collection v2.1 格式
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
 * 生成示例 JSON 数据
 */
const generateExampleJson = (params: Parameter[]): any => {
  if (!params || params.length === 0) {
    return {};
  }

  const result: any = {};

  params.forEach((param) => {
    let value: any;

    if (param.example) {
      value = param.example;
    } else if (param.defaultValue) {
      value = param.defaultValue;
    } else if (param.children && param.children.length > 0) {
      // 处理嵌套对象
      if (param.dataType.includes('List') || param.dataType.includes('Array')) {
        value = [generateExampleJson(param.children)];
      } else {
        value = generateExampleJson(param.children);
      }
    } else {
      // 根据数据类型生成默认值
      switch (param.dataType) {
        case 'String':
          value = '';
          break;
        case 'Integer':
        case 'Long':
          value = 0;
          break;
        case 'Double':
        case 'Float':
        case 'BigDecimal':
          value = 0.0;
          break;
        case 'Boolean':
          value = false;
          break;
        case 'Date':
        case 'DateTime':
          value = new Date().toISOString();
          break;
        default:
          value = '';
      }
    }

    result[param.name] = value;
  });

  return result;
};

/**
 * 生成 Postman 请求项
 */
const generatePostmanItem = (api: ApiDetail, baseUrl: string): any => {
  const item: any = {
    name: api.title,
    request: {
      method: api.method.toUpperCase(),
      header: [],
      url: {
        raw: '',
        protocol: '',
        host: [],
        path: [],
        query: [],
      },
      description: api.title,
    },
    response: [],
  };

  // 解析 URL
  const fullUrl = `${baseUrl}${api.path}`;
  const urlObj = new URL(fullUrl.startsWith('http') ? fullUrl : `http://localhost${api.path}`);

  // 设置 URL 信息
  item.request.url.raw = `{{baseUrl}}${api.path}`;
  item.request.url.protocol = urlObj.protocol.replace(':', '');
  item.request.url.host = urlObj.hostname.split('.');
  item.request.url.path = api.path.split('/').filter((p) => p);

  // 添加 Headers
  if (api.headers && api.headers.length > 0) {
    api.headers.forEach((header) => {
      item.request.header.push({
        key: header.key,
        value: header.value || '',
        description: header.description,
        type: 'text',
      });
    });
  }

  // 添加 Content-Type
  if (api.method.toUpperCase() === 'POST' || api.method.toUpperCase() === 'PUT') {
    const hasContentType = api.headers?.some((h) => h.key.toLowerCase() === 'content-type');
    if (!hasContentType) {
      item.request.header.push({
        key: 'Content-Type',
        value: 'application/json',
        type: 'text',
      });
    }
  }

  // 根据请求方法添加参数
  if (api.request && api.request.length > 0) {
    if (api.method.toUpperCase() === 'GET' || api.method.toUpperCase() === 'DELETE') {
      // Query 参数
      api.request.forEach((param) => {
        item.request.url.query.push({
          key: param.name,
          value: param.example || param.defaultValue || '',
          description: param.description,
          disabled: !param.required,
        });
      });
      // 更新 raw URL
      const queryParams = item.request.url.query
        .map((q: any) => `${q.key}=${q.value}`)
        .join('&');
      if (queryParams) {
        item.request.url.raw += `?${queryParams}`;
      }
    } else if (
      api.method.toUpperCase() === 'POST' ||
      api.method.toUpperCase() === 'PUT' ||
      api.method.toUpperCase() === 'PATCH'
    ) {
      // Request Body
      const exampleBody = generateExampleJson(api.request);
      item.request.body = {
        mode: 'raw',
        raw: JSON.stringify(exampleBody, null, 2),
        options: {
          raw: {
            language: 'json',
          },
        },
      };
    }
  }

  // 添加示例响应
  if (api.response && api.response.length > 0) {
    const exampleResponse = generateExampleJson(api.response);
    item.response.push({
      name: 'Successful Response',
      originalRequest: item.request,
      status: 'OK',
      code: 200,
      _postman_previewlanguage: 'json',
      header: [
        {
          key: 'Content-Type',
          value: 'application/json',
        },
      ],
      cookie: [],
      body: JSON.stringify(exampleResponse, null, 2),
    });
  }

  return item;
};

/**
 * 将树形结构转换为 Postman Collection 结构
 */
const convertTreeToPostmanItems = (
  nodes: ApiNode[],
  apiDetails: Map<string, ApiDetail>,
  baseUrl: string
): any[] => {
  const items: any[] = [];

  nodes.forEach((node) => {
    if (node.children && node.children.length > 0) {
      // 分组节点
      const folderItem: any = {
        name: node.title,
        item: convertTreeToPostmanItems(node.children, apiDetails, baseUrl),
      };
      items.push(folderItem);
    } else if (node.path && node.method) {
      // API 节点
      const detail = apiDetails.get(node.key);
      if (detail) {
        const postmanItem = generatePostmanItem(detail, baseUrl);
        items.push(postmanItem);
      }
    }
  });

  return items;
};

/**
 * 导出为 Postman Collection v2.1 格式
 * @param treeData API 树形数据
 * @param getApiDetail 获取 API 详情的函数
 * @param baseUrl API 基础 URL
 * @param collectionName Collection 名称
 */
export const exportToPostman = async (
  treeData: ApiNode[],
  getApiDetail: (apiNode: ApiNode) => Promise<ApiDetail>,
  baseUrl: string = 'http://localhost:8080',
  collectionName: string = 'API Documentation'
): Promise<string> => {
  // 收集所有 API 详情
  const apiDetails = new Map<string, ApiDetail>();

  const collectApiDetails = async (nodes: ApiNode[]) => {
    for (const node of nodes) {
      if (node.children && node.children.length > 0) {
        await collectApiDetails(node.children);
      } else if (node.path && node.method) {
        try {
          const detail = await getApiDetail(node);
          apiDetails.set(node.key, detail);
        } catch (error) {
          console.error(`Failed to get detail for API: ${node.title}`, error);
        }
      }
    }
  };

  await collectApiDetails(treeData);

  // 创建 Postman Collection 结构
  const postmanCollection: any = {
    info: {
      _postman_id: generateUuid(),
      name: collectionName,
      description: 'Generated API documentation from CSAP Framework',
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
      _exporter_id: '',
    },
    item: convertTreeToPostmanItems(treeData, apiDetails, baseUrl),
    variable: [
      {
        key: 'baseUrl',
        value: baseUrl,
        type: 'string',
      },
    ],
  };

  return JSON.stringify(postmanCollection, null, 2);
};

/**
 * 生成 UUID
 */
const generateUuid = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

/**
 * 下载 Postman Collection
 */
export const downloadPostmanCollection = (
  content: string,
  filename: string = 'postman_collection.json'
) => {
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

