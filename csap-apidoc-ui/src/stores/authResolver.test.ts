// @vitest-environment jsdom

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  applyApiKey,
  applyAuth,
  applyAuthSync,
  applyBasic,
  applyBearer,
  fetchOAuth2ClientToken,
} from './authResolver';
import type {
  ApiKeyConfig,
  AuthScheme,
  BasicConfig,
  BearerConfig,
  OAuth2ClientConfig,
} from './authStore';
import type { Environment } from './environmentStore';
import { vault } from './vault';
import { PlaintextDriver } from './vaultDriver';

const env: Environment = {
  id: 'env_a',
  name: 'dev',
  color: '#000',
  baseUrl: 'https://api.dev',
  variables: { tenant: 't42', who: 'alice' },
};

beforeEach(() => {
  window.localStorage.clear();
  vault.__installDriver(new PlaintextDriver({}));
  vault.reset();
});

/**
 * Build a minimal Response-shaped duck for the OAuth2 fetcher.
 * `fetchOAuth2ClientToken` only reads .ok, .status, .json(), .text().
 */
function fakeResponse(
  body: unknown,
  init: { ok?: boolean; status?: number } = {},
): Response {
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    json: async () => body,
    text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
  } as unknown as Response;
}

/* ------------------------------------------------------------------ */
/* applyBearer / applyBasic / applyApiKey (covered via applyAuthSync)  */
/* ------------------------------------------------------------------ */

