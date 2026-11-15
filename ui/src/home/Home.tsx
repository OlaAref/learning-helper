import { useEffect, useState, type ReactNode } from 'react'
import type { Theme } from '../shared/theme'
import { ArrowIcon, BigSyncIcon, DatabaseIcon, GridIcon, MoonIcon, SunIcon } from '../shared/Icons'
import { fetchServices, type ServiceStatus } from './api'

type Project = {
  id: ServiceStatus['id']
  href: string
  name: string
  description: string
  screens: string[]
  icon: ReactNode
}

const PROJECTS: Project[] = [
  {
    id: 'notion',
    href: '#/notion',
    name: 'Notion Sync',
    description:
      "Mirror Notion pages and sub-pages to local Markdown files, with a page tree that matches Notion's structure.",
    screens: ['Workspace', 'New page', 'Connect & sync'],
    icon: <BigSyncIcon />,
  },
  {
    id: 'db',
    href: '#/db',
    name: 'DB Practice Lab',
    description:
      'Quizzes and hands-on tasks for each lecture of Fundamentals of Database Engineering, checked against a throwaway PostgreSQL database.',
    screens: ['Overview', 'Lecture + quiz', 'Concurrency task', 'Crash task'],
    icon: <DatabaseIcon />,
  },
]

export default function Home({ theme, onToggleTheme }: { theme: Theme; onToggleTheme: () => void }) {
  const [services, setServices] = useState<ServiceStatus[] | null | undefined>(undefined)

  useEffect(() => {
    let alive = true
    const load = () => fetchServices().then((s) => alive && setServices(s))
    load()
    const timer = setInterval(load, 5000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [])

  const statusOf = (id: Project['id']) => {
    if (services === undefined) return null
    const up = services?.find((s) => s.id === id)?.up ?? false
    return up ? <span className="chip ok">Running</span> : <span className="chip warn">Service not running</span>
  }

  return (
    <div className={`app ${theme} home`}>
      <div className="home-inner">
        <header className="home-head">
          <div>
            <div className="home-eyebrow">
              <span className="home-logo" aria-hidden="true"><GridIcon /></span>
              <span className="label">My projects</span>
            </div>
            <h1 className="home-title">Choose a project</h1>
            <p className="home-lead">Open a project to continue working in its screens.</p>
          </div>
          <button className="home-theme" onClick={onToggleTheme} aria-label="Switch between light and dark mode">
            {theme === 'dk' ? <SunIcon /> : <MoonIcon />}
            {theme === 'dk' ? 'Light mode' : 'Dark mode'}
          </button>
        </header>

        <div className="home-grid">
          {PROJECTS.map((p) => (
            <a key={p.id} className="home-card" href={p.href}>
              <div className="home-cardtop">
                <span className="home-icon" aria-hidden="true">{p.icon}</span>
                {statusOf(p.id)}
              </div>
              <div>
                <h2 className="home-name">{p.name}</h2>
                <p className="home-desc">{p.description}</p>
              </div>
              <div className="home-tags">
                {p.screens.map((s) => <span key={s} className="home-tag">{s}</span>)}
              </div>
              <span className="home-open">Open {p.name}<ArrowIcon /></span>
            </a>
          ))}
        </div>
      </div>
    </div>
  )
}
