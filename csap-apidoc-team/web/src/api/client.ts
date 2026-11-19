// 统一请求层:Cookie 鉴权;非 GET 带 X-CSAP-Team: 1;错误码统一分发。
export interface ApiErrorBody {
  code?: string;
  message?: string;
  items?: unknown;
}

export class ApiError extends Error {
  status: number;
  code: string;
  body: ApiErrorBody;
  constructor(status: number, body: ApiErrorBody) {
    super(body.message || `请求失败(${status})`);
    this.status = status;
    this.code = body.code || 'INTERNAL';
    this.body = body;
  }
}

type Handler = (e: ApiError) => void;
const handlers: { unauthorized?: Handler; setupRequired?: Handler } = {};

/** 全局错误处理注册(由应用壳设置:跳登录 / 跳初始化) */
export function setErrorHandlers(h: typeof handlers) {
  Object.assign(handlers, h);
}

let base = '';
/** 测试专用:Node 的 fetch 不接受相对地址 */
export function setApiBase(b: string) {
  base = b;
}

export interface RequestOptions {
  method?: string;
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  /** 登录页等场景自己处理 401,不触发全局跳转 */
  silent?: boolean;
}

export function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return '';
  const sp = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

async function doFetch(path: string, opts: RequestOptions): Promise<Response> {
  const method = opts.method || 'GET';
  const headers: Record<string, string> = {};
  if (method !== 'GET') headers['X-CSAP-Team'] = '1';
  let body: string | undefined;
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  const res = await fetch(`${base}/api/v1${path}${buildQuery(opts.query)}`, {
    method,
    headers,
    body,
    credentials: 'same-origin',
  });
  if (!res.ok) {
    let parsed: ApiErrorBody = {};
    try {
      parsed = await res.json();
    } catch {
      /* 非 JSON 错误体 */
    }
    const err = new ApiError(res.status, parsed);
    if (!opts.silent) {
      if (err.code === 'UNAUTHORIZED') handlers.unauthorized?.(err);
      else if (err.code === 'SETUP_REQUIRED') handlers.setupRequired?.(err);
    }
    throw err;
  }
  return res;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await doFetch(path, opts);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function requestRaw(path: string, opts: RequestOptions = {}): Promise<Response> {
  return doFetch(path, opts);
}

/** 导出下载直链(GET,浏览器带 Cookie) */
export function url(path: string, query?: RequestOptions['query']): string {
  return `${base}/api/v1${path}${buildQuery(query)}`;
}
