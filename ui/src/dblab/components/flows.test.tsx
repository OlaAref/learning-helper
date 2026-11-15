import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import QuizPlayer from './QuizPlayer'
import TaskRunner from './TaskRunner'

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

const json = (body: unknown) => Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))

type Handler = (url: string, init?: RequestInit) => Promise<Response> | undefined

function mockFetch(handlers: Handler[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      for (const h of handlers) {
        const r = h(url, init)
        if (r) return r
      }
      return Promise.resolve(new Response(JSON.stringify({ message: `unexpected ${init?.method ?? 'GET'} ${url}` }), { status: 500 }))
    }),
  )
}

beforeEach(() => vi.unstubAllGlobals())
afterEach(() => cleanup())

describe('QuizPlayer', () => {
  it('shows the explanation after checking an answer and moves on', async () => {
    mockFetch([
      (u) =>
        u === '/api/db/quiz/acid/l08'
          ? json({
              section: 'acid',
              lecture: 'l08',
              lectureTitle: 'Transactions',
              questions: [
                { id: 'q1', kind: 'mcq', prompt: 'What is a transaction?', code: '', options: ['A row', 'A unit of work'], answered: false, correct: false },
              ],
            })
          : undefined,
      (u, i) =>
        u === '/api/db/quiz/acid/l08/q1/answer' && i?.method === 'POST' && i.body === JSON.stringify({ choice: 1 })
          ? json({ correct: true, correctIndex: 1, explanation: 'Queries succeed or fail **together**.' })
          : undefined,
      (u) => (u === '/api/db/overview' ? json([]) : undefined),
    ])
    wrap(<QuizPlayer section="acid" lecture="l08" />)
    fireEvent.click(await screen.findByLabelText('A unit of work'))
    fireEvent.click(screen.getByText('Check answer'))
    expect(await screen.findByText('Correct.')).toBeTruthy()
    expect(screen.getByText('together').tagName).toBe('STRONG')
    fireEvent.click(screen.getByText('Finish'))
    expect(await screen.findByText('Quiz finished')).toBeTruthy()
  })
})

describe('TaskRunner', () => {
  const task = {
    section: 'acid', lecture: 'l10', lectureTitle: 'Isolation', id: 't5', title: 'Fix the lost update', kind: 'concurrency',
    target: 'main', difficulty: 'hard', sessions: ['A', 'B'], statement: 'Use `FOR UPDATE`.', hints: ['Lock the row'],
    checks: ['Final qty is 25'], done: false,
  }
  const emptyLab = { id: 'lab1', taskKey: 'acid/l10/t5', target: 'main', kills: 0, sessions: [{ id: 'A', entries: [] }, { id: 'B', entries: [] }] }

  it('opens a lab, runs SQL, polls a blocked statement until it finishes, and grades', async () => {
    let polls = 0
    const blocked = { index: 1, sql: 'SELECT 1', status: 'RUNNING', millis: 0, statements: [] }
    const finished = {
      index: 1, sql: 'SELECT 1', status: 'OK', millis: 12,
      statements: [{ sql: 'SELECT 1', columns: ['?column?'], rows: [['1']], truncated: false, updateCount: null, error: null }],
    }
    mockFetch([
      (u) => (u === '/api/db/tasks/acid/l10/t5' ? json(task) : undefined),
      (u, i) => (u === '/api/db/tasks/acid/l10/t5/lab' && i?.method === 'POST' ? json(emptyLab) : undefined),
      (u, i) =>
        u === '/api/db/labs/lab1/sessions/B/execute' && i?.method === 'POST'
          ? json({ ...emptyLab, sessions: [emptyLab.sessions[0], { id: 'B', entries: [blocked] }] })
          : undefined,
      (u) => {
        if (u !== '/api/db/labs/lab1') return undefined
        polls++
        return json({ ...emptyLab, sessions: [emptyLab.sessions[0], { id: 'B', entries: [finished] }] })
      },
      (u, i) =>
        u === '/api/db/labs/lab1/check' && i?.method === 'POST'
          ? json({ passed: false, items: [{ description: 'Final qty is 25', passed: false, detail: 'expected [[25]] but committed data is [[20]]' }] })
          : undefined,
      (u) => (u === '/api/db/overview' ? json([]) : undefined),
    ])
    wrap(<TaskRunner section="acid" lecture="l10" task="t5" />)

    const boxB = await screen.findByLabelText('SQL for session B')
    fireEvent.change(boxB, { target: { value: 'SELECT 1' } })
    fireEvent.keyDown(boxB, { key: 'Enter', ctrlKey: true })

    // the blocked statement is shown as running, then the poll brings the result
    expect(await screen.findByText(/Waiting…/)).toBeTruthy()
    await waitFor(() => expect(screen.getByText('12 ms')).toBeTruthy(), { timeout: 3000 })
    expect(polls).toBeGreaterThan(0)
    expect(screen.getByText('1 row')).toBeTruthy()

    fireEvent.click(screen.getByText('Check my work'))
    expect(await screen.findByText(/committed data is \[\[20\]\]/)).toBeTruthy()
  })

  it('shows kill and start controls only for crash tasks', async () => {
    mockFetch([
      (u) => (u === '/api/db/tasks/acid/l10/t5' ? json({ ...task, kind: 'crash', sessions: ['A'] }) : undefined),
      (u, i) => (u === '/api/db/tasks/acid/l10/t5/lab' && i?.method === 'POST' ? json({ ...emptyLab, target: 'crash', sessions: [emptyLab.sessions[0]] }) : undefined),
      (u, i) =>
        u === '/api/db/labs/lab1/chaos/kill' && i?.method === 'POST'
          ? json({ ...emptyLab, target: 'crash', kills: 1, sessions: [emptyLab.sessions[0]] })
          : undefined,
    ])
    wrap(<TaskRunner section="acid" lecture="l10" task="t5" />)
    const kill = await screen.findByText('Kill server (hard crash)')
    await waitFor(() => expect((kill as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(kill)
    expect(await screen.findByText('killed 1 time')).toBeTruthy()
  })
})
