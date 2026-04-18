/**
 * authResolver — pure(ish) function that converts an AuthScheme + active env
 * + vault contents into an "auth patch" that the request builder (M5) can
 * merge into the outgoing request.
 *
 * Patch shape:
 *
 *   {
 *     headers: { Authorization: "Bearer …", … },
 *     query:   { api_key: "…" },
 *     cookies: { session: "…" },
 *   }
 *
 * The four scheme types map cleanly:
 *
 *   bearer        → headers.Authorization = "Bearer <token>"
 *   basic         → headers.Authorization = "Basic <base64(user:pwd)>"
 *   apikey        → one of headers/query/cookies based on `in`
 *   oauth2_client → fetch token (cached until expiry) → bearer treatment
 *
 * For oauth2_client the resolver mutates the scheme (cachedTokenRef +
 * cachedExpiresAt) via the supplied `onCacheUpdate` callback so the cache
 * persists across requests. This is the only side-effect inside the
 * resolver; everything else is pure.
 */

import { resolveVariables } from './variableResolver';
import type { Environment } from './environmentStore';
import type {
  ApiKeyConfig,
  AuthScheme,
  AuthSchemeConfig,
  BasicConfig,
  BearerConfig,
  OAuth2ClientConfig,
} from './authStore';
import { vault } from './vault';

export interface AuthApplyResult {
  headers: Record<string, string>;
  query: Record<string, string>;
  cookies: Record<string, string>;
}

const EMPTY_RESULT: AuthApplyResult = Object.freeze({
  headers: {},
  query: {},
  cookies: {},
}) as AuthApplyResult;

export interface ApplyAuthOptions {
  env?: Environment | null;
  /** Optional fetch implementation; defaults to global fetch. */
  fetcher?: typeof fetch;
  /**
   * Called when the OAuth2-CC cache is mutated (token fetched / refreshed).
   * Receives the patch to apply to scheme.config; the AuthContext layer
   * persists it via authStore.update.
   */
  onCacheUpdate?: (
    schemeId: string,
    cachePatch: Partial<OAuth2ClientConfig>,
  ) => void;
  /** Treat cached tokens whose expiry is within this many ms as stale. */
  refreshSkewMs?: number;
}

/**
 * Pick the active config for a scheme, honouring per-env overrides.
 */
function pickActiveConfig(
  scheme: AuthScheme,
  env: Environment | null | undefined,
): AuthSchemeConfig {
  if (env && scheme.envBindings && scheme.envBindings[env.id]) {
    return scheme.envBindings[env.id];
  }
  return scheme.config;
}

function readSecret(
  ref: string | undefined | null,
  env: Environment | null | undefined,
): string {
  if (!ref) return '';
  // Vault refs go through the vault module; literal values pass through with
  // {{var}} expansion so a power-user can reference an env variable directly.
  const v = vault.isVaultRef(ref) ? vault.get(ref) ?? '' : ref;
  return resolveVariables(v, env ?? null);
}

function readPlain(
  value: string | undefined | null,
  env: Environment | null | undefined,
): string {
  if (!value) return '';
  return resolveVariables(value, env ?? null);
}

function base64(input: string): string {
  if (typeof window !== 'undefined' && typeof window.btoa === 'function') {
    return window.btoa(unescape(encodeURIComponent(input)));
  }
  // Fallback for non-browser test environments.
  return Buffer.from(input, 'utf-8').toString('base64');
}

export function applyBearer(
  cfg: BearerConfig,
  env: Environment | null | undefined,
): AuthApplyResult {
  const token = readSecret(cfg.tokenRef, env);
  if (!token) return { ...EMPTY_RESULT };
  return {
    headers: { Authorization: `Bearer ${token}` },
    query: {},
    cookies: {},
  };
}

export function applyBasic(
  cfg: BasicConfig,
  env: Environment | null | undefined,
): AuthApplyResult {
  const user = readPlain(cfg.username, env);
  const pwd = readSecret(cfg.passwordRef, env);
  if (!user && !pwd) return { ...EMPTY_RESULT };
  return {
    headers: { Authorization: `Basic ${base64(`${user}:${pwd}`)}` },
    query: {},
    cookies: {},
  };
}

export function applyApiKey(
  cfg: ApiKeyConfig,
  env: Environment | null | undefined,
): AuthApplyResult {
  const name = readPlain(cfg.name, env);
  const value = readSecret(cfg.valueRef, env);
  if (!name || !value) return { ...EMPTY_RESULT };
  switch (cfg.in) {
    case 'header':
      return { headers: { [name]: value }, query: {}, cookies: {} };
    case 'query':
      return { headers: {}, query: { [name]: value }, cookies: {} };
    case 'cookie':
      return { headers: {}, query: {}, cookies: { [name]: value } };
  }
}

interface OAuth2TokenResponse {
  access_token?: string;
  expires_in?: number;
  token_type?: string;
}

