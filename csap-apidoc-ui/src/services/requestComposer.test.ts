import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { composeTryItOutSpec, explainContextBadges } from './requestComposer';
import type { Environment } from '@/stores/environmentStore';
import type { HeaderRule } from '@/stores/headersStore';
import type { AuthScheme } from '@/stores/authStore';
import type { AuthApplyResult } from '@/stores/authResolver';

const env: Environment = {
  id: 'env_a',
  name: 'staging',
  color: '#f5222d',
  baseUrl: 'https://api.staging.example.com/',
  variables: { tenant: '42' },
};

const emptyAuth: AuthApplyResult = { headers: {}, query: {}, cookies: {} };

describe('composeTryItOutSpec', () => {
  it('joins relative URLs against env.baseUrl after expanding {{vars}}', () => {
    const out = composeTryItOutSpec({
      spec: { method: 'GET', url: '/users/{{tenant}}' },
      env,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: emptyAuth,
    });
    expect(out.url).toBe('https://api.staging.example.com/users/42');
  });

  it('passes absolute URLs through unchanged', () => {
    const out = composeTryItOutSpec({
      spec: { method: 'GET', url: 'https://other.example/x' },
      env,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: emptyAuth,
    });
    expect(out.url).toBe('https://other.example/x');
  });

  it('leaves the URL untouched when no env is active', () => {
    const out = composeTryItOutSpec({
      spec: { method: 'GET', url: '/users' },
      env: null,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: emptyAuth,
    });
    expect(out.url).toBe('/users');
  });

  it('merges headers with case-insensitive last-write-wins (resolved → spec → auth)', () => {
    const out = composeTryItOutSpec({
      spec: {
        method: 'GET',
        url: '/x',
        headers: { 'x-tenant': 'spec-overrides' },
      },
      env: null,
      serviceRefId: null,
      resolvedHeaders: { 'X-Tenant': 'from-resolved' },
      authPatch: { headers: {}, query: {}, cookies: {} },
    });
    expect(out.headers).toEqual({ 'x-tenant': 'spec-overrides' });
  });

  it('auth headers override both spec and resolved headers', () => {
    const out = composeTryItOutSpec({
      spec: {
        method: 'GET',
        url: '/x',
        headers: { Authorization: 'Bearer spec-token' },
      },
      env: null,
      serviceRefId: null,
      resolvedHeaders: { authorization: 'Bearer resolved-token' },
      authPatch: { headers: { Authorization: 'Bearer auth-token' }, query: {}, cookies: {} },
    });
    expect(out.headers).toEqual({ Authorization: 'Bearer auth-token' });
  });

  it('expands {{vars}} inside header and query values via resolveDeep', () => {
    const out = composeTryItOutSpec({
      spec: { method: 'GET', url: '/x', headers: { 'X-T': '{{tenant}}' }, query: { t: '{{tenant}}' } },
      env,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: emptyAuth,
    });
    expect(out.headers).toEqual({ 'X-T': '42' });
    expect(out.query).toEqual({ t: '42' });
  });

  it('merges query params with auth winning on conflict', () => {
    const out = composeTryItOutSpec({
      spec: { method: 'GET', url: '/x', query: { api_key: 'spec' } },
      env: null,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: { headers: {}, query: { api_key: 'auth' }, cookies: {} },
    });
    expect(out.query).toEqual({ api_key: 'auth' });
  });

  it('warns and drops cookies the auth scheme tried to set', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const out = composeTryItOutSpec({
      spec: { method: 'GET', url: '/x' },
      env: null,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: { headers: {}, query: {}, cookies: { session: 'abc' } },
    });
    expect(warn).toHaveBeenCalled();
    // Cookies are not surfaced anywhere on the RequestSpec
    expect((out as any).cookies).toBeUndefined();
    warn.mockRestore();
  });

  it('preserves the body verbatim', () => {
    const out = composeTryItOutSpec({
      spec: { method: 'POST', url: '/x', body: '{"a":1}' },
      env: null,
      serviceRefId: null,
      resolvedHeaders: {},
      authPatch: emptyAuth,
    });
    expect(out.body).toBe('{"a":1}');
  });
});

describe('explainContextBadges', () => {
  let warnSpy: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
  });
  afterEach(() => warnSpy.mockRestore());

  const ruleOf = (over: Partial<HeaderRule>): HeaderRule => ({
    id: 'h_' + Math.random().toString(36).slice(2),
    scope: 'global',
    scopeRefId: null,
    key: 'X',
    value: 'v',
    enabled: true,
    ...over,
  });
  const schemeOf = (name: string): AuthScheme => ({
    id: 's',
    name,
    type: 'bearer',
    config: { tokenRef: '' },
  });

  it('emits an env badge with the env name + colour when an env is active', () => {
    const badges = explainContextBadges({
      env,
      activeHeaderRules: [],
      activeAuth: null,
    });
    expect(badges[0]).toMatchObject({
      label: '环境',
      value: 'staging',
      color: '#f5222d',
    });
  });

  it('omits the env badge when no env is active', () => {
    const badges = explainContextBadges({
      env: null,
      activeHeaderRules: [],
      activeAuth: null,
    });
    expect(badges.find((b) => b.label === '环境')).toBeUndefined();
  });

  it('reports the active header rule count', () => {
    const badges = explainContextBadges({
      env: null,
      activeHeaderRules: [ruleOf({}), ruleOf({}), ruleOf({})],
      activeAuth: null,
    });
    expect(badges.find((b) => b.label === '请求头')?.value).toBe('3');
  });

  it('reports the active auth scheme name when present', () => {
    const badges = explainContextBadges({
      env: null,
      activeHeaderRules: [],
      activeAuth: schemeOf('Dev Bearer'),
    });
    expect(badges.find((b) => b.label === '认证')?.value).toBe('Dev Bearer');
  });

  it('reports "无" when no auth is active', () => {
    const badges = explainContextBadges({
      env: null,
      activeHeaderRules: [],
      activeAuth: null,
    });
    expect(badges.find((b) => b.label === '认证')?.value).toBe('无');
  });
});
