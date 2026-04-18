import { describe, it, expect } from 'vitest';
import { mergeHeaders, explainActiveRules } from './headersResolver';
import type { HeaderRule } from './headersStore';
import type { Environment } from './environmentStore';

const rule = (over: Partial<HeaderRule>): HeaderRule => ({
  id: over.id ?? Math.random().toString(36).slice(2),
  scope: 'global',
  scopeRefId: null,
  key: 'X-Test',
  value: 'v',
  enabled: true,
  ...over,
});

const env: Environment = {
  id: 'env_a',
  name: 'dev',
  color: '#000',
  baseUrl: 'https://api',
  variables: { tenant: '42' },
};

describe('mergeHeaders precedence', () => {
  it('returns an empty map when there are no rules and no endpoint headers', () => {
    expect(mergeHeaders({ rules: [] })).toEqual({});
  });

  it('applies global rules', () => {
    const rules = [rule({ key: 'X-Tenant', value: 'global' })];
    expect(mergeHeaders({ rules })).toEqual({ 'X-Tenant': 'global' });
  });

  it('service rules override global rules on the same key (case-insensitive)', () => {
    const rules = [
      rule({ key: 'X-Tenant', value: 'from-global', scope: 'global' }),
      rule({
        key: 'x-tenant',
        value: 'from-service',
        scope: 'service',
        scopeRefId: 'svc-1',
      }),
    ];
    const out = mergeHeaders({ rules, serviceRefId: 'svc-1' });
    expect(out).toEqual({ 'x-tenant': 'from-service' });
  });

  it('environment rules override service rules', () => {
    const rules = [
      rule({ key: 'X', value: 'global', scope: 'global' }),
      rule({ key: 'X', value: 'service', scope: 'service', scopeRefId: 'svc' }),
      rule({
        key: 'X',
        value: 'env',
        scope: 'environment',
        scopeRefId: 'env_a',
      }),
    ];
    expect(
      mergeHeaders({ rules, serviceRefId: 'svc', envId: 'env_a' }),
    ).toEqual({ X: 'env' });
  });

  it('endpoint headers win over every scope', () => {
    const rules = [
      rule({ key: 'X', value: 'global' }),
      rule({ key: 'X', value: 'env', scope: 'environment', scopeRefId: 'env_a' }),
    ];
    expect(
      mergeHeaders({
        rules,
        envId: 'env_a',
        endpointHeaders: { x: 'endpoint' },
      }),
    ).toEqual({ x: 'endpoint' });
  });

  it('skips disabled rules', () => {
    const rules = [
      rule({ key: 'X-On', value: 'on', enabled: true }),
      rule({ key: 'X-Off', value: 'off', enabled: false }),
    ];
    expect(mergeHeaders({ rules })).toEqual({ 'X-On': 'on' });
  });

  it('does not apply service rules when serviceRefId does not match', () => {
    const rules = [
      rule({ key: 'X', value: 'svc', scope: 'service', scopeRefId: 'other' }),
    ];
    expect(mergeHeaders({ rules, serviceRefId: 'svc-1' })).toEqual({});
  });

  it('does not apply env rules when envId is null', () => {
    const rules = [
      rule({ key: 'X', value: 'env', scope: 'environment', scopeRefId: 'env_a' }),
    ];
    expect(mergeHeaders({ rules })).toEqual({});
  });

  it('expands {{vars}} in header values using the active environment', () => {
    const rules = [rule({ key: 'X-Tenant', value: '{{tenant}}' })];
    expect(mergeHeaders({ rules, env })).toEqual({ 'X-Tenant': '42' });
  });

  it('skips rules with empty keys', () => {
    const rules = [rule({ key: '', value: 'orphan' })];
    expect(mergeHeaders({ rules })).toEqual({});
  });
});

describe('explainActiveRules', () => {
  it('returns only the rules that would actually take effect, in application order', () => {
    const rules: HeaderRule[] = [
      rule({ id: 'g', scope: 'global', enabled: true }),
      rule({ id: 'g_off', scope: 'global', enabled: false }),
      rule({ id: 's_match', scope: 'service', scopeRefId: 'svc-1' }),
      rule({ id: 's_other', scope: 'service', scopeRefId: 'svc-X' }),
      rule({ id: 'e_match', scope: 'environment', scopeRefId: 'env_a' }),
      rule({ id: 'e_other', scope: 'environment', scopeRefId: 'env_z' }),
    ];
    const out = explainActiveRules({
      rules,
      serviceRefId: 'svc-1',
      envId: 'env_a',
    });
    expect(out.map((r) => r.id)).toEqual(['g', 's_match', 'e_match']);
  });
});
