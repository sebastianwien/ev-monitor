/**
 * Markdown rendering for user-generated Trip-Stories.
 *
 * Unlike the editorial blog (controlled .md files in the repo), story markdown is
 * untrusted user input: it is rendered with `marked` and then sanitized with
 * DOMPurify. All links get rel="nofollow ugc noopener noreferrer" so user content
 * cannot pass link equity or open tabs with window.opener access.
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const purify = DOMPurify

// Force safe link attributes after sanitizing (DOMPurify removes nothing here, it
// just lets us rewrite attributes on every element it keeps).
purify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A') {
        node.setAttribute('rel', 'nofollow ugc noopener noreferrer')
        node.setAttribute('target', '_blank')
    }
})

export function renderStoryMarkdown(markdown: string): string {
    if (!markdown) return ''
    const rawHtml = marked.parse(markdown, { async: false }) as string
    return purify.sanitize(rawHtml, {
        ALLOWED_TAGS: ['h1', 'h2', 'h3', 'h4', 'p', 'br', 'hr', 'strong', 'em', 'del', 's',
            'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'a', 'table', 'thead', 'tbody',
            'tr', 'th', 'td'],
        ALLOWED_ATTR: ['href', 'rel', 'target'],
        ALLOWED_URI_REGEXP: /^(?:https?|mailto):/i,
    })
}
