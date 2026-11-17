/**
 * Derive the doc endpoints of a service from the resource URL picked in the
 * header "服务" select (gateway aggregation, see
 * docs/guides/GATEWAY-AGGREGATION.md). Resource URLs look like
 *   /example-admin-api/csap/apidoc/parent
 *   /a/b/csap/apidoc
 *   http://localhost:8081/ctx/csap/apidoc/parent
 * The service base is the URL with the trailing `/csap/apidoc/parent` (or
 * `/csap/apidoc`) removed; relative / absolute form and every prefix
 * segment are preserved. A query string (e.g. `?token=`) is kept on the
 * derived endpoint URLs.
 */

const SUFFIXES = ['/csap/apidoc/parent', '/csap/apidoc'];

function splitQuery(url: string): [string, string] {
  const i = url.search(/[?#]/);
  return i < 0 ? [url, ''] : [url.slice(0, i), url.slice(i)];
}

/** Service base URL without trailing slash, e.g. `/example-admin-api`, `http://h:1/ctx`, or `''` for the root. */
export function apidocServiceBase(resourceUrl: string): string {
  let [path] = splitQuery(resourceUrl.trim());
  path = path.replace(/\/+$/, '');
  for (const suffix of SUFFIXES) {
    if (path.endsWith(suffix)) {
      path = path.slice(0, -suffix.length);
      break;
    }
  }
  return path.replace(/\/+$/, '');
}

function endpoint(resourceUrl: string, tail: string): string {
  const [, query] = splitQuery(resourceUrl.trim());
  return apidocServiceBase(resourceUrl) + tail + query;
}

/** `GET …/csap/apidoc/method` for the selected resource. */
export function apidocMethodUrl(resourceUrl: string): string {
  return endpoint(resourceUrl, '/csap/apidoc/method');
}

/** `GET …/csap/apidoc/parent` for the selected resource. */
export function apidocParentUrl(resourceUrl: string): string {
  return endpoint(resourceUrl, '/csap/apidoc/parent');
}
