import { describe, it, expect } from 'vitest';
import { apidocMethodUrl, apidocParentUrl, apidocServiceBase } from './apidocUrl';

describe('apidocMethodUrl — gateway resource URL → method endpoint', () => {
  it('relative, single prefix segment', () => {
    expect(apidocMethodUrl('/example-admin-api/csap/apidoc/parent')).toBe(
      '/example-admin-api/csap/apidoc/method',
    );
    expect(apidocMethodUrl('/qx-oauth2-api/csap/apidoc')).toBe('/qx-oauth2-api/csap/apidoc/method');
  });

  it('relative, multi-segment prefix', () => {
    expect(apidocMethodUrl('/gw/v2/admin-api/csap/apidoc/parent')).toBe(
      '/gw/v2/admin-api/csap/apidoc/method',
    );
    expect(apidocMethodUrl('/gw/admin-api/csap/apidoc/')).toBe('/gw/admin-api/csap/apidoc/method');
  });

  it('absolute http(s) URLs', () => {
    expect(apidocMethodUrl('http://localhost:8081/csap/apidoc/parent')).toBe(
      'http://localhost:8081/csap/apidoc/method',
    );
    expect(apidocMethodUrl('https://api.example.com/ctx/sub/csap/apidoc/parent/')).toBe(
      'https://api.example.com/ctx/sub/csap/apidoc/method',
    );
  });

  it('keeps a query string', () => {
    expect(apidocMethodUrl('/svc/csap/apidoc/parent?token=abc')).toBe(
      '/svc/csap/apidoc/method?token=abc',
    );
  });

  it('URL without the doc suffix is treated as the service base', () => {
    expect(apidocMethodUrl('http://h:1/svc')).toBe('http://h:1/svc/csap/apidoc/method');
  });
});

describe('apidocServiceBase / apidocParentUrl', () => {
  it('strips the doc suffix only', () => {
    expect(apidocServiceBase('/a/b/csap/apidoc/parent')).toBe('/a/b');
    expect(apidocServiceBase('/a/csap/apidoc')).toBe('/a');
    expect(apidocServiceBase('https://h/csap/apidoc/parent')).toBe('https://h');
    expect(apidocServiceBase('/csap/apidoc/parent')).toBe('');
  });

  it('parent endpoint from either form', () => {
    expect(apidocParentUrl('/a/csap/apidoc')).toBe('/a/csap/apidoc/parent');
    expect(apidocParentUrl('/a/csap/apidoc/parent')).toBe('/a/csap/apidoc/parent');
  });
});
