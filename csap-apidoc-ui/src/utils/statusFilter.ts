/**
 * 接口状态筛选:菜单树上的方法节点带 status(DEFAULT 开发中 / FINISH 完成 / DEPRECATED 过时),
 * 按状态过滤;没有任何方法的分组(控制器)节点一并去掉。
 */

export type StatusFilter = 'all' | 'DEFAULT' | 'FINISH' | 'DEPRECATED';

type TreeNode = {
  status?: unknown;
  children?: TreeNode[];
  [k: string]: unknown;
};

/** 方法节点的状态,没有 status 字段按开发中处理 */
export function nodeStatus(node: TreeNode): 'DEFAULT' | 'FINISH' | 'DEPRECATED' {
  return node.status === 'FINISH' || node.status === 'DEPRECATED' ? node.status : 'DEFAULT';
}

export function filterTreeByStatus<T extends TreeNode>(tree: T[], status: StatusFilter | null | undefined): T[] {
  if (!status || status === 'all') return tree;
  const walk = (nodes: TreeNode[]): TreeNode[] => {
    const out: TreeNode[] = [];
    for (const n of nodes) {
      if (Array.isArray(n.children) && n.children.length > 0) {
        const kids = walk(n.children);
        if (kids.length > 0) out.push({ ...n, children: kids });
      } else if (n.isLeaf === true || n.method !== undefined) {
        if (nodeStatus(n) === status) out.push(n);
      }
    }
    return out;
  };
  return walk(tree) as T[];
}
