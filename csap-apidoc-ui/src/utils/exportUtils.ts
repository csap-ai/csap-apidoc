/**
 * 统一的导出工具入口
 * 支持导出为 OpenAPI 3.0、Postman Collection 2.1、Markdown 三种格式
 */

export { exportToOpenApi, downloadOpenApiDoc } from './exportOpenApi';
export { exportToPostman, downloadPostmanCollection } from './exportPostman';
export { exportToMarkdown, downloadMarkdown } from './exportMarkdown';

// 类型定义
export interface ApiNode {
  key: string;
  title: string;
  path: string;
  method: string;
  children?: ApiNode[];
  [key: string]: any;
}

export interface Parameter {
  name: string;
  dataType: string;
  required: boolean;
  description: string;
  example?: string;
  defaultValue?: string;
  children?: Parameter[];
  extendDescr?: any[];
  validate?: any[];
}

export interface ApiDetail {
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

export type ExportFormat = 'openapi' | 'postman' | 'markdown';

/**
 * 统一的导出函数
 * @param format 导出格式
 * @param treeData API 树形数据
 * @param getApiDetail 获取 API 详情的函数
 * @param options 导出选项
 */
export const exportApi = async (
  format: ExportFormat,
  treeData: ApiNode[],
  getApiDetail: (apiNode: ApiNode) => Promise<ApiDetail>,
  options: {
    baseUrl?: string;
    title?: string;
    collectionName?: string;
  } = {}
): Promise<string> => {
  const {
    baseUrl = 'http://localhost:8080',
    title = 'API Documentation',
    collectionName = 'API Documentation',
  } = options;

  switch (format) {
    case 'openapi': {
      const { exportToOpenApi } = await import('./exportOpenApi');
      return exportToOpenApi(treeData, getApiDetail, baseUrl);
    }
    case 'postman': {
      const { exportToPostman } = await import('./exportPostman');
      return exportToPostman(treeData, getApiDetail, baseUrl, collectionName);
    }
    case 'markdown': {
      const { exportToMarkdown } = await import('./exportMarkdown');
      return exportToMarkdown(treeData, getApiDetail, title);
    }
    default:
      throw new Error(`Unsupported export format: ${format}`);
  }
};

/**
 * 统一的下载函数
 * @param format 导出格式
 * @param content 文件内容
 * @param filename 文件名（可选）
 */
export const downloadFile = (
  format: ExportFormat,
  content: string,
  filename?: string
): void => {
  switch (format) {
    case 'openapi': {
      const { downloadOpenApiDoc } = require('./exportOpenApi');
      downloadOpenApiDoc(content, filename || 'openapi.json');
      break;
    }
    case 'postman': {
      const { downloadPostmanCollection } = require('./exportPostman');
      downloadPostmanCollection(content, filename || 'postman_collection.json');
      break;
    }
    case 'markdown': {
      const { downloadMarkdown } = require('./exportMarkdown');
      downloadMarkdown(content, filename || 'api-documentation.md');
      break;
    }
    default:
      throw new Error(`Unsupported export format: ${format}`);
  }
};

