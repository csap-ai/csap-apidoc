import React, { useState } from 'react';
import { Button, message, Modal, Typography, Space, Tooltip } from 'antd';
import { CopyOutlined, CheckOutlined } from '@ant-design/icons';
import { generateTypeScriptTypes } from '../../utils/typeScriptConverter';
import { logApiStructure } from '../../utils/debug';
import './index.less';

const { Text } = Typography;

interface CopyTypeScriptProps {
  apiDetail: any;
  apiName: string;
}

const CopyTypeScript: React.FC<CopyTypeScriptProps> = ({ apiDetail, apiName }) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [typeScriptCode, setTypeScriptCode] = useState('');
  const [copied, setCopied] = useState(false);

  const showModal = () => {
    try {
      // 调试API结构
      logApiStructure(apiDetail);
      
      if (!apiDetail) {
        message.error('API详情数据为空');
        return;
      }
      
      const code = generateTypeScriptTypes(apiDetail, apiName);
      setTypeScriptCode(code);
      setIsModalVisible(true);
      setCopied(false);
    } catch (error) {
      console.error('生成TypeScript类型失败:', error);
      message.error('生成TypeScript类型失败，请检查API数据结构');
    }
  };

  const handleCancel = () => {
    setIsModalVisible(false);
  };

  const copyToClipboard = () => {
    navigator.clipboard.writeText(typeScriptCode)
      .then(() => {
        message.success('TypeScript类型已复制到剪贴板');
        setCopied(true);
        setTimeout(() => setCopied(false), 3000);
      })
      .catch(() => {
        message.error('复制失败，请手动复制');
      });
  };

  return (
    <>
      <Button 
        className="copy-typescript-btn"
        type="primary" 
        icon={<CopyOutlined />} 
        onClick={showModal}
      >
        复制为 TypeScript
      </Button>

      <Modal
        title="TypeScript 类型定义"
        open={isModalVisible}
        onCancel={handleCancel}
        width={800}
        className="typescript-modal"
        footer={[
          <Button 
            key="copy" 
            type="primary" 
            onClick={copyToClipboard}
            icon={copied ? <CheckOutlined /> : <CopyOutlined />}
          >
            {copied ? '已复制' : '复制到剪贴板'}
          </Button>,
          <Button key="close" onClick={handleCancel}>
            关闭
          </Button>
        ]}
      >
        <div style={{ marginBottom: 20 }}>
          <Text type="secondary" style={{ fontSize: 14, lineHeight: 1.6 }}>
            以下是根据 API 参数自动生成的 TypeScript 类型定义，可以直接复制到你的前端项目中使用。
          </Text>
        </div>
        
        <div style={{ position: 'relative' }}>
          <pre
            style={{ 
              borderRadius: '12px',
              padding: '20px',
              maxHeight: '500px',
              overflow: 'auto',
              backgroundColor: '#1e293b',
              color: '#e2e8f0',
              fontFamily: "'SF Mono', 'Monaco', 'Menlo', 'Consolas', 'Courier New', monospace",
              fontSize: '13px',
              lineHeight: 1.6,
              border: '1px solid #334155',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)'
            }}
          >
            {typeScriptCode}
          </pre>
        </div>
      </Modal>
    </>
  );
};

export default CopyTypeScript;