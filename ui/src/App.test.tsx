import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import App from './App'

const json = (body: unknown) => Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))

/** Answers the calls each screen makes on load, and records every URL so tests can check which service was used. */
function mockBackend(): string[] {
  const calls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      calls.push(url)
      if (url === '/api/services') {
        return json([
          { id: 'notion', name: 'Notion Sync', up: true },
          { id: 'db', name: 'DB Practice Lab', up: false },
        ])
      }
      if (url === '/api/notion/status') {
        return json({ connected: true, localFolder: 'D:/x', mode: 'auto', intervalSeconds: 60, pushEnabled: true })
      }
      if (url === '/api/notion/pages') return json([])
      if (url === '/api/db/overview') {
        return json([{ id: 'acid', course: 'Course', title: 'ACID', notes: '', lectures: [{ id: 'l08', number: 8, title: 'Transactions', quizTotal: 3, quizCorrect: 1, tasks: [] }] }])
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    }),
  )
  vi.stubGlobal('EventSource', class { addEventListener() {} close() {} })
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  return calls
}

beforeEach(() => vi.unstubAllGlobals())
afterEach(() => {
  cleanup()
  window.location.hash = ''
})

describe('App', () => {
  it('lists both projects on the home screen with their service status', async () => {
    mockBackend()
    window.location.hash = '#/'
    render(<App />)
    expect((screen.getByText('Open Notion Sync').closest('a') as HTMLAnchorElement).getAttribute('href')).toBe('#/notion')
    expect((screen.getByText('Open DB Practice Lab').closest('a') as HTMLAnchorElement).getAttribute('href')).toBe('#/db')
    expect(await screen.findByText('Running')).toBeTruthy()
    expect(screen.getByText('Service not running')).toBeTruthy()
  })

  it('opens the Notion project against notion-service only, with a way back', async () => {
    const calls = mockBackend()
    window.location.hash = '#/notion'
    render(<App />)
    expect(await screen.findByText('Connected workspace')).toBeTruthy()
    expect((screen.getByText('All projects').closest('a') as HTMLAnchorElement).getAttribute('href')).toBe('#/')
    expect(calls.some((u) => u.startsWith('/api/db/'))).toBe(false)
  })

  it('opens the DB lab against db-lab-service only, with a way back', async () => {
    const calls = mockBackend()
    window.location.hash = '#/db'
    render(<App />)
    expect(await screen.findByText('8. Transactions', { selector: '.ellipsis' })).toBeTruthy()
    expect((screen.getByText('All projects').closest('a') as HTMLAnchorElement).getAttribute('href')).toBe('#/')
    expect(calls.some((u) => u.startsWith('/api/notion/'))).toBe(false)
  })
})
