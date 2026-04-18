import { useState, forwardRef, useImperativeHandle } from 'react'
import { Modal, Input, Space, Tag, Tabs, InputNumber, Radio, Checkbox, message, Card, Row, Col, Button } from 'antd'
import { ThunderboltOutlined, SearchOutlined, BulbOutlined, ToolOutlined, CopyOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons'

export interface RegexTemplateModalRef {
  open: (currentPattern?: string) => void
}

interface RegexTemplateModalProps {
  onConfirm: (pattern: string, descr: string) => void
}

// 常见正则模板
const REGEX_TEMPLATES = [
  // 数字类
  {
    category: '数字',
    items: [
      { name: '整数', pattern: '^\\d+$', descr: '只能输入整数', example: '123' },
      { name: '正整数', pattern: '^[1-9]\\d*$', descr: '只能输入正整数（不含0）', example: '123' },
      { name: '负整数', pattern: '^-[1-9]\\d*$', descr: '只能输入负整数', example: '-123' },
      { name: '浮点数', pattern: '^\\d+\\.\\d+$', descr: '只能输入浮点数', example: '123.45' },
      { name: '金额', pattern: '^(([1-9]\\d{0,9})|0)(\\.\\d{1,2})?$', descr: '金额格式（最多2位小数）', example: '1234.56' },
    ]
  },
  // 字符类
  {
    category: '字符',
    items: [
      { name: '字母', pattern: '^[A-Za-z]+$', descr: '只能输入字母', example: 'abc' },
      { name: '大写字母', pattern: '^[A-Z]+$', descr: '只能输入大写字母', example: 'ABC' },
      { name: '小写字母', pattern: '^[a-z]+$', descr: '只能输入小写字母', example: 'abc' },
      { name: '字母数字', pattern: '^[A-Za-z0-9]+$', descr: '只能输入字母和数字', example: 'abc123' },
      { name: '中文', pattern: '^[\\u4e00-\\u9fa5]+$', descr: '只能输入中文', example: '你好' },
      { name: '中文字母数字', pattern: '^[\\u4e00-\\u9fa5A-Za-z0-9]+$', descr: '中文、字母、数字', example: '你好abc123' },
    ]
  },
  // 联系方式
  {
    category: '联系方式',
    items: [
      { name: '手机号', pattern: '^1[3-9]\\d{9}$', descr: '中国大陆手机号', example: '13812345678' },
      { name: '固定电话', pattern: '^0\\d{2,3}-?\\d{7,8}$', descr: '固定电话号码', example: '010-12345678' },
      { name: '邮箱', pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$', descr: '电子邮箱地址', example: 'example@email.com' },
      { name: 'QQ号', pattern: '^[1-9]\\d{4,10}$', descr: 'QQ号码', example: '123456789' },
    ]
  },
  // 证件类
  {
    category: '证件',
    items: [
      { name: '身份证', pattern: '^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$', descr: '18位身份证号', example: '110101199001011234' },
      { name: '护照', pattern: '^[A-Z]\\d{8}$', descr: '中国护照号码', example: 'E12345678' },
      { name: '统一社会信用代码', pattern: '^[0-9A-HJ-NPQRTUWXY]{2}\\d{6}[0-9A-HJ-NPQRTUWXY]{10}$', descr: '18位统一社会信用代码', example: '91110000123456789X' },
    ]
  },
  // 网络类
  {
    category: '网络',
    items: [
      { name: 'URL', pattern: '^(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([\\/\\w \\.-]*)*\\/?$', descr: '网址链接', example: 'https://www.example.com' },
      { name: 'IP地址', pattern: '^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$', descr: 'IPv4地址', example: '192.168.1.1' },
      { name: '域名', pattern: '^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$', descr: '域名格式', example: 'example.com' },
      { name: 'MAC地址', pattern: '^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$', descr: 'MAC地址', example: '00:1A:2B:3C:4D:5E' },
    ]
  },
  // 日期时间
  {
    category: '日期时间',
    items: [
      { name: '日期(YYYY-MM-DD)', pattern: '^\\d{4}-\\d{2}-\\d{2}$', descr: '日期格式', example: '2024-01-01' },
      { name: '日期(YYYY/MM/DD)', pattern: '^\\d{4}/\\d{2}/\\d{2}$', descr: '日期格式', example: '2024/01/01' },
      { name: '时间(HH:MM:SS)', pattern: '^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$', descr: '24小时时间格式', example: '23:59:59' },
      { name: '日期时间', pattern: '^\\d{4}-\\d{2}-\\d{2} ([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$', descr: '日期时间格式', example: '2024-01-01 23:59:59' },
    ]
  },
]

const RegexTemplateModal = forwardRef<RegexTemplateModalRef, RegexTemplateModalProps>(({ onConfirm }, ref) => {
  const [visible, setVisible] = useState(false)
  const [activeTab, setActiveTab] = useState('templates')
  const [searchText, setSearchText] = useState('')

  // 可视化构建器状态
  const [builderType, setBuilderType] = useState('length') // length, number, match, combined, repeat, exclude, or
  const [lengthMin, setLengthMin] = useState<number>(1)
  const [lengthMax, setLengthMax] = useState<number>(10)
  const [charTypes, setCharTypes] = useState<string[]>(['number'])
  const [numberMin, setNumberMin] = useState<number>(0)
  const [numberMax, setNumberMax] = useState<number>(999999)

  // 匹配规则
  const [matchType, setMatchType] = useState('startsWith') // startsWith, endsWith, contains, exact
  const [matchValue, setMatchValue] = useState('')

  // 组合规则
  const [combinedPrefix, setCombinedPrefix] = useState('')
  const [combinedMiddle, setCombinedMiddle] = useState<string[]>(['number'])
  const [combinedMiddleLength, setCombinedMiddleLength] = useState(6)
  const [combinedSuffix, setCombinedSuffix] = useState('')

  // 重复模式规则
  const [repeatPattern, setRepeatPattern] = useState<string[]>(['number'])
  const [repeatLength, setRepeatLength] = useState(4)
  const [repeatCount, setRepeatCount] = useState(3)
  const [repeatSeparator, setRepeatSeparator] = useState('-')

  // 排除规则
  const [excludeChars, setExcludeChars] = useState('')
  const [excludeBasePattern, setExcludeBasePattern] = useState<string[]>(['number', 'letter'])
  const [excludeLength, setExcludeLength] = useState([6, 20])

  // 或选择规则
  const [orOptions, setOrOptions] = useState<string[]>(['选项1', '选项2'])

  useImperativeHandle(ref, () => ({
    open: () => {
      setVisible(true)
      setSearchText('')
      setActiveTab('templates')
    }
  }))

  const handleClose = () => {
    setVisible(false)
  }

  const handleSelectTemplate = (item: any) => {
    onConfirm(item.pattern, item.descr)
    message.success(`已选择：${item.name}`)
    handleClose()
  }

  // 转义正则特殊字符
  const escapeRegex = (str: string) => {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  }

  // 生成可视化构建器的正则
  const generateBuilderPattern = () => {
    let pattern = ''
    let descr = ''

    if (builderType === 'length') {
      // 长度规则
      let charPattern = ''
      const types = []
      if (charTypes.includes('number')) {
        charPattern += '\\d'
        types.push('数字')
      }
      if (charTypes.includes('letter')) {
        charPattern += 'A-Za-z'
        types.push('字母')
      }
      if (charTypes.includes('chinese')) {
        charPattern += '\\u4e00-\\u9fa5'
        types.push('中文')
      }
      if (charPattern === '') {
        charPattern = '\\w'
        types.push('任意字符')
      }

      if (lengthMin === lengthMax) {
        pattern = `^[${charPattern}]{${lengthMin}}$`
        descr = `${types.join('、')}，长度为${lengthMin}位`
      } else {
        pattern = `^[${charPattern}]{${lengthMin},${lengthMax}}$`
        descr = `${types.join('、')}，长度${lengthMin}-${lengthMax}位`
      }
    } else if (builderType === 'number') {
      // 数字范围
      const minStr = numberMin.toString()
      const maxStr = numberMax.toString()
      pattern = `^\\d{${minStr.length},${maxStr.length}}$`
      descr = `数字范围 ${numberMin} - ${numberMax}`
    } else if (builderType === 'match') {
      // 匹配规则
      if (!matchValue) {
        return { pattern: '', descr: '请输入匹配内容' }
      }
      const escaped = escapeRegex(matchValue)

      if (matchType === 'startsWith') {
        pattern = `^${escaped}.*$`
        descr = `以"${matchValue}"开头`
      } else if (matchType === 'endsWith') {
        pattern = `^.*${escaped}$`
        descr = `以"${matchValue}"结尾`
      } else if (matchType === 'contains') {
        pattern = `^.*${escaped}.*$`
        descr = `包含"${matchValue}"`
      } else if (matchType === 'exact') {
        pattern = `^${escaped}$`
        descr = `精确匹配"${matchValue}"`
      }
    } else if (builderType === 'combined') {
      // 组合规则
      const parts = []
      const descrParts = []

      if (combinedPrefix) {
        const escaped = escapeRegex(combinedPrefix)
        parts.push(escaped)
        descrParts.push(`前缀"${combinedPrefix}"`)
      }

      if (combinedMiddleLength > 0) {
        let charPattern = ''
        const types = []
        if (combinedMiddle.includes('number')) {
          charPattern += '\\d'
          types.push('数字')
        }
        if (combinedMiddle.includes('letter')) {
          charPattern += 'A-Za-z'
          types.push('字母')
        }
        if (combinedMiddle.includes('chinese')) {
          charPattern += '\\u4e00-\\u9fa5'
          types.push('中文')
        }
        if (charPattern === '') {
          charPattern = '\\w'
          types.push('任意字符')
        }
        parts.push(`[${charPattern}]{${combinedMiddleLength}}`)
        descrParts.push(`${types.join('、')}${combinedMiddleLength}位`)
      }

      if (combinedSuffix) {
        const escaped = escapeRegex(combinedSuffix)
        parts.push(escaped)
        descrParts.push(`后缀"${combinedSuffix}"`)
      }

      if (parts.length === 0) {
        return { pattern: '', descr: '请配置至少一项规则' }
      }

      pattern = `^${parts.join('')}$`
      descr = descrParts.join(' + ')
    } else if (builderType === 'repeat') {
      // 重复模式规则
      let charPattern = ''
      const types = []
      if (repeatPattern.includes('number')) {
        charPattern += '\\d'
        types.push('数字')
      }
      if (repeatPattern.includes('letter')) {
        charPattern += 'A-Za-z'
        types.push('字母')
      }
      if (charPattern === '') {
        charPattern = '\\d'
        types.push('数字')
      }

      const segment = `[${charPattern}]{${repeatLength}}`
      const escapedSep = repeatSeparator ? escapeRegex(repeatSeparator) : ''

      if (repeatCount === 1) {
        pattern = `^${segment}$`
        descr = `${types.join('、')}${repeatLength}位`
      } else {
        const segments = []
        for (let i = 0; i < repeatCount; i++) {
          segments.push(segment)
        }
        pattern = `^${segments.join(escapedSep)}$`
        descr = `${repeatCount}组${types.join('、')}，每组${repeatLength}位${repeatSeparator ? `，用"${repeatSeparator}"分隔` : ''}`
      }
    } else if (builderType === 'exclude') {
      // 排除规则
      let charPattern = ''
      const types = []
      if (excludeBasePattern.includes('number')) {
        charPattern += '\\d'
        types.push('数字')
      }
      if (excludeBasePattern.includes('letter')) {
        charPattern += 'A-Za-z'
        types.push('字母')
      }
      if (charPattern === '') {
        return { pattern: '', descr: '请选择允许的字符类型' }
      }

      // 排除特定字符
      if (excludeChars) {
        const excludedChars = excludeChars.split('').map(c => escapeRegex(c))
        // 使用负向前瞻
        pattern = `^(?!.*[${excludedChars.join('')}])[${charPattern}]{${excludeLength[0]},${excludeLength[1]}}$`
        descr = `${types.join('、')}，长度${excludeLength[0]}-${excludeLength[1]}位，不能包含"${excludeChars}"`
      } else {
        pattern = `^[${charPattern}]{${excludeLength[0]},${excludeLength[1]}}$`
        descr = `${types.join('、')}，长度${excludeLength[0]}-${excludeLength[1]}位`
      }
    } else if (builderType === 'or') {
      // 或选择规则
      const validOptions = orOptions.filter(opt => opt.trim() !== '')
      if (validOptions.length === 0) {
        return { pattern: '', descr: '请至少添加一个选项' }
      }

      const escapedOptions = validOptions.map(opt => escapeRegex(opt.trim()))
      pattern = `^(${escapedOptions.join('|')})$`
      descr = `必须是：${validOptions.map(opt => `"${opt}"`).join('、')} 其中之一`
    }

    return { pattern, descr }
  }

  const handleUseBuilder = () => {
    const { pattern, descr } = generateBuilderPattern()
    if (!pattern) {
      message.warning('请配置规则')
      return
    }
    onConfirm(pattern, descr)
    message.success('已生成正则表达式')
    handleClose()
  }

  const handleCopyPattern = (pattern: string) => {
    navigator.clipboard.writeText(pattern)
    message.success('已复制到剪贴板')
  }

  // 搜索过滤
  const filteredTemplates = REGEX_TEMPLATES.map(category => ({
    ...category,
    items: category.items.filter(item =>
      searchText === '' ||
      item.name.includes(searchText) ||
      item.descr.includes(searchText) ||
      item.example.includes(searchText)
    )
  })).filter(category => category.items.length > 0)

  return (
    <Modal
      title={
        <Space>
          <BulbOutlined style={{ color: '#52c41a' }} />
          <span>正则表达式助手</span>
        </Space>
      }
      open={visible}
      onCancel={handleClose}
      width={900}
      footer={null}
      destroyOnClose
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'templates',
            label: (
              <span>
                <ThunderboltOutlined />
                常见模板
              </span>
            ),
            children: (
              <div>
                {/* 搜索框 */}
                <Input
                  placeholder="搜索模板：手机号、邮箱、身份证..."
                  prefix={<SearchOutlined />}
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
                  style={{ marginBottom: 16 }}
                  allowClear
                />

                {/* 模板列表 */}
                <div style={{ maxHeight: '500px', overflowY: 'auto' }}>
                  {filteredTemplates.map(category => (
                    <div key={category.category} style={{ marginBottom: 24 }}>
                      <div style={{
                        fontSize: 14,
                        fontWeight: 600,
                        color: '#1890ff',
                        marginBottom: 12,
                        borderBottom: '2px solid #e8e8e8',
                        paddingBottom: 8
                      }}>
                        {category.category}
                      </div>
                      <Row gutter={[16, 16]}>
                        {category.items.map((item, idx) => (
                          <Col span={24} key={idx}>
                            <Card
                              size="small"
                              hoverable
                              onClick={() => handleSelectTemplate(item)}
                              style={{
                                borderLeft: '3px solid #52c41a',
                                cursor: 'pointer'
                              }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                <div style={{ flex: 1 }}>
                                  <div style={{ marginBottom: 8 }}>
                                    <Tag color="blue" style={{ fontSize: 14, padding: '2px 12px' }}>
                                      {item.name}
                                    </Tag>
                                    <span style={{ color: '#666', marginLeft: 8 }}>
                                      {item.descr}
                                    </span>
                                  </div>
                                  <div style={{
                                    fontSize: 12,
                                    color: '#999',
                                    fontFamily: 'monospace',
                                    backgroundColor: '#f5f5f5',
                                    padding: '4px 8px',
                                    borderRadius: 4,
                                    marginBottom: 4
                                  }}>
                                    {item.pattern}
                                  </div>
                                  <div style={{ fontSize: 12, color: '#52c41a' }}>
                                    示例：{item.example}
                                  </div>
                                </div>
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<CopyOutlined />}
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    handleCopyPattern(item.pattern)
                                  }}
                                />
                              </div>
                            </Card>
                          </Col>
                        ))}
                      </Row>
                    </div>
                  ))}
                </div>
              </div>
            ),
          },
          {
            key: 'builder',
            label: (
              <span>
                <ToolOutlined />
                可视化构建
              </span>
            ),
            children: (
              <div style={{ padding: '20px 0' }}>
                <Row gutter={16}>
                  {/* 左侧：规则类型选择 */}
                  <Col span={8}>
                    <Card
                      title="规则类型"
                      style={{ height: 'calc(100vh - 300px)', overflow: 'auto' }}
                      bodyStyle={{ padding: 0 }}
                    >
                      <Radio.Group
                        value={builderType}
                        onChange={(e) => setBuilderType(e.target.value)}
                        style={{ width: '100%' }}
                      >
                        <div
                          onClick={() => setBuilderType('length')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'length' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'length' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="length" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>字符长度规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>用户名、密码等</div>
                          </Radio>
                        </div>
                        <div
                          onClick={() => setBuilderType('number')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'number' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'number' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="number" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>数字范围规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>数量、金额等</div>
                          </Radio>
                        </div>
                        <div
                          onClick={() => setBuilderType('match')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'match' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'match' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="match" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>匹配规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>开头/结尾/包含</div>
                          </Radio>
                        </div>
                        <div
                          onClick={() => setBuilderType('combined')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'combined' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'combined' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="combined" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>组合规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>前缀+内容+后缀</div>
                          </Radio>
                        </div>
                        <div
                          onClick={() => setBuilderType('repeat')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'repeat' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'repeat' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="repeat" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>重复模式规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>电话号码、分段格式</div>
                          </Radio>
                        </div>
                        <div
                          onClick={() => setBuilderType('exclude')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'exclude' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'exclude' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="exclude" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>排除规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>不能包含特定字符</div>
                          </Radio>
                        </div>
                        <div
                          onClick={() => setBuilderType('or')}
                          style={{
                            padding: '16px 20px',
                            cursor: 'pointer',
                            borderLeft: builderType === 'or' ? '3px solid #1890ff' : '3px solid transparent',
                            backgroundColor: builderType === 'or' ? '#e6f7ff' : 'transparent',
                            transition: 'all 0.3s'
                          }}
                        >
                          <Radio value="or" style={{ width: '100%' }}>
                            <div style={{ fontWeight: 600, marginBottom: 4 }}>或选择规则</div>
                            <div style={{ fontSize: 12, color: '#999' }}>多个选项其中之一</div>
                          </Radio>
                        </div>
                      </Radio.Group>
                    </Card>
                  </Col>

                  {/* 右侧：配置区域 */}
                  <Col span={16}>
                    <div style={{ height: 'calc(100vh - 300px)', overflow: 'auto' }}>

                      {builderType === 'length' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置字符长度规则</span>}>
                          <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>允许的字符类型：</div>
                        <Checkbox.Group
                          value={charTypes}
                          onChange={(values) => setCharTypes(values as string[])}
                        >
                          <Space>
                            <Checkbox value="number">数字</Checkbox>
                            <Checkbox value="letter">字母</Checkbox>
                            <Checkbox value="chinese">中文</Checkbox>
                          </Space>
                        </Checkbox.Group>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>长度范围：</div>
                        <Space>
                          <span>最小</span>
                          <InputNumber
                            min={1}
                            max={100}
                            value={lengthMin}
                            onChange={(val) => setLengthMin(val || 1)}
                          />
                          <span>位</span>
                          <span style={{ margin: '0 8px' }}>~</span>
                          <span>最大</span>
                          <InputNumber
                            min={1}
                            max={100}
                            value={lengthMax}
                            onChange={(val) => setLengthMax(val || 10)}
                          />
                          <span>位</span>
                        </Space>
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                        </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      {builderType === 'number' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置数字范围规则</span>}>
                    <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>数字范围：</div>
                        <Space>
                          <InputNumber
                            min={0}
                            value={numberMin}
                            onChange={(val) => setNumberMin(val || 0)}
                            style={{ width: 150 }}
                          />
                          <span>~</span>
                          <InputNumber
                            min={0}
                            value={numberMax}
                            onChange={(val) => setNumberMax(val || 999999)}
                            style={{ width: 150 }}
                          />
                        </Space>
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                        </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      {builderType === 'match' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置匹配规则</span>}>
                    <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>匹配类型：</div>
                        <Radio.Group value={matchType} onChange={(e) => setMatchType(e.target.value)}>
                          <Space direction="vertical">
                            <Radio value="startsWith">以...开头</Radio>
                            <Radio value="endsWith">以...结尾</Radio>
                            <Radio value="contains">包含...</Radio>
                            <Radio value="exact">精确匹配</Radio>
                          </Space>
                        </Radio.Group>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>匹配内容：</div>
                        <Input
                          value={matchValue}
                          onChange={(e) => setMatchValue(e.target.value)}
                          placeholder="请输入要匹配的内容"
                          style={{ width: '100%' }}
                        />
                        <div style={{ marginTop: 4, fontSize: 12, color: '#999' }}>
                          示例：输入 &quot;ABC&quot; 生成相应的匹配规则
                        </div>
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern || '请输入匹配内容'}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                        </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      {builderType === 'combined' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置组合规则</span>}>
                    <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>固定前缀（可选）：</div>
                        <Input
                          value={combinedPrefix}
                          onChange={(e) => setCombinedPrefix(e.target.value)}
                          placeholder="例如：ORD、USER、ID 等"
                          style={{ width: '100%' }}
                        />
                        <div style={{ marginTop: 4, fontSize: 12, color: '#999' }}>
                          订单号、用户ID等常见场景的固定前缀
                        </div>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>中间内容类型：</div>
                        <Checkbox.Group
                          value={combinedMiddle}
                          onChange={(values) => setCombinedMiddle(values as string[])}
                        >
                          <Space>
                            <Checkbox value="number">数字</Checkbox>
                            <Checkbox value="letter">字母</Checkbox>
                            <Checkbox value="chinese">中文</Checkbox>
                          </Space>
                        </Checkbox.Group>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>中间内容长度：</div>
                        <InputNumber
                          min={0}
                          max={100}
                          value={combinedMiddleLength}
                          onChange={(val) => setCombinedMiddleLength(val || 6)}
                          style={{ width: 150 }}
                          addonAfter="位"
                        />
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>固定后缀（可选）：</div>
                        <Input
                          value={combinedSuffix}
                          onChange={(e) => setCombinedSuffix(e.target.value)}
                          placeholder="例如：-SUFFIX、_END 等"
                          style={{ width: '100%' }}
                        />
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern || '请配置规则'}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                        </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      {builderType === 'repeat' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置重复模式规则</span>}>
                    <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>字符类型：</div>
                        <Checkbox.Group
                          value={repeatPattern}
                          onChange={(values) => setRepeatPattern(values as string[])}
                        >
                          <Space>
                            <Checkbox value="number">数字</Checkbox>
                            <Checkbox value="letter">字母</Checkbox>
                          </Space>
                        </Checkbox.Group>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>每组长度：</div>
                        <InputNumber
                          min={1}
                          max={20}
                          value={repeatLength}
                          onChange={(val) => setRepeatLength(val || 4)}
                          style={{ width: 150 }}
                          addonAfter="位"
                        />
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>重复次数：</div>
                        <InputNumber
                          min={1}
                          max={10}
                          value={repeatCount}
                          onChange={(val) => setRepeatCount(val || 3)}
                          style={{ width: 150 }}
                          addonAfter="组"
                        />
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>分隔符（可选）：</div>
                        <Input
                          value={repeatSeparator}
                          onChange={(e) => setRepeatSeparator(e.target.value)}
                          placeholder="例如：-、空格、/ 等"
                          style={{ width: 200 }}
                          maxLength={1}
                        />
                        <div style={{ marginTop: 4, fontSize: 12, color: '#999' }}>
                          常见：- (短横线)、空格、/ (斜线)，留空则无分隔符
                        </div>
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                        </div>
                        <div style={{ marginTop: 8, fontSize: 12, color: '#52c41a' }}>
                          示例：{repeatPattern.includes('number') ? '1234' : 'ABCD'}{repeatSeparator}{repeatPattern.includes('number') ? '5678' : 'EFGH'}{repeatSeparator}{repeatPattern.includes('number') ? '9012' : 'IJKL'}
                        </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      {builderType === 'exclude' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置排除规则</span>}>
                    <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>允许的字符类型：</div>
                        <Checkbox.Group
                          value={excludeBasePattern}
                          onChange={(values) => setExcludeBasePattern(values as string[])}
                        >
                          <Space>
                            <Checkbox value="number">数字</Checkbox>
                            <Checkbox value="letter">字母</Checkbox>
                          </Space>
                        </Checkbox.Group>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>长度范围：</div>
                        <Space>
                          <span>最小</span>
                          <InputNumber
                            min={1}
                            max={100}
                            value={excludeLength[0]}
                            onChange={(val) => setExcludeLength([val || 1, excludeLength[1]])}
                            style={{ width: 80 }}
                          />
                          <span>位</span>
                          <span>~</span>
                          <span>最大</span>
                          <InputNumber
                            min={1}
                            max={100}
                            value={excludeLength[1]}
                            onChange={(val) => setExcludeLength([excludeLength[0], val || 20])}
                            style={{ width: 80 }}
                          />
                          <span>位</span>
                        </Space>
                      </div>
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>不能包含的字符：</div>
                        <Input
                          value={excludeChars}
                          onChange={(e) => setExcludeChars(e.target.value)}
                          placeholder="例如：@#$%& 等特殊字符"
                          style={{ width: '100%' }}
                        />
                        <div style={{ marginTop: 4, fontSize: 12, color: '#999' }}>
                          输入不允许出现的字符，如特殊符号、敏感字符等
                        </div>
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern || '请配置规则'}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                        </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      {builderType === 'or' && (
                        <Card title={<span style={{ fontSize: 16, fontWeight: 600 }}>⚙️ 配置或选择规则</span>}>
                    <Space direction="vertical" style={{ width: '100%' }} size="large">
                      <div>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>可选项列表：</div>
                        {orOptions.map((option, index) => (
                          <div key={index} style={{ marginBottom: 8 }}>
                            <Space style={{ width: '100%' }}>
                              <span style={{ width: 60 }}>选项 {index + 1}：</span>
                              <Input
                                value={option}
                                onChange={(e) => {
                                  const newOptions = [...orOptions]
                                  newOptions[index] = e.target.value
                                  setOrOptions(newOptions)
                                }}
                                placeholder="输入选项内容"
                                style={{ flex: 1 }}
                              />
                              {orOptions.length > 2 && (
                                <Button
                                  danger
                                  size="small"
                                  icon={<DeleteOutlined />}
                                  onClick={() => {
                                    const newOptions = orOptions.filter((_, i) => i !== index)
                                    setOrOptions(newOptions)
                                  }}
                                />
                              )}
                            </Space>
                          </div>
                        ))}
                        <Button
                          type="dashed"
                          icon={<PlusOutlined />}
                          onClick={() => setOrOptions([...orOptions, `选项${orOptions.length + 1}`])}
                          style={{ width: '100%', marginTop: 8 }}
                        >
                          添加选项
                        </Button>
                        <div style={{ marginTop: 8, fontSize: 12, color: '#999' }}>
                          常见场景：状态（启用/禁用）、性别（男/女）、类型选择等
                        </div>
                      </div>
                      <div style={{
                        padding: 12,
                        background: '#f0f5ff',
                        borderRadius: 8,
                        border: '1px solid #adc6ff'
                      }}>
                        <div style={{ fontWeight: 500, marginBottom: 4 }}>生成的正则：</div>
                        <code style={{ color: '#1890ff', fontSize: 14 }}>
                          {generateBuilderPattern().pattern || '请添加选项'}
                        </code>
                        <div style={{ marginTop: 8, color: '#666' }}>
                          {generateBuilderPattern().descr}
                          </div>
                          </div>
                          </Space>
                        </Card>
                      )}

                      <div style={{ marginTop: 16, textAlign: 'center', position: 'sticky', bottom: 0, backgroundColor: '#fff', padding: '16px 0', borderTop: '1px solid #f0f0f0' }}>
                        <Button type="primary" size="large" onClick={handleUseBuilder} style={{ minWidth: 200 }}>
                          使用此规则
                        </Button>
                      </div>
                    </div>
                  </Col>
                </Row>
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
})

RegexTemplateModal.displayName = 'RegexTemplateModal'

export default RegexTemplateModal

