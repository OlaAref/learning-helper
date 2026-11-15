import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type PageDoc, type Status, type TreeNode } from '../api'
import { localizeImages, renderMarkdown } from '../markdown'
import { FolderIcon, PageIcon } from '../../shared/Icons'

type Props = {
  id: string
  status: Status | undefined
  kids: TreeNode[]
  onSelect: (id: string) => void
}

export default function PageView({ id, status, kids, onSelect }: Props) {
  const queryClient = useQueryClient()
  const page = useQuery({ queryKey: ['page', id], queryFn: () => api.page(id) })

  if (page.isPending) return <article className="article"><p className="muted">Loading…</p></article>
  if (page.isError) return <article className="article"><p className="error">{page.error.message}</p></article>

  return (
    <Editor key={page.data.id} doc={page.data} status={status}
            kids={kids} onSelect={onSelect} onSaved={() => queryClient.invalidateQueries()} />
  )
}

type EditorProps = {
  doc: PageDoc
  status: Status | undefined
  kids: TreeNode[]
  onSelect: (id: string) => void
  onSaved: () => void
}

function Editor({ doc, status, kids, onSelect, onSaved }: EditorProps) {
  // `base` is the last version received from the server. Unsaved typing is never replaced by a newer
  // server version; a newer version is adopted only when there is nothing unsaved.
  const [base, setBase] = useState({ title: doc.title, markdown: doc.markdown })
  const [title, setTitle] = useState(doc.title)
  const [draft, setDraft] = useState(doc.markdown)
  const [mode, setMode] = useState<'preview' | 'edit'>('preview')
  const queryClient = useQueryClient()
  const dirty = title !== base.title || draft !== base.markdown

  useEffect(() => {
    if (doc.title === base.title && doc.markdown === base.markdown) return
    if (title === base.title && draft === base.markdown) {
      setTitle(doc.title)
      setDraft(doc.markdown)
    }
    setBase({ title: doc.title, markdown: doc.markdown })
  }, [doc.title, doc.markdown])
  const taRef = useRef<HTMLTextAreaElement>(null)
  const [imgMsg, setImgMsg] = useState<string | null>(null)

  async function addImages(files: File[]) {
    const imgs = files.filter((f) => f.type.startsWith('image/'))
    if (imgs.length === 0) return
    setImgMsg('Saving image…')
    try {
      let insert = ''
      for (const f of imgs) insert += `\n\n${(await api.addImage(doc.id, f)).markdown}\n\n`
      const ta = taRef.current
      const start = ta?.selectionStart ?? draft.length
      const end = ta?.selectionEnd ?? draft.length
      setDraft((d) => d.slice(0, start) + insert + d.slice(end))
      setImgMsg(null)
      queryClient.invalidateQueries({ queryKey: ['page', doc.id] })
    } catch (e) {
      setImgMsg(e instanceof Error ? e.message : 'Could not save the image')
    }
  }
  const html = useMemo(() => renderMarkdown(localizeImages(draft, doc.id, doc.assets ?? [])), [draft, doc.id, doc.assets])

  const save = useMutation({
    mutationFn: () => api.save(doc.id, { title, markdown: draft }),
    onSuccess: onSaved,
  })

  const localPath = `${status?.localFolder ?? ''}\\${doc.path.replaceAll('/', '\\')}\\index.md`
  const chip = doc.partial
    ? { cls: 'warn', text: 'Partly downloaded (read only)' }
    : dirty
      ? { cls: 'warn', text: 'Unsaved changes' }
      : status?.pushEnabled
        ? { cls: 'ok', text: 'In sync with Notion' }
        : { cls: 'ok', text: 'Mirrored locally (push is off)' }

  return (
    <div className="pagewrap">
      <article className="article">
        <input className="titleinput" value={title} onChange={(e) => setTitle(e.target.value)}
               placeholder="Untitled" aria-label="Page title" disabled={doc.partial} />
        <div className="muted small metaline">
          <span className={`chip ${chip.cls}`}>{chip.text}</span>
          <span>Last edited in Notion {new Date(doc.lastEdited).toLocaleString()}</span>
        </div>

        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={mode === 'preview'} className={mode === 'preview' ? 'active' : ''} onClick={() => setMode('preview')}>Preview</button>
          <button role="tab" aria-selected={mode === 'edit'} className={mode === 'edit' ? 'active' : ''} onClick={() => setMode('edit')}>Markdown</button>
          <span className="spacer" />
          {save.isError && <span className="error small">{save.error.message}</span>}
          <button className="btn" disabled={!dirty} onClick={() => { setTitle(base.title); setDraft(base.markdown) }}>Discard</button>
          <button className="btn primary" disabled={!dirty || doc.partial || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>

        {mode === 'edit' ? (
          <>
            <textarea ref={taRef} className="editor" value={draft} onChange={(e) => setDraft(e.target.value)}
                      spellCheck={false} aria-label="Markdown" readOnly={doc.partial}
                      onPaste={(e) => {
                        const files = Array.from(e.clipboardData.files)
                        if (files.some((f) => f.type.startsWith('image/'))) {
                          e.preventDefault()
                          void addImages(files)
                        }
                      }}
                      onDragOver={(e) => e.preventDefault()}
                      onDrop={(e) => {
                        const files = Array.from(e.dataTransfer.files)
                        if (files.some((f) => f.type.startsWith('image/'))) {
                          e.preventDefault()
                          void addImages(files)
                        }
                      }} />
            <p className="muted">Paste or drop an image to add it. It is uploaded to Notion when you save and push is on.</p>
            {imgMsg && <p className="error">{imgMsg}</p>}
          </>
        ) : (
          <div className="prose" dangerouslySetInnerHTML={{ __html: html }} />
        )}

        {kids.length > 0 && (
          <>
            <h2 className="subhead">Sub-pages</h2>
            <div className="kidgrid">
              {kids.map((k) => (
                <button key={k.id} className="kid" onClick={() => onSelect(k.id)}>
                  <PageIcon /> {k.title || 'Untitled'}
                </button>
              ))}
            </div>
          </>
        )}
      </article>

      <aside className="localpanel">
        <div className="label iconlabel"><FolderIcon /> Local copy</div>
        <div className="pathbox mono">{localPath}</div>
        <p className="muted small">
          {status?.pushEnabled
            ? 'Edits made here or in the file are sent to Notion on the next sync.'
            : 'Push is off: edits stay in this file until sync.push-enabled is set to true.'}
        </p>
      </aside>
    </div>
  )
}
