import { useState, useEffect, useRef, useMemo } from 'react'
import { Card, Table, Button, message, Spin, Tag, Tooltip, Input, Space, Select, Statistic, Row, Col, Empty } from 'antd'
import { SearchOutlined, ReloadOutlined, FilterOutlined, StarOutlined, StarFilled, ApiOutlined, HomeOutlined, FolderOpenOutlined, CheckCircleOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { scannerApi, getApiDocList, type ControllerModel, type MethodModel } from '@/api/devtools'
import RequestParamModal from './components/RequestParamModal'
import ResponseParamModal from './components/ResponseParamModal'
import './index.scss'

const ApiManagement: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const [controllerList, setControllerList] = useState<ControllerModel[]>([])
  const [_currentIndex, setCurrentIndex] = useState(0)
  const [currentController, setCurrentController] = useState<ControllerModel | null>(null)
  const [methodList, setMethodList] = useState<MethodModel[]>([])

  // 搜索相关状态
  const [controllerSearchText, setControllerSearchText] = useState('')
  const [filteredControllerList, setFilteredControllerList] = useState<ControllerModel[]>([])
  const [methodSearchText, setMethodSearchText] = useState('')
  const [filteredMethodList, setFilteredMethodList] = useState<MethodModel[]>([])

  // 监听关键state变化
  useEffect(() => {
    console.log('📊 [State] currentController:', currentController?.name || 'null')
  }, [currentController])

  useEffect(() => {
    console.log('📊 [State] methodList长度:', methodList.length, '内容:', methodList)
  }, [methodList])

  useEffect(() => {
    console.log('📊 [State] filteredMethodList长度:', filteredMethodList.length, '内容:', filteredMethodList)
  }, [filteredMethodList])

  // 筛选相关状态
  const [methodTypeFilter, setMethodTypeFilter] = useState<string>('all') // 请求方式筛选

  // 收藏相关状态
  const [favoriteControllers, setFavoriteControllers] = useState<Set<string>>(new Set())
  const [recentControllers, setRecentControllers] = useState<string[]>([])

  // Refs for child components
  const requestModalRef = useRef<any>(null)
  const responseModalRef = useRef<any>(null)

  // 当前正在编辑的接口（用于高亮显示）
  const [editingMethod, setEditingMethod] = useState<{
    controllerName: string
    methodName: string
    type: 'request' | 'response'
  } | null>(null)

  // 防止React StrictMode导致的重复请求
  const isInitialized = useRef(false)

  // 从localStorage加载收藏和最近访问
  useEffect(() => {
    const savedFavorites = localStorage.getItem('favoriteControllers')
    if (savedFavorites) {
      setFavoriteControllers(new Set(JSON.parse(savedFavorites)))
    }
    const savedRecent = localStorage.getItem('recentControllers')
    if (savedRecent) {
      setRecentControllers(JSON.parse(savedRecent))
    }
  }, [])

  // 保存收藏到localStorage
  useEffect(() => {
    localStorage.setItem('favoriteControllers', JSON.stringify(Array.from(favoriteControllers)))
  }, [favoriteControllers])

  // 保存最近访问到localStorage
  useEffect(() => {
    localStorage.setItem('recentControllers', JSON.stringify(recentControllers))
  }, [recentControllers])

  // 加载Controller列表
  useEffect(() => {
    // 如果已经初始化过，则跳过（防止StrictMode重复执行）
    if (isInitialized.current) {
      return
    }
    isInitialized.current = true
    loadControllerList()
  }, [])

  // 获取请求方式的颜色
  const getMethodColor = (method: string): string => {
    const methodUpper = method?.toUpperCase() || ''
    const colorMap: Record<string, string> = {
      'GET': 'success',
      'POST': 'processing',
      'PUT': 'warning',
      'DELETE': 'error',
      'PATCH': 'default',
      'HEAD': 'default',
      'OPTIONS': 'default'
    }
    return colorMap[methodUpper] || 'default'
  }

  // 当methodList、搜索文本或筛选条件变化时，重新过滤
  useEffect(() => {
    console.log('🔄 [useEffect-methodList] 触发，methodList长度:', methodList?.length, 'searchText:', methodSearchText, 'filter:', methodTypeFilter)

    if (!Array.isArray(methodList)) {
      console.warn('⚠️ [useEffect-methodList] methodList不是数组:', methodList)
      setFilteredMethodList([])
      return
    }

    try {
      let filtered = [...methodList]
      console.log('📝 [useEffect-methodList] 初始filtered长度:', filtered.length)

      // 应用搜索过滤
      if (methodSearchText && methodSearchText.trim()) {
        const searchLower = methodSearchText.toLowerCase()
        filtered = filtered.filter(method => {
          if (!method) return false

          const name = method.name || ''
          const paths = Array.isArray(method.paths) ? method.paths.join(',') : (method.paths || '')
          const methods = Array.isArray(method.methods) ? method.methods.join(',') : (method.methods || '')
          const value = method.value || ''

          const nameMatch = name.toLowerCase().includes(searchLower)
          const pathMatch = paths.toLowerCase().includes(searchLower)
          const methodMatch = methods.toLowerCase().includes(searchLower)
          const valueMatch = value.toLowerCase().includes(searchLower)

          return nameMatch || pathMatch || methodMatch || valueMatch
        })
        console.log('🔍 [useEffect-methodList] 搜索后filtered长度:', filtered.length)
      }

      // 应用请求方式筛选
      if (methodTypeFilter && methodTypeFilter !== 'all') {
        filtered = filtered.filter(method => {
          const methods = Array.isArray(method.methods) ? method.methods.join(',') : (method.methods || '')
          return methods.toUpperCase().includes(methodTypeFilter.toUpperCase())
        })
        console.log('🎯 [useEffect-methodList] 筛选后filtered长度:', filtered.length)
      }

      console.log('✅ [useEffect-methodList] 最终设置filteredMethodList长度:', filtered.length)
      setFilteredMethodList(filtered)
    } catch (error) {
      console.error('❌ [useEffect-methodList] 过滤出错:', error)
      setFilteredMethodList(methodList)
    }
  }, [methodList, methodSearchText, methodTypeFilter])

  const loadControllerList = async () => {
    try {
      setLoading(true)
      const data = await scannerApi()
      setControllerList(data || [])
      // 不需要手动设置filteredControllerList，useEffect会自动处理
      // 默认不选择任何Controller，显示统计大屏
    } catch (error: any) {
      message.error('加载API列表失败：' + error.message)
    } finally {
      setLoading(false)
    }
  }

  // 获取当前表的接口信息（与Vue代码逻辑一致）
  const loadApiDocList = async (className: string) => {
    console.log('🔍 [loadApiDocList] 开始加载 className:', className)
    try {
      setLoading(true)
      const res: any = await getApiDocList(className, true)
      console.log('📦 [loadApiDocList] API返回数据:', res)
      console.log('📦 [loadApiDocList] apiList所有controller名称:', res?.apiList?.map((a: any) => a.name))

      if (res && res.apiList != null && res.apiList.length > 0) {
        // 🔥 修复：在apiList中查找匹配className的controller，而不是直接取第一个
        const apiInfo = res.apiList.find((api: ControllerModel) => api.name === className)

        if (!apiInfo) {
          console.error('❌ [loadApiDocList] 未找到匹配的controller!')
          console.error('❌ [loadApiDocList] 想要:', className)
          console.error('❌ [loadApiDocList] 可用的:', res.apiList.map((a: any) => a.name))
          message.error('未找到对应的Controller')
          setMethodList([])
          return
        }

        console.log('✅ [loadApiDocList] 找到匹配的controller:', apiInfo.name)
        console.log('✅ [loadApiDocList] 解析到apiInfo:', apiInfo)
        console.log('📋 [loadApiDocList] methodList长度:', apiInfo.methodList?.length || 0)
        console.log('📋 [loadApiDocList] methodList内容:', apiInfo.methodList)

        setCurrentController(apiInfo)
        setMethodList(apiInfo.methodList || [])
        // 不需要手动设置filteredMethodList，useEffect会自动处理
      } else {
        console.warn('⚠️ [loadApiDocList] 没有数据，res:', res)
        setMethodList([])
      }
    } catch (error: any) {
      console.error('❌ [loadApiDocList] 加载失败:', error)
      message.error('加载方法列表失败：' + error.message)
      setMethodList([])
    } finally {
      setLoading(false)
      console.log('🏁 [loadApiDocList] 加载完成')
    }
  }

  // 当controllerList或搜索文本变化时，重新过滤Controller
  useEffect(() => {
    if (!Array.isArray(controllerList)) {
      setFilteredControllerList([])
      return
    }

    if (!controllerSearchText || !controllerSearchText.trim()) {
      setFilteredControllerList(controllerList)
      return
    }

    try {
      const searchLower = controllerSearchText.toLowerCase()
      const filtered = controllerList.filter(controller => {
        if (!controller) return false

        // 安全地获取字符串值
        const name = controller.name || ''
        const simpleName = controller.simpleName || ''
        const value = controller.value || ''

        // 匹配搜索文本
        const nameMatch = name.toLowerCase().includes(searchLower)
        const simpleNameMatch = simpleName.toLowerCase().includes(searchLower)
        const valueMatch = value.toLowerCase().includes(searchLower)

        return nameMatch || simpleNameMatch || valueMatch
      })
      setFilteredControllerList(filtered)
    } catch (error) {
      console.error('Error filtering controllers:', error)
      setFilteredControllerList(controllerList)
    }
  }, [controllerList, controllerSearchText])

  const handleControllerSearch = (value: string) => {
    setControllerSearchText(value)
  }

  const handleMethodSearch = (value: string) => {
    setMethodSearchText(value)
  }

  // 收藏Controller
  const toggleFavorite = (controllerName: string) => {
    setFavoriteControllers(prev => {
      const newSet = new Set(prev)
      if (newSet.has(controllerName)) {
        newSet.delete(controllerName)
        message.success('已取消收藏')
      } else {
        newSet.add(controllerName)
        message.success('已添加到收藏')
      }
      return newSet
    })
  }

  // 添加到最近访问
  const addToRecent = (controllerName: string) => {
    setRecentControllers(prev => {
      const filtered = prev.filter(name => name !== controllerName)
      return [controllerName, ...filtered].slice(0, 10) // 保留最近10个
    })
  }

  const handleControllerClick = async (controller: ControllerModel, index: number) => {
    console.log('🖱️🖱️🖱️ [handleControllerClick] ===== 点击触发了！=====')
    console.log('🖱️ [handleControllerClick] 点击Controller:', controller.name, 'index:', index)
    console.log('🖱️ [handleControllerClick] controller完整信息:', controller)

    setCurrentIndex(index)
    setCurrentController(controller)
    console.log('✏️ [handleControllerClick] 已设置currentController:', controller.name)

    // 清空方法搜索
    setMethodSearchText('')
    // 清空筛选
    setMethodTypeFilter('all')
    // 添加到最近访问
    addToRecent(controller.name)
    await loadApiDocList(controller.name)
    console.log('✅ [handleControllerClick] 处理完成')
  }

  // 刷新当前Controller
  const handleRefresh = async () => {
    if (currentController) {
      message.loading('刷新中...', 0)
      await loadApiDocList(currentController.name)
      message.destroy()
      message.success('刷新成功')
    } else {
      await loadControllerList()
      message.success('刷新成功')
    }
  }

  const handleRequestParam = (method: MethodModel) => {
    if (!currentController) return
    console.log('📝 [handleRequestParam] 打开请求参数弹窗, currentController:', currentController.name, 'method:', method.name)
    // 设置当前正在编辑的接口（用于高亮显示）
    const editingInfo = {
      controllerName: currentController.name,
      methodName: method.name,
      type: 'request' as const
    }
    console.log('🎯 [handleRequestParam] 设置编辑状态:', editingInfo)
    setEditingMethod(editingInfo)
    requestModalRef.current?.open(currentController, method)
  }

  const handleResponseParam = (method: MethodModel) => {
    if (!currentController) return
    console.log('📝 [handleResponseParam] 打开返回参数弹窗, currentController:', currentController.name, 'method:', method.name)
    // 设置当前正在编辑的接口（用于高亮显示）
    const editingInfo = {
      controllerName: currentController.name,
      methodName: method.name,
      type: 'response' as const
    }
    console.log('🎯 [handleResponseParam] 设置编辑状态:', editingInfo)
    setEditingMethod(editingInfo)
    responseModalRef.current?.open(currentController, method)
  }

  // 注意：不清除编辑状态，保留高亮状态，直到用户点击其他接口
  // 这样用户可以清楚地知道当前正在编辑哪个接口

  // 监听 editingMethod 变化
  useEffect(() => {
    console.log('🔄 [useEffect-editingMethod] 编辑状态变化:', editingMethod)
  }, [editingMethod])

  // 回到首页（统计大屏）
  const handleBackToHome = () => {
    setCurrentController(null)
    setCurrentIndex(-1)
    setMethodList([])
    setFilteredMethodList([])
    setMethodSearchText('')
    setMethodTypeFilter('all')
  }

  // 全局统计信息（所有Controller的汇总）
  const globalStatistics = useMemo(() => {
    const totalControllers = controllerList.length
    const favoriteCount = favoriteControllers.size
    const recentCount = recentControllers.length

    // 这里可以扩展，如果需要统计所有方法，需要从后端获取
    return {
      totalControllers,
      favoriteCount,
      recentCount,
      filteredControllers: filteredControllerList.length
    }
  }, [controllerList, favoriteControllers, recentControllers, filteredControllerList])

  // 当前Controller的统计信息
  const statistics = useMemo(() => {
    const totalMethods = methodList.length
    const filteredCount = filteredMethodList.length

    // 统计各请求方式数量
    const methodTypes: Record<string, number> = {}
    methodList.forEach(method => {
      const methods = Array.isArray(method.methods) ? method.methods.join(',') : (method.methods || '')
      const methodsArray = methods.split(',').map(m => m.trim().toUpperCase()).filter(Boolean)
      methodsArray.forEach(m => {
        methodTypes[m] = (methodTypes[m] || 0) + 1
      })
    })

    return {
      totalMethods,
      filteredCount,
      methodTypes,
      totalControllers: controllerList.length,
      favoriteCount: favoriteControllers.size
    }
  }, [methodList, filteredMethodList, controllerList, favoriteControllers])

  const methodColumns: ColumnsType<MethodModel> = [
    {
      title: '接口名称',
      dataIndex: 'name',
      key: 'name',
      width: 180,
      ellipsis: {
        showTitle: false,
      },
      render: (text, record) => (
        <Tooltip
          placement="topLeft"
          title={
            <div>
              <div><strong>方法名:</strong> {text}</div>
              {record.paths && <div><strong>路径:</strong> {record.paths}</div>}
              {record.methods && <div><strong>请求方式:</strong> {record.methods}</div>}
              {record.value && <div><strong>备注:</strong> {record.value}</div>}
            </div>
          }
        >
          <span style={{ fontSize: '13px', cursor: 'pointer' }}>{text}</span>
        </Tooltip>
      )
    },
    {
      title: '路径',
      dataIndex: 'paths',
      key: 'paths',
      width: 150,
      ellipsis: {
        showTitle: false,
      },
      render: (text) => (
        <Tooltip placement="topLeft" title={text}>
          <span style={{ cursor: 'pointer' }}>{text}</span>
        </Tooltip>
      ),
    },
    {
      title: '请求方式',
      dataIndex: 'methods',
      key: 'methods',
      width: 90,
      align: 'center',
      render: (text) => {
        const methods = Array.isArray(text) ? text : [text]
        return (
          <Space size={4} wrap>
            {methods.map((method, index) => (
              <Tag key={index} color={getMethodColor(method)} style={{ margin: 0, fontWeight: 'bold' }}>
                {method}
              </Tag>
            ))}
          </Space>
        )
      }
    },
    {
      title: '备注',
      dataIndex: 'value',
      key: 'value',
      width: 120,
      ellipsis: {
        showTitle: false,
      },
      render: (text) => (
        <Tooltip placement="topLeft" title={text || '无备注'}>
          <span style={{ cursor: 'pointer' }}>{text || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      align: 'center',
      render: (_, record) => (
        <div className="action-buttons">
          <Button
            type="primary"
            size="small"
            onClick={() => handleRequestParam(record)}
          >
            请求参数
          </Button>
          <Button
            type="primary"
            size="small"
            onClick={() => handleResponseParam(record)}
          >
            返回参数
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="api-container">
      <div className="api-sidebar">
        {/* 首页按钮 */}
        <div
          className={`home-button ${!currentController ? 'active' : ''}`}
          onClick={handleBackToHome}
        >
          <HomeOutlined style={{ marginRight: 8, fontSize: 16 }} />
          <span style={{ fontWeight: 600 }}>统计大屏</span>
        </div>

        <div className="controller-search">
          <Input
            placeholder="搜索Controller..."
            prefix={<SearchOutlined />}
            value={controllerSearchText}
            onChange={(e) => handleControllerSearch(e.target.value)}
            allowClear
            size="small"
          />
        </div>
        <div className="controller-list">
          {filteredControllerList.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="未找到匹配的Controller"
              style={{ padding: '40px 20px' }}
            />
          ) : (
            filteredControllerList.map((controller, index) => {
              const isFavorite = favoriteControllers.has(controller.name)
              const isRecent = recentControllers.includes(controller.name)
              return (
                <div
                  key={controller.name}
                  className={`controller-item ${currentController?.name === controller.name ? 'active' : ''}`}
                >
                  <div onClick={() => handleControllerClick(controller, index)} style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <p className="controller-name" style={{ flex: 1, margin: '0 0 4px 0' }}>
                        {controller.simpleName}
                      </p>
                      {isRecent && !isFavorite && (
                        <Tag color="orange" style={{ fontSize: '10px', padding: '0 4px', margin: 0 }}>最近</Tag>
                      )}
                    </div>
                    <p className="controller-desc">{controller.value}</p>
                  </div>
                  <div
                    onClick={(e) => {
                      e.stopPropagation()
                      toggleFavorite(controller.name)
                    }}
                    style={{ cursor: 'pointer', padding: '4px', marginLeft: 4 }}
                  >
                    {isFavorite ? (
                      <StarFilled style={{ color: '#faad14', fontSize: 16 }} />
                    ) : (
                      <StarOutlined style={{ color: '#d9d9d9', fontSize: 16 }} />
                    )}
                  </div>
                </div>
              )
            })
          )}
        </div>
      </div>

      <div className="api-content">
        {/* 统计大屏 - 未选择Controller时显示 */}
        {!currentController ? (
          <Card>
            <Spin spinning={loading}>
              <div className="dashboard">
                <div className="dashboard-header">
                  <h1 style={{ fontSize: 32, fontWeight: 700, margin: 0, color: '#1890ff' }}>
                    <ApiOutlined style={{ marginRight: 12 }} />
                    API 管理统计大屏
                  </h1>
                  <p style={{ color: '#666', marginTop: 8, fontSize: 14 }}>
                    实时监控您的API接口状态和使用情况
                  </p>
                </div>

                {/* 核心统计卡片 */}
                <Row gutter={[16, 16]} style={{ marginTop: 32 }}>
                  <Col span={6}>
                    <Card className="stat-card" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', border: 'none' }}>
                      <Statistic
                        title={<span style={{ color: 'rgba(255,255,255,0.9)', fontSize: 14 }}>Controller总数</span>}
                        value={globalStatistics.totalControllers}
                        prefix={<FolderOpenOutlined style={{ fontSize: 28 }} />}
                        valueStyle={{ color: '#fff', fontSize: 36, fontWeight: 'bold' }}
                        suffix={<span style={{ fontSize: 14, color: 'rgba(255,255,255,0.8)' }}>个</span>}
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card className="stat-card" style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', border: 'none' }}>
                      <Statistic
                        title={<span style={{ color: 'rgba(255,255,255,0.9)', fontSize: 14 }}>收藏数量</span>}
                        value={globalStatistics.favoriteCount}
                        prefix={<StarFilled style={{ fontSize: 28 }} />}
                        valueStyle={{ color: '#fff', fontSize: 36, fontWeight: 'bold' }}
                        suffix={<span style={{ fontSize: 14, color: 'rgba(255,255,255,0.8)' }}>个</span>}
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card className="stat-card" style={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', border: 'none' }}>
                      <Statistic
                        title={<span style={{ color: 'rgba(255,255,255,0.9)', fontSize: 14 }}>最近访问</span>}
                        value={globalStatistics.recentCount}
                        prefix={<CheckCircleOutlined style={{ fontSize: 28 }} />}
                        valueStyle={{ color: '#fff', fontSize: 36, fontWeight: 'bold' }}
                        suffix={<span style={{ fontSize: 14, color: 'rgba(255,255,255,0.8)' }}>个</span>}
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card className="stat-card" style={{ background: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)', border: 'none' }}>
                      <Statistic
                        title={<span style={{ color: 'rgba(255,255,255,0.9)', fontSize: 14 }}>当前显示</span>}
                        value={globalStatistics.filteredControllers}
                        prefix={<FilterOutlined style={{ fontSize: 28 }} />}
                        valueStyle={{ color: '#fff', fontSize: 36, fontWeight: 'bold' }}
                        suffix={<span style={{ fontSize: 14, color: 'rgba(255,255,255,0.8)' }}>个</span>}
                      />
                    </Card>
                  </Col>
                </Row>

                {/* 快捷操作区 */}
                <Row gutter={16} style={{ marginTop: 24 }}>
                  <Col span={12}>
                    <Card
                      title={<><StarFilled style={{ color: '#faad14', marginRight: 8 }} />我的收藏</>}
                      bordered={false}
                      style={{ height: 300 }}
                      bodyStyle={{ height: 240, overflowY: 'auto' }}
                    >
                      {favoriteControllers.size === 0 ? (
                        <Empty description="暂无收藏" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ) : (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          {controllerList.filter(c => favoriteControllers.has(c.name)).map(controller => (
                            <div
                              key={controller.name}
                              onClick={() => {
                                const index = controllerList.findIndex(c => c.name === controller.name)
                                handleControllerClick(controller, index)
                              }}
                              style={{
                                padding: '12px',
                                background: '#f5f5f5',
                                borderRadius: 6,
                                cursor: 'pointer',
                                transition: 'all 0.2s',
                              }}
                              onMouseEnter={(e) => e.currentTarget.style.background = '#e6f7ff'}
                              onMouseLeave={(e) => e.currentTarget.style.background = '#f5f5f5'}
                            >
                              <div style={{ fontWeight: 600, color: '#1890ff' }}>{controller.simpleName}</div>
                              <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>{controller.value}</div>
                            </div>
                          ))}
                        </Space>
                      )}
                    </Card>
                  </Col>
                  <Col span={12}>
                    <Card
                      title={<><CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />最近访问</>}
                      bordered={false}
                      style={{ height: 300 }}
                      bodyStyle={{ height: 240, overflowY: 'auto' }}
                    >
                      {recentControllers.length === 0 ? (
                        <Empty description="暂无访问记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ) : (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          {recentControllers.slice(0, 5).map(name => {
                            const controller = controllerList.find(c => c.name === name)
                            if (!controller) return null
                            return (
                              <div
                                key={controller.name}
                                onClick={() => {
                                  const index = controllerList.findIndex(c => c.name === controller.name)
                                  handleControllerClick(controller, index)
                                }}
                                style={{
                                  padding: '12px',
                                  background: '#f5f5f5',
                                  borderRadius: 6,
                                  cursor: 'pointer',
                                  transition: 'all 0.2s',
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.background = '#e6f7ff'}
                                onMouseLeave={(e) => e.currentTarget.style.background = '#f5f5f5'}
                              >
                                <div style={{ fontWeight: 600, color: '#1890ff' }}>{controller.simpleName}</div>
                                <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>{controller.value}</div>
                              </div>
                            )
                          })}
                        </Space>
                      )}
                    </Card>
                  </Col>
                </Row>

                {/* 使用提示 */}
                <Card
                  title="💡 使用提示"
                  style={{ marginTop: 24 }}
                  bordered={false}
                >
                  <Row gutter={16}>
                    <Col span={8}>
                      <div style={{ textAlign: 'center', padding: 20 }}>
                        <SearchOutlined style={{ fontSize: 32, color: '#1890ff', marginBottom: 12 }} />
                        <h3>快速搜索</h3>
                        <p style={{ color: '#666', fontSize: 13 }}>
                          在左侧搜索框输入关键词，快速定位Controller
                        </p>
                      </div>
                    </Col>
                    <Col span={8}>
                      <div style={{ textAlign: 'center', padding: 20 }}>
                        <StarFilled style={{ fontSize: 32, color: '#faad14', marginBottom: 12 }} />
                        <h3>收藏功能</h3>
                        <p style={{ color: '#666', fontSize: 13 }}>
                          点击Controller右侧星标即可收藏常用接口
                        </p>
                      </div>
                    </Col>
                    <Col span={8}>
                      <div style={{ textAlign: 'center', padding: 20 }}>
                        <FilterOutlined style={{ fontSize: 32, color: '#52c41a', marginBottom: 12 }} />
                        <h3>多条件筛选</h3>
                        <p style={{ color: '#666', fontSize: 13 }}>
                          支持按请求方式、隐藏状态等多条件筛选
                        </p>
                      </div>
                    </Col>
                  </Row>
                </Card>
              </div>
            </Spin>
          </Card>
        ) : (
          /* Controller详情视图 - 移除Tabs，直接显示API接口内容 */
          <Card>
            <Spin spinning={loading}>
              {currentController && (
                <>
                  {/* 统计信息卡片 */}
                  <Row gutter={16} style={{ marginBottom: 16 }}>
                    <Col span={12}>
                      <Card size="small" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
                        <Statistic
                          title={<span style={{ color: 'rgba(255,255,255,0.85)' }}>接口总数</span>}
                          value={statistics.totalMethods}
                          prefix={<ApiOutlined />}
                          valueStyle={{ color: '#fff', fontSize: 24 }}
                        />
                      </Card>
                    </Col>
                    <Col span={12}>
                      <Card size="small" style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' }}>
                        <Statistic
                          title={<span style={{ color: 'rgba(255,255,255,0.85)' }}>当前显示</span>}
                          value={statistics.filteredCount}
                          prefix={<FilterOutlined />}
                          valueStyle={{ color: '#fff', fontSize: 24 }}
                        />
                      </Card>
                    </Col>
                  </Row>

                  <div className="api-info">
                    <p><strong>类名：</strong>{currentController.name}</p>
                    <p><strong>描述：</strong>{currentController.value}</p>
                    <p><strong>路径：</strong>{currentController.path?.join(', ') || ''}</p>
                  </div>

                  {/* 工具栏：搜索、筛选、批量操作 */}
                  <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }} size={12}>
                    <Row gutter={8}>
                      <Col flex="auto">
                        <Input
                          placeholder="搜索方法名、路径、请求方式、备注..."
                          prefix={<SearchOutlined />}
                          value={methodSearchText}
                          onChange={(e) => handleMethodSearch(e.target.value)}
                          allowClear
                          size="middle"
                        />
                      </Col>
                      <Col>
                        <Select
                          value={methodTypeFilter}
                          onChange={setMethodTypeFilter}
                          style={{ width: 120 }}
                          size="middle"
                        >
                          <Select.Option value="all">全部方式</Select.Option>
                          <Select.Option value="GET">GET</Select.Option>
                          <Select.Option value="POST">POST</Select.Option>
                          <Select.Option value="PUT">PUT</Select.Option>
                          <Select.Option value="DELETE">DELETE</Select.Option>
                          <Select.Option value="PATCH">PATCH</Select.Option>
                        </Select>
                      </Col>
                      <Col>
                        <Button
                          icon={<ReloadOutlined />}
                          onClick={handleRefresh}
                          size="middle"
                        >
                          刷新
                        </Button>
                      </Col>
                    </Row>
                  </Space>

                  {/* 调试信息 */}
                  {process.env.NODE_ENV === 'development' && (
                    <div style={{
                      padding: '8px',
                      background: '#f0f0f0',
                      marginBottom: '8px',
                      fontSize: '12px',
                      borderRadius: '4px'
                    }}>
                      <div>🐛 <strong>调试信息:</strong></div>
                      <div>• methodList长度: {methodList.length}</div>
                      <div>• filteredMethodList长度: {filteredMethodList.length}</div>
                      <div>• currentController: {currentController?.name || 'null'}</div>
                      <div>• loading: {loading ? '是' : '否'}</div>
                    </div>
                  )}

                  <Table
                    columns={methodColumns}
                    dataSource={filteredMethodList}
                    rowKey="name"
                    size="small"
                    rowClassName={(record) => {
                      // 高亮显示当前正在编辑的接口
                      const isEditing = editingMethod &&
                          editingMethod.controllerName === currentController?.name &&
                          editingMethod.methodName === record.name

                      if (isEditing) {
                        console.log('✅ [Table] 高亮行:', record.name, 'editingMethod:', editingMethod)
                      }

                      return isEditing ? 'editing-row' : ''
                    }}
                    onRow={(record) => {
                      // 通过 rowProps 添加 className 和 style，确保样式生效
                      const isEditing = editingMethod &&
                          editingMethod.controllerName === currentController?.name &&
                          editingMethod.methodName === record.name

                      if (isEditing) {
                        return {
                          className: 'editing-row',
                          style: {
                            background: 'linear-gradient(90deg, #e6f7ff 0%, #f0f9ff 100%)',
                            borderLeft: '4px solid #1890ff',
                            boxShadow: '0 2px 8px rgba(24, 144, 255, 0.2)',
                            position: 'relative'
                          }
                        }
                      }
                      return {}
                    }}
                    pagination={{
                      defaultPageSize: 15,
                      showSizeChanger: true,
                      pageSizeOptions: ['10', '15', '20', '30', '50', '100'],
                      showTotal: (total) => {
                        console.log('📋 [Table] 渲染表格，总数:', total, 'dataSource长度:', filteredMethodList.length)
                        return `共 ${total} 条`
                      },
                      size: 'small'
                    }}
                    scroll={{ x: 900 }}
                    locale={{
                      emptyText: (
                        <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description={
                            <Space direction="vertical">
                              <span>暂无数据</span>
                              {(methodSearchText || methodTypeFilter !== 'all') && (
                                <Button size="small" onClick={() => {
                                  setMethodSearchText('')
                                  setMethodTypeFilter('all')
                                }}>
                                  清除筛选
                                </Button>
                              )}
                            </Space>
                          }
                        />
                      )
                    }}
                  />
                </>
              )}
            </Spin>
          </Card>
        )}
      </div>

      {/* 请求参数弹窗 */}
      <RequestParamModal ref={requestModalRef} />

      {/* 返回参数弹窗 */}
      <ResponseParamModal ref={responseModalRef} />
    </div>
  )
}

export default ApiManagement
