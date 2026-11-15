import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api, type TreeNode } from '../api'
import { flatten } from '../tree'

type Props = {
  nodes: TreeNode[]
  defaultParent: string | null
  onClose: () => void
  onCreated: (id: string) => void
}

export default function NewPageDialog({ nodes, defaultParent, onClose, onCreated }: Props) {
  const [title, setTitle] = useState('')
  const [parentId, setParentId] = useState<string>(defaultParent ?? '')
  const [markdown, setMarkdown] = useState('')

  const create = useMutation({
    mutationFn: () => api.create({ title, parentId: parentId || null, markdown }),
    onSuccess: (doc) => onCreated(doc.id),
  })

  return (
    <div className="overlay" onClick={onClose}>
      <form className="dialog" role="dialog" aria-modal="true" aria-label="New page"
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => { e.preventDefault(); create.mutate() }}>
        <h1>New page</h1>
        <label>Title
          <input autoFocus value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Untitled" />
        </label>
        <label>Parent page
          <select value={parentId} onChange={(e) => setParentId(e.target.value)}>
            <option value="">Top level (no parent)</option>
            {flatten(nodes).map(({ node, depth }) => (
              <option key={node.id} value={node.id}>{'  '.repeat(depth)}{node.title || 'Untitled'}</option>
            ))}
          </select>
        </label>
        <label>Content (Markdown, optional)
          <textarea value={markdown} onChange={(e) => setMarkdown(e.target.value)} rows={6} spellCheck={false} />
        </label>
        <p className="muted small">The page is created in Notion first, then saved as a folder with an index.md under the parent's folder.</p>
        {create.isError && <p className="error">{create.error.message}</p>}
        <div className="actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={create.isPending}>
            {create.isPending ? 'Creating…' : 'Create page'}
          </button>
        </div>
      </form>
    </div>
  )
}
