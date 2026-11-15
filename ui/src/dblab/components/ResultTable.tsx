import type { StatementResult } from '../api'

/** One statement's outcome, shown the way psql would: a result grid, an OK line, or an error. */
export default function StatementOutput({ result }: { result: StatementResult }) {
  if (result.error) {
    return (
      <div className="stmterror">
        <strong>ERROR</strong> <span className="mono">[{result.error.sqlstate}]</span> {result.error.message}
      </div>
    )
  }
  if (result.columns && result.rows) {
    return (
      <div className="tablewrap">
        <table className="grid">
          <thead>
            <tr>
              {result.columns.map((c, i) => (
                <th key={i}>{c}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {result.rows.map((row, r) => (
              <tr key={r}>
                {row.map((cell, c) => (
                  <td key={c} className={cell === null ? 'nullcell' : ''}>
                    {cell === null ? 'NULL' : cell}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        <div className="muted small">
          {result.rows.length} row{result.rows.length === 1 ? '' : 's'}
          {result.truncated ? ' (more rows were cut off)' : ''}
        </div>
      </div>
    )
  }
  return (
    <div className="muted small">
      OK{result.updateCount ? ` (${result.updateCount} row${result.updateCount === 1 ? '' : 's'} affected)` : ''}
    </div>
  )
}
