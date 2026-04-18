import { useState, forwardRef, useImperativeHandle } from 'react'
import { Modal, Table, Button, message, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { getFields } from '@/api/devtools'

interface FieldData {
  id: string
  key: string
  name: string
  dataType: string
  value?: string
  childrenField?: FieldData[]
  required?: boolean
  paramType?: string
  keyName?: string
  parameters?: FieldData[]
}

interface ModelFieldModalProps {
  onConfirm: (selectedFields: any[]) => void
}

export interface ModelFieldModalRef {
  open: (className: string, fields: any, paramName: string, filedNames: Record<string, any>, api: any, clzName: string) => void
}

const ModelFieldModal = forwardRef<ModelFieldModalRef, ModelFieldModalProps>(({ onConfirm }, ref) => {
  const [visible, setVisible] = useState(false)
  const [loading, setLoading] = useState(false)
  const [fields, setFields] = useState<FieldData[]>([])
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [filedNames, setFiledNames] = useState<Record<string, any>>({})
  const [api, setApi] = useState<any>(null)

  useImperativeHandle(ref, () => ({
    open: async (className: string, _fields: any, pName: string, fNames: Record<string, any>, apiData: any, clzName: string) => {
      setVisible(true)
      setFiledNames(fNames)
      setApi(apiData)
      // 直接传递参数，而不是依赖state（因为setState是异步的）
      await loadFields(className, clzName, pName, fNames, apiData)
    }
  }))

  const loadFields = async (className: string, clzName: string, pName: string, fNames: Record<string, any>, apiData: any) => {
    try {
      setLoading(true)
      const data = await getFields(className, [], clzName)

      // 转换字段数据结构，添加id和keyName
      const processFields = (fields: any[], parentKey = ''): FieldData[] => {
        return fields.map((field) => {
          const key = parentKey ? `${parentKey}.${field.name}` : field.name
          // 根据Vue代码逻辑构建keyName
          let fieldKeyName = ''
          if (apiData) {
            const separator = pName.endsWith('.') ? '' : '.'
            fieldKeyName = pName + separator + key
          } else {
            fieldKeyName = key
          }

          const processed: FieldData = {
            ...field,
            id: key,
            key: field.name,
            keyName: fieldKeyName,
          }

          // 如果该字段已存在，恢复其属性
          const existingField = fNames[fieldKeyName]
          if (existingField) {
            processed.paramType = existingField.paramType
            processed.required = existingField.required
          }

          // 处理子字段 - 只有真正有子字段时才设置childrenField
          if (field.childrenField && field.childrenField.length > 0) {
            processed.childrenField = processFields(field.childrenField, key)
          } else {
            // 删除空的childrenField属性，避免Ant Design Table显示+号
            delete processed.childrenField
          }

          return processed
        })
      }

      const processedFields = processFields(data)
      setFields(processedFields)

      // 展平所有字段
      const flattenFields: FieldData[] = []
      const flatten = (fields: FieldData[]) => {
        fields.forEach(field => {
          flattenFields.push(field)
          if (field.childrenField && field.childrenField.length > 0) {
            flatten(field.childrenField)
          }
        })
      }
      flatten(processedFields)

      // 根据已存在的字段自动选中（反选）
      const selectedKeys: React.Key[] = []
      flattenFields.forEach(field => {
        const exists = fNames[field.keyName || '']
        if (exists) {
          selectedKeys.push(field.id)
        }
      })

      setSelectedRowKeys(selectedKeys)
    } catch (error: any) {
      message.error('加载字段失败：' + error.message)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setVisible(false)
    setFields([])
    setSelectedRowKeys([])
  }

  const handleConfirm = () => {
    // 构建选中的字段数据（与Vue代码逻辑一致）
    const buildFields = (fieldList: FieldData[], currentFiledNames: Record<string, any>, currentApi: any): any[] => {
      const result: any[] = []
      fieldList.forEach(field => {
        if (selectedRowKeys.includes(field.id)) {
          const existingField = currentFiledNames[field.keyName || '']

          // 构建字段对象
          const newField: any = {
            value: field.value,
            dataType: field.dataType,
            key: field.key,
            name: field.name,
            parameters: [],
            keyName: field.keyName,
            required: field.required || false,
            paramType: field.paramType || (currentApi?.paramType || null),
            validate: existingField?.validate || []
          }

          // 如果字段已存在，保留其原有属性
          if (existingField) {
            newField.required = existingField.required
            newField.paramType = existingField.paramType
            newField.validate = existingField.validate
          }

          // 递归处理子字段
          if (field.childrenField && field.childrenField.length > 0) {
            newField.parameters = buildFields(field.childrenField, currentFiledNames, currentApi)
          }

          result.push(newField)
        }
      })
      return result
    }

    const selectedFields = buildFields(fields, filedNames, api)
    onConfirm(selectedFields)
    handleClose()
  }

  const rowSelection = {
    selectedRowKeys,
    onChange: (selectedRowKeys: React.Key[]) => {
      setSelectedRowKeys(selectedRowKeys)
    },
  }

  const columns: ColumnsType<FieldData> = [
    {
      title: '参数名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: {
        showTitle: false,
      },
      render: (text, record) => {
        // 如果字段已存在，添加标记
        const isExisting = filedNames[record.keyName || '']
        return (
          <Tooltip
            placement="topLeft"
            title={
              <div>
                <div><strong>字段名:</strong> {text}</div>
                <div><strong>键名:</strong> {record.keyName}</div>
                {isExisting && <div style={{ color: '#52c41a' }}>✓ 已添加</div>}
              </div>
            }
          >
            <span style={{ color: isExisting ? '#1890ff' : undefined, cursor: 'pointer' }}>
              {text}
              {isExisting && <span style={{ marginLeft: 8, fontSize: 12, color: '#52c41a' }}>✓ 已添加</span>}
            </span>
          </Tooltip>
        )
      }
    },
    {
      title: '数据类型',
      dataIndex: 'dataType',
      key: 'dataType',
      ellipsis: {
        showTitle: false,
      },
      render: (text, record) => (
        <Tooltip
          placement="topLeft"
          title={
            <div>
              <div><strong>数据类型:</strong> {text}</div>
              {record.childrenField && record.childrenField.length > 0 && (
                <div><strong>子字段数量:</strong> {record.childrenField.length}</div>
              )}
            </div>
          }
        >
          <span style={{ cursor: 'pointer' }}>{text}</span>
        </Tooltip>
      ),
    },
    {
      title: '备注',
      dataIndex: 'value',
      key: 'value',
      ellipsis: {
        showTitle: false,
      },
      render: (text) => (
        <Tooltip placement="topLeft" title={text || '无备注'}>
          <span style={{ cursor: 'pointer' }}>{text || '-'}</span>
        </Tooltip>
      ),
    },
  ]

  return (
    <Modal
      title="参数管理"
      open={visible}
      onCancel={handleClose}
      width="70%"
      footer={[
        <Button key="confirm" type="primary" onClick={handleConfirm}>
          确定 {selectedRowKeys.length > 0 && `(已选 ${selectedRowKeys.length} 个)`}
        </Button>,
      ]}
    >
      <div style={{ marginBottom: 12, padding: 8, background: '#e6f7ff', borderRadius: 4 }}>
        <span style={{ color: '#1890ff', fontWeight: 500 }}>
          💡 提示：已添加的字段会自动选中并显示&quot;✓ 已添加&quot;标记，再次选中可修改其配置
        </span>
      </div>

      <Table
        loading={loading}
        columns={columns}
        dataSource={fields}
        rowKey="id"
        rowSelection={rowSelection}
        pagination={false}
        scroll={{ y: 450 }}
        expandable={{
          childrenColumnName: 'childrenField',
          defaultExpandAllRows: true,
        }}
      />
    </Modal>
  )
})

ModelFieldModal.displayName = 'ModelFieldModal'

export default ModelFieldModal

