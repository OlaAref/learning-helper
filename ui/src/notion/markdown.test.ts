import { describe, expect, it } from 'vitest'
import { assetName, localizeImages, renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('keeps the inline HTML that Notion returns', () => {
    expect(renderMarkdown('Hello <b>world</b>')).toContain('<b>world</b>')
  })

  it('renders headings and lists', () => {
    const html = renderMarkdown('## Section\n\n- a\n- b')
    expect(html).toContain('<h2')
    expect(html).toContain('<li>a</li>')
  })

  it('removes scripts and event handlers', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)"><script>alert(2)</script>text')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('<script')
    expect(html).toContain('text')
  })

  it('removes javascript links', () => {
    expect(renderMarkdown('[x](javascript:alert(1))')).not.toContain('javascript:')
  })
})

describe('assetName', () => {
  it('matches the Java implementation', () => {
    expect(assetName('https://s3.us-west-2.amazonaws.com/secure.notion-static.com/eeaf8bbe-5b27-4cdd-a2fe-328be0cc3060/9d130837-27e2-445a-b486-9d4a6695844b/image.png?X-Amz-Signature=abc')).toMatch(/^[0-9a-f]{8}-image\.png$/)
    expect(assetName('https://h.amazonaws.com/a b/Untitled diagram (1).png?x=1#frag')).toBe('c7faac13-Untitled_diagram__1_.png')
  })
})

describe('localizeImages local files', () => {
  it('shows images that were just added to assets/', () => {
    expect(localizeImages('![cap](assets/new.png)', 'p1', [])).toBe('![cap](/api/notion/assets/p1/new.png)')
  })
})

describe('localizeImages', () => {
  const url = 'https://h.amazonaws.com/a b/Untitled diagram (1).png?X-Amz-Signature=1&amp;x=2'
  const name = 'c7faac13-Untitled_diagram__1_.png'
  it('rewrites downloaded images and keeps the rest', () => {
    const md = `<image source="${url}">A caption</image>\n\n<image source="https://h.amazonaws.com/other.png"></image>`
    const out = localizeImages(md, 'p1', [name])
    expect(out).toContain(`![A caption](/api/notion/assets/p1/${name})`)
    expect(out).toContain('<image source="https://h.amazonaws.com/other.png">')
  })
  it('handles markdown images', () => {
    const out = localizeImages('![x](https://h.amazonaws.com/a b/x.png)'.replace(' ', '%20'), 'p1', [])
    expect(out).toContain('https://')
  })
  it('survives sanitising', () => {
    const html = renderMarkdown(localizeImages(`<image source="${url}"/>`, 'p1', [name]))
    expect(html).toContain(`src="/api/notion/assets/p1/${name}"`)
  })
})
