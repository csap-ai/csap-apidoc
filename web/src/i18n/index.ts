import zh, { type MsgKey } from './zh-CN';

export type { MsgKey };

/** 取文案;参数形如 {n}。以后加英文:按 locale 切换字典即可。 */
export function t(key: MsgKey, params?: Record<string, string | number>): string {
  let s: string = zh[key] ?? key;
  if (params) Object.entries(params).forEach(([k, v]) => (s = s.split(`{${k}}`).join(String(v))));
  return s;
}

/** 允许动态拼接 key(枚举值)时使用,缺失时回退为原值 */
export function tDyn(prefix: string, value: string | null | undefined): string {
  if (!value) return '';
  const k = `${prefix}.${value}` as MsgKey;
  return zh[k] ?? value;
}
