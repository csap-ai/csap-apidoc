/**
 * mockStatus — detects the server's embedded Mock (`/csap/mock/**`, 1.4.0) and
 * builds the "Mock" environment from it. Pure helpers; the only side effect is
 * the injected `fetch` in {@link probeMock}.
 *
 * Contract (identical for the Java and Go servers, see docs/design/1.4.0-mock-schema.md 2.7):
 *   GET {base}/csap/mock-status → 200 {"enabled":true,"prefix":"/csap/mock","seed":"csap"}
 *   when Mock is off the route does not exist (404) and nothing is shown.
 *   Mock responses carry `X-CSAP-Mock: 1`.
 */

export interface MockStatus {
  enabled: boolean;
  prefix: string;
  seed?: string;
}

/** Marker kept in `Environment.variables` so the Mock environment can be found again. */
export const MOCK_ENV_MARKER = '__csapMock';
export const MOCK_ENV_NAME = 'Mock';
export const MOCK_ENV_COLOR = '#722ed1';

type FetchLike = (input: string, init?: { headers?: Record<string, string> }) => Promise<{
  ok: boolean;
  json: () => Promise<unknown>;
}>;

/** `{serviceBase}/csap/mock-status` (serviceBase has no trailing slash). */
export function mockStatusUrl(serviceBase: string): string {
  return `${serviceBase.replace(/\/+$/, '')}/csap/mock-status`;
}

/**
 * Ask the server whether Mock is enabled. Never throws: any failure (404, network,
 * not JSON, unexpected shape) means "not available" and yields null.
 */
export async function probeMock(
  serviceBase: string,
  fetchImpl: FetchLike = (u, i) => fetch(u, i) as ReturnType<FetchLike>,
): Promise<MockStatus | null> {
  try {
    const res = await fetchImpl(mockStatusUrl(serviceBase), { headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    const body = (await res.json()) as Record<string, unknown> | null;
    // tolerate both the bare object and a {data: {...}} wrapper
    const d = (body && typeof body === 'object' && 'data' in body && body.data ? body.data : body) as
      | Record<string, unknown>
      | null;
    if (!d || d.enabled !== true || typeof d.prefix !== 'string') return null;
    return { enabled: true, prefix: d.prefix, seed: typeof d.seed === 'string' ? d.seed : undefined };
  } catch {
    return null;
  }
}

/**
 * Base URL of the Mock environment: `{origin}{serviceBase}{prefix}`. `serviceBase` may be
 * relative (`/api`, `''`) or absolute (`http://h:8080/ctx`); an absolute one wins over origin.
 */
export function mockBaseUrl(origin: string, serviceBase: string, status: MockStatus): string {
  const base = serviceBase.replace(/\/+$/, '');
  const prefix = status.prefix.startsWith('/') ? status.prefix : `/${status.prefix}`;
  const root = /^https?:\/\//i.test(base) ? base : `${origin.replace(/\/+$/, '')}${base}`;
  return `${root}${prefix}`;
}

/** True when the response came from the Mock (`X-CSAP-Mock` header, case-insensitive). */
export function isMockResponse(headers: Record<string, string> | undefined | null): boolean {
  if (!headers) return false;
  return Object.keys(headers).some((k) => k.toLowerCase() === 'x-csap-mock');
}
