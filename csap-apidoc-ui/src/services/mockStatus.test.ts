import { describe, expect, it } from 'vitest';
import { isMockResponse, mockBaseUrl, mockStatusUrl, probeMock } from './mockStatus';

const ok = (body: unknown) => async () => ({ ok: true, json: async () => body });

describe('mockStatus', () => {
  it('builds the status url', () => {
    expect(mockStatusUrl('')).toBe('/csap/mock-status');
    expect(mockStatusUrl('/api/')).toBe('/api/csap/mock-status');
  });

  it('probe returns the status when enabled', async () => {
    const s = await probeMock('', ok({ enabled: true, prefix: '/csap/mock', seed: 'csap' }));
    expect(s).toEqual({ enabled: true, prefix: '/csap/mock', seed: 'csap' });
  });

  it('probe accepts the wrapped form', async () => {
    const s = await probeMock('', ok({ code: '200', data: { enabled: true, prefix: '/csap/mock' } }));
    expect(s?.prefix).toBe('/csap/mock');
  });

  it('probe is null on 404, bad shape, disabled and network errors', async () => {
    expect(await probeMock('', async () => ({ ok: false, json: async () => ({}) }))).toBeNull();
    expect(await probeMock('', ok({ enabled: false, prefix: '/csap/mock' }))).toBeNull();
    expect(await probeMock('', ok({ hello: 1 }))).toBeNull();
    expect(await probeMock('', ok(null))).toBeNull();
    expect(
      await probeMock('', async () => {
        throw new Error('network');
      }),
    ).toBeNull();
  });

  it('builds the mock base url from origin + service base + prefix', () => {
    const st = { enabled: true, prefix: '/csap/mock' };
    expect(mockBaseUrl('http://h:8080', '', st)).toBe('http://h:8080/csap/mock');
    expect(mockBaseUrl('http://h:8080/', '/svc/', st)).toBe('http://h:8080/svc/csap/mock');
    expect(mockBaseUrl('http://h:8080', 'https://x.io/ctx', st)).toBe('https://x.io/ctx/csap/mock');
  });

  it('detects mock responses case-insensitively', () => {
    expect(isMockResponse({ 'x-csap-mock': '1' })).toBe(true);
    expect(isMockResponse({ 'X-CSAP-Mock': '1' })).toBe(true);
    expect(isMockResponse({ 'content-type': 'x' })).toBe(false);
    expect(isMockResponse(undefined)).toBe(false);
  });
});
