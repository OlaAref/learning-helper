export type TaskSummary = { id: string; title: string; kind: string; difficulty: string; done: boolean; attempts: number }
export type LectureView = {
  id: string
  number: number
  title: string
  quizTotal: number
  quizCorrect: number
  tasks: TaskSummary[]
}
export type SectionView = { id: string; course: string; title: string; notes: string; lectures: LectureView[] }

export type QuestionView = {
  id: string
  kind: string
  prompt: string
  code: string
  options: string[]
  answered: boolean
  correct: boolean
}
export type QuizView = { section: string; lecture: string; lectureTitle: string; questions: QuestionView[] }
export type AnswerResult = { correct: boolean; correctIndex: number; explanation: string }

export type TaskView = {
  section: string
  lecture: string
  lectureTitle: string
  id: string
  title: string
  kind: 'sql' | 'concurrency' | 'crash'
  target: string
  difficulty: string
  sessions: string[]
  statement: string
  hints: string[]
  checks: string[]
  done: boolean
}
export type SolutionView = { explanation: string; steps: { session: string; sql: string; chaos: string }[] }

export type StatementResult = {
  sql: string
  columns: string[] | null
  rows: (string | null)[][] | null
  truncated: boolean
  updateCount: number | null
  error: { sqlstate: string; message: string } | null
}
export type EntryView = { index: number; sql: string; status: 'RUNNING' | 'OK' | 'ERROR'; millis: number; statements: StatementResult[] }
export type SessionView = { id: string; entries: EntryView[] }
export type LabView = { id: string; taskKey: string; target: string; kills: number; sessions: SessionView[] }
export type CheckResponse = { passed: boolean; items: { description: string; passed: boolean; detail: string }[] }

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...init })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.message) message = body.message
      else if (body?.error) message = body.error
    } catch {
      /* no JSON body */
    }
    throw new Error(message)
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

const enc = encodeURIComponent
const post = <T>(url: string, body?: unknown) =>
  request<T>(url, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const api = {
  overview: () => request<SectionView[]>('/api/db/overview'),
  quiz: (s: string, l: string) => request<QuizView>(`/api/db/quiz/${enc(s)}/${enc(l)}`),
  answer: (s: string, l: string, q: string, choice: number) =>
    post<AnswerResult>(`/api/db/quiz/${enc(s)}/${enc(l)}/${enc(q)}/answer`, { choice }),
  task: (s: string, l: string, t: string) => request<TaskView>(`/api/db/tasks/${enc(s)}/${enc(l)}/${enc(t)}`),
  solution: (s: string, l: string, t: string) =>
    request<SolutionView>(`/api/db/tasks/${enc(s)}/${enc(l)}/${enc(t)}/solution`),
  openLab: (s: string, l: string, t: string) => post<LabView>(`/api/db/tasks/${enc(s)}/${enc(l)}/${enc(t)}/lab`),
  lab: (id: string) => request<LabView>(`/api/db/labs/${enc(id)}`),
  execute: (id: string, session: string, sql: string) =>
    post<LabView>(`/api/db/labs/${enc(id)}/sessions/${enc(session)}/execute`, { sql }),
  cancel: (id: string, session: string) => post<LabView>(`/api/db/labs/${enc(id)}/sessions/${enc(session)}/cancel`),
  chaos: (id: string, action: 'kill' | 'start') => post<LabView>(`/api/db/labs/${enc(id)}/chaos/${action}`),
  check: (id: string) => post<CheckResponse>(`/api/db/labs/${enc(id)}/check`),
}
