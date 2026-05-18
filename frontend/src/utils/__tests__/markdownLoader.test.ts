import { describe, it, expect } from 'vitest'
import {
    parseMarkdown,
    sortPostsByDateDesc,
    loadAllPosts,
    findPostBySlug,
    type RawBlogPost,
} from '../markdownLoader'

describe('parseMarkdown', () => {
    it('parses frontmatter fields and body', () => {
        const raw = [
            '---',
            'title: "Hello world"',
            'description: "A description"',
            'slug: "hello-world"',
            'date: "2026-05-18"',
            'author: "Sebastian"',
            '---',
            '',
            '# Heading',
            '',
            'Some body text.',
        ].join('\n')

        const post = parseMarkdown(raw)

        expect(post.title).toBe('Hello world')
        expect(post.description).toBe('A description')
        expect(post.slug).toBe('hello-world')
        expect(post.date).toBe('2026-05-18')
        expect(post.author).toBe('Sebastian')
        expect(post.bodyHtml).toContain('<h1')
        expect(post.bodyHtml).toContain('Heading')
        expect(post.bodyHtml).toContain('Some body text')
    })

    it('strips surrounding quotes from frontmatter values', () => {
        const raw = [
            '---',
            'title: "Quoted"',
            "description: 'single quoted'",
            'slug: unquoted',
            'date: "2026-01-01"',
            'author: "X"',
            '---',
            'body',
        ].join('\n')
        const post = parseMarkdown(raw)
        expect(post.title).toBe('Quoted')
        expect(post.description).toBe('single quoted')
        expect(post.slug).toBe('unquoted')
    })

    it('throws when frontmatter is missing', () => {
        expect(() => parseMarkdown('no frontmatter here')).toThrow()
    })

    it('throws when required fields are missing', () => {
        const raw = '---\ntitle: "x"\n---\nbody'
        expect(() => parseMarkdown(raw)).toThrow()
    })

    it('renders markdown body as HTML', () => {
        const raw = [
            '---',
            'title: "t"',
            'description: "d"',
            'slug: "s"',
            'date: "2026-05-18"',
            'author: "a"',
            '---',
            '',
            '**bold** and [link](https://example.com)',
        ].join('\n')
        const post = parseMarkdown(raw)
        expect(post.bodyHtml).toMatch(/<strong>bold<\/strong>/)
        expect(post.bodyHtml).toMatch(/href="https:\/\/example\.com"/)
    })
})

describe('sortPostsByDateDesc', () => {
    const make = (slug: string, date: string): RawBlogPost => ({
        title: slug,
        description: '',
        slug,
        date,
        author: 'x',
        bodyHtml: '',
    })

    it('sorts by date descending', () => {
        const posts = [
            make('a', '2026-01-01'),
            make('b', '2026-05-18'),
            make('c', '2026-03-10'),
        ]
        const sorted = sortPostsByDateDesc(posts)
        expect(sorted.map(p => p.slug)).toEqual(['b', 'c', 'a'])
    })

    it('returns empty array unchanged', () => {
        expect(sortPostsByDateDesc([])).toEqual([])
    })

    it('does not mutate the input array', () => {
        const posts = [make('a', '2026-01-01'), make('b', '2026-05-18')]
        const snapshot = [...posts]
        sortPostsByDateDesc(posts)
        expect(posts).toEqual(snapshot)
    })
})

describe('loadAllPosts (real content)', () => {
    it('loads the skeleton smartcar post from src/content/blog', () => {
        const posts = loadAllPosts()
        const slugs = posts.map(p => p.slug)
        expect(slugs).toContain('smartcar-energyadded-skoda-19kwh')
    })

    it('findPostBySlug returns the post for a known slug and null otherwise', () => {
        const post = findPostBySlug('smartcar-energyadded-skoda-19kwh')
        expect(post).not.toBeNull()
        expect(post?.author).toBe('Sebastian')
        expect(findPostBySlug('does-not-exist')).toBeNull()
    })

    it('every loaded post has the required frontmatter fields', () => {
        for (const post of loadAllPosts()) {
            expect(post.title).toBeTruthy()
            expect(post.description).toBeTruthy()
            expect(post.slug).toBeTruthy()
            expect(post.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
            expect(post.author).toBeTruthy()
        }
    })
})
