import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/react';
import { server } from '@/mock/server';
import { setApiBase } from '@/api/client';

// jsdom 下 Node fetch 不接受相对地址
setApiBase(window.location.origin);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { cleanup(); server.resetHandlers(); try { sessionStorage.clear(); } catch { /* ignore */ } });
afterAll(() => server.close());

if (!window.matchMedia) {
  window.matchMedia = ((q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, onchange: null, dispatchEvent: () => false })) as any;
}
Object.defineProperty(window, 'getComputedStyle', { value: window.getComputedStyle, writable: true });
