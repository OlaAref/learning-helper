import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

/** The selected page id lives in the URL hash (#/notion/page/<id>) so a reload keeps your place. */
export function useSelectedId(): [string | null, (id: string) => void] {
  const read = () => {
    const m = window.location.hash.match(/^#\/notion\/page\/(.+)$/)
    return m ? decodeURIComponent(m[1]) : null
  }
  const [id, setId] = useState<string | null>(read)
  useEffect(() => {
    const onHash = () => setId(read())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])
  return [id, (next) => (window.location.hash = `#/notion/page/${encodeURIComponent(next)}`)]
}

/** Refreshes everything when the server reports that a sync changed something. */
export function useLiveUpdates() {
  const queryClient = useQueryClient()
  useEffect(() => {
    const source = new EventSource('/api/notion/events')
    source.addEventListener('sync', () => queryClient.invalidateQueries())
    return () => source.close()
  }, [queryClient])
}
