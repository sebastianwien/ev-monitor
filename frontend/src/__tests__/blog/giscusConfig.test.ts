import { describe, it, expect } from 'vitest'
import { isGiscusConfigured } from '../../config/giscus'

describe('isGiscusConfigured', () => {
    it('returns false when repoId starts with TODO_', () => {
        expect(isGiscusConfigured({
            repo: 'owner/repo',
            repoId: 'TODO_REPO_ID',
            category: 'Blog Comments',
            categoryId: 'TODO_CATEGORY_ID',
            mapping: 'pathname',
            theme: 'preferred_color_scheme',
            lang: 'de',
        })).toBe(false)
    })

    it('returns false when categoryId starts with TODO_', () => {
        expect(isGiscusConfigured({
            repo: 'owner/repo',
            repoId: 'R_real',
            category: 'Blog Comments',
            categoryId: 'TODO_CATEGORY_ID',
            mapping: 'pathname',
            theme: 'preferred_color_scheme',
            lang: 'de',
        })).toBe(false)
    })

    it('returns true when all real values are set', () => {
        expect(isGiscusConfigured({
            repo: 'sebastianwien/ev-monitor',
            repoId: 'R_kgDOABC',
            category: 'Blog Comments',
            categoryId: 'DIC_kwDOABC',
            mapping: 'pathname',
            theme: 'preferred_color_scheme',
            lang: 'de',
        })).toBe(true)
    })

    it('returns false when repo still contains TODO_OWNER', () => {
        expect(isGiscusConfigured({
            repo: 'TODO_OWNER/TODO_REPO',
            repoId: 'R_real',
            category: 'Blog Comments',
            categoryId: 'DIC_real',
            mapping: 'pathname',
            theme: 'preferred_color_scheme',
            lang: 'de',
        })).toBe(false)
    })
})
