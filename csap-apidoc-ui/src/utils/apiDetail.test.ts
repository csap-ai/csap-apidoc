import { describe, it, expect } from 'vitest';
import { hasApiDetail, EMPTY_API_DETAIL } from './apiDetail';

describe('hasApiDetail', () => {
  it('true for a loaded endpoint even without declared headers', () => {
    expect(hasApiDetail({ apiTitle: 'List users', method: 'GET', patch: '/users' })).toBe(true);
    expect(hasApiDetail({ method: 'GET' })).toBe(true);
  });

  it('false before anything is loaded / after a failed load', () => {
    expect(hasApiDetail(EMPTY_API_DETAIL)).toBe(false);
    expect(hasApiDetail(null)).toBe(false);
  });
});
