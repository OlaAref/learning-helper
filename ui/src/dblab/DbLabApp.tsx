import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Theme } from '../shared/theme'
import { useRoute } from './router'
import Sidebar from './components/Sidebar'
import Home from './components/Home'
import LecturePage from './components/LecturePage'
import TaskRunner from './components/TaskRunner'
import './dblab.css'

export default function DbLabApp({ theme, onToggleTheme }: { theme: Theme; onToggleTheme: () => void }) {
  const route = useRoute()
  const overview = useQuery({ queryKey: ['overview'], queryFn: api.overview })

  return (
    <div className={`app ${theme} f-db`}>
      <Sidebar sections={overview.data ?? []} route={route} theme={theme} onToggleTheme={onToggleTheme} />
      <main className="main">
        {overview.isError && (
          <div className="banner error">Cannot load content: {(overview.error as Error).message}</div>
        )}
        {route.name === 'home' && <Home sections={overview.data ?? []} />}
        {route.name === 'lecture' && (
          <LecturePage key={`${route.section}/${route.lecture}`} section={route.section} lecture={route.lecture} />
        )}
        {route.name === 'task' && (
          <TaskRunner
            key={`${route.section}/${route.lecture}/${route.task}`}
            section={route.section}
            lecture={route.lecture}
            task={route.task}
          />
        )}
      </main>
    </div>
  )
}

