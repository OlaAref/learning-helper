import type { Status } from '../api'

export default function SettingsPanel({ status, onClose }: { status: Status | undefined; onClose: () => void }) {
  const rows: [string, string][] = status
    ? [
        ['Notion token', status.connected ? 'Set (from NOTION_TOKEN)' : 'Not set'],
        ['Local folder', status.localFolder],
        ['Sync mode', status.mode],
        ['Poll interval', `${status.intervalSeconds} seconds`],
        ['Push to Notion', status.pushEnabled ? 'On' : 'Off'],
      ]
    : []
  return (
    <div className="overlay" onClick={onClose}>
      <div className="dialog" role="dialog" aria-modal="true" aria-label="Connection and sync" onClick={(e) => e.stopPropagation()}>
        <h1>Connection &amp; sync</h1>
        <dl className="settings">
          {rows.map(([k, v]) => (
            <div key={k}><dt>{k}</dt><dd className={k === 'Local folder' ? 'mono' : ''}>{v}</dd></div>
          ))}
        </dl>
        <p className="muted small">
          These come from application.yml and the NOTION_TOKEN environment variable. Change them there and restart the app.
          The token is never shown or stored by this screen.
        </p>
        <div className="actions"><button className="btn primary" onClick={onClose}>Close</button></div>
      </div>
    </div>
  )
}
