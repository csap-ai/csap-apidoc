import React, { useState } from 'react';
import { Button, message, Modal, Typography, Space, Tooltip } from 'antd';
import { CopyOutlined, CheckOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { generateTypeScriptTypes } from '../../utils/typeScriptConverter';
import { logApiStructure } from '../../utils/debug';
import './index.less';

const { Text } = Typography;

interface CopyTypeScriptProps {
  apiDetail: any;
  apiName: string;
}

const CopyTypeScript: React.FC<CopyTypeScriptProps> = ({ apiDetail, apiName }) => {
  const { t } = useTranslation();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [typeScriptCode, setTypeScriptCode] = useState('');
  const [copied, setCopied] = useState(false);

  const showModal = () => {
    try {
      logApiStructure(apiDetail);

      if (!apiDetail) {
        message.error(t('ts.error.empty'));
        return;
      }

      const code = generateTypeScriptTypes(apiDetail, apiName);
      setTypeScriptCode(code);
      setIsModalVisible(true);
      setCopied(false);
    } catch (error) {
      console.error('生成TypeScript类型失败:', error);
      message.error(t('ts.error.generate'));
    }
  };

  const handleCancel = () => {
    setIsModalVisible(false);
  };

  const copyToClipboard = () => {
    navigator.clipboard.writeText(typeScriptCode)
      .then(() => {
        message.success(t('ts.copy.success'));
        setCopied(true);
        setTimeout(() => setCopied(false), 3000);
      })
      .catch(() => {
        message.error(t('ts.copy.failed'));
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
        {t('ts.button')}
      </Button>

      <Modal
        title={t('ts.modal.title')}
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
            {copied ? t('ts.modal.copied') : t('ts.modal.copy')}
          </Button>,
          <Button key="close" onClick={handleCancel}>
            {t('ts.modal.close')}
          </Button>
        ]}
      >
        <div style={{ marginBottom: 20 }}>
          <Text type="secondary" style={{ fontSize: 14, lineHeight: 1.6 }}>
            {t('ts.modal.desc')}
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