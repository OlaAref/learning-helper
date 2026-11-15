import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Summary } from './api'
import { useLiveUpdates, useSelectedId } from './hooks'
import type { Theme } from '../shared/theme'
import { pathTo } from './tree'
import Sidebar from './components/Sidebar'
import PageView from './components/PageView'
import NewPageDialog from './components/NewPageDialog'
import SettingsPanel from './components/SettingsPanel'
import { MoonIcon, PlusIcon, SunIcon, SyncIcon } from '../shared/Icons'
import './notion.css'

function summaryText(s: Summary): string {
  if (s.busy) return 'A sync is already running.'
  const parts = [
    s.pulled && `${s.pulled} pulled`,
    s.pushed && `${s.pushed} pushed`,
    s.created && `${s.created} created`,
    s.conflicts && `${s.conflicts} conflicts`,
    s.trashed && `${s.trashed} moved to trash`,
  ].filter(Boolean)
  const base = parts.length ? parts.join(', ') : 'Everything is up to date'
  return s.errors.length ? `${base}. ${s.errors.length} errors: ${s.errors[0]}` : base
}

export default function NotionApp({ theme, onToggleTheme }: { theme: Theme; onToggleTheme: () => void }) {
  const queryClient = useQueryClient()
  const [selectedId, select] = useSelectedId()
  const [showNew, setShowNew] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  useLiveUpdates()

  const status = useQuery({ queryKey: ['status'], queryFn: api.status })
  const tree = useQuery({ queryKey: ['tree'], queryFn: api.tree })
  const nodes = tree.data ?? []

  // open the first page when nothing is selected, or when the selected page no longer exists
  useEffect(() => {
    if (!tree.data || tree.data.length === 0) return
    if (!selectedId || pathTo(tree.data, selectedId).length === 0) select(tree.data[0].id)
  }, [tree.data, selectedId])

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 6000)
    return () => clearTimeout(t)
  }, [toast])

  const sync = useMutation({
    mutationFn: api.sync,
    onSuccess: (s) => {
      setToast(summaryText(s))
      queryClient.invalidateQueries()
    },
    onError: (e: Error) => setToast(`Sync failed: ${e.message}`),
  })

  const chain = selectedId ? pathTo(nodes, selectedId) : []
  const current = chain[chain.length - 1]

  return (
    <div className={`app ${theme} f-notion`}>
      <Sidebar nodes={nodes} selectedId={selectedId} onSelect={select} onNew={() => setShowNew(true)}
               onSettings={() => setShowSettings(true)} connected={!!status.data?.connected} />
      <main className="main">
        <header className="topbar">
          <div className="crumb muted">{chain.map((n) => n.title || 'Untitled').join('  /  ') || 'Notion Mirror'}</div>
          <button className="btn tall" onClick={onToggleTheme} aria-label="Switch between light and dark mode">
            {theme === 'dk' ? <SunIcon /> : <MoonIcon />}{theme === 'dk' ? 'Light mode' : 'Dark mode'}
          </button>
          <button className="btn" onClick={() => sync.mutate()} disabled={sync.isPending}>
            <SyncIcon />{sync.isPending ? 'Syncing…' : 'Sync now'}
          </button>
          <button className="btn primary" onClick={() => setShowNew(true)}><PlusIcon />New page</button>
        </header>

        {tree.isError && <p className="error pad">Could not load pages: {tree.error.message}</p>}
        {current
          ? <PageView key={current.id} id={current.id} status={status.data} kids={current.children} onSelect={select} />
          : !tree.isPending && <article className="article"><p className="muted">No pages yet. Press “Sync now” to pull your Notion pages.</p></article>}
      </main>

      {toast && <div className="toast" role="status">{toast}</div>}
      {showNew && (
        <NewPageDialog nodes={nodes} defaultParent={current?.id ?? null} onClose={() => setShowNew(false)}
                       onCreated={(id) => { setShowNew(false); select(id); queryClient.invalidateQueries() }} />
      )}
      {showSettings && <SettingsPanel status={status.data} onClose={() => setShowSettings(false)} />}
    </div>
  )
}
