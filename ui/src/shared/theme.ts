import { useEffect, useState } from 'react'

export type Theme = 'lt' | 'dk'

/** One theme for the whole app, remembered across screens and reloads. */
export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      const saved = localStorage.getItem('theme')
      if (saved === 'lt' || saved === 'dk') return saved
    } catch {
      /* storage unavailable */
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dk' : 'lt'
  })
  useEffect(() => {
    try {
      localStorage.setItem('theme', theme)
    } catch {
      /* storage unavailable */
    }
  }, [theme])
  return [theme, () => setTheme((t) => (t === 'dk' ? 'lt' : 'dk'))]
}
