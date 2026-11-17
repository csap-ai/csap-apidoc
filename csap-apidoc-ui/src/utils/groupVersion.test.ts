import { describe, it, expect } from 'vitest';
import { filterTreeByGroupVersion, groupVersionOptions } from './groupVersion';

// Shape returned by the current backend: no group/version on nodes.
const PLAIN_TREE = [
  { key: 'C1', title: 'Users', children: [{ key: 'm1', title: 'get', path: '/u' }] },
];

const TAGGED_TREE = [
  {
    key: 'C1',
    title: 'Users',
    group: ['admin'],
    children: [
      { key: 'm1', title: 'v1 get', version: ['v1'] },
      { key: 'm2', title: 'v2 get', version: ['v2'] },
    ],
  },
  {
    key: 'C2',
    title: 'Shop',
    group: ['shop'],
    children: [{ key: 'm3', title: 'list', version: ['v1'] }],
  },
];

describe('groupVersionOptions', () => {
  it('hides both selects when the tree cannot be filtered (current backend)', () => {
    expect(
      groupVersionOptions({ groups: ['default'], versions: ['default'] }, PLAIN_TREE),
    ).toEqual({ groups: [], versions: [] });
  });

  it('hides both selects when /parent returns no groups / versions', () => {
    expect(groupVersionOptions({}, TAGGED_TREE)).toEqual({ groups: [], versions: [] });
    expect(groupVersionOptions(null, TAGGED_TREE)).toEqual({ groups: [], versions: [] });
  });

  it('uses the real lists when nodes carry the fields', () => {
    expect(
      groupVersionOptions({ groups: ['shop', 'admin'], versions: ['v2', 'v1'] }, TAGGED_TREE),
    ).toEqual({ groups: ['admin', 'shop'], versions: ['v1', 'v2'] });
  });
});

describe('filterTreeByGroupVersion', () => {
  it('no selection → unchanged', () => {
    expect(filterTreeByGroupVersion(TAGGED_TREE, null, null)).toBe(TAGGED_TREE);
  });

  it('filters by group', () => {
    const r = filterTreeByGroupVersion(TAGGED_TREE, 'shop', null);
    expect(r.map((n) => n.key)).toEqual(['C2']);
  });

  it('filters by version and drops empty controllers', () => {
    const r = filterTreeByGroupVersion(TAGGED_TREE, null, 'v2');
    expect(r.map((n) => n.key)).toEqual(['C1']);
    expect(r[0].children!.map((n) => n.key)).toEqual(['m2']);
  });

  it('group + version combined', () => {
    expect(filterTreeByGroupVersion(TAGGED_TREE, 'shop', 'v2')).toEqual([]);
  });
});
