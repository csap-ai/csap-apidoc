// @vitest-environment jsdom

import { describe, it, expect, beforeEach } from 'vitest';
import i18n from './index';
import zhCN from './locales/zh-CN.json';
import enUS from './locales/en-US.json';

beforeEach(async () => {
  window.localStorage.clear();
  // Reset to default zh-CN before every test so order doesn't matter.
  await i18n.changeLanguage('zh-CN');
});

describe('i18n bootstrap (M8.1)', () => {
  it('defaults to zh-CN to keep Playwright E2E and existing UI compatible', async () => {
    // Simulate a fresh load: detector reads localStorage which we cleared,
    // and falls back to zh-CN per init config.
    await i18n.changeLanguage(undefined as unknown as string);
    const lng = i18n.resolvedLanguage ?? i18n.language;
    expect(lng === 'zh-CN' || lng === 'cimode').toBe(true);
    expect(i18n.t('header.export.button')).toBe('导出文档');
  });

  it('round-trips locale switches without losing keys', async () => {
    expect(i18n.t('common.save')).toBe('保存');
    await i18n.changeLanguage('en-US');
    expect(i18n.t('common.save')).toBe('Save');
    await i18n.changeLanguage('zh-CN');
    expect(i18n.t('common.save')).toBe('保存');
  });

  it('keeps zh-CN and en-US locale files in lockstep (no missing keys)', () => {
    const zhKeys = new Set(Object.keys(zhCN));
    const enKeys = new Set(Object.keys(enUS));
    const onlyInZh: string[] = [];
    const onlyInEn: string[] = [];
    zhKeys.forEach((k) => {
      if (!enKeys.has(k)) onlyInZh.push(k);
    });
    enKeys.forEach((k) => {
      if (!zhKeys.has(k)) onlyInEn.push(k);
    });
    expect(onlyInZh, `keys missing in en-US: ${onlyInZh.join(', ')}`).toEqual([]);
    expect(onlyInEn, `keys missing in zh-CN: ${onlyInEn.join(', ')}`).toEqual([]);
  });

  it('uses <<var>> interpolation (does NOT consume literal {{var}})', async () => {
    // Custom delimiters are critical: the UI hosts literal {{var}} strings
    // (env vars, header tokens) that must pass through translations as-is.
    await i18n.changeLanguage('en-US');
    expect(i18n.t('headers.row.value.placeholder')).toBe('e.g. 42 or {{tenantId}}');
    expect(i18n.t('layout.req.success', { status: 200 })).toBe(
      'Request succeeded (HTTP 200)',
    );
  });

  it('persists language choice to localStorage under csap-apidoc:locale', async () => {
    await i18n.changeLanguage('en-US');
    expect(window.localStorage.getItem('csap-apidoc:locale')).toBe('en-US');
    await i18n.changeLanguage('zh-CN');
    expect(window.localStorage.getItem('csap-apidoc:locale')).toBe('zh-CN');
  });
});
