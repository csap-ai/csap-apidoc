import React, { useState, forwardRef, useImperativeHandle, useRef, useContext } from 'react'
import { Drawer, Table, Button, Input, message, Space, Tag, Modal, Alert } from 'antd'
import { SafetyOutlined, PlusOutlined, DeleteOutlined, ThunderboltOutlined, InfoCircleOutlined, MenuOutlined, CheckCircleOutlined, CloseCircleOutlined, BulbOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { getMethodValidateFields } from '@/api/devtools'
import ValidateTableModal from './ValidateTableModal'
import RegexTemplateModal from './RegexTemplateModal'
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core'
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

interface ValidateData {
  id?: string
  descr: string
  pattern: string
  code: string
  message: string
  level: number
  type: string | number
}

export interface FieldValidateModalRef {
  open: (row: any, api: any) => void
}

// 拖动行组件
interface RowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  'data-row-key': string
}

// 创建一个 context 来传递拖动监听器
const DragHandleContext = React.createContext<any>(null)

const Row: React.FC<RowProps> = (props) => {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: props['data-row-key'],
  })

  const style: React.CSSProperties = {
    ...props.style,
    transform: CSS.Transform.toString(transform && { ...transform, scaleY: 1 }),
    transition,
    ...(isDragging ? { position: 'relative', zIndex: 9999 } : {}),
  }

  return (
    <DragHandleContext.Provider value={{ setActivatorNodeRef, listeners }}>
      <tr {...props} ref={setNodeRef} style={style} {...attributes} />
    </DragHandleContext.Provider>
  )
}

// 拖动手柄组件
const DragHandle: React.FC = () => {
  const dragHandle = useContext(DragHandleContext)

  return (
    <div
      ref={dragHandle?.setActivatorNodeRef}
      {...dragHandle?.listeners}
      style={{ cursor: 'move', padding: '4px 0', display: 'inline-block' }}
    >
      <MenuOutlined style={{ color: '#999', fontSize: 16 }} />
    </div>
  )
}

