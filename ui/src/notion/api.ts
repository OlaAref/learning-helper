export type TreeNode = { id: string; title: string; partial: boolean; children: TreeNode[] }

export type PageDoc = {
  id: string
  title: string
  parentId: string | null
  path: string
  lastEdited: string
  partial: boolean
  markdown: string
  assets: string[]
}

export type Status = {
  connected: boolean
  localFolder: string
  mode: string
  intervalSeconds: number
  pushEnabled: boolean
}

export type Summary = {
  busy: boolean
  pulled: number
  pushed: number
  created: number
  conflicts: number
  skipped: number
  moved: number
  trashed: number
  notes: string[]
  errors: string[]
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...init })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.error) message = body.error
    } catch {
      /* no JSON body */
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

async function upload<T>(url: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file, file.name || 'image.png')
  const res = await fetch(url, { method: 'POST', body: form })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.error) message = body.error
    } catch {
      /* no JSON body */
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

export const api = {
  addImage: (id: string, file: File) =>
    upload<{ name: string; markdown: string }>(`/api/notion/pages/${encodeURIComponent(id)}/images`, file),
  status: () => request<Status>('/api/notion/status'),
  tree: () => request<TreeNode[]>('/api/notion/pages'),
  page: (id: string) => request<PageDoc>(`/api/notion/pages/${encodeURIComponent(id)}`),
  save: (id: string, body: { title: string; markdown: string }) =>
    request<PageDoc>(`/api/notion/pages/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(body) }),
  create: (body: { title: string; parentId: string | null; markdown: string }) =>
    request<PageDoc>('/api/notion/pages', { method: 'POST', body: JSON.stringify(body) }),
  sync: () => request<Summary>('/api/notion/sync', { method: 'POST' }),
}
