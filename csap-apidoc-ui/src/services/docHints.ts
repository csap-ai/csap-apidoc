/**
 * docHints — turn the backend's `@DocGlobalHeader` / `@DocAuth` hints
 * (`CsapDocMethod.globalHeaderHints` / `authHint`, see
 * csap-apidoc-common CsapDocGlobalHeaderHint / CsapDocAuthHint and
 * docs/features/environment-auth-headers.md) into a Try-it-out prefill.
 *
 * Prefill rules — never override what the user configured:
 *   - a header hint is skipped when the user's global headers (already
 *     resolved for this service) or the panel already carry that header;
 *   - the auth hint is only used when no auth scheme is active for this
 *     service, and it only adds an empty placeholder row (the credential
 *     itself is for the user to fill in). Nothing is persisted.
 *
 * Pure functions, no React — easy to unit test.
 */

export interface GlobalHeaderHint {
  name: string;
  description?: string;
  example?: string;
  required?: boolean;
}

export interface AuthHint {
  /** bearer / basic / apikey / oauth2_client / none */
  scheme: string;
  description?: string;
  /** apikey only: header / query */
  in?: string;
  /** apikey only: header / query parameter name */
  name?: string;
}

export interface DocHints {
  globalHeaderHints: GlobalHeaderHint[];
  authHint: AuthHint | null;
}

export const EMPTY_HINTS: DocHints = { globalHeaderHints: [], authHint: null };

function normaliseHeaderHints(raw: unknown): GlobalHeaderHint[] {
  if (!Array.isArray(raw)) return [];
  const out: GlobalHeaderHint[] = [];
  for (const h of raw) {
    if (!h || typeof h !== 'object') continue;
    const name = (h as GlobalHeaderHint).name;
    if (typeof name !== 'string' || !name.trim()) continue;
    const { description, example, required } = h as GlobalHeaderHint;
    out.push({
      name: name.trim(),
      description: typeof description === 'string' ? description : undefined,
      example: typeof example === 'string' ? example : undefined,
      required: !!required,
    });
  }
  return out;
}

function normaliseAuthHint(raw: unknown): AuthHint | null {
  if (!raw || typeof raw !== 'object') return null;
  const a = raw as AuthHint;
  if (typeof a.scheme !== 'string' || !a.scheme.trim()) return null;
  return {
    scheme: a.scheme.trim().toLowerCase(),
    description: typeof a.description === 'string' ? a.description : undefined,
    in: typeof a.in === 'string' ? a.in.toLowerCase() : undefined,
    name: typeof a.name === 'string' ? a.name : undefined,
  };
}

/** True when an object carries at least one of the hint fields. */
export function hasHintFields(obj: unknown): boolean {
  return (
    !!obj &&
    typeof obj === 'object' &&
    ('globalHeaderHints' in (obj as object) || 'authHint' in (obj as object))
  );
}

/** Read hints off a single method object (CsapDocMethod-shaped). */
export function hintsFromMethod(method: unknown): DocHints {
  if (!method || typeof method !== 'object') return EMPTY_HINTS;
  const m = method as { globalHeaderHints?: unknown; authHint?: unknown };
  return {
    globalHeaderHints: normaliseHeaderHints(m.globalHeaderHints),
    authHint: normaliseAuthHint(m.authHint),
  };
}

/**
 * Find the method `key` inside a `GET /csap/apidoc/{className}?key=`
 * response (`CsapDocResponse`: `apiList[].methodList[]`).
 */
export function hintsFromDocResponse(doc: unknown, methodKey: string): DocHints {
  const apiList = (doc as { apiList?: unknown })?.apiList;
  if (!Array.isArray(apiList)) return EMPTY_HINTS;
  for (const controller of apiList) {
    const methods = (controller as { methodList?: unknown })?.methodList;
    if (!Array.isArray(methods)) continue;
    const hit = methods.find((m) => (m as { key?: unknown })?.key === methodKey);
    if (hit) return hintsFromMethod(hit);
  }
  return EMPTY_HINTS;
}

export interface HintPrefill {
  headers: Record<string, string>;
  query: Record<string, string>;
  /** Auth scheme suggested by @DocAuth, when no scheme is active. */
  suggestedAuth: AuthHint | null;
}

function hasKeyCI(rec: Record<string, string> | undefined | null, name: string): boolean {
  if (!rec) return false;
  const lower = name.toLowerCase();
  return Object.keys(rec).some((k) => k.toLowerCase() === lower);
}

export function buildHintPrefill(input: {
  hints: DocHints;
  /** `useHeaders().resolve({ serviceRefId })` — the user's global headers. */
  resolvedHeaders: Record<string, string>;
  /** Whether an auth scheme is already active for this service. */
  hasActiveAuth: boolean;
}): HintPrefill {
  const { hints, resolvedHeaders, hasActiveAuth } = input;
  const headers: Record<string, string> = {};
  const query: Record<string, string> = {};

  for (const h of hints.globalHeaderHints) {
    if (hasKeyCI(resolvedHeaders, h.name) || hasKeyCI(headers, h.name)) continue;
    headers[h.name] = h.example ?? '';
  }

  const auth = hints.authHint;
  let suggestedAuth: AuthHint | null = null;
  if (auth && auth.scheme !== 'none' && !hasActiveAuth) {
    suggestedAuth = auth;
    const addHeader = (name: string, value: string): void => {
      if (hasKeyCI(resolvedHeaders, name) || hasKeyCI(headers, name)) return;
      headers[name] = value;
    };
    if (auth.scheme === 'bearer') {
      addHeader('Authorization', 'Bearer ');
    } else if (auth.scheme === 'basic') {
      addHeader('Authorization', 'Basic ');
    } else if (auth.scheme === 'apikey' && auth.name) {
      if (auth.in === 'query') {
        query[auth.name] = '';
      } else {
        addHeader(auth.name, '');
      }
    }
    // oauth2_client: needs a configured scheme (token endpoint etc.) —
    // only surfaced as a suggestion, nothing to prefill.
  }

  return { headers, query, suggestedAuth };
}

/**
 * Add rows for keys the user doesn't have yet; existing keys (matched
 * case-insensitively) keep the user's value.
 */
export function missingEntries(
  existingKeys: string[],
  prefill: Record<string, string> | undefined,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (!prefill) return out;
  const have = new Set(existingKeys.map((k) => k.trim().toLowerCase()));
  for (const [k, v] of Object.entries(prefill)) {
    if (!have.has(k.toLowerCase())) out[k] = v;
  }
  return out;
}
