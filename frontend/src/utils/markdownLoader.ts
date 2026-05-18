/**
 * Lightweight markdown loader for the blog.
 *
 * Posts live in `src/content/blog/*.md` with a small YAML-ish frontmatter
 * (title, description, slug, date, author). Frontmatter values may optionally
 * be wrapped in single or double quotes. The body is rendered to HTML via
 * `marked`.
 *
 * We deliberately avoid `gray-matter` here because it pulls in Node-only
 * modules (Buffer, fs) that bloat the browser bundle. The frontmatter format
 * is small and we control it, so a tiny parser is the simpler choice.
 */

import { marked } from 'marked'

export interface RawBlogPost {
    title: string
    description: string
    slug: string
    date: string // ISO date string (YYYY-MM-DD)
    author: string
    bodyHtml: string
}

const REQUIRED_FIELDS = ['title', 'description', 'slug', 'date', 'author'] as const

function stripQuotes(value: string): string {
    const trimmed = value.trim()
    if (trimmed.length >= 2) {
        const first = trimmed[0]
        const last = trimmed[trimmed.length - 1]
        if ((first === '"' && last === '"') || (first === "'" && last === "'")) {
            return trimmed.slice(1, -1)
        }
    }
    return trimmed
}

/**
 * Parse a single `.md` file's raw content into a blog post.
 * Throws if the frontmatter block is missing or required fields are absent.
 */
export function parseMarkdown(raw: string): RawBlogPost {
    const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/)
    if (!match) {
        throw new Error('Blog post is missing YAML frontmatter (--- ... ---)')
    }
    const [, frontmatterBlock, body] = match

    const data: Record<string, string> = {}
    for (const line of frontmatterBlock.split(/\r?\n/)) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith('#')) continue
        const colon = trimmed.indexOf(':')
        if (colon === -1) continue
        const key = trimmed.slice(0, colon).trim()
        const value = stripQuotes(trimmed.slice(colon + 1))
        if (key) data[key] = value
    }

    for (const field of REQUIRED_FIELDS) {
        if (!data[field]) {
            throw new Error(`Blog post frontmatter is missing required field: ${field}`)
        }
    }

    const bodyHtml = marked.parse(body ?? '', { async: false }) as string

    return {
        title: data.title,
        description: data.description,
        slug: data.slug,
        date: data.date,
        author: data.author,
        bodyHtml,
    }
}

/** Returns a new array sorted by `date` descending (newest first). Does not mutate input. */
export function sortPostsByDateDesc(posts: RawBlogPost[]): RawBlogPost[] {
    return [...posts].sort((a, b) => b.date.localeCompare(a.date))
}

/**
 * Eagerly loads all blog posts from `src/content/blog/*.md` at build time
 * (Vite's `import.meta.glob` with `?raw`).
 *
 * Returns posts sorted newest first.
 */
const rawFiles = import.meta.glob('../content/blog/*.md', {
    eager: true,
    query: '?raw',
    import: 'default',
}) as Record<string, string>

let cachedPosts: RawBlogPost[] | null = null

export function loadAllPosts(): RawBlogPost[] {
    if (cachedPosts) return cachedPosts
    const parsed: RawBlogPost[] = []
    for (const [path, content] of Object.entries(rawFiles)) {
        try {
            parsed.push(parseMarkdown(content))
        } catch (err) {
            // eslint-disable-next-line no-console
            console.error(`[blog] Failed to parse ${path}:`, err)
        }
    }
    cachedPosts = sortPostsByDateDesc(parsed)
    return cachedPosts
}

export function findPostBySlug(slug: string): RawBlogPost | null {
    return loadAllPosts().find(p => p.slug === slug) ?? null
}
