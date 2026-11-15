import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type CheckResponse, type LabView, type SolutionView } from '../api'
import { hrefFor } from '../router'
import { renderMarkdown } from '../../shared/markdown'
import SessionPanel from './SessionPanel'

type Props = { section: string; lecture: string; task: string }

const anyRunning = (lab?: LabView) => !!lab?.sessions.some((s) => s.entries.some((e) => e.status === 'RUNNING'))

export default function TaskRunner({ section, lecture, task }: Props) {
  const qc = useQueryClient()
  const taskQ = useQuery({ queryKey: ['task', section, lecture, task], queryFn: () => api.task(section, lecture, task) })
  const [labId, setLabId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [report, setReport] = useState<CheckResponse | null>(null)
  const [hintsShown, setHintsShown] = useState(0)
  const [solution, setSolution] = useState<SolutionView | null>(null)
  const [confirmSolution, setConfirmSolution] = useState(false)
  const started = useRef(false)

  const labQ = useQuery({
    queryKey: ['lab', labId],
    enabled: labId !== null,
    queryFn: () => api.lab(labId as string),
    refetchInterval: (query) => (anyRunning(query.state.data) ? 700 : false),
  })
  const lab = labQ.data

  const open = useMutation({
    mutationFn: () => api.openLab(section, lecture, task),
    onSuccess: (view) => {
      qc.setQueryData(['lab', view.id], view)
      setLabId(view.id)
      setReport(null)
      setError(null)
    },
    onError: (e) => setError((e as Error).message),
  })

  // One lab per visit. (Opening again for the same task replaces the old lab on the server.)
  useEffect(() => {
    if (started.current) return
    started.current = true
    open.mutate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const apply = (view: LabView) => qc.setQueryData(['lab', view.id], view)
  const run = useMutation({
    mutationFn: (a: { session: string; sql: string }) => api.execute(labId as string, a.session, a.sql),
    onSuccess: apply,
    onError: (e) => setError((e as Error).message),
  })
  const cancel = useMutation({ mutationFn: (session: string) => api.cancel(labId as string, session), onSuccess: apply })
  const chaos = useMutation({
    mutationFn: (action: 'kill' | 'start') => api.chaos(labId as string, action),
    onSuccess: (v) => {
      apply(v)
      setError(null)
    },
    onError: (e) => setError((e as Error).message),
  })
  const check = useMutation({
    mutationFn: () => api.check(labId as string),
    onSuccess: (r) => {
      setReport(r)
      setError(null)
      qc.invalidateQueries({ queryKey: ['overview'] })
      qc.invalidateQueries({ queryKey: ['task', section, lecture, task] })
    },
    onError: (e) => setError((e as Error).message),
  })

  const t = taskQ.data
  const backToLecture = hrefFor({ name: 'lecture', section, lecture })

  async function showSolution() {
    try {
      setSolution(await api.solution(section, lecture, task))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  function reset() {
    setSolution(null)
    setConfirmSolution(false)
    setHintsShown(0)
    open.mutate()
  }

  if (taskQ.isLoading) return <div className="page muted">Loading task…</div>
  if (taskQ.isError || !t) return <div className="page error">{(taskQ.error as Error)?.message ?? 'Unknown task'}</div>

  return (
    <div className="page wide">
      <a className="link" href={backToLecture}>
        ← {t.lectureTitle}
      </a>
      <div className="metaline">
        <h1 className="pagetitle tight">{t.title}</h1>
        <span className="chip neutral">{t.kind}</span>
        <span className="muted small">{t.difficulty}</span>
        {t.done && <span className="chip ok">done</span>}
      </div>

      <div className="card">
        <div className="prose" dangerouslySetInnerHTML={{ __html: renderMarkdown(t.statement) }} />
        {t.hints.length > 0 && (
          <div className="hints">
            {t.hints.slice(0, hintsShown).map((h, i) => (
              <div key={i} className="banner hintbanner">
                <strong>Hint {i + 1}:</strong> {h}
              </div>
            ))}
            {hintsShown < t.hints.length && (
              <button className="link" onClick={() => setHintsShown(hintsShown + 1)}>
                Show hint ({hintsShown + 1}/{t.hints.length})
              </button>
            )}
          </div>
        )}
      </div>

      {t.kind === 'crash' && (
        <div className="chaosbar">
          <strong>Server</strong>
          <button className="btn danger" disabled={!lab || chaos.isPending} onClick={() => chaos.mutate('kill')}>
            Kill server (hard crash)
          </button>
          <button className="btn" disabled={!lab || chaos.isPending} onClick={() => chaos.mutate('start')}>
            Start server
          </button>
          <span className="muted small">
            {chaos.isPending ? 'working…' : `killed ${lab?.kills ?? 0} time${lab?.kills === 1 ? '' : 's'}`}
          </span>
        </div>
      )}

      {error && <div className="banner error">{error}</div>}
      {labQ.isError && <div className="banner error">{(labQ.error as Error).message}</div>}
      {open.isPending && <p className="muted">Creating a fresh database…</p>}

      {lab && (
        <div className={`sessions cols${lab.sessions.length}`}>
          {lab.sessions.map((s) => (
            <SessionPanel
              key={s.id}
              session={s}
              disabled={run.isPending}
              onRun={(sql) => run.mutate({ session: s.id, sql })}
              onCancel={() => cancel.mutate(s.id)}
            />
          ))}
        </div>
      )}

      <div className="card">
        <div className="actions">
          <button className="btn primary" disabled={!lab || check.isPending} onClick={() => check.mutate()}>
            Check my work
          </button>
          <button className="btn" disabled={open.isPending} onClick={reset}>
            Reset lab
          </button>
          {!solution && !confirmSolution && (
            <button className="link" onClick={() => setConfirmSolution(true)}>
              Show solution
            </button>
          )}
          {!solution && confirmSolution && (
            <span className="small">
              Try a bit longer first?{' '}
              <button className="link" onClick={showSolution}>
                Yes, show it
              </button>{' '}
              <button className="link" onClick={() => setConfirmSolution(false)}>
                No
              </button>
            </span>
          )}
        </div>
        <ul className="checks">
          {t.checks.map((desc, i) => {
            const item = report?.items[i]
            return (
              <li key={i} className={item ? (item.passed ? 'pass' : 'fail') : ''}>
                <span className="mark">{item ? (item.passed ? '✓' : '✗') : '○'}</span>
                <span>
                  {desc}
                  {item && !item.passed && item.detail && <div className="muted small">{item.detail}</div>}
                </span>
              </li>
            )
          })}
        </ul>
        {report?.passed && <div className="banner okbanner">All checks pass. Task completed.</div>}
      </div>

      {solution && (
        <div className="card">
          <h3 className="cardtitle">Reference solution</h3>
          {solution.steps.map((s, i) => (
            <div key={i} className="solstep">
              <div className="label">{s.chaos ? `Server: ${s.chaos}` : `Session ${s.session}`}</div>
              {s.sql && <pre className="code">{s.sql.trim()}</pre>}
            </div>
          ))}
          {solution.explanation && <div className="prose" dangerouslySetInnerHTML={{ __html: renderMarkdown(solution.explanation) }} />}
        </div>
      )}
    </div>
  )
}
