import LayoutHeader from './Header';
import { Tree, Input, Table, message, Tooltip, Result, Button, Spin, Tag } from 'antd';
import { FileTextOutlined, ApiOutlined, FileSearchOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import type { DirectoryTreeProps } from 'antd/es/tree';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import Clipboard from 'clipboard';
import { getMenuList, getApiDetail } from '@/api/apidoc';
import { columns, columnsBody, columnsBodyResponse } from './columns';
import CodeMirror from '@uiw/react-codemirror';
import { githubLight } from '@uiw/codemirror-theme-github';
import CopyTypeScript from '@/components/CopyTypeScript';
import axios from 'axios';
import { extractApiListFromBrokenJson } from '@/utils/jsonFixer';
import { exportToOpenApi, downloadOpenApiDoc } from '@/utils/exportOpenApi';
import { exportToPostman, downloadPostmanCollection } from '@/utils/exportPostman';
import { exportToMarkdown, downloadMarkdown } from '@/utils/exportMarkdown';
import { groupParametersByType, getNonEmptyParamTypes, getParamTypeLabel, buildRequestData, replacePathParams } from '@/utils/paramTypeUtils';
import { ExclamationCircleFilled, ThunderboltOutlined } from '@ant-design/icons';
import TryItOutPanel, { RequestSpec } from '@/components/TryItOutPanel';
import type { HttpMethod } from '@/services/tryItOutClient';

import './index.less';

const { Search } = Input;
const { DirectoryTree } = Tree;

const LayoutIndex = () => {
  const [toolipTitle, setToolipTitle] = useState('点击复制');
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [defaultExpandeKeys, SetDefaultExpandeKeys] = useState<React.Key[]>([]);
  const [apiOptions, setApiOptions] = useState<any[]>([]);
  const [treeData, setTreeData] = useState<any[]>([]);
  const [dataSource, setDataSource] = useState([]);
  const [dataBody, setDataBody] = useState([]);
  const [dataRes, setDataRes] = useState([]);
  const [dataObj, setDataObj] = useState({
    apiTitle: '',
    paramsTitle: '',
    patch: '',
    method: '',
  });
  const [finalData, setFinalData] = useState([]);
  const [DocText, setDocText] = useState(false);
  const [tryV2, setTryV2] = useState(false);
  const [ObjQuery, setObjQuery] = useState<any>({});
  const [loading, setLoading] = useState(false);
  const [totalApis, setTotalApis] = useState(0);

  // 计算总API数量
  const countApis = (data: any[]): number => {
    let count = 0;
    data.forEach(item => {
      if (item.children && item.children.length > 0) {
        count += countApis(item.children);
      } else if (!item.children) {
        count += 1;
      }
    });
    return count;
  };

  const onSelect: DirectoryTreeProps['onSelect'] = (keys: React.Key[], info) => {
    if (info.node.children === undefined) {
      if (keys[0] === expandedKeys[0]) return;
      setExpandedKeys(() => keys);
      if (pageView.current) {
        pageView.current.scrollTo({
          top: 0,
          behavior: 'smooth',
        });
      }
      setDocText(false);
      // 重置在线测试的状态
      setRespCode({});
      setRequestStatus(null);
    }
  };

  const getParentKey = (key, tree) => {
    let parentKey;
    for (let i = 0; i < tree.length; i++) {
      const node = tree[i];
      if (node.children) {
        if (node.children.some((item) => item.key === key)) {
          parentKey = node.key;
        } else if (getParentKey(key, node.children)) {
          parentKey = getParentKey(key, node.children);
        }
      }
    }
    return parentKey;
  };

  const DeleteChildren = (data) => {
    if (!data || !Array.isArray(data)) {
      return data;
    }

    for (let i = 0; i < data.length; i++) {
      const node = data[i];
      if (node && node.children) {
        if (node.children.length === 0) {
          delete node.children;
        } else {
          DeleteChildren(node.children);
        }
      }
    }
    return data;
  };

  const getTreeDetail = async (qxapi: string, { name, key }) => {
    try {
      const result = await getApiDetail(qxapi, { name, key });
      console.log('API响应数据:', result);

      const apiData = result?.data?.data;
      const {
        response = [],
        request = [],
        headers = [],
        title = '',
        paramType = '',
        method = '',
        patch = ''
      } = apiData || {};

      console.log('解析的数据:', { response, request, headers, title, paramType, method, patch });

      DeleteChildren(response);
      if (request) DeleteChildren(request);
      setDataSource(headers || []);
      const patchAPI = selectRefValue?.current?.value?.replace(
        '/csap/apidoc/parent',
        ''
      );
      setDataObj({
        apiTitle: title || '',
        paramsTitle: paramType || '',
        method: method || '',
        patch: patchAPI ? patchAPI + patch : patch || '',
      });
      setDataBody(() => request || []);
      setDataRes(() => response || []);
    } catch (error) {
      console.error('获取菜单详情失败:', error);
      setDataSource([]);
      setDataBody([]);
      setDataRes([]);
    }
  };

  const pageView = useRef<HTMLDivElement>();

  useEffect(() => {
    if (expandedKeys.length > 0 && treeData.length > 0) {
      if (selectRefValue.current?.value) {
        getTreeDetail(
          `${selectRefValue.current.value.split('/')[1]}/csap/apidoc/method`,
          {
            name: getParentKey(expandedKeys[0], treeData),
            key: expandedKeys.join(''),
          }
        );
      } else {
        getTreeDetail(`${import.meta.env.VITE_API_URL}/csap/apidoc/method`, {
          name: getParentKey(expandedKeys[0], treeData),
          key: expandedKeys.join(''),
        });
      }
    }
  }, [expandedKeys]);

  const getTreeList = async (url: string, isInitialLoad: boolean = false) => {
    try {
      setLoading(true);
      const response = await axios.get(url, {
        transformResponse: [data => data],
        headers: {
          'Content-Type': 'application/json',
        },
      });

      const text = response.data;

      let apiList = [];
      let resources = [];
      try {
        const parsedData = JSON.parse(text);

        // 直接从data中提取apiList
        apiList = parsedData?.data?.apiList || [];
        resources = parsedData?.data?.resources || [];
        // 确保apiList是数组
        if (!Array.isArray(apiList)) {
          apiList = [];
        }
      } catch (e) {
        console.warn('JSON解析失败，尝试使用修复工具:', e);
        apiList = extractApiListFromBrokenJson(text) || [];
      }
      // 确保apiList是数组
      if (!Array.isArray(apiList)) {
        apiList = [];
      }

      // 验证数据格式是否符合Tree组件要求
      const validatedList = apiList.map(item => ({
        ...item,
        key: item.key || item.id || Math.random().toString(36).substr(2, 9),
        title: item.title || item.name || '未命名',
        children: item.children || []
      }));

      if (apiList.length > 0) {
        console.log('有效的apiList数据:', apiList);
        setTreeData(validatedList);
        setFinalData(validatedList);
        setTotalApis(countApis(validatedList));
      }

      if (apiList.length > 0 && apiList[0].children?.length > 0) {
        setExpandedKeys([apiList[0].children[0].key]);
        SetDefaultExpandeKeys([apiList[0].key]);
      } else {
        setExpandedKeys([]);
        SetDefaultExpandeKeys([]);
      }

      // 只在初始加载时设置 apiOptions，切换服务时不更新，避免下拉选择刷新
      if (isInitialLoad) {
        setApiOptions(resources);
      }
    } catch (error) {
      console.error('获取菜单列表失败:', error);
      message.error(`加载API列表失败: ${error.message}`);
      setTreeData([]);
      setFinalData([]);
      // 只在初始加载时清空 apiOptions
      if (isInitialLoad) {
        setApiOptions([]);
      }
      setExpandedKeys([]);
      SetDefaultExpandeKeys([]);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const onExpand: DirectoryTreeProps['onExpand'] = (keys, info) => {
    if (info.expanded === true) {
      SetDefaultExpandeKeys(() => keys);
    } else {
      SetDefaultExpandeKeys([]);
    }
  };

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { value } = e.target;
    let ds = finalData
      .map((i) => {
        return Object.assign({}, i);
      })
      .filter((i) => {
        let list = [];
        if (i.children != null && i.children.length > 0) {
          list = childrenList(i.children, value);
        }
        if (list.length == 0) {
          return i.title.includes(value);
        }
        i.children = list;
        return true;
      });
    setTreeData(ds);
    if (value) {
      setExpandedKeys([]);
      SetDefaultExpandeKeys(() => ds.map((i) => i.key));
    }
    if (pageView.current) {
      pageView.current.scrollTo({
        top: 0,
        behavior: 'smooth',
      });
    }
  };

  const childrenList = (c: any[], v) => {
    return c.filter((i) => {
      return i.title.includes(v) || i.path.includes(v);
    });
  };

  useEffect(() => {
    const copy = new Clipboard('.api-ri');
    copy.on('success', (e) => {
      message.success('复制成功');
      setToolipTitle('已复制');
      setTimeout(() => {
        setToolipTitle('点击复制');
      }, 3000);
    });
    copy.on('error', function (e) {
      message.error('复制失败');
      setToolipTitle('点击复制');
    });
    return () => {
      copy?.destroy();
    };
  }, []);

  const onChangeValue = (value: string) => {
    if (value) {
      getTreeList(value);
    }
  };

  const selectRefValue = useRef<HTMLInputElement>(null);
  useEffect(() => {
    setLoading(true);
    // 初始加载时传入 true，会设置 apiOptions
    getTreeList(`${import.meta.env.VITE_API_URL}/csap/apidoc/parent`, true)
      .catch(error => {
        console.error('初始化加载菜单失败:', error);
        message.error('加载API文档失败，请检查网络或API服务');
      });
  }, []);

  // 导出功能处理
  const handleExport = async (format: 'openapi' | 'postman' | 'markdown') => {
    if (!finalData || finalData.length === 0) {
      message.warning('当前没有可导出的 API 数据');
      return;
    }

    const loadingMsg = message.loading('正在生成文档...', 0);

    try {
      // 获取 API 详情的辅助函数
      const getApiDetailForExport = async (apiNode: any) => {
        const qxapi = selectRefValue.current?.value
          ? `${selectRefValue.current.value.split('/')[1]}/csap/apidoc/method`
          : `${import.meta.env.VITE_API_URL}/csap/apidoc/method`;

        const result = await getApiDetail(qxapi, {
          name: getParentKey(apiNode.key, finalData),
          key: apiNode.key,
        });

        const apiData = result?.data?.data;
        return {
          title: apiData?.title || apiNode.title,
          path: apiData?.patch || apiNode.path,
          method: apiData?.method || apiNode.method,
          headers: apiData?.headers || [],
          request: apiData?.request || [],
          response: apiData?.response || [],
          paramType: apiData?.paramType || 'DEFAULT',
        };
      };

      // 获取基础 URL
      const baseUrl = selectRefValue.current?.value
        ? selectRefValue.current.value.replace('/csap/apidoc/parent', '')
        : import.meta.env.VITE_API_URL || 'http://localhost:8080';

      let content: string;
      let filename: string;

      switch (format) {
        case 'openapi':
          content = await exportToOpenApi(finalData, getApiDetailForExport, baseUrl);
          filename = 'openapi.json';
          downloadOpenApiDoc(content, filename);
          message.success('OpenAPI 文档导出成功！');
          break;

        case 'postman':
          content = await exportToPostman(
            finalData,
            getApiDetailForExport,
            baseUrl,
            'API Documentation'
          );
          filename = 'postman_collection.json';
          downloadPostmanCollection(content, filename);
          message.success('Postman Collection 导出成功！');
          break;

        case 'markdown':
          content = await exportToMarkdown(
            finalData,
            getApiDetailForExport,
            'API Documentation'
          );
          filename = 'api-documentation.md';
          downloadMarkdown(content, filename);
          message.success('Markdown 文档导出成功！');
          break;

        default:
          message.error('不支持的导出格式');
      }
    } catch (error) {
      console.error('导出失败:', error);
      message.error('导出失败，请稍后重试');
    } finally {
      loadingMsg();
    }
  };

  const Doc = () => {
    // 按参数类型分组
    const groupedParams = groupParametersByType(dataBody);
    const nonEmptyTypes = getNonEmptyParamTypes(groupedParams);

    return (
      <>
        <div className="api-headers">请求参数</div>

        {/* 参数说明图例 */}
        {(dataSource && dataSource.length > 0) || nonEmptyTypes.length > 0 ? (
          <div style={{
            padding: '12px 16px',
            marginBottom: '12px',
            backgroundColor: '#fafafa',
            border: '1px solid #f0f0f0',
            borderRadius: '4px',
            fontSize: '12px',
            display: 'flex',
            gap: '24px',
            alignItems: 'center'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ExclamationCircleFilled style={{ color: '#ff4d4f', fontSize: '12px' }} />
              <span>必填参数</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <CheckCircleOutlined style={{ color: '#d9d9d9', fontSize: '12px' }} />
              <span>可选参数</span>
            </div>
          </div>
        ) : null}

        {/* Headers 参数 */}
        {dataSource && dataSource.length > 0 && (
          <div>
            <div className="api-headers-item">Headers</div>
            <Table
              dataSource={dataSource}
              columns={columns}
              bordered
              pagination={false}
            />
          </div>
        )}

        {/* 按类型分组显示请求参数 */}
        {nonEmptyTypes.length > 0 ? (
          nonEmptyTypes.map((paramType) => (
            <div key={paramType}>
              <div className="api-headers-item">
                <Tag color={
                  paramType === 'PATH' ? 'blue' :
                  paramType === 'QUERY' ? 'cyan' :
                  paramType === 'BODY' ? 'green' :
                  paramType === 'FORM_DATA' ? 'orange' :
                  paramType === 'HEADER' ? 'purple' : 'default'
                } style={{ marginRight: 8 }}>
                  {paramType === 'DEFAULT' ? 'QUERY' : paramType}
                </Tag>
                {getParamTypeLabel(paramType)}
              </div>
              <Table
                columns={columnsBody as any}
                bordered
                defaultExpandAllRows={true}
                dataSource={groupedParams[paramType]}
                key={`${dataObj.patch}-${paramType}`}
                pagination={false}
              />
            </div>
          ))
        ) : (
          <div style={{ padding: '20px', textAlign: 'center', color: '#999' }}>
            暂无请求参数
          </div>
        )}
      </>
    );
  };

  const transformArrayToObject = (arr) => {
    if (!Array.isArray(arr)) return {};
    return arr.reduce((acc, { name, children, modelType }) => {
      if (children?.length > 0) {
        // 根据 modelType 判断是否是数组类型
        if (modelType === 'ARRAY') {
          // 数组类型
          acc[name] = [transformArrayToObject(children)];
        } else if (modelType === 'OBJECT' || modelType === 'T_OBJECT') {
          // 对象类型
          acc[name] = transformArrayToObject(children);
        } else {
          // 其他情况（如 BASE_DATA），默认为空字符串
          acc[name] = '';
        }
      } else {
        // 没有子对象的情况
        if (modelType === 'OBJECT' || modelType === 'T_OBJECT') {
          // 对象类型返回空对象
          acc[name] = {};
        } else if (modelType === 'ARRAY') {
          // 数组类型返回空数组
          acc[name] = [];
        } else {
          // BASE_DATA 基本数据类型返回空字符串
          acc[name] = '';
        }
      }
      return acc;
    }, {});
  };

  let code = {};
  const JsonMirrorRef = useRef<any>();
  const [editorLoaded, setEditorLoaded] = useState(false);
  useEffect(() => {
    if (JsonMirrorRef.current) {
      JsonMirrorRef.current.editor.on('load', () => {
        setEditorLoaded(true);
      });
    }
  }, []);

  // 生成 BODY 请求参数的 JSON 模板
  // 根据请求方法和参数类型过滤参数
  (Array.isArray(dataBody) ? dataBody : [])
    .filter((item) => {
      const paramType = item.paramType;
      const method = dataObj.method;

      // GET/DELETE 请求：只显示 QUERY 和 DEFAULT 参数
      if (method === 'GET' || method === 'DELETE') {
        return !paramType || paramType === 'QUERY' || paramType === 'DEFAULT';
      }

      // POST/PUT/PATCH 请求：只显示 BODY 参数，排除 PATH、QUERY、HEADER、FORM_DATA
      return !paramType || paramType === 'BODY';
    })
    .forEach((item) => {
      const { name, children, modelType } = item;
      if (children?.length > 0) {
        // 根据 modelType 判断是否是数组类型
        if (modelType === 'ARRAY') {
          // 数组类型
          code[name] = [transformArrayToObject(children)];
        } else if (modelType === 'OBJECT' || modelType === 'T_OBJECT') {
          // 对象类型
          code[name] = transformArrayToObject(children);
        } else {
          // 其他情况（如 BASE_DATA），默认为空字符串
          code[name] = '';
        }
      } else {
        // 没有子对象的情况
        if (modelType === 'OBJECT' || modelType === 'T_OBJECT') {
          // 对象类型返回空对象
          code[name] = {};
        } else if (modelType === 'ARRAY') {
          // 数组类型返回空数组
          code[name] = [];
        } else {
          // BASE_DATA 基本数据类型返回空字符串
          code[name] = '';
        }
      }
    });

  const onChangeQuery = useCallback((value: string) => {
    console.log(value, 'value');
    setObjQuery(value);
  }, []);

  const [respCode, setRespCode] = useState({});
  const [requestStatus, setRequestStatus] = useState<'success' | 'error' | null>(null);

  const handleRequest = async () => {
    let jsonData;
    if (Object.keys(ObjQuery).length != 0) {
      try {
        jsonData = JSON.parse(ObjQuery);
      } catch (e) {
        message.error('JSON 格式错误，请检查参数格式');
        return;
      }

      // 构建基础URL，与获取API详情保持一致
      let baseUrl = '';
      if (selectRefValue.current?.value) {
        // 使用用户选择的API地址
        baseUrl = selectRefValue.current.value.replace('/csap/apidoc/parent', '');
      } else {
        // 使用默认的VITE_API_URL
        baseUrl = import.meta.env.VITE_API_URL;
      }

      // 根据参数类型构建请求数据
      const { pathParams, queryParams, bodyData, formData, headers: customHeaders } = buildRequestData(dataBody, jsonData);

      // 替换 URL 中的 Path 参数
      let apiPath = dataObj.patch;
      if (Object.keys(pathParams).length > 0) {
        apiPath = replacePathParams(apiPath, pathParams);
        console.log('替换 Path 参数后的 URL:', apiPath);
      }

      const RequestUrl = `${apiPath}`;

      try {
        let response;
        const requestConfig: any = {
          timeout: 10000,
          headers: {
            ...customHeaders,
          },
          // 完全接收响应，不做任何转换
          transformResponse: [(data: any) => {
            try {
              return JSON.parse(data);
            } catch (e) {
              return data;
            }
          }]
        };

        if (dataObj.method === 'GET') {
          console.log('发送GET请求到:', RequestUrl, 'Query参数:', queryParams, 'Headers:', customHeaders);
          response = await axios.get(RequestUrl, {
            params: queryParams,
            ...requestConfig
          });
        } else if (dataObj.method === 'POST') {
          // 判断是 BODY 还是 FORM_DATA
          const hasBodyParams = dataBody.some(p => p.paramType === 'BODY');
          const hasFormDataParams = dataBody.some(p => p.paramType === 'FORM_DATA');

          let postData;
          if (hasFormDataParams) {
            // Form Data 提交
            const formDataObj = new FormData();
            Object.keys(formData).forEach(key => {
              formDataObj.append(key, formData[key]);
            });
            postData = formDataObj;
            requestConfig.headers['Content-Type'] = 'multipart/form-data';
            console.log('发送POST请求到:', RequestUrl, 'FormData:', formData, 'Query参数:', queryParams);
          } else if (hasBodyParams) {
            // JSON Body 提交
            postData = bodyData;
            requestConfig.headers['Content-Type'] = 'application/json';
            console.log('发送POST请求到:', RequestUrl, 'Body:', bodyData, 'Query参数:', queryParams);
          } else {
            // 默认使用 Query 参数作为 Body
            postData = queryParams;
            requestConfig.headers['Content-Type'] = 'application/json';
            console.log('发送POST请求到:', RequestUrl, '数据:', queryParams);
          }

          response = await axios.post(RequestUrl, postData, {
            params: Object.keys(queryParams).length > 0 ? queryParams : undefined,
            ...requestConfig
          });
        } else if (dataObj.method === 'PUT') {
          const hasBodyParams = dataBody.some(p => p.paramType === 'BODY');
          const postData = hasBodyParams ? bodyData : queryParams;
          requestConfig.headers['Content-Type'] = 'application/json';
          console.log('发送PUT请求到:', RequestUrl, '数据:', postData, 'Query参数:', queryParams);

          response = await axios.put(RequestUrl, postData, {
            params: Object.keys(queryParams).length > 0 ? queryParams : undefined,
            ...requestConfig
          });
        } else if (dataObj.method === 'DELETE') {
          console.log('发送DELETE请求到:', RequestUrl, 'Query参数:', queryParams, 'Headers:', customHeaders);
          response = await axios.delete(RequestUrl, {
            params: queryParams,
            ...requestConfig
          });
        } else if (dataObj.method === 'PATCH') {
          const hasBodyParams = dataBody.some(p => p.paramType === 'BODY');
          const postData = hasBodyParams ? bodyData : queryParams;
          requestConfig.headers['Content-Type'] = 'application/json';
          console.log('发送PATCH请求到:', RequestUrl, '数据:', postData);

          response = await axios.patch(RequestUrl, postData, {
            params: Object.keys(queryParams).length > 0 ? queryParams : undefined,
            ...requestConfig
          });
        }

        // 只根据 HTTP 状态码判断成功（2xx 范围）
        if (response && response.status >= 200 && response.status < 300) {
          console.log('请求成功，HTTP状态码:', response.status, '响应数据:', response.data);
          setRespCode(response.data);
          setRequestStatus('success');
          message.success(`请求成功 (HTTP ${response.status})`);
        } else {
          console.warn('请求返回非2xx状态码:', response?.status);
          setRespCode(response?.data || {});
          setRequestStatus('error');
          message.warning(`请求完成，但HTTP状态码为 ${response?.status}`);
        }
      } catch (error: any) {
        console.error('请求失败:', error);

        // 提取错误信息
        const status = error.response?.status;
        const errorData = error.response?.data;

        // 设置响应数据（即使是错误响应，也可能有返回数据）
        setRespCode(errorData || { error: error.message });
        setRequestStatus('error');

        // 根据 HTTP 状态码显示具体错误
        if (status) {
          message.error(`请求失败 (HTTP ${status})`);
        } else if (error.code === 'ECONNABORTED') {
          message.error('请求超时，请稍后重试');
        } else if (error.message === 'Network Error') {
          message.error('网络错误，请检查网络连接');
        } else {
          message.error('请求失败，请检查网络或API服务');
        }
      }
    } else {
      message.warning('请填写参数');
    }
  };

  return (
    <>
      <LayoutHeader
        onChangeValue={onChangeValue}
        apiOptions={apiOptions}
        ref={selectRefValue}
        onExport={handleExport}
      />
      {/* 调试按钮 - 开发时可启用 */}
      {/* <Button
        style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 999 }}
        onClick={() => {
          console.log('当前treeData:', treeData);
          console.log('当前expandedKeys:', expandedKeys);
          console.log('当前defaultExpandeKeys:', defaultExpandeKeys);
        }}
      >
        调试数据
      </Button> */}
      <div className="container">
        <div className="container-lf">
          <div className="search-container">
            <Search
              placeholder="搜索 API 接口..."
              onChange={onChange}
              allowClear
              size="large"
            />
            {treeData.length > 0 && (
              <div className="api-stats">
                <span>{treeData.length} 个分组</span>
                <span className="divider">•</span>
                <span>{totalApis} 个接口</span>
              </div>
            )}
          </div>
          <div className="tree-container">
            {treeData && treeData.length > 0 && (
              <DirectoryTree
              onSelect={onSelect}
              onExpand={onExpand}
              expandedKeys={defaultExpandeKeys}
              defaultSelectedKeys={expandedKeys}
              selectedKeys={expandedKeys}
              treeData={treeData}
              loadData={async ({ key, children }) => {
                if (children) return;
                // 这里可以添加子节点加载逻辑
              }}
              style={{ width: '100%' }}
              titleRender={(nodeData) => (
                <>
                  {nodeData?.children ? (
                    nodeData.title
                  ) : (
                    <div className="tree-span-title">
                      {nodeData.method == 'POST' && (
                        <span className="post-color">{nodeData.method}</span>
                      )}
                      {nodeData.method == 'GET' && (
                        <span className="get-color">{nodeData.method}</span>
                      )}
                      {nodeData.method == 'DELETE' && (
                        <span className="del-color">DEL</span>
                      )}
                      {nodeData.method == 'PUT' && (
                        <span className="put-color">{nodeData.method}</span>
                      )}
                      <span>{nodeData.title}</span>
                    </div>
                  )}
                </>
              )}
            />
            )}
          </div>
        </div>
        {loading ? (
          <div className="container-ri loading-container">
            <Spin size="large" tip="加载中..." />
          </div>
        ) : dataSource && dataSource.length > 0 ? (
          <div className="container-ri">
            <div className="container-ri-content" ref={pageView}>
              {/* 固定头部区域 - 单行布局 */}
              <div className="api-fixed-header">
                <div className="api-header-single-line">
                  <div className="api-title-section">
                    <h1 className="api-title">{dataObj.apiTitle}</h1>
                    <CopyTypeScript
                      apiDetail={{
                        request: dataBody,
                        response: dataRes,
                        title: dataObj.apiTitle,
                        method: dataObj.method,
                        path: dataObj.patch,
                        key: expandedKeys[0] // 添加 key 用于提取 Controller 名称
                      }}
                      apiName={dataObj.apiTitle}
                    />
                  </div>

                  <div className="api-patch">
                    {dataObj.method == 'POST' && (
                      <span className="api-lf api-lf-post-bg">
                        {dataObj.method}
                      </span>
                    )}
                    {dataObj.method == 'GET' && (
                      <span className="api-lf api-lf-get-bg">{dataObj.method}</span>
                    )}
                    {dataObj.method == 'PUT' && (
                      <span className="api-lf api-lf-put-bg">{dataObj.method}</span>
                    )}
                    {dataObj.method == 'DELETE' && (
                      <span className="api-lf api-lf-del-bg">{dataObj.method}</span>
                    )}
                    <span className="api-ri" data-clipboard-text={dataObj.patch}>
                      <Tooltip title={toolipTitle}>
                        <span>{dataObj.patch}</span>
                      </Tooltip>
                    </span>
                  </div>

                  <div className="api-actions">
                    <div className="postContent">
                      <div
                        className={`postGet ${!DocText && !tryV2 ? 'active' : ''}`}
                        onClick={() => {
                          setDocText(false);
                          setTryV2(false);
                          setRespCode({});
                          setRequestStatus(null);
                        }}
                      >
                        参数文档
                      </div>
                      <div
                        className={`postGet ${DocText && !tryV2 ? 'active' : ''}`}
                        onClick={() => {
                          setDocText(true);
                          setTryV2(false);
                          setRespCode({});
                          setRequestStatus(null);
                        }}
                      >
                        在线测试
                      </div>
                      <div
                        className={`postGet ${tryV2 ? 'active' : ''}`}
                        onClick={() => {
                          setTryV2(true);
                          setDocText(false);
                          setRespCode({});
                          setRequestStatus(null);
                        }}
                      >
                        <ThunderboltOutlined style={{ marginRight: 4 }} />
                        试运行 v2
                      </div>
                    </div>
                    {DocText && !tryV2 && (
                      <Button
                        type="primary"
                        onClick={handleRequest}
                        style={{ marginLeft: 12 }}
                      >
                        发送请求
                      </Button>
                    )}
                  </div>
                </div>
              </div>

              {tryV2 ? (
                <TryItOutPanel
                  initial={(() => {
                    const baseUrl = selectRefValue.current?.value
                      ? selectRefValue.current.value.replace('/csap/apidoc/parent', '')
                      : (import.meta.env.VITE_API_URL ?? '');
                    const path = dataObj.patch || '';
                    const url = baseUrl ? `${baseUrl}${path.startsWith('/') ? '' : '/'}${path}` : path;
                    const spec: RequestSpec = {
                      method: ((dataObj.method || 'GET').toUpperCase() as HttpMethod),
                      url,
                    };
                    return spec;
                  })()}
                />
              ) : DocText ? (
                <div className="jsonDiv">
                  <div>
                    <div className="jsonDiv-title">
                      <FileTextOutlined />
                      <span>
                        {dataObj.paramsTitle == 'DEFAULT'
                          ? 'QUERY'
                          : dataObj.paramsTitle} 请求参数
                      </span>
                    </div>
                    <CodeMirror
                      theme={githubLight}
                      onChange={onChangeQuery}
                      ref={JsonMirrorRef}
                      height="300px"
                      value={JSON.stringify(code, null, 2)}
                    />
                  </div>
                  <div>
                    <div className="jsonDiv-title">
                      <ApiOutlined />
                      <span>返回数据</span>
                      {requestStatus === 'success' && (
                        <Tag icon={<CheckCircleOutlined />} color="success" style={{ marginLeft: 8 }}>
                          请求成功
                        </Tag>
                      )}
                      {requestStatus === 'error' && (
                        <Tag icon={<CloseCircleOutlined />} color="error" style={{ marginLeft: 8 }}>
                          请求失败
                        </Tag>
                      )}
                    </div>
                    <CodeMirror
                      theme={githubLight}
                      value={JSON.stringify(respCode, null, 2)}
                      height="300px"
                    />
                  </div>
                </div>
              ) : (
                <Doc />
              )}

              <div className="api-headers">返回数据</div>
              <Table
                columns={columnsBodyResponse}
                bordered
                dataSource={dataRes}
                pagination={false}
                defaultExpandAllRows={true}
                key={dataObj.patch}
              />
            </div>
          </div>
        ) : (
          <div style={{ width: '100%', height: '80%' }}>
            <Result
              icon={<FileSearchOutlined style={{ fontSize: 80, color: '#6fd4c8' }} />}
              title="选择一个 API 开始探索"
              subTitle="从左侧列表中选择您想要查看的 API 接口，查看详细的参数说明和返回数据"
              style={{ margin: '200px auto 0px' }}
            />
          </div>
        )}
      </div>
    </>
  );
};

export default LayoutIndex;
