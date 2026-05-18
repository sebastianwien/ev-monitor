/**
 * Giscus (GitHub Discussions) configuration for blog comments.
 *
 * Setup steps:
 *   1. Install the giscus app on the GitHub repo: https://github.com/apps/giscus
 *   2. Enable "Discussions" in the repo settings.
 *   3. Open https://giscus.app/de and select repo + category ("Blog Comments")
 *      and copy the generated `repo`, `repoId`, `category`, `categoryId`.
 *   4. Replace the TODO_ placeholders below.
 *
 * Until the TODO_ placeholders are replaced, BlogComments.vue renders a small
 * "Kommentare bald verfügbar" placeholder instead of loading the Giscus script.
 */

export interface GiscusConfig {
    repo: string
    repoId: string
    category: string
    categoryId: string
    mapping: 'pathname' | 'url' | 'title' | 'og:title' | 'specific' | 'number'
    theme: string
    lang: string
}

export const GISCUS_CONFIG: GiscusConfig = {
    repo: 'sebastianwien/ev-monitor',
    repoId: 'R_kgDORW-ykQ',
    category: 'Blog Comments',
    categoryId: 'DIC_kwDORW-ykc4C9T05',
    mapping: 'pathname',
    theme: 'preferred_color_scheme',
    lang: 'de',
}

/**
 * Returns true once the config has been populated with real values from
 * giscus.app. While any placeholder still starts with `TODO_`, we treat the
 * widget as not configured and render a soft placeholder instead.
 */
export function isGiscusConfigured(config: GiscusConfig = GISCUS_CONFIG): boolean {
    return !config.repo.includes('TODO_')
        && !config.repoId.startsWith('TODO_')
        && !config.categoryId.startsWith('TODO_')
}
