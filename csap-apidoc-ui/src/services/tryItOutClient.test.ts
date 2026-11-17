import { describe, it, expect, vi, afterEach } from 'vitest';
import axios from 'csap-axios';
import {
  appendQuery,
  isDevtoolsProxyUrl,
  prepareTransport,
  resolveTryItOutOptions,
  sendTryItOutRequest,
  toAbsoluteUrl,
  DEFAULT_TIMEOUT_MS,
} from './tryItOutClient';

const ORIGIN = 'http://doc.local:8080';
const SETTINGS = {
  tryItOutProxyUrl: null as string | null,
  tryItOutTimeoutMs: 30_000,
  tryItOutWithCredentials: false,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('resolveTryItOutOptions — settings reach the request', () => {
  it('uses settings when the panel gets no explicit props', () => {
    const r = resolveTryItOutOptions(
      {},
      { ...SETTINGS, tryItOutProxyUrl: ' https://p/?u= ', tryItOutTimeoutMs: 5000 },
    );
    expect(r).toEqual({ proxyUrl: 'https://p/?u=', timeoutMs: 5000, withCredentials: false });
  });

  it('explicit props override settings; blanks normalise', () => {
    const r = resolveTryItOutOptions(
      { proxyUrl: null, timeoutMs: 1234 },
      { ...SETTINGS, tryItOutProxyUrl: 'https://p/?u=', tryItOutWithCredentials: true },
    );
    expect(r).toEqual({ proxyUrl: null, timeoutMs: 1234, withCredentials: true });
    expect(resolveTryItOutOptions({}, { ...SETTINGS, tryItOutProxyUrl: '  ', tryItOutTimeoutMs: 0 }))
      .toEqual({ proxyUrl: null, timeoutMs: DEFAULT_TIMEOUT_MS, withCredentials: false });
  });
});

describe('url helpers', () => {
  it('detects the devtools proxy endpoint', () => {
    expect(isDevtoolsProxyUrl('/csap/apidoc/devtools/proxy')).toBe(true);
    expect(isDevtoolsProxyUrl('http://h:1/ctx/csap/apidoc/devtools/proxy/')).toBe(true);
    expect(isDevtoolsProxyUrl('https://proxy.example/?url=')).toBe(false);
    expect(isDevtoolsProxyUrl(null)).toBe(false);
  });

  it('appendQuery merges params and skips null', () => {
    expect(appendQuery('http://a/b', { x: 1, y: null, z: 'a b' })).toBe('http://a/b?x=1&z=a%20b');
    expect(appendQuery('http://a/b?k=v#h', { x: true })).toBe('http://a/b?k=v&x=true#h');
    expect(appendQuery('http://a/b', {})).toBe('http://a/b');
  });

  it('toAbsoluteUrl resolves relative paths against the origin', () => {
    expect(toAbsoluteUrl('/api/x', ORIGIN)).toBe('http://doc.local:8080/api/x');
    expect(toAbsoluteUrl('https://h/x', ORIGIN)).toBe('https://h/x');
  });
});

describe('prepareTransport', () => {
  const headers = { Authorization: 'Bearer t', 'Content-Type': 'application/json' };

  it('direct: url untouched, query via params', () => {
    const w = prepareTransport({ method: 'GET', url: '/a', query: { q: 1 } }, {}, ORIGIN);
    expect(w).toMatchObject({ via: 'direct', url: '/a', params: { q: 1 }, method: 'GET' });
  });

  it('prefix proxy: encodes absolute target incl. query into the proxy URL', () => {
    const w = prepareTransport(
      { method: 'GET', url: '/a', query: { q: 1 }, proxyUrl: 'https://p/?url=' },
      headers,
      ORIGIN,
    );
    expect(w.via).toBe('prefix-proxy');
    expect(w.url).toBe('https://p/?url=' + encodeURIComponent('http://doc.local:8080/a?q=1'));
    expect(w.params).toBeUndefined();
    expect(w.headers).toBe(headers);
  });

  it('devtools proxy: POSTs the JSON envelope DevtoolsProxyController expects', () => {
    const w = prepareTransport(
      {
        method: 'PUT',
        url: 'http://api.local/users/1',
        query: { dry: true },
        body: { name: 'n' },
        proxyUrl: '/csap/apidoc/devtools/proxy',
      },
      headers,
      ORIGIN,
    );
    expect(w.via).toBe('devtools-proxy');
    expect(w.method).toBe('POST');
    expect(w.url).toBe('/csap/apidoc/devtools/proxy');
    expect(w.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(JSON.parse(w.data as string)).toEqual({
      method: 'PUT',
      url: 'http://api.local/users/1?dry=true',
      headers,
      body: '{"name":"n"}',
    });
  });

  it('devtools proxy: no body → null, relative target made absolute', () => {
    const w = prepareTransport(
      { method: 'GET', url: '/x', proxyUrl: 'http://doc.local:8080/csap/apidoc/devtools/proxy' },
      {},
      ORIGIN,
    );
    expect(JSON.parse(w.data as string)).toEqual({
      method: 'GET',
      url: 'http://doc.local:8080/x',
      headers: {},
      body: null,
    });
  });

  it('devtools proxy: rejects multipart bodies', () => {
    const fd = new FormData();
    fd.append('a', '1');
    expect(() =>
      prepareTransport({ method: 'POST', url: '/x', body: fd, proxyUrl: '/csap/apidoc/devtools/proxy' }, {}, ORIGIN),
    ).toThrow();
  });
});

describe('sendTryItOutRequest passes timeout to axios', () => {
  it('uses the configured timeout instead of a fixed 30s', async () => {
    const spy = vi.spyOn(axios, 'request').mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'text/plain' },
      data: 'ok',
    } as never);
    const r = await sendTryItOutRequest({ method: 'GET', url: 'http://h/x', timeoutMs: 4321 });
    expect(r.ok).toBe(true);
    expect(spy.mock.calls[0][0]).toMatchObject({ timeout: 4321, url: 'http://h/x', method: 'GET' });
  });

  it('routes through the devtools proxy envelope', async () => {
    const spy = vi.spyOn(axios, 'request').mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: {},
      data: '',
    } as never);
    await sendTryItOutRequest({
      method: 'DELETE',
      url: 'http://h/x',
      proxyUrl: '/csap/apidoc/devtools/proxy',
      timeoutMs: 1000,
    });
    const cfg = spy.mock.calls[0][0];
    expect(cfg.method).toBe('POST');
    expect(cfg.url).toBe('/csap/apidoc/devtools/proxy');
    expect(JSON.parse(cfg.data as string)).toMatchObject({ method: 'DELETE', url: 'http://h/x' });
    expect(cfg.timeout).toBe(1000);
  });
});
