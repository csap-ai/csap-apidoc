import { describe, expect, it } from 'vitest';
import { filterTreeByStatus, nodeStatus } from './statusFilter';

const tree = [
  {
    key: 'a', title: 'A', children: [
      { key: 'a1', title: 'get', method: 'GET', isLeaf: true, status: 'FINISH' },
      { key: 'a2', title: 'old', method: 'GET', isLeaf: true, status: 'DEPRECATED' },
      { key: 'a3', title: 'wip', method: 'POST', isLeaf: true },
    ],
  },
  { key: 'b', title: 'B', children: [{ key: 'b1', title: 'x', method: 'GET', isLeaf: true, status: 'DEPRECATED' }] },
];

describe('filterTreeByStatus', () => {
  it('all 或空不过滤', () => {
    expect(filterTreeByStatus(tree, 'all')).toBe(tree);
    expect(filterTreeByStatus(tree, undefined)).toBe(tree);
  });

  it('按状态过滤并去掉空的分组', () => {
    const dep = filterTreeByStatus(tree, 'DEPRECATED');
    expect(dep.map((n) => n.key)).toEqual(['a', 'b']);
    expect(dep[0].children.map((n: { key: string }) => n.key)).toEqual(['a2']);
    const fin = filterTreeByStatus(tree, 'FINISH');
    expect(fin.map((n) => n.key)).toEqual(['a']);
  });

  it('没有 status 的按开发中处理', () => {
    expect(nodeStatus({})).toBe('DEFAULT');
    const dev = filterTreeByStatus(tree, 'DEFAULT');
    expect(dev).toHaveLength(1);
    expect(dev[0].children.map((n: { key: string }) => n.key)).toEqual(['a3']);
  });
});
