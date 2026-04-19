import { describe, it, expect } from 'vitest';
import { resolveVariables, resolveDeep, joinBaseUrl } from './variableResolver';
import type { Environment } from './environmentStore';

const makeEnv = (overrides: Partial<Environment> = {}): Environment => ({
  id: 'e1',
  name: 'dev',
  color: '#000',
  baseUrl: 'https://api.dev.example.com',
  variables: { tenant: '42', region: 'eu-west-1' },
  ...overrides,
});

describe('resolveVariables', () => {
  it('expands the reserved {{baseUrl}} token from env.baseUrl', () => {
    expect(resolveVariables('{{baseUrl}}/users', makeEnv())).toBe(
      'https://api.dev.example.com/users',
    );
  });

  it('expands user-defined variables from env.variables', () => {
    expect(
      resolveVariables('/t/{{tenant}}/r/{{region}}', makeEnv()),
    ).toBe('/t/42/r/eu-west-1');
  });

  it('refuses to let env.variables override the reserved {{baseUrl}}', () => {
    const env = makeEnv({
      baseUrl: 'https://real',
      variables: { baseUrl: 'https://OVERRIDE-ATTEMPT' },
    });
    expect(resolveVariables('{{baseUrl}}/x', env)).toBe('https://real/x');
  });

  it('passes unknown keys through unchanged for visibility', () => {
    expect(resolveVariables('hello {{nope}} world', makeEnv())).toBe(
      'hello {{nope}} world',
    );
  });

  it('returns the template untouched when env is null', () => {
    expect(resolveVariables('/x/{{tenant}}', null)).toBe('/x/{{tenant}}');
  });

  it('returns empty templates as-is', () => {
    expect(resolveVariables('', makeEnv())).toBe('');
  });
});

describe('resolveDeep', () => {
  const env = makeEnv();

  it('expands strings inside arrays', () => {
    expect(resolveDeep(['{{tenant}}', 'plain'], env)).toEqual(['42', 'plain']);
  });

  it('expands strings inside nested objects', () => {
    const input = {
      a: '{{tenant}}',
      b: { c: ['{{region}}', { d: 'static' }] },
    };
    expect(resolveDeep(input, env)).toEqual({
      a: '42',
      b: { c: ['eu-west-1', { d: 'static' }] },
    });
  });

  it('passes through non-string primitives unchanged', () => {
    expect(resolveDeep(123 as unknown as string, env)).toBe(123);
    expect(resolveDeep(true as unknown as string, env)).toBe(true);
  });

  it('returns the value untouched when env is null', () => {
    const v = { a: '{{x}}' };
    expect(resolveDeep(v, null)).toBe(v);
  });

  it('returns null/undefined unchanged', () => {
    expect(resolveDeep(null as unknown as string, env)).toBeNull();
    expect(resolveDeep(undefined as unknown as string, env)).toBeUndefined();
  });
});

describe('joinBaseUrl', () => {
  it('returns absolute http/https URLs unchanged', () => {
    const env = makeEnv();
    expect(joinBaseUrl('http://other.example/x', env)).toBe(
      'http://other.example/x',
    );
    expect(joinBaseUrl('https://other.example/x', env)).toBe(
      'https://other.example/x',
    );
  });

  it('returns the path untouched when path is empty', () => {
    expect(joinBaseUrl('', makeEnv())).toBe('');
  });

  it('returns the path untouched when env is null or has no baseUrl', () => {
    expect(joinBaseUrl('/x', null)).toBe('/x');
    expect(joinBaseUrl('/x', makeEnv({ baseUrl: '' }))).toBe('/x');
  });

  it('strips trailing slashes from baseUrl and ensures a leading / on the path', () => {
    const env = makeEnv({ baseUrl: 'https://api.example.com/' });
    expect(joinBaseUrl('users', env)).toBe('https://api.example.com/users');
    expect(joinBaseUrl('/users', env)).toBe('https://api.example.com/users');
  });

  it('handles multiple trailing slashes on the baseUrl', () => {
    const env = makeEnv({ baseUrl: 'https://api.example.com///' });
    expect(joinBaseUrl('/x', env)).toBe('https://api.example.com/x');
  });
});
