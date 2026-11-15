import { describe, expect, it } from 'vitest'
import { hrefFor, parseHash } from './router'

describe('router', () => {
  it('falls back to home', () => {
    expect(parseHash('')).toEqual({ name: 'home' })
    expect(parseHash('#/nonsense/1')).toEqual({ name: 'home' })
  })

  it('round-trips lecture and task routes', () => {
    const lecture = { name: 'lecture', section: 'acid', lecture: 'l08-transaction' } as const
    expect(parseHash(hrefFor(lecture))).toEqual(lecture)
    const task = { name: 'task', section: 'acid', lecture: 'l10-isolation', task: 't1-no-dirty-read' } as const
    expect(parseHash(hrefFor(task))).toEqual(task)
  })
})
