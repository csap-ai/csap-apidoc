/**
 * Variable resolver — expands `{{key}}` tokens against an Environment.
 *
 * Reserved keys:
 *   {{baseUrl}} → env.baseUrl (cannot be overridden via variables map)
 *
 * Unknown keys pass through unchanged so that mistakes are visible to the
 * user in the rendered URL / header value, rather than silently dropping.
 */

import type { Environment } from './environmentStore';

const TOKEN_RE = /\{\{(\w+)\}\}/g;

export function resolveVariables(
  template: string,
  env: Environment | null,
): string {
  if (!env || !template) return template;
  return template.replace(TOKEN_RE, (whole, key: string) => {
    if (key === 'baseUrl') return env.baseUrl;
    if (Object.prototype.hasOwnProperty.call(env.variables, key)) {
      return env.variables[key];
    }
    return whole;
  });
}

/**
 * Recursively resolve every string value in an object tree. Used by future
 * milestones to apply variables to header maps, request bodies, etc.
 */
export function resolveDeep<T>(value: T, env: Environment | null): T {
  if (env == null || value == null) return value;
  if (typeof value === 'string') {
    return resolveVariables(value, env) as unknown as T;
  }
  if (Array.isArray(value)) {
    return value.map((v) => resolveDeep(v, env)) as unknown as T;
  }
  if (typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      out[k] = resolveDeep(v, env);
    }
    return out as unknown as T;
  }
  return value;
}

/**
 * Build a fully-qualified request URL by joining env.baseUrl with a relative
 * path. Absolute URLs (`http://`, `https://`) are returned unchanged so the
 * caller can pass either form. Empty baseUrl leaves the path untouched (vite
 * dev-proxy compatibility).
 */
export function joinBaseUrl(path: string, env: Environment | null): string {
  if (!path) return path;
  if (/^https?:\/\//i.test(path)) return path;
  if (!env?.baseUrl) return path;
  const base = env.baseUrl.replace(/\/+$/, '');
  const rest = path.startsWith('/') ? path : '/' + path;
  return base + rest;
}
