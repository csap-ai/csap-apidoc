/**
 * Header "分组 / 版本" selects.
 *
 * `GET /csap/apidoc/parent` returns `groups` / `versions` (sets of names).
 * A select is only useful when the menu can actually be filtered by it,
 * i.e. when the tree nodes carry a `group` / `version` field. The current
 * backend tree nodes (CsapDocParentResponseModel / CsapDocMethodModel) do
 * not, so in that case the select is hidden instead of showing a
 * placeholder that does nothing.
 */

type TreeNode = {
  group?: unknown;
  version?: unknown;
  children?: TreeNode[];
  [k: string]: unknown;
};

function toStringList(v: unknown): string[] {
  if (Array.isArray(v)) return v.filter((x): x is string => typeof x === 'string' && x !== '');
  if (typeof v === 'string' && v) return [v];
  return [];
}

function treeHasField(nodes: TreeNode[] | undefined, field: 'group' | 'version'): boolean {
  if (!Array.isArray(nodes)) return false;
  return nodes.some(
    (n) =>
      !!n &&
      ((field in n && toStringList(n[field]).length > 0) ||
        treeHasField(n.children, field)),
  );
}

export interface GroupVersionOptions {
  groups: string[];
  versions: string[];
}

/** Options to show; an empty list means "hide that select". */
export function groupVersionOptions(
  parentData: { groups?: unknown; versions?: unknown } | null | undefined,
  apiList: TreeNode[] | undefined,
): GroupVersionOptions {
  const groups = toStringList(parentData?.groups).sort();
  const versions = toStringList(parentData?.versions).sort();
  return {
    groups: groups.length > 0 && treeHasField(apiList, 'group') ? groups : [],
    versions: versions.length > 0 && treeHasField(apiList, 'version') ? versions : [],
  };
}

function nodeMatches(node: TreeNode, field: 'group' | 'version', wanted: string | null | undefined): boolean {
  if (!wanted) return true;
  const values = toStringList(node[field]);
  // A node without the field inherits from its parent (already matched).
  return values.length === 0 || values.includes(wanted);
}

/**
 * Filter the menu tree by group / version. Controllers are kept when they
 * match themselves and still have matching children (or are leaves).
 */
export function filterTreeByGroupVersion<T extends TreeNode>(
  tree: T[],
  group: string | null | undefined,
  version: string | null | undefined,
): T[] {
  if (!group && !version) return tree;
  const walk = (nodes: TreeNode[]): TreeNode[] => {
    const out: TreeNode[] = [];
    for (const n of nodes) {
      if (!nodeMatches(n, 'group', group) || !nodeMatches(n, 'version', version)) continue;
      if (Array.isArray(n.children) && n.children.length > 0) {
        const kids = walk(n.children);
        if (kids.length === 0) continue;
        out.push({ ...n, children: kids });
      } else {
        out.push(n);
      }
    }
    return out;
  };
  return walk(tree) as T[];
}
