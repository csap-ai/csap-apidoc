import { useMemo, useState } from 'react';
import { Segmented, Empty } from 'antd';
import { parseUnified, toSplit } from '@/utils/diff';
import { t } from '@/i18n';

const COLORS = {
  add: { bg: 'rgba(82,196,26,0.18)', mark: '+' },
  del: { bg: 'rgba(255,77,79,0.18)', mark: '-' },
  ctx: { bg: 'transparent', mark: ' ' },
};
const mono: React.CSSProperties = {
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
  fontSize: 12.5,
  lineHeight: '20px',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
};

/** 统一差异文本 → 统一 / 并排视图 */
export default function DiffView({ diff, empty, defaultMode = 'unified', fixedMode }: {
  diff: string | null | undefined;
  empty?: string;
  defaultMode?: 'unified' | 'split';
  fixedMode?: 'unified' | 'split';
}) {
  const [mode, setMode] = useState<'unified' | 'split'>(defaultMode);
  const rows = useMemo(() => parseUnified(diff || ''), [diff]);
  const split = useMemo(() => toSplit(rows), [rows]);
  const m = fixedMode ?? mode;
  if (!rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={empty || t('audit.noDiff')} />;
  return (
    <div data-testid="diff-view">
      {!fixedMode && (
        <Segmented
          size="small"
          style={{ marginBottom: 8 }}
          value={mode}
          onChange={(v) => setMode(v as 'unified' | 'split')}
          options={[{ label: t('rules.viewUnified'), value: 'unified' }, { label: t('rules.viewSplit'), value: 'split' }]}
        />
      )}
      <div style={{ border: '1px solid var(--ant-color-border-secondary, #f0f0f0)', borderRadius: 6, overflow: 'auto', maxHeight: 480 }}>
        {m === 'unified' ? (
          rows.map((r, i) =>
            r.kind === 'hunk' ? (
              <div key={i} style={{ ...mono, padding: '0 8px', opacity: 0.55, background: 'rgba(22,119,255,0.08)' }}>{r.text}</div>
            ) : (
              <div key={i} data-kind={r.kind} style={{ ...mono, padding: '0 8px', background: COLORS[r.kind].bg }}>
                {COLORS[r.kind].mark} {r.text}
              </div>
            ),
          )
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr' }}>
            {split.map((r, i) =>
              r.hunk ? (
                <div key={i} style={{ ...mono, gridColumn: '1 / 3', padding: '0 8px', opacity: 0.55, background: 'rgba(22,119,255,0.08)' }}>{r.hunk}</div>
              ) : (
                <div key={i} style={{ display: 'contents' }}>
                  <div style={{ ...mono, padding: '0 8px', background: r.left ? COLORS[r.left.kind].bg : 'transparent', borderRight: '1px solid rgba(128,128,128,0.2)', minHeight: 20 }}>{r.left?.text}</div>
                  <div style={{ ...mono, padding: '0 8px', background: r.right ? COLORS[r.right.kind].bg : 'transparent', minHeight: 20 }}>{r.right?.text}</div>
                </div>
              ),
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export function CodeBlock({ text, maxHeight = 360 }: { text: string; maxHeight?: number }) {
  return (
    <pre style={{ ...mono, margin: 0, padding: 10, maxHeight, overflow: 'auto', border: '1px solid rgba(128,128,128,0.25)', borderRadius: 6 }}>{text}</pre>
  );
}
