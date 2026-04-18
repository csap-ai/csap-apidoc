import { useState, forwardRef, useImperativeHandle } from 'react'
import { Drawer, Table, Button, Select, message, Tag, Space, Tooltip } from 'antd'
import { AppstoreAddOutlined, SearchOutlined, ThunderboltOutlined, InfoCircleOutlined, CheckCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { validatePatternTypes } from '@/api/devtools'

const { Option } = Select

interface ValidatePattern {
  descr: string
  pattern: string
  type: string
}

interface ValidateTableModalProps {
  onConfirm: (selected: ValidatePattern[]) => void
}

export interface ValidateTableModalRef {
  open: (currentValidates: any[]) => void
}

const ValidateTableModal = forwardRef<ValidateTableModalRef, ValidateTableModalProps>(({ onConfirm }, ref) => {
  const [visible, setVisible] = useState(false)
  const [loading, setLoading] = useState(false)
  const [validates, setValidates] = useState<ValidatePattern[]>([])
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [selectedRows, setSelectedRows] = useState<ValidatePattern[]>([])
  const [options, setOptions] = useState<any[]>([])
  const [searchType, setSearchType] = useState<string>('')
  const [fieldValidates, setFieldValidates] = useState<any[]>([])

  useImperativeHandle(ref, () => ({
    open: async (currentValidates: any[]) => {
      setVisible(true)
      setFieldValidates(currentValidates || [])
      await loadValidateList(currentValidates)
    }
  }))

  const loadValidateList = async (currentValidates: any[], filterType?: string) => {
    try {
      setLoading(true)
      const data = await validatePatternTypes()

      setOptions(data.patternTypeList || [])

      let patternList = data.patternList || []

      // 按类型过滤
      if (filterType) {
        patternList = patternList.filter((p: any) => p.type === filterType)
      }

      // 过滤掉已经添加的验证规则
      if (currentValidates && currentValidates.length > 0) {
        patternList = patternList.filter((p: any) =>
          !currentValidates.find((cv: any) => cv.pattern === p.pattern)
        )
      }

      setValidates(patternList)
    } catch (error: any) {
      message.error('加载验证规则失败：' + error.message)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setVisible(false)
    setValidates([])
    setSelectedRowKeys([])
    setSelectedRows([])
    setSearchType('')
  }

  const handleConfirm = () => {
    onConfirm(selectedRows)
    handleClose()
  }

  const handleSearch = () => {
    loadValidateList(fieldValidates, searchType)
  }

  const getTypeName = (type: string) => {
    if (!type || !options.length) return type || '-'
    const option = options.find(o => o.type === type)
    return option?.descr || type
  }

  const rowSelection = {
    selectedRowKeys,
    onChange: (keys: React.Key[], rows: ValidatePattern[]) => {
      setSelectedRowKeys(keys)
      setSelectedRows(rows)
    },
  }

  const columns: ColumnsType<ValidatePattern> = [
    {
      title: (
        <Space>
          <InfoCircleOutlined />
          <span>验证名称</span>
        </Space>
      ),
      dataIndex: 'descr',
      key: 'descr',
      width: 250,
      ellipsis: {
        showTitle: false,
      },
      render: (text) => (
        <Tooltip placement="topLeft" title={text || ''}>
          <Space>
            <CheckCircleOutlined style={{ color: '#52c41a' }} />
            <span style={{ fontWeight: 500 }}>{text || '-'}</span>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: (
        <Space>
          <ThunderboltOutlined />
          <span>正则表达式</span>
        </Space>
      ),
      dataIndex: 'pattern',
      key: 'pattern',
      ellipsis: {
        showTitle: false,
      },
      render: (text) => (
        <Tooltip placement="topLeft" title={text || ''}>
          <code style={{
            fontFamily: 'monospace',
            fontSize: '12px',
            color: '#1890ff',
            backgroundColor: '#f0f5ff',
            padding: '2px 8px',
            borderRadius: '4px',
            border: '1px solid #d6e4ff'
          }}>
            {text || '-'}
          </code>
        </Tooltip>
      ),
    },
    {
      title: '验证类型',
      key: 'type',
      width: 150,
      align: 'center',
      render: (_, record) => {
        const typeName = getTypeName(record.type)
        const colors = ['purple', 'blue', 'cyan', 'green', 'orange', 'red', 'magenta']
        const colorIndex = record.type ? String(record.type).charCodeAt(0) % colors.length : 0
        return (
          <Tag color={colors[colorIndex]} style={{ borderRadius: '4px' }}>
            {typeName}
          </Tag>
        )
      },
    },
  ]

  return (
    <Drawer
      title={
        <Space>
          <AppstoreAddOutlined style={{ color: '#722ed1' }} />
          <span>可用的验证规则库</span>
        </Space>
      }
      open={visible}
      onClose={handleClose}
      width="80%"
      footer={
        <div style={{ textAlign: 'right' }}>
          <Space>
            <Button onClick={handleClose}>取消</Button>
            <Button
              type="primary"
              onClick={handleConfirm}
              disabled={selectedRowKeys.length === 0}
              icon={selectedRowKeys.length > 0 ? <CheckCircleOutlined /> : undefined}
            >
              {selectedRowKeys.length > 0
                ? `确定添加 (${selectedRowKeys.length} 条规则)`
                : '请选择验证规则'
              }
            </Button>
          </Space>
        </div>
      }
      destroyOnClose
    >
      <div style={{
        marginBottom: 16,
        padding: '16px',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        borderRadius: '8px',
        boxShadow: '0 2px 8px rgba(102, 126, 234, 0.15)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
          <div style={{ color: '#fff' }}>
            <Space size={12} wrap>
              <Space>
                <InfoCircleOutlined style={{ fontSize: 16 }} />
                <span style={{ fontWeight: 500 }}>筛选验证规则</span>
              </Space>
              {validates.length > 0 && (
                <Tag color="rgba(255,255,255,0.2)" style={{
                  color: '#fff',
                  border: '1px solid rgba(255,255,255,0.3)',
                  borderRadius: '4px'
                }}>
                  共 {validates.length} 条可用规则
                </Tag>
              )}
              {selectedRowKeys.length > 0 && (
                <Tag color="rgba(82, 196, 26, 0.3)" style={{
                  color: '#fff',
                  border: '1px solid rgba(255,255,255,0.3)',
                  borderRadius: '4px'
                }}>
                  已选 {selectedRowKeys.length} 条
                </Tag>
              )}
            </Space>
          </div>
          <Space size={8}>
            <Select
              value={searchType}
              onChange={setSearchType}
              placeholder="选择验证类型"
              style={{ width: 180 }}
              allowClear
              size="middle"
            >
              {options.map(opt => (
                <Option key={opt.type} value={opt.type}>
                  {opt.descr}
                </Option>
              ))}
            </Select>
            <Button
              type="primary"
              icon={<SearchOutlined />}
              onClick={handleSearch}
              ghost
              style={{ borderColor: '#fff', color: '#fff' }}
            >
              搜索
            </Button>
          </Space>
        </div>
      </div>

      <Table
        loading={loading}
        columns={columns}
        dataSource={validates}
        rowKey={(record, index) => `${record.pattern}-${index}`}
        rowSelection={{
          ...rowSelection,
          columnWidth: 50,
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          pageSizeOptions: ['10', '20', '50', '100'],
          position: ['bottomCenter']
        }}
        scroll={{ x: 850, y: 'calc(100vh - 300px)' }}
        size="middle"
        bordered
        style={{
          background: '#fff',
          borderRadius: '8px'
        }}
      />
    </Drawer>
  )
})

ValidateTableModal.displayName = 'ValidateTableModal'

export default ValidateTableModal
