/**
 * requestComposer — pure functions that fold the user's environment,
 * scoped global headers, and active auth scheme into the final RequestSpec
 * the Try-it-out panel should send.
 *
 * No React imports here on purpose: this module is the seam between the
 * three stateful contexts (Environment / Headers / Auth) and the
 * presentational TryItOutPanel. It receives already-resolved data and
 * returns a deterministic RequestSpec, which makes it trivial to unit
 * test and reason about.
 *
 * See docs/features/environment-auth-headers.md §5, §6, §7.2/§7.3, §8 (M5).
 */

import type { RequestSpec } from '@/components/TryItOutPanel';
import type { Environment } from '@/stores/environmentStore';
import type { AuthApplyResult } from '@/stores/authResolver';
import type { HeaderRule } from '@/stores/headersStore';
import type { AuthScheme } from '@/stores/authStore';
import {
  joinBaseUrl,
  resolveDeep,
  resolveVariables,
} from '@/stores/variableResolver';
import { serviceRefIdFor } from '@/stores/serviceRefId';

export interface ContextBadge {
  label: string;
  value: string;
  color?: string;
}

/**
 * Discriminated union of non-fatal issues the composer encountered while
 * folding env / headers / auth into the outbound spec. The TryItOutPanel
 * surfaces these as AntD `<Alert type="warning" />` so the user understands
 * why the request being sent doesn't fully match what their auth scheme
 * intended.
 *
 * Designed to grow — add new `kind` variants as more silent-drop or
 * coercion cases appear (e.g. proxied-url, header-stripped, etc.).
 */
export type ComposerWarning =
  | { kind: 'cookies-dropped'; names: string[] };

export interface ComposeResult {
  spec: RequestSpec;
  warnings: ComposerWarning[];
}

export interface ComposeInput {
  spec: RequestSpec;
  env: Environment | null;
  serviceRefId: string | null;
  /** Output of `useHeaders().resolve({ serviceRefId })`. */
  resolvedHeaders: Record<string, string>;
  /** Output of `await useAuth().apply(serviceRefId)`. */
  authPatch: AuthApplyResult;
}

/**
 * Case-insensitive merge: callers in precedence order (low → high).
 * The final entry for a given header (matched case-insensitively) wins
 * AND keeps the casing of the latest setter, mirroring how
 * `headersResolver.mergeHeaders` resolves global → service → env.
 */
function mergeHeadersCaseInsensitive(
  ...sources: Array<Record<string, string> | undefined | null>
): Record<string, string> {
  const out = new Map<string, { key: string; value: string }>();
  for (const src of sources) {
    if (!src) continue;
    for (const [k, v] of Object.entries(src)) {
      if (!k) continue;
      out.set(k.toLowerCase(), { key: k, value: v });
    }
  }
  const merged: Record<string, string> = {};
  for (const { key, value } of out.values()) merged[key] = value;
  return merged;
}

/**
 * Compose the final outbound request spec by folding env / global headers /
 * auth into the user-edited spec coming from the panel.
 *
 * Behaviour summary:
 *   - URL: expand {{vars}} from env, then `joinBaseUrl` so a relative
 *     path picks up `env.baseUrl`. Absolute URLs pass through unchanged.
 *   - Headers: precedence resolvedHeaders → spec.headers → authPatch.headers.
 *     Case-insensitive last-write-wins; final casing matches the latest
 *     contributor. Values are variable-expanded after merge (idempotent
 *     for already-expanded values).
 *   - Query: spec.query then authPatch.query. Auth overrides on conflict
 *     because the user explicitly bound this scheme to the service.
 *   - Cookies: dropped with a `cookies-dropped` warning emitted into the
 *     `warnings` array AND a console.warn — browsers refuse to set the
 *     `Cookie` header from JS for cross-origin XHR / fetch.
 *
 * Returns `{ spec, warnings }`. The TryItOutPanel surfaces non-empty
 * warnings as an AntD Alert above the response area so silent drops are
 * visible to the user.
 */
export function composeTryItOutSpec(input: ComposeInput): ComposeResult {
  const { spec, env, resolvedHeaders, authPatch } = input;
  const warnings: ComposerWarning[] = [];
  // Defensive — normalize at this seam so future logic (cookie scoping,
  // per-service proxy hints, etc.) can rely on a stable key. The composer
  // itself doesn't currently branch on it.
  serviceRefIdFor(input.serviceRefId);

  const expandedUrl = resolveVariables(spec.url ?? '', env);
  const url = joinBaseUrl(expandedUrl, env);

  const mergedHeaders = mergeHeadersCaseInsensitive(
    resolvedHeaders,
    spec.headers,
    authPatch.headers,
  );
  const finalHeaders = resolveDeep(mergedHeaders, env);

  const mergedQuery: Record<string, string> = {
    ...(spec.query ?? {}),
    ...(authPatch.query ?? {}),
  };
  const finalQuery = resolveDeep(mergedQuery, env);

  if (authPatch.cookies && Object.keys(authPatch.cookies).length > 0) {
    const names = Object.keys(authPatch.cookies);
    warnings.push({ kind: 'cookies-dropped', names });
    console.warn(
      '[csap-apidoc] Auth scheme produced cookies, but browsers cannot set ' +
        'the Cookie header for cross-origin requests from JavaScript. ' +
        'Skipping: ' +
        names.join(', '),
    );
  }

  const out: RequestSpec = {
    method: spec.method,
    url,
    headers: finalHeaders,
    query: finalQuery,
    body: spec.body,
  };
  return { spec: out, warnings };
}

/**
 * Build the row of pills shown above the Send button so the user can see
 * at a glance which env / how many headers / which auth scheme will be
 * applied to the next request.
 *
 * The env badge is omitted when no environment is active, because that's
 * a legitimate state (e.g. dev-mode same-origin via vite proxy) and a
 * "环境: 未选择" badge would be misleading noise.
 */
export function explainContextBadges(input: {
  env: Environment | null;
  activeHeaderRules: HeaderRule[];
  activeAuth: AuthScheme | null;
}): ContextBadge[] {
  const badges: ContextBadge[] = [];

  if (input.env) {
    badges.push({
      label: '环境',
      value: input.env.name,
      color: input.env.color,
    });
  }

  badges.push({
    label: '请求头',
    value: String(input.activeHeaderRules.length),
  });

  badges.push({
    label: '认证',
    value: input.activeAuth ? input.activeAuth.name : '无',
  });

  return badges;
}
