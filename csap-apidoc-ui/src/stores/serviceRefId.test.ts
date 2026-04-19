import { describe, it, expect } from 'vitest';
import { serviceRefIdFor } from './serviceRefId';

describe('serviceRefIdFor', () => {
  it('returns null for null / undefined / empty / whitespace', () => {
    expect(serviceRefIdFor(null)).toBeNull();
    expect(serviceRefIdFor(undefined)).toBeNull();
    expect(serviceRefIdFor('')).toBeNull();
    expect(serviceRefIdFor('   ')).toBeNull();
  });

  it('strips a single trailing slash from URL paths', () => {
    expect(serviceRefIdFor('https://api.example.com/foo/')).toBe(
      'https://api.example.com/foo',
    );
  });

  it('collapses bare-host URLs with and without a trailing slash', () => {
    // For idempotence (the URL constructor re-adds the root slash on a
    // second pass) we strip it. `https://x.com/` and `https://x.com`
    // therefore canonicalize to the same key.
    expect(serviceRefIdFor('https://api.example.com/')).toBe(
      'https://api.example.com',
    );
    expect(serviceRefIdFor('https://api.example.com')).toBe(
      'https://api.example.com',
    );
  });

  it('lowercases protocol and host but preserves path casing', () => {
    expect(serviceRefIdFor('HTTPS://API.Example.COM/CamelCase/Path')).toBe(
      'https://api.example.com/CamelCase/Path',
    );
  });

  it('treats trailing-slash and no-trailing-slash variants as equal', () => {
    const a = serviceRefIdFor('https://x.com/csap/apidoc/parent/');
    const b = serviceRefIdFor('https://x.com/csap/apidoc/parent');
    expect(a).toBe(b);
  });

  it('strips the /csap/apidoc/parent suffix', () => {
    expect(serviceRefIdFor('https://x.com/csap/apidoc/parent')).toBe(
      'https://x.com',
    );
    expect(serviceRefIdFor('https://x.com/csap/apidoc/parent/')).toBe(
      'https://x.com',
    );
    expect(serviceRefIdFor('https://x.com:8443/api/csap/apidoc/parent')).toBe(
      'https://x.com:8443/api',
    );
  });

  it('drops the query string and fragment', () => {
    expect(
      serviceRefIdFor('https://x.com/csap/apidoc/parent?token=abc&v=1'),
    ).toBe('https://x.com');
    expect(serviceRefIdFor('https://x.com/foo?a=1#section')).toBe(
      'https://x.com/foo',
    );
  });

  it('is idempotent — canonicalizing twice yields the same value', () => {
    const inputs = [
      'https://x.com/csap/apidoc/parent/',
      'HTTPS://X.com/foo/',
      'http://localhost:8080/',
      'plain-service-id',
    ];
    for (const i of inputs) {
      const once = serviceRefIdFor(i);
      const twice = serviceRefIdFor(once);
      expect(twice).toBe(once);
    }
  });

  it('falls back to best-effort trimming for non-URL strings', () => {
    expect(serviceRefIdFor('  my-service  ')).toBe('my-service');
    expect(serviceRefIdFor('my-service/')).toBe('my-service');
    expect(serviceRefIdFor('my-service/csap/apidoc/parent')).toBe('my-service');
  });

  it('preserves wildcard sentinel "*"', () => {
    expect(serviceRefIdFor('*')).toBe('*');
  });
});