describe('applyBearer', () => {
  it('returns a Bearer header from a vault-stored token', () => {
    const ref = vault.put('the-token');
    const cfg: BearerConfig = { tokenRef: ref };
    expect(applyBearer(cfg, env)).toEqual({
      headers: { Authorization: 'Bearer the-token' },
      query: {},
      cookies: {},
    });
  });

  it('returns a Bearer header from a literal value (no vault prefix)', () => {
    const cfg: BearerConfig = { tokenRef: 'literal-token' };
    expect(applyBearer(cfg, env).headers).toEqual({
      Authorization: 'Bearer literal-token',
    });
  });

  it('expands {{vars}} inside literal token values', () => {
    const cfg: BearerConfig = { tokenRef: 'tok-{{tenant}}' };
    expect(applyBearer(cfg, env).headers.Authorization).toBe('Bearer tok-t42');
  });

  it('returns an empty patch when token is blank', () => {
    expect(applyBearer({ tokenRef: '' }, env)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
  });
});

describe('applyBasic', () => {
  it('emits Basic header with base64(user:pwd) for both fields', () => {
    const ref = vault.put('hunter2');
    const cfg: BasicConfig = { username: 'alice', passwordRef: ref };
    const out = applyBasic(cfg, env);
    expect(out.headers.Authorization).toBe(
      'Basic ' +
        Buffer.from('alice:hunter2', 'utf-8').toString('base64'),
    );
  });

  it('still emits a header when only the password is set', () => {
    const cfg: BasicConfig = { username: '', passwordRef: 'pw' };
    expect(applyBasic(cfg, env).headers.Authorization).toMatch(/^Basic /);
  });

  it('returns empty when both username and password are blank', () => {
    expect(applyBasic({ username: '', passwordRef: '' }, env)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
  });
});

describe('applyApiKey', () => {
  const each = ['header', 'query', 'cookie'] as const;
  each.forEach((loc) => {
    it(`places the api key in the ${loc}`, () => {
      const cfg: ApiKeyConfig = {
        in: loc,
        name: 'X-Api-Key',
        valueRef: 'val',
      };
      const out = applyApiKey(cfg, env);
      const bucket =
        loc === 'header' ? out.headers : loc === 'query' ? out.query : out.cookies;
      expect(bucket['X-Api-Key']).toBe('val');
    });
  });

  it('returns empty when name or value is missing', () => {
    expect(applyApiKey({ in: 'header', name: '', valueRef: 'x' }, env)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
    expect(applyApiKey({ in: 'header', name: 'k', valueRef: '' }, env)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
  });

  it('expands {{vars}} in the value', () => {
    const cfg: ApiKeyConfig = {
      in: 'header',
      name: 'X-Tenant',
      valueRef: '{{tenant}}',
    };
    expect(applyApiKey(cfg, env).headers).toEqual({ 'X-Tenant': 't42' });
  });
});

/* ------------------------------------------------------------------ */
/* applyAuthSync (top-level dispatcher + envBindings + cached oauth2)  */
/* ------------------------------------------------------------------ */

describe('applyAuthSync', () => {
  it('returns an empty patch for null/undefined scheme', () => {
    expect(applyAuthSync(null)).toEqual({ headers: {}, query: {}, cookies: {} });
    expect(applyAuthSync(undefined)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
  });

  it('dispatches to bearer/basic/apikey by scheme.type', () => {
    const bearer: AuthScheme = {
      id: 's',
      name: '',
      type: 'bearer',
      config: { tokenRef: 'tok' },
    };
    expect(applyAuthSync(bearer).headers.Authorization).toBe('Bearer tok');

    const basic: AuthScheme = {
      id: 's',
      name: '',
      type: 'basic',
      config: { username: 'u', passwordRef: 'p' },
    };
    expect(applyAuthSync(basic).headers.Authorization).toMatch(/^Basic /);

    const ak: AuthScheme = {
      id: 's',
      name: '',
      type: 'apikey',
      config: { in: 'query', name: 'k', valueRef: 'v' },
    };
    expect(applyAuthSync(ak).query).toEqual({ k: 'v' });
  });

  it('prefers envBindings[env.id] over the default config', () => {
    const scheme: AuthScheme = {
      id: 's',
      name: '',
      type: 'bearer',
      config: { tokenRef: 'default-tok' },
      envBindings: {
        env_a: { tokenRef: 'env-a-tok' } as BearerConfig,
      },
    };
    expect(applyAuthSync(scheme, { env }).headers.Authorization).toBe(
      'Bearer env-a-tok',
    );
    expect(applyAuthSync(scheme, { env: null }).headers.Authorization).toBe(
      'Bearer default-tok',
    );
  });

  it('oauth2_client returns cached token when not yet within refresh skew', () => {
    const ref = vault.put('cached-tok');
    const cfg: OAuth2ClientConfig = {
      tokenUrl: 'http://x',
      clientId: 'c',
      clientSecretRef: 's',
      cachedTokenRef: ref,
      cachedExpiresAt: Date.now() + 5 * 60_000,
    };
    const scheme: AuthScheme = {
      id: 's',
      name: '',
      type: 'oauth2_client',
      config: cfg,
    };
    expect(applyAuthSync(scheme).headers.Authorization).toBe('Bearer cached-tok');
  });

  it('oauth2_client returns empty patch when cached token is missing or expired', () => {
    const expired: OAuth2ClientConfig = {
      tokenUrl: 'http://x',
      clientId: 'c',
      clientSecretRef: 's',
      cachedTokenRef: vault.put('old'),
      cachedExpiresAt: Date.now() - 1000,
    };
    const schemeExpired: AuthScheme = {
      id: 's',
      name: '',
      type: 'oauth2_client',
      config: expired,
    };
    expect(applyAuthSync(schemeExpired)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });

    const noCache: AuthScheme = {
      id: 's',
      name: '',
      type: 'oauth2_client',
      config: {
        tokenUrl: 'http://x',
        clientId: 'c',
        clientSecretRef: 's',
      },
    };
    expect(applyAuthSync(noCache)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
  });
});

/* ------------------------------------------------------------------ */
/* fetchOAuth2ClientToken — direct HTTP-shape tests                    */
/* ------------------------------------------------------------------ */

describe('fetchOAuth2ClientToken', () => {
  it('POSTs urlencoded grant_type=client_credentials with the configured fields', async () => {
    let captured: { url: string; init: RequestInit } | null = null;
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
      captured = { url, init: init ?? {} };
      return fakeResponse({ access_token: 'AT', expires_in: 60 });
    }) as unknown as typeof fetch;
    const cfg: OAuth2ClientConfig = {
      tokenUrl: 'https://idp.example/token',
      clientId: 'cid',
      clientSecretRef: 'sec',
      scope: 'read write',
    };
    const out = await fetchOAuth2ClientToken(cfg, env, fetcher);
    expect(out.accessToken).toBe('AT');
    expect(out.expiresInMs).toBe(60_000);
    expect(captured!.url).toBe('https://idp.example/token');
    expect((captured!.init.headers as any)['Content-Type']).toBe(
      'application/x-www-form-urlencoded',
    );
    const body = String(captured!.init.body);
    expect(body).toContain('grant_type=client_credentials');
    expect(body).toContain('client_id=cid');
    expect(body).toContain('client_secret=sec');
    expect(body).toContain('scope=read+write');
  });

  it('expands {{vars}} in tokenUrl / clientId / scope', async () => {
    let captured: { url: string; init: RequestInit } | null = null;
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
      captured = { url, init: init ?? {} };
      return fakeResponse({ access_token: 'AT' });
    }) as unknown as typeof fetch;
    const cfg: OAuth2ClientConfig = {
      tokenUrl: '{{baseUrl}}/oauth/token',
      clientId: 'client-{{tenant}}',
      clientSecretRef: 'sec',
      scope: 'org-{{tenant}}',
    };
    await fetchOAuth2ClientToken(cfg, env, fetcher);
    expect(captured!.url).toBe('https://api.dev/oauth/token');
    const body = String(captured!.init.body);
    expect(body).toContain('client_id=client-t42');
    expect(body).toContain('scope=org-t42');
  });

  it('falls back to a 1-hour default when the response omits expires_in', async () => {
    const fetcher = vi.fn(async () =>
      fakeResponse({ access_token: 'AT' }),
    ) as unknown as typeof fetch;
    const out = await fetchOAuth2ClientToken(
      { tokenUrl: 'http://x', clientId: 'c', clientSecretRef: '' },
      env,
      fetcher,
    );
    expect(out.expiresInMs).toBe(60 * 60 * 1000);
  });

  it('throws when tokenUrl or clientId are missing', async () => {
    const fetcher = vi.fn(async () => fakeResponse({})) as unknown as typeof fetch;
    await expect(
      fetchOAuth2ClientToken(
        { tokenUrl: '', clientId: 'c', clientSecretRef: '' },
        env,
        fetcher,
      ),
    ).rejects.toThrow(/tokenUrl and clientId/);
  });

  it('throws on non-2xx response, preserving the body in the error', async () => {
    const fetcher = vi.fn(async () =>
      fakeResponse('forbidden', { ok: false, status: 403 }),
    ) as unknown as typeof fetch;
    await expect(
      fetchOAuth2ClientToken(
        { tokenUrl: 'http://x', clientId: 'c', clientSecretRef: '' },
        env,
        fetcher,
      ),
    ).rejects.toThrow(/HTTP 403/);
  });

  it('throws when the response body lacks access_token', async () => {
    const fetcher = vi.fn(async () =>
      fakeResponse({ token_type: 'Bearer' }),
    ) as unknown as typeof fetch;
    await expect(
      fetchOAuth2ClientToken(
        { tokenUrl: 'http://x', clientId: 'c', clientSecretRef: '' },
        env,
        fetcher,
      ),
    ).rejects.toThrow(/missing access_token/);
  });
});

/* ------------------------------------------------------------------ */
/* applyAuth (async dispatcher + OAuth2 cache lifecycle)               */
/* ------------------------------------------------------------------ */

describe('applyAuth — OAuth2 client_credentials cache lifecycle', () => {
  it('skips the network when a cached token is still well within expiry', async () => {
    const ref = vault.put('cached');
    const scheme: AuthScheme = {
      id: 's_cached',
      name: '',
      type: 'oauth2_client',
      config: {
        tokenUrl: 'http://x',
        clientId: 'c',
        clientSecretRef: '',
        cachedTokenRef: ref,
        cachedExpiresAt: Date.now() + 10 * 60_000,
      },
    };
    const fetcher = vi.fn() as unknown as typeof fetch;
    const onCacheUpdate = vi.fn();
    const out = await applyAuth(scheme, { fetcher, onCacheUpdate });
    expect(fetcher).not.toHaveBeenCalled();
    expect(onCacheUpdate).not.toHaveBeenCalled();
    expect(out.headers.Authorization).toBe('Bearer cached');
  });

  it('refreshes when the cached token is within the refreshSkewMs window', async () => {
    const ref = vault.put('about-to-expire');
    const scheme: AuthScheme = {
      id: 's_skew',
      name: '',
      type: 'oauth2_client',
      config: {
        tokenUrl: 'http://x',
        clientId: 'c',
        clientSecretRef: '',
        cachedTokenRef: ref,
        // expires in 5s, but skew=10s → considered stale
        cachedExpiresAt: Date.now() + 5_000,
      },
    };
    const fetcher = vi.fn(async () =>
      fakeResponse({ access_token: 'fresh', expires_in: 120 }),
    ) as unknown as typeof fetch;
    const onCacheUpdate = vi.fn();
    const out = await applyAuth(scheme, {
      fetcher,
      onCacheUpdate,
      refreshSkewMs: 10_000,
    });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(out.headers.Authorization).toBe('Bearer fresh');
    expect(onCacheUpdate).toHaveBeenCalledTimes(1);
    const [schemeId, patch] = onCacheUpdate.mock.calls[0];
    expect(schemeId).toBe('s_skew');
    expect(patch.cachedTokenRef).toBeTruthy();
    expect(typeof patch.cachedExpiresAt).toBe('number');
    expect(patch.cachedExpiresAt).toBeGreaterThan(Date.now());
  });

  it('reuses the existing vault slot when refreshing (no orphan tokens)', async () => {
    const ref = vault.put('old');
    const scheme: AuthScheme = {
      id: 's_reuse',
      name: '',
      type: 'oauth2_client',
      config: {
        tokenUrl: 'http://x',
        clientId: 'c',
        clientSecretRef: '',
        cachedTokenRef: ref,
        cachedExpiresAt: Date.now() - 1000,
      },
    };
    const fetcher = vi.fn(async () =>
      fakeResponse({ access_token: 'rotated', expires_in: 60 }),
    ) as unknown as typeof fetch;
    const updates: any[] = [];
    await applyAuth(scheme, {
      fetcher,
      onCacheUpdate: (id, patch) => updates.push({ id, patch }),
    });
    expect(updates[0].patch.cachedTokenRef).toBe(ref);
    expect(vault.get(ref)).toBe('rotated');
  });

  it('fetches a new token when no cache exists at all', async () => {
    const scheme: AuthScheme = {
      id: 's_initial',
      name: '',
      type: 'oauth2_client',
      config: {
        tokenUrl: 'http://x',
        clientId: 'c',
        clientSecretRef: '',
      },
    };
    const fetcher = vi.fn(async () =>
      fakeResponse({ access_token: 'first', expires_in: 60 }),
    ) as unknown as typeof fetch;
    const onCacheUpdate = vi.fn();
    const out = await applyAuth(scheme, { fetcher, onCacheUpdate });
    expect(out.headers.Authorization).toBe('Bearer first');
    expect(onCacheUpdate).toHaveBeenCalledTimes(1);
    expect(onCacheUpdate.mock.calls[0][1].cachedTokenRef).toBeTruthy();
  });

  it('returns an empty patch for null scheme', async () => {
    expect(await applyAuth(null)).toEqual({
      headers: {},
      query: {},
      cookies: {},
    });
  });

  it('dispatches non-oauth2 types to their sync helpers', async () => {
    const bearer: AuthScheme = {
      id: 's',
      name: '',
      type: 'bearer',
      config: { tokenRef: 'tk' },
    };
    expect((await applyAuth(bearer)).headers.Authorization).toBe('Bearer tk');
  });
});
