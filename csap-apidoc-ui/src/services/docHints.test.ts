import { describe, it, expect } from 'vitest';
import {
  buildHintPrefill,
  EMPTY_HINTS,
  hasHintFields,
  hintsFromDocResponse,
  hintsFromMethod,
  missingEntries,
} from './docHints';

const DOC = {
  apiList: [
    {
      name: 'com.example.UserController',
      methodList: [
        { key: 'other', globalHeaderHints: [{ name: 'X-Other' }] },
        {
          key: 'com.example.UserController.get',
          globalHeaderHints: [
            { name: 'X-Tenant-Id', example: '42', required: true, description: 'tenant' },
            { name: 'X-Trace-Id' },
            { name: '' },
          ],
          authHint: { scheme: 'Bearer', description: 'jwt' },
        },
      ],
    },
  ],
};

describe('reading hints from backend JSON', () => {
  it('finds the method inside a /csap/apidoc/{className} response', () => {
    const h = hintsFromDocResponse(DOC, 'com.example.UserController.get');
    expect(h.globalHeaderHints.map((x) => x.name)).toEqual(['X-Tenant-Id', 'X-Trace-Id']);
    expect(h.globalHeaderHints[0]).toMatchObject({ example: '42', required: true });
    expect(h.authHint).toMatchObject({ scheme: 'bearer' });
  });

  it('missing method / malformed payload → empty hints', () => {
    expect(hintsFromDocResponse(DOC, 'nope')).toEqual(EMPTY_HINTS);
    expect(hintsFromDocResponse(null, 'x')).toEqual(EMPTY_HINTS);
    expect(hintsFromMethod({ globalHeaderHints: 'bad', authHint: {} })).toEqual(EMPTY_HINTS);
  });

  it('hasHintFields detects a method response that already carries hints', () => {
    expect(hasHintFields({ title: 't' })).toBe(false);
    expect(hasHintFields({ authHint: null })).toBe(true);
  });
});

describe('buildHintPrefill — never overrides the user', () => {
  const hints = hintsFromDocResponse(DOC, 'com.example.UserController.get');

  it('prefills hinted headers with their example and a bearer placeholder', () => {
    const p = buildHintPrefill({ hints, resolvedHeaders: {}, hasActiveAuth: false });
    expect(p.headers).toEqual({
      'X-Tenant-Id': '42',
      'X-Trace-Id': '',
      Authorization: 'Bearer ',
    });
    expect(p.suggestedAuth?.scheme).toBe('bearer');
  });

  it('skips headers the user already configured (case-insensitive)', () => {
    const p = buildHintPrefill({
      hints,
      resolvedHeaders: { 'x-tenant-id': '7', authorization: 'Bearer real' },
      hasActiveAuth: false,
    });
    expect(p.headers).toEqual({ 'X-Trace-Id': '' });
  });

  it('ignores the auth hint when a scheme is already active', () => {
    const p = buildHintPrefill({ hints, resolvedHeaders: {}, hasActiveAuth: true });
    expect(p.headers).not.toHaveProperty('Authorization');
    expect(p.suggestedAuth).toBeNull();
  });

  it('apikey in query / header, basic, none, oauth2_client', () => {
    const mk = (authHint: object) =>
      buildHintPrefill({
        hints: { globalHeaderHints: [], authHint: hintsFromMethod({ authHint }).authHint },
        resolvedHeaders: {},
        hasActiveAuth: false,
      });
    expect(mk({ scheme: 'apikey', in: 'query', name: 'api_key' }).query).toEqual({ api_key: '' });
    expect(mk({ scheme: 'apikey', in: 'header', name: 'X-Api-Key' }).headers).toEqual({ 'X-Api-Key': '' });
    expect(mk({ scheme: 'basic' }).headers).toEqual({ Authorization: 'Basic ' });
    expect(mk({ scheme: 'none' })).toEqual({ headers: {}, query: {}, suggestedAuth: null });
    const oauth = mk({ scheme: 'oauth2_client' });
    expect(oauth.headers).toEqual({});
    expect(oauth.suggestedAuth?.scheme).toBe('oauth2_client');
  });
});

describe('missingEntries — panel merge keeps user rows', () => {
  it('adds only keys the panel does not have yet', () => {
    expect(missingEntries(['x-tenant-id', ''], { 'X-Tenant-Id': '42', 'X-Trace-Id': '' })).toEqual({
      'X-Trace-Id': '',
    });
    expect(missingEntries([], undefined)).toEqual({});
  });
});
