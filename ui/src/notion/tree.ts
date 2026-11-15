import type { TreeNode } from './api'

/** Nodes from a root down to the node with the given id, or [] when it is not in the tree. */
export function pathTo(nodes: TreeNode[], id: string): TreeNode[] {
  for (const n of nodes) {
    if (n.id === id) return [n]
    const below = pathTo(n.children, id)
    if (below.length) return [n, ...below]
  }
  return []
}

export function flatten(nodes: TreeNode[], depth = 0): { node: TreeNode; depth: number }[] {
  return nodes.flatMap((n) => [{ node: n, depth }, ...flatten(n.children, depth + 1)])
}

export function countNodes(nodes: TreeNode[]): number {
  return flatten(nodes).length
}
