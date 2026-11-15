import DOMPurify from 'dompurify'
import { marked } from 'marked'

/**
 * Renders Markdown (including the inline HTML that Notion-flavored Markdown uses, such as <b> and <p>) to HTML
 * that is safe to show. Anything executable (script tags, event handlers, javascript: links) is removed.
 */
export function renderMarkdown(markdown: string): string {
  const html = marked.parse(markdown, { async: false, gfm: true }) as string
  return DOMPurify.sanitize(html)
}
