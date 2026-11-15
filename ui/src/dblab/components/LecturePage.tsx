import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { hrefFor } from '../router'
import QuizPlayer from './QuizPlayer'

const KIND_LABEL: Record<string, string> = { sql: 'SQL', concurrency: 'Concurrency', crash: 'Crash' }

export default function LecturePage({ section, lecture }: { section: string; lecture: string }) {
  const overview = useQuery({ queryKey: ['overview'], queryFn: api.overview })
  const sec = overview.data?.find((s) => s.id === section)
  const lec = sec?.lectures.find((l) => l.id === lecture)

  return (
    <div className="page">
      <div className="label">{sec?.title ?? section}</div>
      <h1 className="pagetitle">{lec ? `${lec.number}. ${lec.title}` : lecture}</h1>

      <h2 className="subhead">Quiz</h2>
      {lec && lec.quizTotal === 0 ? <p className="muted">No quiz for this lecture.</p> : <QuizPlayer section={section} lecture={lecture} />}

      <h2 className="subhead">Hands-on tasks</h2>
      {lec && lec.tasks.length === 0 && <p className="muted">No tasks for this lecture.</p>}
      <ul className="tasklist">
        {lec?.tasks.map((t) => (
          <li key={t.id}>
            <a className="taskrow" href={hrefFor({ name: 'task', section, lecture, task: t.id })}>
              <span className={`dot ${t.done ? 'ok' : ''}`} />
              <span className="ellipsis">{t.title}</span>
              <span className="chip neutral">{KIND_LABEL[t.kind] ?? t.kind}</span>
              <span className="muted small">{t.difficulty}</span>
              {t.done && <span className="chip ok">done</span>}
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}
