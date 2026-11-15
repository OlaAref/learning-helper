import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('renders inline code and lists', () => {
    const html = renderMarkdown('1. run `BEGIN`\n2. run `COMMIT`')
    expect(html).toContain('<ol>')
    expect(html).toContain('<code>BEGIN</code>')
  })

  it('removes scripts and event handlers', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)"><script>alert(1)</script>ok')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('<script')
    expect(html).toContain('ok')
  })
})
