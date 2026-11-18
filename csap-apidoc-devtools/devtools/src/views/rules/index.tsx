import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Input, Modal, Segmented, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { HistoryOutlined, ReloadOutlined, RollbackOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { getRuleHistory, getRuleStatus, rollbackRule, type RuleChange, type RuleStatus } from '@/api/rules'
import { initialLang, messages, saveLang, type Lang } from './i18n'
import { lineDiff, pretty } from './diff'
import './index.scss'

type Kind = 'add' | 'update' | 'delete'

function kindOf(c: RuleChange): Kind {
  if (c.before == null || c.before === '') return 'add'
  if (c.after == null || c.after === '') return 'delete'
  return 'update'
}

const KIND_COLOR: Record<Kind, string> = { add: 'green', update: 'blue', delete: 'red' }

const RuleHistory: React.FC = () => {
  const [lang, setLang] = useState<Lang>(initialLang())
  const t = messages[lang]
  const [status, setStatus] = useState<RuleStatus | null>(null)
  const [rows, setRows] = useState<RuleChange[]>([])
  const [loading, setLoading] = useState(false)
  const [filter, setFilter] = useState('')
  const [detail, setDetail] = useState<RuleChange | null>(null)

  const changeLang = (l: Lang) => {
    setLang(l)
    saveLang(l)
  }

  const load = useCallback(async (ruleKey?: string) => {
    setLoading(true)
    try {
      const s = await getRuleStatus()
      setStatus(s)
      if (s.supported) {
        setRows(await getRuleHistory({ ruleKey: ruleKey || undefined, limit: 200 }))
      }
    } catch {
      // 错误提示由请求拦截器统一显示
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const kindText = (k: Kind) => (k === 'add' ? t.kindAdd : k === 'delete' ? t.kindDelete : t.kindUpdate)

  const doRollback = (c: RuleChange) => {
    Modal.confirm({
      title: t.rollbackTitle,
      content: (
        <div>
          <p>{c.ruleKey}</p>
          <p>{t.rollbackContent}</p>
        </div>
      ),
      okText: t.rollbackOk,
      okButtonProps: { danger: true },
      cancelText: t.cancel,
      onOk: async () => {
        const r = await rollbackRule(c.id)
        if (r.rolledBack) {
          message.success(`${t.rollbackDone} ${r.version}`)
        } else {
          message.info(t.rollbackSame)
        }
        await load(filter)
      }
    })
  }

  const columns: ColumnsType<RuleChange> = [
    { title: t.time, dataIndex: 'updatedAt', width: 170, render: (v: number) => new Date(v).toLocaleString() },
    { title: t.ruleKey, dataIndex: 'ruleKey', ellipsis: true },
    { title: t.kind, width: 90, render: (_: unknown, c) => <Tag color={KIND_COLOR[kindOf(c)]}>{kindText(kindOf(c))}</Tag> },
    { title: t.version, dataIndex: 'version', width: 80 },
    { title: t.updatedBy, dataIndex: 'updatedBy', width: 150 },
    {
      title: t.actions,
      width: 190,
      render: (_: unknown, c) => (
        <Space>
          <Button size="small" onClick={() => setDetail(c)}>{t.diff}</Button>
          <Button size="small" danger icon={<RollbackOutlined />} disabled={!!status?.readOnly} onClick={() => doRollback(c)}>
            {t.rollback}
          </Button>
        </Space>
      )
    }
  ]

  const diff = useMemo(() => {
    if (!detail) return null
    const before = pretty(detail.before)
    const after = pretty(detail.after)
    const lines = lineDiff(before, after)
    return {
      same: before === after,
      left: lines.filter(l => l.kind !== 'add'),
      right: lines.filter(l => l.kind !== 'del')
    }
  }, [detail])

  const side = (title: string, lines: { kind: string; text: string }[]) => (
    <div className="rules-diff-side">
      <h4>{title}</h4>
      <pre>
        {lines.length === 0
          ? <div className="rules-line">{t.none}</div>
          : lines.map((l, i) => (
            <div key={i} className={`rules-line ${l.kind === 'add' ? 'rules-line-add' : l.kind === 'del' ? 'rules-line-del' : ''}`}>
              {l.text || ' '}
            </div>
          ))}
      </pre>
    </div>
  )

  const header = (
    <div className="rules-header">
      <div>
        <h2><HistoryOutlined /> {t.title}</h2>
        <Typography.Text type="secondary">{t.subtitle}</Typography.Text>
      </div>
      <Space>
        <Segmented size="small" value={lang} onChange={v => changeLang(v as Lang)}
          options={[{ label: '中文', value: 'zh' }, { label: 'EN', value: 'en' }]} />
        <Button icon={<ReloadOutlined />} onClick={() => load(filter)}>{t.refresh}</Button>
      </Space>
    </div>
  )

  if (loading && !status) {
    return <div className="rules-container"><Spin /></div>
  }

  if (status && !status.supported) {
    return (
      <div className="rules-container">
        {header}
        <Card className="rules-card">
          <Alert type="info" showIcon message={t.unsupported} description={
            <div>
              <p>{t.unsupportedTip}</p>
              <pre className="rules-code">{'@EnableApidoc(paramType = ApiStrategyType.DATABASE)'}</pre>
            </div>
          } />
        </Card>
      </div>
    )
  }

  return (
    <div className="rules-container">
      {header}
      <Card className="rules-card">
        {status?.readOnly && <Alert className="rules-alert" type="warning" showIcon message={t.readOnly} description={t.readOnlyTip} />}
        <div className="rules-toolbar">
          <Input allowClear placeholder={t.filterPlaceholder} value={filter}
            onChange={e => setFilter(e.target.value)} onPressEnter={() => load(filter)} />
          <Button type="primary" onClick={() => load(filter)}>{t.search}</Button>
        </div>
        <Table<RuleChange> rowKey="id" size="small" loading={loading} columns={columns} dataSource={rows}
          locale={{ emptyText: <Empty description={t.empty} /> }} pagination={{ pageSize: 20, showSizeChanger: false }} />
      </Card>
      <Modal open={!!detail} title={`${t.diffTitle} · ${detail?.ruleKey ?? ''}`} width={960} footer={
        <Button onClick={() => setDetail(null)}>{t.close}</Button>
      } onCancel={() => setDetail(null)}>
        {diff?.same && <Alert type="info" showIcon message={t.noChange} style={{ marginBottom: 8 }} />}
        {diff && (
          <div className="rules-diff">
            {side(t.before, diff.left)}
            {side(t.after, diff.right)}
          </div>
        )}
      </Modal>
    </div>
  )
}

export default RuleHistory