export async function fetchOAuth2ClientToken(
  cfg: OAuth2ClientConfig,
  env: Environment | null | undefined,
  fetcher: typeof fetch = fetch,
): Promise<{ accessToken: string; expiresInMs: number }> {
  const tokenUrl = readPlain(cfg.tokenUrl, env);
  const clientId = readPlain(cfg.clientId, env);
  const clientSecret = readSecret(cfg.clientSecretRef, env);
  if (!tokenUrl || !clientId) {
    throw new Error('OAuth2 client_credentials: tokenUrl and clientId are required');
  }
  const body = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: clientId,
    client_secret: clientSecret,
  });
  if (cfg.scope) body.set('scope', readPlain(cfg.scope, env));
  const res = await fetcher(tokenUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Accept: 'application/json',
    },
    body: body.toString(),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(
      `OAuth2 token endpoint returned HTTP ${res.status}${text ? `: ${text}` : ''}`,
    );
  }
  const data = (await res.json()) as OAuth2TokenResponse;
  if (!data.access_token) {
    throw new Error('OAuth2 token response missing access_token');
  }
  const expiresInMs =
    typeof data.expires_in === 'number' && data.expires_in > 0
      ? data.expires_in * 1000
      : 60 * 60 * 1000;
  return { accessToken: data.access_token, expiresInMs };
}

export async function applyOAuth2Client(
  scheme: AuthScheme,
  cfg: OAuth2ClientConfig,
  options: ApplyAuthOptions,
): Promise<AuthApplyResult> {
  const refreshSkew = options.refreshSkewMs ?? 30_000;
  const now = Date.now();

  const cached =
    cfg.cachedTokenRef && cfg.cachedExpiresAt
      ? { token: vault.get(cfg.cachedTokenRef), exp: cfg.cachedExpiresAt }
      : null;

  if (cached?.token && cached.exp - refreshSkew > now) {
    return {
      headers: { Authorization: `Bearer ${cached.token}` },
      query: {},
      cookies: {},
    };
  }

  const fetched = await fetchOAuth2ClientToken(
    cfg,
    options.env,
    options.fetcher,
  );
  const newRef = vault.put(fetched.accessToken, cfg.cachedTokenRef ?? null);
  const newExp = now + fetched.expiresInMs;
  options.onCacheUpdate?.(scheme.id, {
    cachedTokenRef: newRef,
    cachedExpiresAt: newExp,
  });
  return {
    headers: { Authorization: `Bearer ${fetched.accessToken}` },
    query: {},
    cookies: {},
  };
}

/**
 * Top-level entry. Returns an empty patch if the scheme is null or its
 * required fields are blank (e.g. a half-edited form). Throws only for
 * the OAuth2 token-endpoint failure case — callers should display that
 * to the user rather than silently dropping auth.
 */
export async function applyAuth(
  scheme: AuthScheme | null | undefined,
  options: ApplyAuthOptions = {},
): Promise<AuthApplyResult> {
  if (!scheme) return { ...EMPTY_RESULT };
  const cfg = pickActiveConfig(scheme, options.env);
  switch (scheme.type) {
    case 'bearer':
      return applyBearer(cfg as BearerConfig, options.env);
    case 'basic':
      return applyBasic(cfg as BasicConfig, options.env);
    case 'apikey':
      return applyApiKey(cfg as ApiKeyConfig, options.env);
    case 'oauth2_client':
      return applyOAuth2Client(scheme, cfg as OAuth2ClientConfig, options);
  }
}

/**
 * Synchronous variant — returns whatever can be computed without hitting
 * the network. OAuth2 schemes return their cached token if any, else an
 * empty patch. Used by the badge / preview UI where we can't await.
 */
export function applyAuthSync(
  scheme: AuthScheme | null | undefined,
  options: Omit<ApplyAuthOptions, 'fetcher' | 'onCacheUpdate'> = {},
): AuthApplyResult {
  if (!scheme) return { ...EMPTY_RESULT };
  const cfg = pickActiveConfig(scheme, options.env);
  switch (scheme.type) {
    case 'bearer':
      return applyBearer(cfg as BearerConfig, options.env);
    case 'basic':
      return applyBasic(cfg as BasicConfig, options.env);
    case 'apikey':
      return applyApiKey(cfg as ApiKeyConfig, options.env);
    case 'oauth2_client': {
      const o = cfg as OAuth2ClientConfig;
      const cachedTok = o.cachedTokenRef ? vault.get(o.cachedTokenRef) : null;
      if (
        cachedTok &&
        o.cachedExpiresAt &&
        o.cachedExpiresAt - 30_000 > Date.now()
      ) {
        return {
          headers: { Authorization: `Bearer ${cachedTok}` },
          query: {},
          cookies: {},
        };
      }
      return { ...EMPTY_RESULT };
    }
  }
}
