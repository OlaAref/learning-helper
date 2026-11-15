export { renderMarkdown } from '../shared/markdown'

/** File name a downloaded link gets on disk. Must stay identical to Assets.nameFor on the Java side. */
export function assetName(url: string): string {
  const scheme = url.indexOf('://')
  const rest = scheme >= 0 ? url.slice(scheme + 3) : url
  const slash = rest.indexOf('/')
  let path = slash >= 0 ? rest.slice(slash) : '/'
  const cut = path.search(/[?#]/)
  if (cut >= 0) path = path.slice(0, cut)
  let last = path.slice(path.lastIndexOf('/') + 1).replace(/[^A-Za-z0-9._-]/g, '_')
  if (last.length > 60) last = last.slice(last.length - 60)
  let h = 0x811c9dc5
  for (let i = 0; i < path.length; i++) {
    h ^= path.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return `${(h >>> 0).toString(16).padStart(8, '0')}-${last}`
}

/**
 * Turns Notion's <image source="..."> tags and https Markdown images into Markdown images that point at the
 * copies downloaded next to the page, so they keep working after Notion's signed links expire.
 * Images that were not downloaded are left as they are.
 */
export function localizeImages(markdown: string, pageId: string, assets: string[]): string {
  const have = new Set(assets)
  const local = (rawUrl: string): string | null => {
    const name = assetName(rawUrl.replaceAll('&amp;', '&'))
    return have.has(name) ? `/api/notion/assets/${encodeURIComponent(pageId)}/${name}` : null
  }
  const alt = (s: string) => s.replace(/[\[\]\r\n]+/g, ' ').trim()
  return markdown
    .replace(/!\[([^\]]*)\]\((?:\.\/)?assets\/([A-Za-z0-9._-]+)\)/g, (_all, a: string, name: string) =>
      `![${a}](/api/notion/assets/${encodeURIComponent(pageId)}/${name})`)
    .replace(/<image\s+source="([^"]+)"[^>]*?(?:\/>|>([\s\S]*?)<\/image>)/g, (all, url: string, caption?: string) => {
      const l = local(url)
      return l ? `![${alt(caption ?? '')}](${l})` : all
    })
    .replace(/!\[([^\]]*)\]\((https:\/\/[^)\s]+)\)/g, (all, a: string, url: string) => {
      const l = local(url)
      return l ? `![${a}](${l})` : all
    })
}
