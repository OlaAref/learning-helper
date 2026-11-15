import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useArea } from './router'
import { useTheme } from './shared/theme'
import Home from './home/Home'
import NotionApp from './notion/NotionApp'
import DbLabApp from './dblab/DbLabApp'

// Each project keeps its own cache, so refreshing one never refetches the other's service.
const notionQueries = new QueryClient()
const dbLabQueries = new QueryClient({ defaultOptions: { queries: { refetchOnWindowFocus: false, retry: false } } })

export default function App() {
  const [theme, toggleTheme] = useTheme()
  const area = useArea()

  if (area === 'notion') {
    return (
      <QueryClientProvider client={notionQueries}>
        <NotionApp theme={theme} onToggleTheme={toggleTheme} />
      </QueryClientProvider>
    )
  }
  if (area === 'db') {
    return (
      <QueryClientProvider client={dbLabQueries}>
        <DbLabApp theme={theme} onToggleTheme={toggleTheme} />
      </QueryClientProvider>
    )
  }
  return <Home theme={theme} onToggleTheme={toggleTheme} />
}
