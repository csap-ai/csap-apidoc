import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert, Button, Card, Checkbox, Col, Descriptions, Empty, Input, Modal, Radio, Row, Segmented, Select, Space,
  Spin, Statistic, Switch, Table, Tabs, Tag, Tooltip, Typography, message
} from 'antd'
import { ReloadOutlined, SaveOutlined, UndoOutlined, CopyOutlined, RobotOutlined, SafetyOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  getMcpAudit, getMcpStatus, getMcpTools, saveMcpAuth, saveMcpTools,
  type McpAuditEntry, type McpSaveResult, type McpStatus, type McpToolRow, type McpToolUpdate
} from '@/api/mcp'
import { initialLang, messages, saveLang, type Lang } from './i18n'
import { DOCS_URL, pickHint } from '@/utils/hint'
import './index.scss'

const METHOD_COLORS: Record<string, string> = {
  GET: 'green', POST: 'blue', PUT: 'orange', PATCH: 'gold', DELETE: 'red'
}
const NAME_PATTERN = /^[A-Za-z0-9_-]{1,64}$/

interface Draft {
  enabled: boolean
  name: string
  description: string
}

const McpManagement: React.FC = () => {
  const [lang, setLang] = useState<Lang>(initialLang())
  const t = messages[lang]
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<McpStatus | null>(null)
  const [tools, setTools] = useState<McpToolRow[]>([])
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [search, setSearch] = useState('')
  const [onlyExposed, setOnlyExposed] = useState(false)
  const [saving, setSaving] = useState(false)
  const [authMode, setAuthMode] = useState<'token' | 'passthrough'>('token')
  const [forwardHeaders, setForwardHeaders] = useState<string[]>([])
  const [audit, setAudit] = useState<McpAuditEntry[]>([])
  const [auditTool, setAuditTool] = useState('')
  const [auditCaller, setAuditCaller] = useState('')
  const [auditLoading, setAuditLoading] = useState(false)

  const changeLang = (l: Lang) => {
    setLang(l)
    saveLang(l)
  }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const s = await getMcpStatus()
      setStatus(s)
      if (s.enabled) {
        const list = await getMcpTools()
        setTools(list)
        setDrafts({})
        setAuthMode((s.authMode as 'token' | 'passthrough') || 'token')
        setForwardHeaders(s.forwardHeaders || [])
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const loadAudit = useCallback(async () => {
    setAuditLoading(true)
    try {
      setAudit(await getMcpAudit({ limit: 200, tool: auditTool || undefined, caller: auditCaller || undefined }))
    } finally {
      setAuditLoading(false)
    }
  }, [auditTool, auditCaller])

  useEffect(() => {
    load()
  }, [load])

  const current = (row: McpToolRow): Draft => drafts[row.methodKey] || {
    enabled: row.exposed,
    name: row.name === row.defaultName ? '' : row.name,
    description: row.description === row.defaultDescription ? '' : row.description
  }

  const edit = (row: McpToolRow, patch: Partial<Draft>) => {
    setDrafts(prev => ({ ...prev, [row.methodKey]: { ...current(row), ...patch } }))
  }

  const changedKeys = useMemo(() => tools.filter(row => {
    const d = drafts[row.methodKey]
    if (!d) return false
    const name = row.name === row.defaultName ? '' : row.name
    const desc = row.description === row.defaultDescription ? '' : row.description
    return d.enabled !== row.exposed || d.name.trim() !== name || d.description.trim() !== desc
  }).map(r => r.methodKey), [tools, drafts])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return tools.filter(row => {
      if (onlyExposed && !current(row).enabled) return false
      if (!q) return true
      return [row.title, row.methodKey, row.path, row.name, row.httpMethod]
        .some(v => (v || '').toLowerCase().includes(q))
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tools, search, onlyExposed, drafts])

  const showSaved = (r: McpSaveResult) => {
    if (r.errors && r.errors.length > 0) {
      message.warning(r.errors.join('; '))
    } else if (r.persisted) {
      message.success(`${t.saved}: ${r.location}`)
    } else {
      message.warning(t.savedMemory)
    }
  }

  const doSaveTools = async () => {
    const rows = tools.filter(r => changedKeys.includes(r.methodKey))
    const updates: McpToolUpdate[] = rows.map(row => {
      const d = current(row)
      return {
        methodKey: row.methodKey,
        enabled: d.enabled,
        name: d.name.trim() || null,
        description: d.description.trim() || null
      }
    })
    setSaving(true)
    try {
      showSaved(await saveMcpTools(updates))
      await load()
    } finally {
      setSaving(false)
    }
  }

  const saveTools = () => {
    if (changedKeys.length === 0) {
      message.info(t.nothingChanged)
      return
    }
    const bad = tools.filter(r => changedKeys.includes(r.methodKey))
      .map(r => current(r).name.trim()).filter(n => n && !NAME_PATTERN.test(n))
    if (bad.length > 0) {
      message.error(`${t.toolName}: ${bad.join(', ')} — ${t.nameRule}`)
      return
    }
    const newWrites = tools.filter(r => changedKeys.includes(r.methodKey) && !r.readOnly && !r.exposed
      && current(r).enabled)
    if (newWrites.length > 0) {
      Modal.confirm({
        title: t.writeConfirm,
        icon: <SafetyOutlined style={{ color: '#fa8c16' }} />,
        content: (
          <ul className="mcp-confirm-list">
            {newWrites.map(r => (
              <li key={r.methodKey}><Tag color={METHOD_COLORS[r.httpMethod]}>{r.httpMethod}</Tag>{r.path} {r.title}</li>
            ))}
          </ul>
        ),
        okText: t.confirm,
        cancelText: t.cancel,
        onOk: doSaveTools
      })
      return
    }
    doSaveTools()
  }

  const saveAuth = async () => {
    setSaving(true)
    try {
      showSaved(await saveMcpAuth({ mode: authMode, forwardHeaders }))
      await load()
    } finally {
      setSaving(false)
    }
  }

  const endpointUrl = status ? `${window.location.origin}${status.path}` : ''

  const copy = (text: string) => {
    navigator.clipboard?.writeText(text).then(() => message.success(t.copied))
  }

  const columns: ColumnsType<McpToolRow> = [
    {
      title: t.expose, width: 70, align: 'center', fixed: 'left',
      render: (_, row) => {
        const disabled = !!row.unsupportedReason || row.hidden
        return <Checkbox checked={current(row).enabled && !disabled} disabled={disabled}
          onChange={e => edit(row, { enabled: e.target.checked })} />
      }
    },
    {
      title: t.method, dataIndex: 'httpMethod', width: 90,
      render: (m: string) => <Tag color={METHOD_COLORS[m] || 'default'}>{m}</Tag>
    },
    {
      title: t.api, key: 'api', width: 280,
      render: (_, row) => (
        <div className="mcp-api-cell">
          <div className="mcp-api-title">{row.title || row.methodName}</div>
          <Typography.Text type="secondary" className="mcp-api-path">{row.path || '-'}</Typography.Text>
        </div>
      )
    },
    {
      title: t.toolName, key: 'name', width: 230,
      render: (_, row) => {
        const v = current(row).name
        return <Input size="small" value={v} placeholder={row.defaultName} allowClear
          status={v && !NAME_PATTERN.test(v.trim()) ? 'error' : undefined}
          onChange={e => edit(row, { name: e.target.value })} />
      }
    },
    {
      title: t.description, key: 'description',
      render: (_, row) => (
        <Input.TextArea value={current(row).description} placeholder={row.defaultDescription}
          autoSize={{ minRows: 1, maxRows: 4 }} onChange={e => edit(row, { description: e.target.value })} />
      )
    },
    {
      title: t.state, key: 'state', width: 150,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.unsupportedReason && <Tooltip title={row.unsupportedReason}><Tag color="default">{t.unsupported}</Tag></Tooltip>}
          {row.hidden && <Tag>{t.hidden}</Tag>}
          {!row.readOnly && <Tag color="orange">{t.writeOp}</Tag>}
          {row.exposedByDefault && <Tag color="cyan">{t.defaultOn}</Tag>}
          {changedKeys.includes(row.methodKey) && <Tag color="processing">*</Tag>}
        </Space>
      )
    }
  ]

  const auditColumns: ColumnsType<McpAuditEntry> = [
    { title: t.time, dataIndex: 'time', width: 170, render: (v: number) => new Date(v).toLocaleString() },
    { title: t.caller, dataIndex: 'caller', width: 130 },
    { title: t.tool, dataIndex: 'tool', width: 180 },
    {
      title: t.target, dataIndex: 'target', width: 260, ellipsis: { showTitle: false },
      render: (v: string) => <Tooltip title={v}><span className="mcp-mono">{v}</span></Tooltip>
    },
    {
      title: t.status, dataIndex: 'status', width: 90,
      render: (v: number, row) => <Tag color={row.success ? 'success' : 'error'}>{v || '-'}</Tag>
    },
    { title: t.duration, dataIndex: 'durationMs', width: 90, render: (v: number) => `${v ?? 0} ms` },
    {
      title: t.args, dataIndex: 'arguments', ellipsis: { showTitle: false },
      render: (v: string) => <Tooltip title={v}><span className="mcp-mono">{v}</span></Tooltip>
    },
    {
      title: t.result, dataIndex: 'result', ellipsis: { showTitle: false },
      render: (v: string) => <Tooltip title={v}><span className="mcp-mono">{v}</span></Tooltip>
    }
  ]

  const claudeCmd = `claude mcp add --transport http csap-apidoc ${endpointUrl}`
    + (status?.authMode === 'token' ? ' --header "Authorization: Bearer $CSAP_MCP_TOKEN"' : '')
  const cursorJson = JSON.stringify({
    mcpServers: {
      'csap-apidoc': {
        url: endpointUrl,
        ...(status?.authMode === 'token' ? { headers: { Authorization: 'Bearer ${env:CSAP_MCP_TOKEN}' } } : {})
      }
    }
  }, null, 2)

  const header = (
    <div className="mcp-header">
      <div>
        <h2><RobotOutlined /> {t.title}</h2>
        <Typography.Text type="secondary">{t.subtitle}</Typography.Text>
      </div>
      <Space>
        <Segmented size="small" value={lang} onChange={v => changeLang(v as Lang)}
          options={[{ label: '中文', value: 'zh' }, { label: 'EN', value: 'en' }]} />
        <Button icon={<ReloadOutlined />} onClick={load}>{t.refresh}</Button>
      </Space>
    </div>
  )

  if (loading && !status) {
    return <div className="mcp-container"><Spin /></div>
  }

  if (status && !status.enabled) {
    return (
      <div className="mcp-container">
        {header}
        <Card>
          <Alert type="info" showIcon message={t.disabled} description={
            <div>
              <p>{t.disabledTip}</p>
              {pickHint(status.hint, lang)
                ? <pre className="mcp-code" style={{ whiteSpace: 'pre-wrap' }}>{pickHint(status.hint, lang)}</pre>
                : <a href={`${DOCS_URL}/guide/mcp`} target="_blank" rel="noreferrer">{DOCS_URL}/guide/mcp</a>}
            </div>
          } />
        </Card>
      </div>
    )
  }

  return (
    <div className="mcp-container">
      {header}
      <Spin spinning={loading}>
        <Card className="mcp-summary">
          <Row gutter={16}>
            <Col xs={12} md={4}><Statistic title={t.exposed} value={status?.exposedCount ?? 0} suffix={`/ ${status?.toolCount ?? 0}`} /></Col>
            <Col xs={24} md={20}>
              <Descriptions size="small" column={{ xs: 1, md: 2 }}>
                <Descriptions.Item label={t.endpoint}>
                  <span className="mcp-mono">POST {endpointUrl}</span>
                  <Button type="link" size="small" icon={<CopyOutlined />} onClick={() => copy(endpointUrl)} />
                </Descriptions.Item>
                <Descriptions.Item label={t.authMode}>
                  <Tag color={status?.authMode === 'token' ? 'blue' : 'orange'}>
                    {status?.authMode === 'token' ? t.modeToken : t.modePassthrough}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t.protocols}>{(status?.protocolVersions || []).join(', ')}</Descriptions.Item>
                <Descriptions.Item label={t.store}>
                  <Space size={4} wrap>
                    <Tag>{status?.storeType}</Tag>
                    <span className="mcp-mono">{status?.storeLocation}</span>
                    {status?.storePersistent
                      ? <Tag color="success">{t.persisted}</Tag>
                      : <Tooltip title={[t.notPersistedTip, pickHint(status?.persistHint, lang)].filter(Boolean).join('; ')}><Tag color="warning">{t.notPersisted}</Tag></Tooltip>}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            </Col>
          </Row>
        </Card>

        <Card className="mcp-tabs">
          <Tabs onChange={key => { if (key === 'audit') loadAudit() }} items={[
            {
              key: 'tools', label: t.tabTools, children: (
                <>
                  <div className="mcp-toolbar">
                    <Space wrap>
                      <Input.Search allowClear placeholder={t.search} style={{ width: 300 }}
                        onChange={e => setSearch(e.target.value)} />
                      <Space size={6}><Switch size="small" checked={onlyExposed} onChange={setOnlyExposed} />{t.onlyExposed}</Space>
                    </Space>
                    <Space>
                      {changedKeys.length > 0 && <Typography.Text type="warning">{changedKeys.length} {t.changed}</Typography.Text>}
                      <Button icon={<UndoOutlined />} disabled={changedKeys.length === 0} onClick={() => setDrafts({})}>{t.reset}</Button>
                      <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveTools}>{t.save}</Button>
                    </Space>
                  </div>
                  <Table rowKey="methodKey" size="middle" columns={columns} dataSource={filtered}
                    pagination={{ pageSize: 50, showSizeChanger: true }} scroll={{ x: 1100 }}
                    rowClassName={row => (current(row).enabled ? 'mcp-row-on' : '')}
                    locale={{ emptyText: <Empty /> }} />
                </>
              )
            },
            {
              key: 'auth', label: t.tabAuth, children: (
                <div className="mcp-auth">
                  <Radio.Group value={authMode} onChange={e => setAuthMode(e.target.value)}>
                    <Space direction="vertical" size={12}>
                      <Radio value="token">
                        <b>{t.modeToken}</b>
                        <div className="mcp-radio-desc">{t.modeTokenDesc}{pickHint(status?.tokenHint, lang) ? ` ${pickHint(status?.tokenHint, lang)}` : ''}</div>
                      </Radio>
                      <Radio value="passthrough">
                        <b>{t.modePassthrough}</b>
                        <div className="mcp-radio-desc">{t.modePassthroughDesc}</div>
                      </Radio>
                    </Space>
                  </Radio.Group>
                  <div className="mcp-field">
                    <div className="mcp-label">{t.forwardHeaders}</div>
                    <Select mode="tags" style={{ width: '100%', maxWidth: 520 }} value={forwardHeaders}
                      onChange={setForwardHeaders} tokenSeparators={[',', ' ']} />
                    <Typography.Text type="secondary">{t.forwardHeadersTip}</Typography.Text>
                  </div>
                  <div className="mcp-field">
                    <div className="mcp-label">{t.tokenNames}</div>
                    {(status?.tokenNames || []).length > 0
                      ? <Space wrap>{status?.tokenNames?.map(n => <Tag key={n}>{n}</Tag>)}</Space>
                      : <Alert type="warning" showIcon message={t.noToken} />}
                  </div>
                  {(status?.upstreamHeaderNames || []).length > 0 && (
                    <div className="mcp-field">
                      <div className="mcp-label">{t.upstreamHeaders}</div>
                      <Space wrap>{status?.upstreamHeaderNames?.map(n => <Tag key={n}>{n}</Tag>)}</Space>
                    </div>
                  )}
                  <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveAuth}>{t.save}</Button>
                </div>
              )
            },
            {
              key: 'audit', label: t.tabAudit, children: (
                <>
                  <div className="mcp-toolbar">
                    <Space wrap>
                      <Input allowClear placeholder={t.filterTool} style={{ width: 200 }} value={auditTool}
                        onChange={e => setAuditTool(e.target.value)} onPressEnter={loadAudit} />
                      <Input allowClear placeholder={t.filterCaller} style={{ width: 200 }} value={auditCaller}
                        onChange={e => setAuditCaller(e.target.value)} onPressEnter={loadAudit} />
                      <Button icon={<ReloadOutlined />} onClick={loadAudit}>{t.refresh}</Button>
                    </Space>
                    <Typography.Text type="secondary">
                      {status?.auditPersistent ? t.auditPersisted : t.auditMemory}
                    </Typography.Text>
                  </div>
                  <Table rowKey="id" size="small" loading={auditLoading} columns={auditColumns} dataSource={audit}
                    pagination={{ pageSize: 50 }} scroll={{ x: 1200 }} />
                </>
              )
            },
            {
              key: 'connect', label: t.tabConnect, children: (
                <div className="mcp-connect">
                  <Typography.Paragraph type="secondary">{t.connectTip}</Typography.Paragraph>
                  <div className="mcp-label">Claude Code</div>
                  <div className="mcp-code-wrap">
                    <pre className="mcp-code">{claudeCmd}</pre>
                    <Button size="small" icon={<CopyOutlined />} onClick={() => copy(claudeCmd)}>{t.copy}</Button>
                  </div>
                  <div className="mcp-label">Cursor (.cursor/mcp.json)</div>
                  <div className="mcp-code-wrap">
                    <pre className="mcp-code">{cursorJson}</pre>
                    <Button size="small" icon={<CopyOutlined />} onClick={() => copy(cursorJson)}>{t.copy}</Button>
                  </div>
                  <Alert type="warning" showIcon message={t.securityTitle} description={
                    <ul className="mcp-tips">
                      <li>{t.security1}</li>
                      <li>{t.security2}</li>
                      {(status?.securityNotes || []).map((n, i) => {
                        const text = pickHint(n, lang)
                        return text ? <li key={i}>{text}</li> : null
                      })}
                    </ul>
                  } />
                </div>
              )
            }
          ]} />
        </Card>
      </Spin>
    </div>
  )
}

export default McpManagement
