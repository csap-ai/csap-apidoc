import { useMemo, useState } from 'react'
import { Button, Empty, Input, List, Modal, Space, Tag, Typography } from 'antd'
import type { FieldTemplate } from '@/api/templates'
import { text } from './statusTemplateI18n'

interface Props {
  open: boolean
  templates: FieldTemplate[]
  onClose: () => void
  /** 选好模板、确认预览后调用;模板内容由调用方复制进字段(不联动) */
  onApply: (template: FieldTemplate) => void
}

/** 字段模板选择:搜索、按分类、预览套用后会改变什么,确认后套用 */
const FieldTemplateModal: React.FC<Props> = ({ open, templates, onClose, onApply }) => {
  const t = text()
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState<FieldTemplate | null>(null)

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return templates.filter(x => !q
      || x.name.toLowerCase().includes(q) || (x.title || '').toLowerCase().includes(q)
      || (x.description || '').toLowerCase().includes(q) || (x.category || '').toLowerCase().includes(q))
  }, [templates, search])

  const close = () => {
    setPicked(null)
    setSearch('')
    onClose()
  }

  const preview = (tpl: FieldTemplate) => {
    const items: string[] = []
    if (tpl.validate && tpl.validate.length > 0) items.push(`${t.tplPreviewRules}: ${tpl.validate.length} ${t.tplRulesCount}`)
    if (tpl.description) items.push(`${t.tplPreviewDesc}: ${tpl.description}`)
    if (tpl.example) items.push(`${t.tplPreviewExample}: ${tpl.example}`)
    if (typeof tpl.required === 'boolean') items.push(`${t.tplPreviewRequired}: ${tpl.required ? 'true' : 'false'}`)
    return items
  }

  return (
    <Modal open={open} title={t.tplTitle} width={720} onCancel={close} footer={
      <Space>
        <Button onClick={close}>{t.cancel}</Button>
        <Button type="primary" disabled={!picked} onClick={() => {
          if (picked) onApply(picked)
          close()
        }}>{t.tplApplyBtn}</Button>
      </Space>
    }>
      <Input allowClear placeholder={t.tplSearch} value={search} onChange={e => setSearch(e.target.value)} style={{ marginBottom: 12 }} />
      <div style={{ display: 'flex', gap: 12 }}>
        <div style={{ flex: 1, maxHeight: 360, overflow: 'auto' }}>
          <List size="small" dataSource={filtered} locale={{ emptyText: <Empty description={t.tplEmpty} /> }}
            renderItem={item => (
              <List.Item style={{ cursor: 'pointer', background: picked?.name === item.name ? '#e6f4ff' : undefined }}
                onClick={() => setPicked(item)}>
                <Space direction="vertical" size={0} style={{ width: '100%' }}>
                  <Space>
                    <strong>{item.title}</strong>
                    <Tag color={item.source === 'project' ? 'purple' : 'blue'}>{item.source === 'project' ? t.tplProject : t.tplBuiltin}</Tag>
                    {item.category && <Tag>{item.category}</Tag>}
                  </Space>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>{item.name}{item.description ? ` · ${item.description}` : ''}</Typography.Text>
                </Space>
              </List.Item>
            )} />
        </div>
        <div style={{ flex: 1, background: '#fafafa', padding: 12, borderRadius: 6, minHeight: 120 }}>
          {picked && (
            <>
              <div style={{ marginBottom: 6 }}><strong>{picked.title}</strong> <Typography.Text type="secondary">({picked.name})</Typography.Text></div>
              <div style={{ marginBottom: 6 }}>{t.tplPreview}</div>
              <ul style={{ paddingLeft: 18, margin: 0 }}>{preview(picked).map((s, i) => <li key={i}>{s}</li>)}</ul>
              {(picked.validate || []).map((v, i) => (
                <div key={i} style={{ fontSize: 12, marginTop: 6 }}>
                  <Tag>{v.type}</Tag><code>{v.pattern}</code>{v.message ? ` — ${v.message}` : ''}
                </div>
              ))}
            </>
          )}
        </div>
      </div>
    </Modal>
  )
}

export default FieldTemplateModal
