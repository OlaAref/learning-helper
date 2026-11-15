import { useEffect, useState } from 'react'

/** Which project the URL hash points at. Each project owns everything after its own prefix. */
export type Area = 'home' | 'notion' | 'db'

export function areaOf(hash: string): Area {
  const first = hash.replace(/^#\/?/, '').split('/')[0]
  if (first === 'notion') return 'notion'
  if (first === 'db') return 'db'
  return 'home'
}

export function useArea(): Area {
  const [area, setArea] = useState<Area>(() => areaOf(window.location.hash))
  useEffect(() => {
    const onChange = () => setArea(areaOf(window.location.hash))
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return area
}
