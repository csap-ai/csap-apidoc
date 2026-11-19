import { createTwoFilesPatch } from 'diff';
import { prettyJson } from './format';

/** 按格式化后的 JSON 行生成统一差异文本(与服务端 diff 风格一致) */
export function makeUnified(before: string | null | undefined, after: string | null | undefined, a = '当前库', b = '我的'): string {
  const x = prettyJson(before ?? '');
  const y = prettyJson(after ?? '');
  if (x === y) return '';
  return createTwoFilesPatch(a, b, x ? x + '\n' : '', y ? y + '\n' : '', '', '', { context: 3 });
}

export type RowKind = 'ctx' | 'add' | 'del' | 'hunk';
export interface UnifiedRow {
  kind: RowKind;
  text: string;
}
export interface SplitRow {
  left?: { text: string; kind: 'ctx' | 'del' };
  right?: { text: string; kind: 'ctx' | 'add' };
  hunk?: string;
}

/** 解析统一差异文本为行(忽略 --- / +++ / Index 头) */
export function parseUnified(diff: string): UnifiedRow[] {
  const rows: UnifiedRow[] = [];
  if (!diff) return rows;
  for (const line of diff.split('\n')) {
    if (line.startsWith('Index:') || line.startsWith('=====') || line.startsWith('--- ') || line.startsWith('+++ ')) continue;
    if (line.startsWith('@@')) rows.push({ kind: 'hunk', text: line });
    else if (line.startsWith('+')) rows.push({ kind: 'add', text: line.slice(1) });
    else if (line.startsWith('-')) rows.push({ kind: 'del', text: line.slice(1) });
    else if (line.startsWith(' ')) rows.push({ kind: 'ctx', text: line.slice(1) });
    else if (line === '\\ No newline at end of file') continue;
    else if (line === '') continue;
    else rows.push({ kind: 'ctx', text: line });
  }
  return rows;
}

/** 把相邻的删除 / 新增配对成并排行 */
export function toSplit(rows: UnifiedRow[]): SplitRow[] {
  const out: SplitRow[] = [];
  let dels: string[] = [];
  let adds: string[] = [];
  const flush = () => {
    const n = Math.max(dels.length, adds.length);
    for (let i = 0; i < n; i++) {
      out.push({
        left: i < dels.length ? { text: dels[i], kind: 'del' } : undefined,
        right: i < adds.length ? { text: adds[i], kind: 'add' } : undefined,
      });
    }
    dels = [];
    adds = [];
  };
  for (const r of rows) {
    if (r.kind === 'del') dels.push(r.text);
    else if (r.kind === 'add') adds.push(r.text);
    else {
      flush();
      if (r.kind === 'hunk') out.push({ hunk: r.text });
      else out.push({ left: { text: r.text, kind: 'ctx' }, right: { text: r.text, kind: 'ctx' } });
    }
  }
  flush();
  return out;
}
