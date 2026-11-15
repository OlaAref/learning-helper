import { describe, expect, it } from 'vitest'
import type { TreeNode } from './api'
import { countNodes, flatten, pathTo } from './tree'

const leaf = (id: string): TreeNode => ({ id, title: id, partial: false, children: [] })
const tree: TreeNode[] = [
  { id: 'a', title: 'a', partial: false, children: [leaf('a1'), { ...leaf('a2'), children: [leaf('a2x')] }] },
  leaf('b'),
]

describe('tree helpers', () => {
  it('finds the path to a nested page', () => {
    expect(pathTo(tree, 'a2x').map((n) => n.id)).toEqual(['a', 'a2', 'a2x'])
  })
  it('returns an empty path for an unknown id', () => {
    expect(pathTo(tree, 'zzz')).toEqual([])
  })
  it('flattens with depth', () => {
    expect(flatten(tree).map((r) => `${r.node.id}:${r.depth}`)).toEqual(['a:0', 'a1:1', 'a2:1', 'a2x:2', 'b:0'])
    expect(countNodes(tree)).toBe(5)
  })
})
