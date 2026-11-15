import type { SectionView } from '../api'
import { hrefFor } from '../router'

export default function Home({ sections }: { sections: SectionView[] }) {
  return (
    <div className="page">
      <h1 className="pagetitle">Practice lab</h1>
      <p className="muted lead">
        Pick a lecture to take its quiz and run its tasks against real PostgreSQL servers. Progress is stored locally in
        <code> data/progress.yml</code>.
      </p>
      {sections.map((s) => {
        const quizTotal = s.lectures.reduce((n, l) => n + l.quizTotal, 0)
        const quizCorrect = s.lectures.reduce((n, l) => n + l.quizCorrect, 0)
        const tasks = s.lectures.flatMap((l) => l.tasks)
        return (
          <section key={s.id} className="card">
            <div className="label">{s.course}</div>
            <h2 className="cardtitle">{s.title}</h2>
            <div className="metaline">
              <span className="chip ok">
                Quiz {quizCorrect}/{quizTotal}
              </span>
              <span className="chip ok">
                Tasks {tasks.filter((t) => t.done).length}/{tasks.length}
              </span>
              {s.notes && <span className="muted small">Notes: {s.notes}</span>}
            </div>
            <ul className="lecturelist">
              {s.lectures.map((l) => (
                <li key={l.id}>
                  <a href={hrefFor({ name: 'lecture', section: s.id, lecture: l.id })}>
                    {l.number}. {l.title}
                  </a>
                  <span className="muted small">
                    {l.quizTotal > 0 ? `quiz ${l.quizCorrect}/${l.quizTotal}` : 'no quiz'}
                    {l.tasks.length > 0 ? ` · tasks ${l.tasks.filter((t) => t.done).length}/${l.tasks.length}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )
      })}
      {sections.length === 0 && <p className="muted">No content found in the content/ folder.</p>}
    </div>
  )
}
