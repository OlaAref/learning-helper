import { useEffect, useRef, useState } from 'react'
import type { SessionView } from '../api'
import StatementOutput from './ResultTable'

type Props = {
  session: SessionView
  disabled: boolean
  onRun: (sql: string) => void
  onCancel: () => void
}

/** One terminal: a SQL box on top, the history of submissions below (newest last). */
export default function SessionPanel({ session, disabled, onRun, onCancel }: Props) {
  const [sql, setSql] = useState('')
  const endRef = useRef<HTMLDivElement>(null)
  const running = session.entries.some((e) => e.status === 'RUNNING')

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'nearest' })
  }, [session.entries])

  function run() {
    if (!sql.trim() || disabled) return
    onRun(sql)
    setSql('')
  }

  return (
    <section className="session" aria-label={`Session ${session.id}`}>
      <header className="sessionhead">
        <strong>Session {session.id}</strong>
        {running && <span className="chip warn">running…</span>}
        <span className="spacer" />
        {running && (
          <button className="btn small" onClick={onCancel}>
            Cancel
          </button>
        )}
      </header>
      <div className="history">
        {session.entries.length === 0 && <div className="muted small pad">Nothing run yet.</div>}
        {session.entries.map((e) => (
          <div key={e.index} className={`entry ${e.status.toLowerCase()}`}>
            <pre className="entrysql">{e.sql.trim()}</pre>
            {e.statements.map((s, i) => (
              <StatementOutput key={i} result={s} />
            ))}
            {e.status === 'RUNNING' && (
              <div className="muted small">Waiting… (blocked by a lock held by another session?)</div>
            )}
            {e.status !== 'RUNNING' && <div className="muted small">{e.millis} ms</div>}
          </div>
        ))}
        <div ref={endRef} />
      </div>
      <textarea
        className="sqlbox mono"
        rows={4}
        spellCheck={false}
        value={sql}
        placeholder="Type SQL, Ctrl+Enter to run (BEGIN / COMMIT work like in psql)"
        aria-label={`SQL for session ${session.id}`}
        onChange={(e) => setSql(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault()
            run()
          }
        }}
      />
      <div className="actions">
        <button className="btn primary" disabled={disabled || !sql.trim()} onClick={run}>
          Run
        </button>
      </div>
    </section>
  )
}
