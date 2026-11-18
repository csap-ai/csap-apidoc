export type Lang = 'zh' | 'en'

const zh = {
  title: '规则记录',
  subtitle: '数据库 / Redis 模式下每次保存规则都会留下记录,可以查看修改前后的差异,并回滚到修改之前',
  refresh: '刷新',
  unsupported: '当前存储不支持修改记录',
  unsupportedTip: '修改记录与回滚只在数据库模式或 Redis 模式下可用,请切换到数据库或 Redis 模式,详见文档:',
  docsDatabase: '数据库模式',
  docsRedis: 'Redis 模式',
  readOnly: '只读模式',
  readOnlyTip: '当前实例是只读模式(read-only=true),可以查看记录,不能回滚。',
  filterPlaceholder: '按规则键筛选,如 com.example.UserController.add',
  search: '查询',
  time: '时间',
  ruleKey: '规则键(类名.方法名)',
  version: '版本',
  updatedBy: '修改人',
  kind: '类型',
  kindAdd: '新增',
  kindUpdate: '修改',
  kindDelete: '删除',
  actions: '操作',
  diff: '查看差异',
  rollback: '回滚',
  empty: '暂无修改记录',
  diffTitle: '修改前后对比',
  before: '修改前',
  after: '修改后',
  none: '(无)',
  noChange: '两边内容相同',
  rollbackTitle: '确认回滚?',
  rollbackContent: '该接口的规则会恢复成这条记录"修改前"的样子(回滚本身也会留下一条记录),其他实例几秒内同步。',
  rollbackOk: '回滚',
  cancel: '取消',
  rollbackDone: '已回滚,新版本',
  rollbackSame: '规则已经是修改前的内容,无需回滚',
  loadFailed: '加载失败',
  close: '关闭'
}

const en: typeof zh = {
  title: 'Rule history',
  subtitle: 'In the database / Redis mode every save leaves a record. Review what changed and roll an endpoint back to its earlier rules.',
  refresh: 'Refresh',
  unsupported: 'This storage does not support change history',
  unsupportedTip: 'Change history and rollback are only available in the database or Redis mode. Switch to the database or Redis mode, see the docs:',
  docsDatabase: 'Database mode',
  docsRedis: 'Redis mode',
  readOnly: 'Read-only',
  readOnlyTip: 'This instance is read-only (read-only=true): you can browse the history but not roll back.',
  filterPlaceholder: 'Filter by rule key, e.g. com.example.UserController.add',
  search: 'Search',
  time: 'Time',
  ruleKey: 'Rule key (class.method)',
  version: 'Version',
  updatedBy: 'Changed by',
  kind: 'Type',
  kindAdd: 'Added',
  kindUpdate: 'Changed',
  kindDelete: 'Deleted',
  actions: 'Actions',
  diff: 'View diff',
  rollback: 'Roll back',
  empty: 'No changes yet',
  diffTitle: 'Before and after',
  before: 'Before',
  after: 'After',
  none: '(none)',
  noChange: 'Both sides are the same',
  rollbackTitle: 'Roll back?',
  rollbackContent: 'The rules of this endpoint are restored to how they were before this change (the rollback itself is recorded too). Other instances pick it up within seconds.',
  rollbackOk: 'Roll back',
  cancel: 'Cancel',
  rollbackDone: 'Rolled back, new version',
  rollbackSame: 'The rules already match the earlier content; nothing to roll back',
  loadFailed: 'Failed to load',
  close: 'Close'
}

export const messages: Record<Lang, typeof zh> = { zh, en }

const KEY = 'csap-devtools-lang'

export function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'zh' || saved === 'en') return saved
  } catch {
    // ignore
  }
  return navigator.language && navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en'
}

export function saveLang(lang: Lang) {
  try {
    localStorage.setItem(KEY, lang)
  } catch {
    // ignore
  }
}
