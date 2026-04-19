/**
 * headersResolver — merge scoped global headers for a given request context.
 *
 * Resolution order (later overrides earlier on the SAME header key,
 * matched case-insensitively):
 *
 *     1. global            (rule.scope === 'global' && enabled)
 *     2. service           (rule.scope === 'service' && rule.scopeRefId === ctx.serviceRefId)
 *     3. environment       (rule.scope === 'environment' && rule.scopeRefId === ctx.envId)
 *     4. endpoint-declared (passed in by caller, e.g. from API doc `headers` array)
 *
 * Variable tokens `{{key}}` in header values are expanded against the active
 * environment (see variableResolver.ts).
 *
 * Output preserves the *last writer's* casing, which matches how most
 * HTTP clients render headers (e.g. axios normalises on the wire, but
 * logs show the caller's chosen casing).
 */

import type { HeaderRule } from './headersStore';
import type { Environment } from './environmentStore';
import { resolveVariables } from './variableResolver';
import { serviceRefIdFor } from './serviceRefId';

export interface HeaderMergeContext {
  rules: HeaderRule[];
  serviceRefId?: string | null;
  envId?: string | null;
  endpointHeaders?: Record<string, string>;
  env?: Environment | null;
}

/**
 * Returns the fully merged, variable-expanded header map for the given
 * request context. Keys are stored in the casing used by the most specific
 * contributor (endpoint > env > service > global).
 */
export function mergeHeaders(ctx: HeaderMergeContext): Record<string, string> {
  const {
    rules = [],
    serviceRefId = null,
    envId = null,
    endpointHeaders = {},
    env = null,
  } = ctx;

  // Case-insensitive storage: canonical lowercase key → { key, value }
  const out = new Map<string, { key: string; value: string }>();

  const apply = (key: string, value: string) => {
    if (!key) return;
    const expanded = resolveVariables(value, env);
    out.set(key.toLowerCase(), { key, value: expanded });
  };

  // Canonicalize once so we don't re-parse the URL for every rule.
  const canonicalServiceRefId = serviceRefIdFor(serviceRefId);

  const globals = rules.filter((r) => r.enabled && r.scope === 'global');
  const services = rules.filter(
    (r) =>
      r.enabled &&
      r.scope === 'service' &&
      canonicalServiceRefId !== null &&
      serviceRefIdFor(r.scopeRefId) === canonicalServiceRefId,
  );
  const envs = rules.filter(
    (r) =>
      r.enabled &&
      r.scope === 'environment' &&
      envId !== null &&
      r.scopeRefId === envId,
  );

  for (const r of globals) apply(r.key, r.value);
  for (const r of services) apply(r.key, r.value);
  for (const r of envs) apply(r.key, r.value);
  for (const [k, v] of Object.entries(endpointHeaders)) apply(k, v);

  const result: Record<string, string> = {};
  for (const { key, value } of out.values()) result[key] = value;
  return result;
}

/**
 * For diagnostics / "active context" pill in the Try-it-out panel (M4+).
 * Returns the list of rules that would actually take effect for the given
 * context, in application order.
 */
export function explainActiveRules(ctx: HeaderMergeContext): HeaderRule[] {
  const { rules = [], serviceRefId = null, envId = null } = ctx;
  const canonicalServiceRefId = serviceRefIdFor(serviceRefId);
  const out: HeaderRule[] = [];
  for (const r of rules) {
    if (!r.enabled) continue;
    if (r.scope === 'global') out.push(r);
    else if (
      r.scope === 'service' &&
      canonicalServiceRefId !== null &&
      serviceRefIdFor(r.scopeRefId) === canonicalServiceRefId
    )
      out.push(r);
    else if (r.scope === 'environment' && r.scopeRefId === envId) out.push(r);
  }
  return out;
}
