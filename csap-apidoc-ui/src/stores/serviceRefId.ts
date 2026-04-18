/**
 * serviceRefId — canonicalization helper for the "service ref" key used by
 * Headers (service-scope rules) and Auth (per-service bindings).
 *
 * Both stores key bindings by a free-form string the user typed (typically
 * the API doc parent URL like `https://x.com/csap/apidoc/parent`). Without
 * normalization, these two strings end up as DIFFERENT bindings even
 * though they obviously refer to the same service:
 *
 *   https://x.com/csap/apidoc/parent
 *   https://x.com/csap/apidoc/parent/
 *   HTTPS://x.com/csap/apidoc/parent
 *   https://x.com/csap/apidoc/parent?token=abc
 *
 * `serviceRefIdFor()` collapses all of those to the same canonical string so
 * binding lookups don't silently miss when the URL casing or trailing slash
 * differs by one character.
 *
 * Canonicalization rules (URL-shaped input):
 *   1. Trim whitespace; null/empty → null.
 *   2. Lowercase the protocol and host (NOT the path — REST paths are
 *      case-sensitive on most servers).
 *   3. Drop the query string and fragment (bindings shouldn't depend on
 *      a token in the URL or a `#section` anchor).
 *   4. Strip the trailing `/csap/apidoc/parent` suffix — the layout's API
 *      detail / try-it-out logic strips it too, so binding under or
 *      without it should resolve to the same scheme.
 *   5. Strip a trailing `/`, including the root `/` — so `https://x.com/`
 *      and `https://x.com` collapse to the same key. This also makes the
 *      function idempotent (the URL constructor would otherwise re-add the
 *      root slash on the second pass).
 *
 * For non-URL strings (plain identifiers like `my-service`), we apply the
 * same suffix/trailing-slash trimming as a best-effort but do NOT lowercase
 * — those are likely opaque ids the user picked deliberately.
 */

const APIDOC_PARENT_SUFFIX = '/csap/apidoc/parent';

export function serviceRefIdFor(
  rawUrl: string | null | undefined,
): string | null {
  if (rawUrl == null) return null;
  const trimmed = rawUrl.trim();
  if (!trimmed) return null;

  try {
    const u = new URL(trimmed);
    let path = u.pathname || '';
    if (path.endsWith(APIDOC_PARENT_SUFFIX + '/')) {
      path = path.slice(0, -(APIDOC_PARENT_SUFFIX.length + 1));
    } else if (path.endsWith(APIDOC_PARENT_SUFFIX)) {
      path = path.slice(0, -APIDOC_PARENT_SUFFIX.length);
    }
    if (path.endsWith('/')) {
      path = path.slice(0, -1);
    }
    return `${u.protocol.toLowerCase()}//${u.host.toLowerCase()}${path}`;
  } catch {
    let s = trimmed;
    if (s.endsWith(APIDOC_PARENT_SUFFIX + '/')) {
      s = s.slice(0, -(APIDOC_PARENT_SUFFIX.length + 1));
    } else if (s.endsWith(APIDOC_PARENT_SUFFIX)) {
      s = s.slice(0, -APIDOC_PARENT_SUFFIX.length);
    }
    if (s.endsWith('/')) s = s.slice(0, -1);
    return s;
  }
}
