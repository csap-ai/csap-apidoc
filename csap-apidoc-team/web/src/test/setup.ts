import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { cleanup, configure } from '@testing-library/react';
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

if (!(globalThis as any).ResizeObserver) {
  (globalThis as any).ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
}

// 路由页面是懒加载的,首次渲染需要多等一会
configure({ asyncUtilTimeout: 5000 });