const FieldValidateModal = forwardRef<FieldValidateModalRef>((_props, ref) => {
  const [visible, setVisible] = useState(false)
  const [loading, setLoading] = useState(false)
  const [validates, setValidates] = useState<ValidateData[]>([])
  const [row, setRow] = useState<any>(null)

  // 测试功能相关状态
  const [testVisible, setTestVisible] = useState(false)
  const [testValue, setTestValue] = useState('')
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [currentTestRule, setCurrentTestRule] = useState<ValidateData | null>(null)

  const validateTableRef = useRef<any>(null)
  const regexTemplateRef = useRef<any>(null)

  // 当前正在编辑正则的规则索引
  const [editingRegexIndex, setEditingRegexIndex] = useState<number | null>(null)

  // 拖动传感器配置
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 1,
      },
    })
  )

  useImperativeHandle(ref, () => ({
    open: async (fieldRow: any, apiData: any) => {
      setVisible(true)
      setRow(fieldRow)

      // 初始化validate数组
      if (!fieldRow.validate) {
        fieldRow.validate = []
      }
      fieldRow.clickValidate = true

      await loadFieldValidates(apiData, fieldRow)
    }
  }))

  const loadFieldValidates = async (apiData: any, fieldRow: any) => {
    try {
      setLoading(true)
      const data = await getMethodValidateFields(apiData.className, apiData.name, fieldRow.keyName)

      // 合并已有的验证规则
      const existingValidates = data.validate || []
      const currentValidates = fieldRow.validate || []

      // 添加当前字段已有但列表中没有的验证规则
      currentValidates.forEach((v: ValidateData) => {
        if (!existingValidates.find((ev: ValidateData) => ev.pattern === v.pattern)) {
          existingValidates.push(v)
        }
      })

      // 按 level 排序并重新索引
      existingValidates.sort((a: ValidateData, b: ValidateData) => (a.level || 0) - (b.level || 0))
      const reindexedValidates = existingValidates.map((v: ValidateData, idx: number) => ({
        ...v,
        level: idx + 1
      }))

      setValidates(reindexedValidates)
    } catch (error: any) {
      message.error('加载验证规则失败：' + error.message)
    } finally {
      setLoading(false)
    }
  }

  const handleClose = () => {
    setVisible(false)
    setValidates([])
    setRow(null)
  }

  const handleConfirm = () => {
    // 更新字段的validate数组
    if (row) {
      row.validate = validates.map(v => {
        // 转换type类型
        const validate = { ...v }
        if (typeof validate.type === 'number') {
          if (validate.type === 1) {
            validate.type = validate.pattern
          } else {
            validate.type = 'Pattern'
          }
        }
        return validate
      })
    }
    message.success('验证规则已更新')
    handleClose()
  }

  const handleAddValidate = () => {
    validateTableRef.current?.open(validates)
  }

  // 打开正则助手
  const handleOpenRegexHelper = (index: number) => {
    setEditingRegexIndex(index)
    regexTemplateRef.current?.open(validates[index]?.pattern)
  }

  // 选择正则模板后
  const handleRegexTemplateConfirm = (pattern: string, descr: string) => {
    if (editingRegexIndex !== null) {
      const newValidates = [...validates]
      newValidates[editingRegexIndex] = {
        ...newValidates[editingRegexIndex],
        pattern,
        descr
      }
      setValidates(newValidates)
      setEditingRegexIndex(null)
    }
  }

  const handleValidatesSelected = (selectedValidates: any[]) => {
    // 添加选中的验证规则，并设置正确的 level
    const newValidates = [...validates]
    selectedValidates.forEach(v => {
      if (!newValidates.find(existing => existing.pattern === v.pattern)) {
        newValidates.push({
          ...v,
          code: '',
          message: '',
          level: newValidates.length + 1
        })
      }
    })
    setValidates(newValidates)
  }

  const handleRemove = (record: ValidateData) => {
    const newValidates = validates.filter(v =>
      (v.descr + v.pattern) !== (record.descr + record.pattern)
    )
    // 重新计算 level 值
    const reindexedValidates = newValidates.map((v, idx) => ({
      ...v,
      level: idx + 1
    }))
    setValidates(reindexedValidates)
  }

  // 判断是否为内置规则
  const isBuiltInRule = (record: ValidateData): boolean => {
    // type === 1 表示内置规则
    if (record.type === 1) return true
    // type 可能被转换成 pattern 值，检查是否为常见内置规则名
    const builtInRules = ['NotNull', 'NotEmpty', 'NotBlank']
    if (typeof record.type === 'string' && record.type === record.pattern) {
      return builtInRules.includes(record.pattern)
    }
    return false
  }

  const handleFieldChange = (index: number, field: keyof ValidateData, value: any) => {
    const newValidates = [...validates]
    newValidates[index] = { ...newValidates[index], [field]: value }
    setValidates(newValidates)
  }

  // 拖动结束处理
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (active.id !== over?.id) {
      setValidates((items) => {
        const oldIndex = items.findIndex((item, idx) => `${item.pattern}-${idx}` === active.id)
        const newIndex = items.findIndex((item, idx) => `${item.pattern}-${idx}` === over?.id)
        const newItems = arrayMove(items, oldIndex, newIndex)
        // 根据新位置更新 level 值
        return newItems.map((item, idx) => ({
          ...item,
          level: idx + 1
        }))
      })
    }
  }

  // 打开测试弹窗
  const handleOpenTest = (record: ValidateData) => {
    setCurrentTestRule(record)
    setTestValue('')
    setTestResult(null)
    setTestVisible(true)
  }

  // 关闭测试弹窗
  const handleCloseTest = () => {
    setTestVisible(false)
    setTestValue('')
    setTestResult(null)
    setCurrentTestRule(null)
  }

  // 验证内置规则
  const validateBuiltInRule = (pattern: string, value: string): boolean => {
    switch (pattern) {
      case 'NotNull':
        // NotNull: 不能为 null、undefined 或空字符串
        return value !== null && value !== undefined && value !== ''
      case 'NotEmpty':
        // NotEmpty: 去除空格后不能为空
        return value !== null && value !== undefined && value.trim() !== ''
      case 'NotBlank':
        // NotBlank: 去除空格后长度必须大于0
        return value !== null && value !== undefined && value.trim().length > 0
      default:
        // 其他内置规则当作正则处理
        try {
          const regex = new RegExp(pattern)
          return regex.test(value)
        } catch {
          return false
        }
    }
  }

  // 执行测试
  const handleTest = () => {
    if (!currentTestRule) {
      message.warning('请选择验证规则')
      return
    }

    // 允许空值测试（用于测试 NotNull, NotEmpty 等）
    const testValueToUse = testValue === null || testValue === undefined ? '' : testValue

    try {
      const pattern = currentTestRule.pattern
      let isValid = false

      // 判断是否是内置规则
      if (isBuiltInRule(currentTestRule)) {
        isValid = validateBuiltInRule(pattern, testValueToUse)
      } else {
        // 自定义正则表达式
        const regex = new RegExp(pattern)
        isValid = regex.test(testValueToUse)
      }

      if (isValid) {
        setTestResult({
          success: true,
          message: '输入值符合规则'
        })
      } else {
        setTestResult({
          success: false,
          message: currentTestRule.message || '输入值不符合规则'
        })
      }
    } catch (error: any) {
      setTestResult({
        success: false,
        message: `正则表达式错误：${error.message}`
      })
    }
  }

  const columns: ColumnsType<ValidateData> = [
    {
      title: '',
      key: 'sort',
      width: 50,
      align: 'center',
      render: () => <DragHandle />,
    },
    {
      title: (
        <Space>
          <InfoCircleOutlined />
          <span>描述</span>
        </Space>
      ),
      key: 'descr',
      width: 200,
      ellipsis: true,
      render: (_, record, index) => (
        <Input
          value={record.descr}
          onChange={(e) => handleFieldChange(index, 'descr', e.target.value)}
          placeholder="请输入描述"
          size="small"
          style={{ borderRadius: '4px' }}
        />
      ),
    },
    {
      title: (
        <Space>
          <ThunderboltOutlined />
          <span>正则</span>
        </Space>
      ),
      key: 'pattern',
      width: 250,
      ellipsis: true,
      render: (_, record, index) => {
        const isBuiltIn = isBuiltInRule(record)
        return (
          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={record.pattern}
              onChange={(e) => handleFieldChange(index, 'pattern', e.target.value)}
              disabled={isBuiltIn}
              placeholder="请输入正则表达式"
              size="small"
              style={{
                fontFamily: 'monospace',
                fontSize: '12px',
                backgroundColor: isBuiltIn ? '#f5f5f5' : '#fff'
              }}
            />
            {!isBuiltIn && (
              <Button
                size="small"
                icon={<BulbOutlined />}
                onClick={() => handleOpenRegexHelper(index)}
                title="正则助手"
                style={{ flexShrink: 0 }}
              />
            )}
          </Space.Compact>
        )
      },
    },
    {
      title: '编码',
      key: 'code',
      width: 120,
      render: (_, record, index) => (
        <Input
          value={record.code}
          onChange={(e) => handleFieldChange(index, 'code', e.target.value)}
          placeholder="错误编码"
          size="small"
          style={{ borderRadius: '4px' }}
        />
      ),
    },
    {
      title: '消息',
      key: 'message',
      width: 200,
      ellipsis: true,
      render: (_, record, index) => (
        <Input
          value={record.message}
          onChange={(e) => handleFieldChange(index, 'message', e.target.value)}
          placeholder="错误消息"
          size="small"
          style={{ borderRadius: '4px' }}
        />
      ),
    },
    {
      title: '等级',
      key: 'level',
      width: 90,
      align: 'center',
      render: (_, _record, index) => (
        <Tag color="blue" style={{ margin: 0, fontSize: '14px', fontWeight: 500 }}>
          {index + 1}
        </Tag>
      ),
    },
    {
      title: '类型',
      key: 'type',
      width: 100,
      align: 'center',
      render: (_, record) => {
        const isBuiltIn = isBuiltInRule(record)
        return isBuiltIn ? (
          <Tag color="blue">内置</Tag>
        ) : (
          <Tag color="purple">自定义</Tag>
        )
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right',
      align: 'center',
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="default"
            size="small"
            icon={<ThunderboltOutlined />}
            onClick={() => handleOpenTest(record)}
          >
            测试
          </Button>
          <Button
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => handleRemove(record)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Drawer
        title={
          <Space>
            <SafetyOutlined style={{ color: '#fa8c16' }} />
            <span>字段验证规则管理</span>
          </Space>
        }
        open={visible}
        onClose={handleClose}
        width="85%"
        footer={
          <div style={{ textAlign: 'right' }}>
            <Space>
              <Button onClick={handleClose}>取消</Button>
              <Button type="primary" onClick={handleConfirm}>
                确定保存
              </Button>
            </Space>
          </div>
        }
        destroyOnClose
      >
        <div style={{
          marginBottom: 16,
          padding: '12px 16px',
          background: 'linear-gradient(135deg, #fa8c16 0%, #faad14 100%)',
          borderRadius: '8px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          boxShadow: '0 2px 8px rgba(250, 140, 22, 0.15)'
        }}>
          <div style={{ color: '#fff' }}>
            <Space>
              <InfoCircleOutlined style={{ fontSize: 16 }} />
              <span style={{ fontWeight: 500 }}>
                {row?.name || '字段'} 的验证规则
              </span>
              <Tag color="rgba(255,255,255,0.2)" style={{
                color: '#fff',
                border: '1px solid rgba(255,255,255,0.3)',
                borderRadius: '4px'
              }}>
                共 {validates.length} 条规则
              </Tag>
            </Space>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddValidate}
            ghost
            style={{ borderColor: '#fff', color: '#fff' }}
          >
            添加验证规则
          </Button>
        </div>

        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={validates.map((item, index) => `${item.pattern}-${index}`)}
            strategy={verticalListSortingStrategy}
          >
            <Table
              loading={loading}
              columns={columns}
              dataSource={validates}
              rowKey={(record, index) => `${record.pattern}-${index}`}
              pagination={false}
              scroll={{ x: 1150, y: 'calc(100vh - 350px)' }}
              size="middle"
              bordered
              components={{
                body: {
                  row: Row,
                },
              }}
              style={{
                background: '#fff',
                borderRadius: '8px'
              }}
            />
          </SortableContext>
        </DndContext>
      </Drawer>

      <ValidateTableModal ref={validateTableRef} onConfirm={handleValidatesSelected} />

      {/* 测试弹窗 */}
      <Modal
        title={
          <Space>
            <ThunderboltOutlined style={{ color: '#1890ff' }} />
            <span>测试验证规则</span>
          </Space>
        }
        open={testVisible}
        onCancel={handleCloseTest}
        onOk={handleTest}
        width={600}
        okText="测试"
        cancelText="关闭"
        okButtonProps={{ icon: <ThunderboltOutlined /> }}
        destroyOnClose
      >
        <div style={{ padding: '20px 0' }}>
          {/* 规则信息 */}
          <div style={{
            marginBottom: 20,
            padding: 16,
            background: '#f5f7fa',
            borderRadius: 8,
            border: '1px solid #e4e7ed'
          }}>
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <div>
                <span style={{ color: '#666', fontWeight: 500 }}>描述：</span>
                <span style={{ color: '#333' }}>{currentTestRule?.descr || '无描述'}</span>
              </div>
              <div>
                <span style={{ color: '#666', fontWeight: 500 }}>正则表达式：</span>
                <code style={{
                  padding: '2px 8px',
                  background: '#fff',
                  border: '1px solid #ddd',
                  borderRadius: 4,
                  fontFamily: 'monospace',
                  fontSize: 12,
                  color: '#e03e2d'
                }}>
                  {currentTestRule?.pattern}
                </code>
              </div>
              <div>
                <span style={{ color: '#666', fontWeight: 500 }}>错误消息：</span>
                <span style={{ color: '#333' }}>{currentTestRule?.message || '无'}</span>
              </div>
            </Space>
          </div>

          {/* 测试输入 */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, color: '#333', fontWeight: 500 }}>
              输入测试值：
            </div>
            <Input.TextArea
              value={testValue}
              onChange={(e) => {
                setTestValue(e.target.value)
                setTestResult(null) // 清除之前的测试结果
              }}
              placeholder={
                currentTestRule && isBuiltInRule(currentTestRule)
                  ? '请输入要测试的值（可以为空来测试空值情况）...'
                  : '请输入要测试的值...'
              }
              rows={3}
              style={{
                fontSize: 14,
                borderRadius: 6
              }}
              onPressEnter={(e) => {
                if (e.ctrlKey || e.metaKey) {
                  handleTest()
                }
              }}
            />
            <div style={{ marginTop: 4, color: '#999', fontSize: 12 }}>
              <Space split="•">
                <span>按 Ctrl+Enter (Mac: ⌘+Enter) 快速测试</span>
                {currentTestRule && isBuiltInRule(currentTestRule) && (
                  <span style={{ color: '#fa8c16' }}>可留空测试空值验证</span>
                )}
              </Space>
            </div>
          </div>

          {/* 测试结果 */}
          {testResult && (
            <Alert
              message={testResult.success ? '验证通过' : '验证失败'}
              description={
                <div>
                  <div style={{ marginBottom: 8 }}>{testResult.message}</div>
                  {testValue && (
                    <div style={{
                      padding: 8,
                      background: '#f5f5f5',
                      borderRadius: 4,
                      fontFamily: 'monospace',
                      fontSize: 12,
                      wordBreak: 'break-all'
                    }}>
                      测试值：{testValue}
                    </div>
                  )}
                </div>
              }
              type={testResult.success ? 'success' : 'error'}
              icon={testResult.success ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
              showIcon
              style={{
                marginTop: 16,
                borderRadius: 8,
                animation: 'fadeIn 0.3s ease-in'
              }}
            />
          )}
        </div>
      </Modal>

      {/* 正则助手 */}
      <RegexTemplateModal ref={regexTemplateRef} onConfirm={handleRegexTemplateConfirm} />
    </>
  )
})

FieldValidateModal.displayName = 'FieldValidateModal'

export default FieldValidateModal
