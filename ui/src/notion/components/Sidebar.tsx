import { useEffect, useMemo, useState } from 'react'
import type { TreeNode } from '../api'
import { countNodes, flatten, pathTo } from '../tree'
import { BackIcon, ChevronIcon, PageIcon, PlusIcon, SearchIcon } from '../../shared/Icons'

type Props = {
  nodes: TreeNode[]
  selectedId: string | null
  onSelect: (id: string) => void
  onNew: () => void
  onSettings: () => void
  connected: boolean
}

export default function Sidebar({ nodes, selectedId, onSelect, onNew, onSettings, connected }: Props) {
  const [open, setOpen] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState('')

  // keep the ancestors of the selected page expanded
  useEffect(() => {
    if (!selectedId) return
    const ancestors = pathTo(nodes, selectedId).slice(0, -1).map((n) => n.id)
    if (ancestors.length) setOpen((prev) => new Set([...prev, ...ancestors]))
  }, [selectedId, nodes])

  const toggle = (id: string) =>
    setOpen((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })

  const matches = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return null
    return flatten(nodes).filter((r) => r.node.title.toLowerCase().includes(q))
  }, [filter, nodes])

  const row = (n: TreeNode, depth: number) => {
    const hasKids = n.children.length > 0
    const isOpen = open.has(n.id)
    const selected = n.id === selectedId
    return (
      <div key={n.id}>
        <div className={`row${selected ? ' selected' : ''}`} style={{ paddingLeft: 4 + depth * 16 }}>
          <button className="chev" aria-label={isOpen ? 'Collapse' : 'Expand'} aria-expanded={hasKids ? isOpen : undefined}
                  onClick={() => toggle(n.id)} style={{ opacity: hasKids ? 1 : 0 }} tabIndex={hasKids ? 0 : -1}>
            <span style={{ display: 'flex', transform: `rotate(${isOpen ? 90 : 0}deg)` }}><ChevronIcon /></span>
          </button>
          <button className="rowtitle" onClick={() => onSelect(n.id)} aria-current={selected ? 'page' : undefined}>
            <PageIcon />
            <span className="ellipsis">{n.title || 'Untitled'}</span>
            {n.partial && <span className="dot warn" title="Only partly downloaded" />}
          </button>
        </div>
        {hasKids && isOpen && n.children.map((c) => row(c, depth + 1))}
      </div>
    )
  }

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="logo" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round"><path d="M4 7h16M4 12h10M4 17h16" /></svg>
        </div>
        <div className="brandtext">
          <span className="brandname">Notion Mirror</span>
          <span className="muted small">{connected ? 'Connected workspace' : 'Not connected'}</span>
        </div>
      </div>

      <label className="search">
        <SearchIcon />
        <input placeholder="Search pages" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Search pages" />
      </label>

      <div className="sectionhead">
        <span className="label">Pages</span>
        <button className="iconbtn" aria-label="New page" onClick={onNew}><PlusIcon /></button>
      </div>

      <nav className="tree" aria-label="Page tree">
        {matches
          ? matches.length
            ? matches.map(({ node }) => row({ ...node, children: [] }, 0))
            : <p className="muted small pad">No pages match.</p>
          : nodes.length
            ? nodes.map((n) => row(n, 0))
            : <p className="muted small pad">No pages yet. Press Sync now.</p>}
      </nav>

      <div className="sidefoot">
        <div className="small ink3"><span className="dot ok" /> {countNodes(nodes)} pages mirrored</div>
        <button className="link" onClick={onSettings}>Connection &amp; sync settings</button>
        <a className="link backlink" href="#/"><BackIcon />All projects</a>
      </div>
    </aside>
  )
}
