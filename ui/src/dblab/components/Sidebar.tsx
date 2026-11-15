import type { SectionView } from '../api'
import { hrefFor, type Route } from '../router'
import { BackIcon } from '../../shared/Icons'
import type { Theme } from '../../shared/theme'

type Props = { sections: SectionView[]; route: Route; theme: Theme; onToggleTheme: () => void }

export default function Sidebar({ sections, route, theme, onToggleTheme }: Props) {
  return (
    <aside className="sidebar">
      <a className="brand" href={hrefFor({ name: 'home' })}>
        <span className="logo">DB</span>
        <span className="brandtext">
          <span className="brandname">DB Practice Lab</span>
          <span className="muted small">Quizzes and hands-on tasks</span>
        </span>
      </a>
      <nav className="tree" aria-label="Course sections">
        {sections.map((s) => (
          <div key={s.id}>
            <div className="label sectionlabel">{s.title}</div>
            {s.lectures.map((l) => {
              const tasksDone = l.tasks.filter((t) => t.done).length
              const complete = l.quizTotal + l.tasks.length > 0 && l.quizCorrect === l.quizTotal && tasksDone === l.tasks.length
              const selected = route.name !== 'home' && route.section === s.id && route.lecture === l.id
              return (
                <a
                  key={l.id}
                  className={`navrow ${selected ? 'selected' : ''}`}
                  href={hrefFor({ name: 'lecture', section: s.id, lecture: l.id })}
                >
                  <span className={`dot ${complete ? 'ok' : ''}`} />
                  <span className="ellipsis">
                    {l.number}. {l.title}
                  </span>
                  <span className="muted small">
                    {l.quizCorrect}/{l.quizTotal}
                    {l.tasks.length > 0 ? ` · ${tasksDone}/${l.tasks.length}` : ''}
                  </span>
                </a>
              )
            })}
          </div>
        ))}
      </nav>
      <div className="sidefoot">
        <button className="link" onClick={onToggleTheme}>
          {theme === 'dk' ? 'Light theme' : 'Dark theme'}
        </button>
        <a className="link backlink" href="#/">
          <BackIcon />All projects
        </a>
      </div>
    </aside>
  )
}
