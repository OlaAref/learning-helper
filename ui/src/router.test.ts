import { describe, expect, it } from 'vitest'
import { areaOf } from './router'

describe('areaOf', () => {
  it('opens the home screen by default', () => {
    expect(areaOf('')).toBe('home')
    expect(areaOf('#/')).toBe('home')
    expect(areaOf('#/nonsense')).toBe('home')
  })

  it('sends each prefix to its project', () => {
    expect(areaOf('#/notion')).toBe('notion')
    expect(areaOf('#/notion/page/abc')).toBe('notion')
    expect(areaOf('#/db')).toBe('db')
    expect(areaOf('#/db/task/acid/l10/t1')).toBe('db')
  })
})
