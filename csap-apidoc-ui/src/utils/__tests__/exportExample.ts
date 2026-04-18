/**
 * 导出功能使用示例
 * 这个文件展示了如何在项目中使用导出功能
 */

import { exportApi, downloadFile, ApiNode, ApiDetail } from '../exportUtils';

// 示例：API 树形数据
const exampleTreeData: ApiNode[] = [
  {
    key: 'user-group',
    title: '用户管理',
    path: '',
    method: '',
    children: [
      {
        key: 'user-list',
        title: '获取用户列表',
        path: '/api/users',
        method: 'GET',
      },
      {
        key: 'user-create',
        title: '创建用户',
        path: '/api/users',
        method: 'POST',
      },
    ],
  },
  {
    key: 'product-group',
    title: '产品管理',
    path: '',
    method: '',
    children: [
      {
        key: 'product-list',
        title: '获取产品列表',
        path: '/api/products',
        method: 'GET',
      },
    ],
  },
];

// 示例：获取 API 详情的函数
const getApiDetailExample = async (apiNode: ApiNode): Promise<ApiDetail> => {
  // 这里应该是实际的 API 调用
  // 为了示例，我们返回模拟数据
  return {
    title: apiNode.title,
    path: apiNode.path,
    method: apiNode.method,
    headers: [
      {
        key: 'Content-Type',
        value: 'application/json',
        required: true,
        description: '请求内容类型',
      },
    ],
    request: [
      {
        name: 'page',
        dataType: 'Integer',
        required: false,
        description: '页码',
        example: '1',
        defaultValue: '1',
      },
      {
        name: 'size',
        dataType: 'Integer',
        required: false,
        description: '每页数量',
        example: '10',
        defaultValue: '10',
      },
    ],
    response: [
      {
        name: 'code',
        dataType: 'Integer',
        required: true,
        description: '响应码',
        example: '200',
      },
      {
        name: 'message',
        dataType: 'String',
        required: true,
        description: '响应消息',
        example: '成功',
      },
      {
        name: 'data',
        dataType: 'Array',
        required: true,
        description: '数据列表',
        children: [
          {
            name: 'id',
            dataType: 'Long',
            required: true,
            description: 'ID',
            example: '1',
          },
          {
            name: 'name',
            dataType: 'String',
            required: true,
            description: '名称',
            example: '示例名称',
          },
        ],
      },
    ],
    paramType: 'QUERY',
  };
};

// 示例 1: 导出为 OpenAPI
export const exportOpenApiExample = async () => {
  try {
    const content = await exportApi('openapi', exampleTreeData, getApiDetailExample, {
      baseUrl: 'https://api.example.com',
      title: 'Example API',
    });

    downloadFile('openapi', content, 'example-openapi.json');
    console.log('OpenAPI 导出成功！');
  } catch (error) {
    console.error('OpenAPI 导出失败:', error);
  }
};

// 示例 2: 导出为 Postman Collection
export const exportPostmanExample = async () => {
  try {
    const content = await exportApi('postman', exampleTreeData, getApiDetailExample, {
      baseUrl: 'https://api.example.com',
      collectionName: 'Example API Collection',
    });

    downloadFile('postman', content, 'example-postman.json');
    console.log('Postman Collection 导出成功！');
  } catch (error) {
    console.error('Postman Collection 导出失败:', error);
  }
};

// 示例 3: 导出为 Markdown
export const exportMarkdownExample = async () => {
  try {
    const content = await exportApi('markdown', exampleTreeData, getApiDetailExample, {
      title: 'Example API Documentation',
    });

    downloadFile('markdown', content, 'example-api.md');
    console.log('Markdown 导出成功！');
  } catch (error) {
    console.error('Markdown 导出失败:', error);
  }
};

// 示例 4: 批量导出所有格式
export const exportAllFormatsExample = async () => {
  const formats: Array<'openapi' | 'postman' | 'markdown'> = [
    'openapi',
    'postman',
    'markdown',
  ];

  for (const format of formats) {
    try {
      const content = await exportApi(format, exampleTreeData, getApiDetailExample, {
        baseUrl: 'https://api.example.com',
        title: 'Example API Documentation',
        collectionName: 'Example API Collection',
      });

      downloadFile(format, content);
      console.log(`${format} 导出成功！`);
    } catch (error) {
      console.error(`${format} 导出失败:`, error);
    }
  }
};

// 如果在浏览器控制台中运行，可以使用以下方式测试
if (typeof window !== 'undefined') {
  // @ts-ignore
  window.exportExamples = {
    openapi: exportOpenApiExample,
    postman: exportPostmanExample,
    markdown: exportMarkdownExample,
    all: exportAllFormatsExample,
  };

  console.log('导出示例已加载，可以在控制台中使用：');
  console.log('- window.exportExamples.openapi()');
  console.log('- window.exportExamples.postman()');
  console.log('- window.exportExamples.markdown()');
  console.log('- window.exportExamples.all()');
}

