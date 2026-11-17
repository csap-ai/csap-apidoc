/**
 * tryItOutClient — low-level transport for the Try-it-out panel.
 *
 * Deliberately separate from `src/api/index.ts` so we don't inherit the
 * doc-fetch axios instance's interceptors (JSON repair / global error
 * surface / loading overlay). For try-it-out we want the raw HTTP picture:
 * the user must see real status codes, headers, and bodies — no silent
 * massaging.
 *
 * Returns a normalised `TryItOutResponse` shape that the UI can render
 * uniformly for both 2xx and error paths. Network/CORS/timeout failures
 * are surfaced as `TryItOutFailure` rather than thrown, so the panel can
 * always render *something* and never traps the user in a toast.
 */

import axios, {
  AxiosError,
  AxiosRequestConfig,
  AxiosResponse,
  CancelTokenSource,
} from 'csap-axios';

export type HttpMethod =
  | 'GET'
  | 'POST'
  | 'PUT'
  | 'DELETE'
  | 'PATCH'
  | 'HEAD'
  | 'OPTIONS';

export interface TryItOutRequest {
  method: HttpMethod;
  /** Fully-qualified URL (M5 will resolve baseURL + path); empty is rejected. */
  url: string;
  headers?: Record<string, string>;
  query?: Record<string, string | number | boolean | null | undefined>;
  /** Pre-serialised body (string) or a structured value (will be JSON-encoded if no Content-Type or content-type is application/json). */
  body?: string | object | FormData | null;
  /** Per-call timeout override in ms; default 30s. */
  timeoutMs?: number;
  /**
   * Optional CORS proxy (Settings → Try it out). A URL ending in
   * `/csap/apidoc/devtools/proxy` uses the built-in devtools proxy's JSON
   * envelope; any other value is used as a prefix:
   * `proxyUrl + encodeURIComponent(targetUrl)`.
   */
  proxyUrl?: string | null;
  /**
   * When true, the underlying axios/XHR layer will set `withCredentials`
   * so cross-origin requests carry cookies and HTTP-auth (and any
   * cookie-bound API key from the active auth scheme is actually
   * honoured by the browser). Default false. The target server must
   * still set `Access-Control-Allow-Credentials: true` and a concrete
   * origin for the request to succeed in the browser.
   */
  withCredentials?: boolean;
}

export const DEFAULT_TIMEOUT_MS = 30_000;

export type TryItOutBodyKind =
  | 'json'
  | 'text'
  | 'html'
  | 'xml'
  | 'image'
  | 'binary'
  | 'empty';

export interface TryItOutResponse {
  ok: boolean;
  status: number;
  statusText: string;
  url: string;
  /** Lower-cased header keys for predictable lookup. */
  headers: Record<string, string>;
  contentType: string;
  bodyKind: TryItOutBodyKind;
  /** Decoded body — string for text-shaped responses; object for JSON; null for empty / binary. */
  body: string | object | null;
  /** Original raw text response for the "Raw" tab; null when body is binary. */
  rawText: string | null;
  /** Length in bytes (best-effort). */
  byteLength: number;
  latencyMs: number;
  /** Echo of the outbound request for the diagnostic / Raw tab. */
  request: {
    method: HttpMethod;
    url: string;
    headers: Record<string, string>;
    bodyPreview: string | null;
  };
}

export interface TryItOutFailure {
  ok: false;
  /** Discriminator vs TryItOutResponse for narrowing. */
  failed: true;
  /** 'network' (CORS / DNS / refused), 'timeout', 'cancelled', 'config' (bad input), 'unknown'. */
  reason: 'network' | 'timeout' | 'cancelled' | 'config' | 'unknown';
  message: string;
  latencyMs: number;
  request: TryItOutResponse['request'];
}

export type TryItOutResult = TryItOutResponse | TryItOutFailure;

