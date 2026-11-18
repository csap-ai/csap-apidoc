/**
 * 后端状态接口里可选的提示(hint):字符串,或 { zh, en };缺哪种语言就退回另一种。
 * 见 docs/design/devtools-hints.md。
 */
export type Hint = string | { zh?: string; en?: string }

export function pickHint(hint: Hint | null | undefined, lang: 'zh' | 'en'): string | undefined {
  if (!hint) return undefined
  if (typeof hint === 'string') return hint
  return (lang === 'zh' ? hint.zh || hint.en : hint.en || hint.zh) || undefined
}

/** 官网文档(通用文案里的链接) */
export const DOCS_URL = 'https://apidoc.csap.ai'
