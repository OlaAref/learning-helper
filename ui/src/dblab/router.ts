import { useEffect, useState } from 'react'

export type Route =
  | { name: 'home' }
  | { name: 'lecture'; section: string; lecture: string }
  | { name: 'task'; section: string; lecture: string; task: string }

const enc = encodeURIComponent

export function hrefFor(route: Route): string {
  switch (route.name) {
    case 'home':
      return '#/db'
    case 'lecture':
      return `#/db/lecture/${enc(route.section)}/${enc(route.lecture)}`
    case 'task':
      return `#/db/task/${enc(route.section)}/${enc(route.lecture)}/${enc(route.task)}`
  }
}

export function parseHash(hash: string): Route {
  const parts = hash.replace(/^#\/?/, '').replace(/^db(\/|$)/, '').split('/').filter(Boolean).map(decodeURIComponent)
  if (parts[0] === 'lecture' && parts.length === 3) return { name: 'lecture', section: parts[1], lecture: parts[2] }
  if (parts[0] === 'task' && parts.length === 4) {
    return { name: 'task', section: parts[1], lecture: parts[2], task: parts[3] }
  }
  return { name: 'home' }
}

/** The current screen lives in the URL hash, so a reload keeps your place. */
export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parseHash(window.location.hash))
  useEffect(() => {
    const onChange = () => setRoute(parseHash(window.location.hash))
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}
