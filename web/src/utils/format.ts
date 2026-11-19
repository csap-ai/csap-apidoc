import dayjs from 'dayjs';

export const fmtTime = (ms?: number | null): string => (ms ? dayjs(ms).format('YYYY-MM-DD HH:mm:ss') : '-');
export const fmtDate = (ms?: number | null): string => (ms ? dayjs(ms).format('YYYY-MM-DD') : '-');

/** 格式化规则 JSON 字符串;无法解析时原样返回 */
export function prettyJson(s: string | null | undefined): string {
  if (s == null) return '';
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}

/** 校验 JSON,返回错误信息或 null */
export function jsonError(s: string): string | null {
  try {
    JSON.parse(s);
    return null;
  } catch (e) {
    return (e as Error).message;
  }
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(ta);
      return ok;
    } catch {
      return false;
    }
  }
}
