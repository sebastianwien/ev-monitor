// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { renderStoryMarkdown } from '../storyMarkdown'

describe('renderStoryMarkdown', () => {
    it('renders basic markdown to HTML', () => {
        const html = renderStoryMarkdown('# Titel\n\nEin **fetter** Text.')
        expect(html).toContain('<h1>')
        expect(html).toContain('<strong>fetter</strong>')
    })

    it('strips script tags (XSS)', () => {
        const html = renderStoryMarkdown('Hallo <script>alert(1)</script> Welt')
        expect(html).not.toContain('<script')
        expect(html).not.toContain('alert(1)')
    })

    it('strips inline event handlers (XSS)', () => {
        const html = renderStoryMarkdown('<img src="x" onerror="alert(1)">')
        expect(html).not.toContain('onerror')
    })

    it('strips iframes', () => {
        const html = renderStoryMarkdown('<iframe src="https://evil.example"></iframe>')
        expect(html).not.toContain('<iframe')
    })

    it('marks external links as nofollow ugc and opens them in a new tab', () => {
        const html = renderStoryMarkdown('[Reddit](https://reddit.com/r/Elektroautos)')
        expect(html).toContain('href="https://reddit.com/r/Elektroautos"')
        expect(html).toContain('rel="nofollow ugc noopener noreferrer"')
        expect(html).toContain('target="_blank"')
    })

    it('blocks javascript: URLs', () => {
        const html = renderStoryMarkdown('[klick](javascript:alert(1))')
        expect(html).not.toContain('javascript:')
    })

    it('returns empty string for empty input', () => {
        expect(renderStoryMarkdown('')).toBe('')
        expect(renderStoryMarkdown(undefined as unknown as string)).toBe('')
    })
})