const HEADER_NAME_RE = /^[!#$%&'*+\-.0-9A-Z^_`a-z|~]+$/;

function lowercaseHeaders(
  raw: Record<string, unknown> | undefined,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (!raw) return out;
  for (const [k, v] of Object.entries(raw)) {
    if (v == null) continue;
    out[k.toLowerCase()] = String(v);
  }
  return out;
}

function pickContentType(headers: Record<string, string>): string {
  return headers['content-type']?.split(';')[0]?.trim().toLowerCase() ?? '';
}

function classifyBody(contentType: string, raw: string | null): TryItOutBodyKind {
  if (raw == null || raw.length === 0) return 'empty';
  if (!contentType) return 'text';
  if (contentType.startsWith('image/')) return 'image';
  if (contentType.includes('json')) return 'json';
  if (contentType.includes('xml')) return 'xml';
  if (contentType.includes('html')) return 'html';
  if (contentType.startsWith('text/')) return 'text';
  if (
    contentType.startsWith('application/octet-stream') ||
    contentType.startsWith('application/pdf') ||
    contentType.startsWith('application/zip') ||
    contentType.startsWith('audio/') ||
    contentType.startsWith('video/')
  ) {
    return 'binary';
  }
  // Form-encoded / urlencoded / others — treat as text by default.
  return 'text';
}

function decodeBody(
  kind: TryItOutBodyKind,
  rawText: string | null,
): string | object | null {
  if (rawText == null || rawText.length === 0) return null;
  if (kind === 'json') {
    try {
      return JSON.parse(rawText);
    } catch {
      return rawText;
    }
  }
  return rawText;
}

function previewBody(body: TryItOutRequest['body']): string | null {
  if (body == null) return null;
  if (typeof body === 'string') return body;
  if (body instanceof FormData) {
    const parts: string[] = [];
    body.forEach((v, k) => {
      parts.push(
        `${k}=${v instanceof File ? `<file:${v.name},${v.size}b>` : String(v)}`,
      );
    });
    return parts.join('&');
  }
  try {
    return JSON.stringify(body, null, 2);
  } catch {
    return String(body);
  }
}

function normaliseHeaders(
  input: Record<string, string> | undefined,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (!input) return out;
  for (const [k, v] of Object.entries(input)) {
    if (!k) continue;
    if (!HEADER_NAME_RE.test(k)) {
      console.warn('[try-it-out] skipping invalid header name:', k);
      continue;
    }
    if (v == null) continue;
    out[k] = String(v);
  }
  return out;
}

function ensureContentType(
  headers: Record<string, string>,
  body: TryItOutRequest['body'],
): Record<string, string> {
  if (body == null || body instanceof FormData) return headers;
  const has = Object.keys(headers).some(
    (k) => k.toLowerCase() === 'content-type',
  );
  if (has) return headers;
  if (typeof body === 'string') {
    return { ...headers, 'Content-Type': 'text/plain;charset=UTF-8' };
  }
  return { ...headers, 'Content-Type': 'application/json' };
}

/** Path of the built-in reverse proxy shipped by csap-apidoc-devtools. */
export const DEVTOOLS_PROXY_PATH = '/csap/apidoc/devtools/proxy';

/**
 * True when `proxyUrl` points at the csap-apidoc-devtools proxy
 * (`POST /csap/apidoc/devtools/proxy`, JSON envelope). Anything else is
 * treated as a generic prefix proxy (`proxyUrl + encodeURIComponent(url)`).
 */
export function isDevtoolsProxyUrl(proxyUrl: string | null | undefined): boolean {
  if (!proxyUrl) return false;
  const path = proxyUrl.trim().split(/[?#]/)[0].replace(/\/+$/, '');
  return path.endsWith(DEVTOOLS_PROXY_PATH);
}

/** Append query params to a URL the same way axios `params` would (null/undefined skipped). */
export function appendQuery(url: string, query: TryItOutRequest['query']): string {
  if (!query) return url;
  const parts: string[] = [];
  for (const [k, v] of Object.entries(query)) {
    if (v === null || v === undefined) continue;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  if (parts.length === 0) return url;
  const hashIdx = url.indexOf('#');
  const base = hashIdx >= 0 ? url.slice(0, hashIdx) : url;
  const hash = hashIdx >= 0 ? url.slice(hashIdx) : '';
  const sep = base.includes('?') ? (/[?&]$/.test(base) ? '' : '&') : '?';
  return base + sep + parts.join('&') + hash;
}

function currentOrigin(): string | undefined {
  return typeof window !== 'undefined' && window.location
    ? window.location.origin
    : undefined;
}

/** A proxy cannot resolve "/path" against the doc page, so resolve it here. */
export function toAbsoluteUrl(url: string, origin = currentOrigin()): string {
  if (/^https?:\/\//i.test(url) || !origin) return url;
  return origin.replace(/\/+$/, '') + (url.startsWith('/') ? url : '/' + url);
}

export interface PreparedTransport {
  /** What axios actually requests. */
  method: HttpMethod;
  url: string;
  headers: Record<string, string>;
  params?: TryItOutRequest['query'];
  data?: unknown;
  via: 'direct' | 'prefix-proxy' | 'devtools-proxy';
}

/**
 * Turn a logical try-it-out request into the wire request:
 *   - no proxy        → direct call, query via axios params;
 *   - prefix proxy    → `proxyUrl + encodeURIComponent(absoluteTargetWithQuery)`;
 *   - devtools proxy  → `POST proxyUrl` with the JSON envelope
 *                       `{ method, url, headers, body }` expected by
 *                       DevtoolsProxyController (body must be a string).
 */
export function prepareTransport(
  req: TryItOutRequest,
  headers: Record<string, string>,
  origin?: string,
): PreparedTransport {
  const proxyUrl = req.proxyUrl ? req.proxyUrl.trim() : '';
  let data: unknown;
  if (req.body !== undefined && req.body !== null) {
    if (
      req.body instanceof FormData ||
      typeof req.body === 'string' ||
      req.method === 'GET' ||
      req.method === 'DELETE' ||
      req.method === 'HEAD'
    ) {
      data = req.body;
    } else {
      data = JSON.stringify(req.body);
    }
  }
  if (!proxyUrl) {
    return { method: req.method, url: req.url, headers, params: req.query, data, via: 'direct' };
  }
  const target = toAbsoluteUrl(appendQuery(req.url, req.query), origin);
  if (isDevtoolsProxyUrl(proxyUrl)) {
    if (data instanceof FormData) {
      throw new Error('内置代理只支持文本/JSON 请求体，不支持 multipart/form-data');
    }
    const body =
      data === undefined || data === null
        ? null
        : typeof data === 'string'
        ? data
        : JSON.stringify(data);
    return {
      method: 'POST',
      url: proxyUrl,
      headers: { 'Content-Type': 'application/json' },
      data: JSON.stringify({ method: req.method, url: target, headers, body }),
      via: 'devtools-proxy',
    };
  }
  // Generic prefix proxy, e.g. "https://proxy.example/?url=".
  return {
    method: req.method,
    url: proxyUrl + encodeURIComponent(target),
    headers,
    data,
    via: 'prefix-proxy',
  };
}

/**
 * Merge the panel's explicit props with the persisted Try-it-out settings.
 * Explicit (non-undefined) props win; otherwise the settings apply.
 */
export function resolveTryItOutOptions(
  overrides: { proxyUrl?: string | null; timeoutMs?: number },
  settings: {
    tryItOutProxyUrl: string | null;
    tryItOutTimeoutMs: number;
    tryItOutWithCredentials: boolean;
  },
): { proxyUrl: string | null; timeoutMs: number; withCredentials: boolean } {
  const proxyUrl =
    overrides.proxyUrl !== undefined ? overrides.proxyUrl : settings.tryItOutProxyUrl;
  const timeoutMs =
    overrides.timeoutMs !== undefined ? overrides.timeoutMs : settings.tryItOutTimeoutMs;
  return {
    proxyUrl: proxyUrl && proxyUrl.trim() ? proxyUrl.trim() : null,
    timeoutMs: timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS,
    withCredentials: !!settings.tryItOutWithCredentials,
  };
}

export interface SendOptions {
  /** Optional cancel token — pass `client.cancelTokenSource()` to support an Abort button. */
  cancelToken?: import('csap-axios').CancelToken;
}

/**
 * Public sender. Resolves with either a TryItOutResponse (HTTP round-trip
 * completed, regardless of status) or TryItOutFailure (no usable response).
 */
export async function sendTryItOutRequest(
  req: TryItOutRequest,
  options?: SendOptions,
): Promise<TryItOutResult> {
  const t0 = Date.now();
  const requestEcho: TryItOutResponse['request'] = {
    method: req.method,
    url: req.url,
    headers: normaliseHeaders(req.headers),
    bodyPreview: previewBody(req.body ?? null),
  };

  if (!req.url || !/^https?:\/\//i.test(req.url) && !req.url.startsWith('/')) {
    return {
      ok: false,
      failed: true,
      reason: 'config',
      message: '请求 URL 不合法（必须是 http(s):// 绝对地址或以 / 开头的相对路径）',
      latencyMs: 0,
      request: requestEcho,
    };
  }

  const headersWithCT = ensureContentType(requestEcho.headers, req.body ?? null);
  let wire: PreparedTransport;
  try {
    wire = prepareTransport(req, headersWithCT);
  } catch (err) {
    return {
      ok: false,
      failed: true,
      reason: 'config',
      message: (err as Error).message,
      latencyMs: 0,
      request: requestEcho,
    };
  }
  const finalUrl = wire.url;

  const config: AxiosRequestConfig = {
    method: wire.method,
    url: wire.url,
    headers: wire.headers,
    params: wire.params,
    timeout: req.timeoutMs && req.timeoutMs > 0 ? req.timeoutMs : DEFAULT_TIMEOUT_MS,
    // Hand over raw text; we'll classify + parse ourselves.
    transformResponse: [(data: any) => data],
    // Treat any HTTP status as a "successful round-trip" so the UI can
    // render 4xx/5xx bodies properly. Network errors still go to .catch.
    validateStatus: () => true,
    cancelToken: options?.cancelToken,
    // Per-call withCredentials. We only set the flag when the caller
    // explicitly opts in so we never override the axios default for
    // unrelated traffic; same-origin requests carry cookies regardless.
    ...(req.withCredentials ? { withCredentials: true } : {}),
  };
  if (wire.data !== undefined) config.data = wire.data;

  let res: AxiosResponse<string>;
  try {
    res = await axios.request<string>(config);
  } catch (err) {
    const ax = err as AxiosError;
    const latencyMs = Date.now() - t0;
    if (axios.isCancel(err)) {
      return {
        ok: false,
        failed: true,
        reason: 'cancelled',
        message: '请求已取消',
        latencyMs,
        request: requestEcho,
      };
    }
    if (ax.code === 'ECONNABORTED' || /timeout/i.test(ax.message)) {
      return {
        ok: false,
        failed: true,
        reason: 'timeout',
        message: '请求超时',
        latencyMs,
        request: requestEcho,
      };
    }
    return {
      ok: false,
      failed: true,
      reason: 'network',
      message:
        ax.message ||
        '网络错误：检查 CORS、代理或目标服务可用性（详见浏览器控制台）',
      latencyMs,
      request: requestEcho,
    };
  }

  const latencyMs = Date.now() - t0;
  const headers = lowercaseHeaders(
    res.headers as unknown as Record<string, unknown>,
  );
  const contentType = pickContentType(headers);
  const rawText = typeof res.data === 'string' ? res.data : JSON.stringify(res.data);
  const bodyKind = classifyBody(contentType, rawText);
  const body = decodeBody(bodyKind, rawText);
  const byteLength = rawText ? new Blob([rawText]).size : 0;

  return {
    ok: res.status >= 200 && res.status < 300,
    status: res.status,
    statusText: res.statusText,
    url: finalUrl,
    headers,
    contentType,
    bodyKind,
    body,
    rawText: bodyKind === 'binary' ? null : rawText,
    byteLength,
    latencyMs,
    request: requestEcho,
  };
}

/** Helper for callers that want to wire an Abort button. */
export function newCancelTokenSource(): CancelTokenSource {
  return axios.CancelToken.source();
}

export function isTryItOutFailure(
  r: TryItOutResult | null | undefined,
): r is TryItOutFailure {
  return !!r && (r as TryItOutFailure).failed === true;
}
