/**
 * 导出 Markdown 格式
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
  paramType?: string;
  children?: Parameter[];
  extendDescr?: any[];
  validate?: any[];
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
 * 转义 Markdown 特殊字符
 */
const escapeMarkdown = (text: string): string => {
  if (!text) return '';
  return text
    .replace(/\\/g, '\\\\')
    .replace(/\|/g, '\\|')
    .replace(/\*/g, '\\*')
    .replace(/_/g, '\\_')
    .replace(/~/g, '\\~')
    .replace(/`/g, '\\`');
};

/**
 * 生成参数表格
 */
const generateParameterTable = (params: Parameter[], level: number = 0): string => {
  if (!params || params.length === 0) {
    return '暂无参数\n\n';
  }

  let markdown = '| 参数名称 | 数据类型 | 必填 | 默认值 | 示例 | 描述 |\n';
  markdown += '|---------|---------|------|--------|------|------|\n';

  const generateRows = (params: Parameter[], prefix: string = '') => {
    params.forEach((param) => {
      const indent = prefix ? `${prefix}.` : '';
      const name = `${indent}${param.name}`;
      const required = param.required ? '是' : '否';
      const dataType = escapeMarkdown(param.dataType || '');
      const defaultValue = escapeMarkdown(param.defaultValue || '-');
      const example = escapeMarkdown(param.example || '-');
      let description = escapeMarkdown(param.description || '');

      // 添加枚举信息
      if (param.extendDescr && param.extendDescr.length > 0) {
        const enumValues = param.extendDescr
          .map((e) => {
            if (e.code && e.message) {
              return `${e.code}:${e.message}`;
            }
            return e.name || e.code || e.message;
          })
          .join(', ');
        description += ` (枚举: ${enumValues})`;
      }

      // 添加验证信息
      if (param.validate && param.validate.length > 0) {
        const validations = param.validate
          .map((v) => {
            let validText = v.type || '验证';
            if (v.pattern) {
              validText += `: ${v.pattern}`;
            }
            return validText;
          })
          .join('; ');
        description += ` [验证: ${validations}]`;
      }

      markdown += `| ${escapeMarkdown(name)} | ${dataType} | ${required} | ${defaultValue} | ${example} | ${description} |\n`;

      // 递归处理嵌套参数
      if (param.children && param.children.length > 0) {
        generateRows(param.children, name);
      }
    });
  };

  generateRows(params);
  markdown += '\n';

  return markdown;
};

/**
 * 生成示例 JSON
 */
const generateExampleJson = (params: Parameter[], indent: number = 0): string => {
  if (!params || params.length === 0) {
    return '{}';
  }

  const result: any = {};

  params.forEach((param) => {
    let value: any;

    if (param.example) {
      // 尝试解析为正确的类型
      try {
        value = JSON.parse(param.example);
      } catch {
        value = param.example;
      }
    } else if (param.defaultValue) {
      value = param.defaultValue;
    } else if (param.children && param.children.length > 0) {
      if (param.dataType.includes('List') || param.dataType.includes('Array')) {
        const childExample = generateExampleJson(param.children, indent + 2);
        value = [JSON.parse(childExample)];
      } else {
        value = JSON.parse(generateExampleJson(param.children, indent + 2));
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
          value = '2024-01-01T00:00:00Z';
          break;
        default:
          value = '';
      }
    }

    result[param.name] = value;
  });

  return JSON.stringify(result, null, 2);
};

/**
 * 生成单个 API 的 Markdown
 */
const generateApiMarkdown = (api: ApiDetail, index: number): string => {
  let markdown = '';

  // API 标题和基本信息
  markdown += `### ${index}. ${api.title}\n\n`;
  markdown += `**请求方法**: \`${api.method}\`\n\n`;
  markdown += `**请求路径**: \`${api.path}\`\n\n`;

  // Headers
  if (api.headers && api.headers.length > 0) {
    markdown += '#### Headers\n\n';
    markdown += '| 参数名称 | 参数值 | 必填 | 描述 |\n';
    markdown += '|---------|--------|------|------|\n';
    api.headers.forEach((header) => {
      const required = header.required ? '是' : '否';
      markdown += `| ${escapeMarkdown(header.key)} | ${escapeMarkdown(header.value)} | ${required} | ${escapeMarkdown(header.description)} |\n`;
    });
    markdown += '\n';
  }

  // Request Parameters - 按 paramType 分组
  if (api.request && api.request.length > 0) {
    // 按 paramType 分组
    const paramGroups: Record<string, Parameter[]> = {};
    api.request.forEach(param => {
      const type = param.paramType || 'DEFAULT';
      if (!paramGroups[type]) {
        paramGroups[type] = [];
      }
      paramGroups[type].push(param);
    });

    // 显示每个分组
    Object.keys(paramGroups).forEach(paramType => {
      const params = paramGroups[paramType];
      if (params.length > 0) {
        const displayType = paramType === 'DEFAULT' ? 'Query' : paramType;
        markdown += `#### 请求参数 - ${displayType}\n\n`;
        markdown += generateParameterTable(params);
        
        // Request Example (只为 BODY 和 FORM_DATA 生成示例)
        if (paramType === 'BODY' || paramType === 'FORM_DATA') {
          markdown += '**请求示例**:\n\n';
          markdown += '```json\n';
          markdown += generateExampleJson(params);
          markdown += '\n```\n\n';
        }
      }
    });
  }

  // Response
  if (api.response && api.response.length > 0) {
    markdown += '#### 返回数据\n\n';
    markdown += generateParameterTable(api.response);

    // Response Example
    markdown += '**返回示例**:\n\n';
    markdown += '```json\n';
    markdown += generateExampleJson(api.response);
    markdown += '\n```\n\n';
  }

  markdown += '---\n\n';

  return markdown;
};

/**
 * 遍历树形结构生成 Markdown
 */
const traverseTreeToMarkdown = (
  nodes: ApiNode[],
  apiDetails: Map<string, ApiDetail>,
  level: number = 2
): string => {
  let markdown = '';
  let apiIndex = 1;

  nodes.forEach((node) => {
    if (node.children && node.children.length > 0) {
      // 分组标题
      const heading = '#'.repeat(level);
      markdown += `${heading} ${node.title}\n\n`;
      markdown += traverseTreeToMarkdown(node.children, apiDetails, level + 1);
    } else if (node.path && node.method) {
      // API 节点
      const detail = apiDetails.get(node.key);
      if (detail) {
        markdown += generateApiMarkdown(detail, apiIndex++);
      }
    }
  });

  return markdown;
};

/**
 * 导出为 Markdown 格式
 * @param treeData API 树形数据
 * @param getApiDetail 获取 API 详情的函数
 * @param title 文档标题
 */
export const exportToMarkdown = async (
  treeData: ApiNode[],
  getApiDetail: (apiNode: ApiNode) => Promise<ApiDetail>,
  title: string = 'API Documentation'
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

  // 生成 Markdown
  let markdown = '';

  // 文档标题
  markdown += `# ${title}\n\n`;

  // 文档说明
  markdown += '> 本文档由 CSAP Framework API Doc 自动生成\n\n';
  markdown += `> 生成时间: ${new Date().toLocaleString('zh-CN')}\n\n`;

  // 目录统计
  const totalApis = apiDetails.size;
  markdown += `## 概览\n\n`;
  markdown += `本文档包含 **${totalApis}** 个 API 接口。\n\n`;

  // API 内容
  markdown += traverseTreeToMarkdown(treeData, apiDetails);

  return markdown;
};

/**
 * 下载 Markdown 文档
 */
export const downloadMarkdown = (content: string, filename: string = 'api-documentation.md') => {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

