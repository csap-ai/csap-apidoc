// Filesystem-driven i18n coverage test (M8.2).
//
// `i18n.test.ts` already verifies that zh-CN.json and en-US.json have the
// same set of keys. This test goes one step further: it scans every TS/TSX
// source file under src/ for literal `t('foo.bar')` calls and asserts that
// each referenced key actually exists in BOTH locale files. That way, a PR
// that adds `<Button>{t('new.key')}</Button>` without updating the bundles
// fails CI immediately instead of silently rendering the raw key at
// runtime.
//
// Dynamic call sites — `t(`auth.type.${scheme}`)` — are deliberately
// allow-listed below by their literal prefix. Whenever code starts using a
// new dynamic prefix, add it to DYNAMIC_PREFIXES and ensure the locale
// files contain at least one key under it (the test enforces that too).

import fs from 'node:fs';
import path from 'node:path';
import { describe, it, expect } from 'vitest';

import zhCN from './locales/zh-CN.json';
import enUS from './locales/en-US.json';

const SRC_ROOT = path.resolve(__dirname, '..');

// Vendored / generated bundles must never be scanned: they contain
// minified `t(...)` calls that have nothing to do with i18next.
const SKIP_PATH_FRAGMENTS = ['static/assets', 'node_modules', '__generated__'];

/**
 * Dynamic key prefixes — code resolves the suffix at runtime via template
 * literals or lookup tables. Listing them here serves two purposes:
 *   1. The literal-key scan ignores them (they aren't string literals).
 *   2. We assert the locale files contain at least one key under each
 *      prefix, so a typo in the prefix itself still surfaces.
 */
const DYNAMIC_PREFIXES = [
  'auth.type.',
  'headers.add.',
  'headers.empty.',
  'layout.params.type.',
  'mpModal.title.',
  'mpModal.ok.',
] as const;

function walk(dir: string, acc: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (SKIP_PATH_FRAGMENTS.some((frag) => full.includes(frag))) continue;
    if (entry.isDirectory()) {
      walk(full, acc);
    } else if (/\.(ts|tsx)$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) {
      acc.push(full);
    }
  }
  return acc;
}

// `[^A-Za-z_]` guards against matching variable names like `mt('foo')`
// inside minified bundles. Both quote styles supported.
const LITERAL_KEY_RE = /[^A-Za-z_]t\(\s*['"]([a-zA-Z0-9_.]+)['"]/g;

function collectLiteralKeys(): { key: string; file: string; line: number }[] {
  const out: { key: string; file: string; line: number }[] = [];
  for (const file of walk(SRC_ROOT)) {
    const text = fs.readFileSync(file, 'utf-8');
    const lines = text.split('\n');
    lines.forEach((line, idx) => {
      LITERAL_KEY_RE.lastIndex = 0;
      let match: RegExpExecArray | null;
      while ((match = LITERAL_KEY_RE.exec(line)) !== null) {
        out.push({ key: match[1], file: path.relative(SRC_ROOT, file), line: idx + 1 });
      }
    });
  }
  return out;
}

describe('i18n coverage (M8.2)', () => {
  const zhKeys = new Set<string>(Object.keys(zhCN));
  const enKeys = new Set<string>(Object.keys(enUS));

  it('every literal t() key in src/ exists in both zh-CN and en-US', () => {
    const refs = collectLiteralKeys();
    expect(refs.length, 'no t() keys found — scanner regression?').toBeGreaterThan(0);

    const missing: string[] = [];
    for (const ref of refs) {
      if (!zhKeys.has(ref.key) || !enKeys.has(ref.key)) {
        missing.push(`${ref.file}:${ref.line} -> "${ref.key}"`);
      }
    }
    expect(
      missing,
      `missing keys (add to BOTH zh-CN.json and en-US.json):\n  ${missing.join('\n  ')}`,
    ).toEqual([]);
  });

  it.each(DYNAMIC_PREFIXES)(
    'dynamic prefix %s has at least one matching key in both locales',
    (prefix) => {
      const zhMatches = [...zhKeys].filter((k) => k.startsWith(prefix));
      const enMatches = [...enKeys].filter((k) => k.startsWith(prefix));
      expect(
        zhMatches.length,
        `zh-CN has no keys under "${prefix}" — drop the prefix or add keys`,
      ).toBeGreaterThan(0);
      expect(
        enMatches.length,
        `en-US has no keys under "${prefix}" — drop the prefix or add keys`,
      ).toBeGreaterThan(0);
      // Suffix parity is already covered by i18n.test.ts's flat key set
      // diff, so we deliberately don't re-check it here.
    },
  );

  // M8.3 — forbid dead keys.
  //
  // After M8.3 pruned 19 truly-orphaned keys (superseded .failed/.failedRetry
  // pairs, autonym lang.* keys that LanguageSwitcher hard-codes, and a
  // never-wired common.* utility bag), the locale files only ship keys that
  // either (a) appear as a literal `t('foo.bar')` call in src/, or (b) sit
  // under one of DYNAMIC_PREFIXES. This test locks that invariant in.
  //
  // If a new key legitimately can't be detected by this scanner (e.g., it's
  // referenced from outside csap-apidoc-ui), add its prefix to
  // DYNAMIC_PREFIXES above rather than grandfathering a bare key here.
  it('every locale key is either directly referenced or under a known dynamic prefix', () => {
    const refs = collectLiteralKeys();
    const referenced = new Set(refs.map((r) => r.key));

    const orphans: string[] = [];
    for (const key of zhKeys) {
      if (referenced.has(key)) continue;
      if (DYNAMIC_PREFIXES.some((p) => key.startsWith(p))) continue;
      orphans.push(key);
    }

    expect(
      orphans,
      'orphaned locale keys (no t() call found in src/, not under a dynamic prefix):\n  ' +
        orphans.join('\n  '),
    ).toEqual([]);
  });
});
