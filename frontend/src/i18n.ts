import { createI18n } from 'vue-i18n'
import type { Ref } from 'vue'
import de from './locales/de.yaml'
import en from './locales/en.yaml'

export type AppLocale = 'de' | 'en' | 'nb' | 'sv'

const VALID_LOCALES = new Set<string>(['de', 'en', 'nb', 'sv'])

export function isValidLocale(value: unknown): value is AppLocale {
    return typeof value === 'string' && VALID_LOCALES.has(value)
}

export function getSavedLocale(): AppLocale {
    const raw = localStorage.getItem('ev-locale')
    return isValidLocale(raw) ? raw : 'de'
}

// DE and EN are bundled statically (main chunk).
// NB and SV are lazy-loaded on demand via dynamic import (separate chunks).
export const i18n = createI18n({
    legacy: false,
    locale: getSavedLocale(),
    fallbackLocale: { nb: ['en', 'de'], sv: ['en', 'de'], de: ['de'], en: ['en'] },
    messages: { de, en },
    missingWarn: false,
    fallbackWarn: false,
})

export async function loadLocaleMessages(locale: AppLocale): Promise<void> {
    if (locale === 'de' || locale === 'en') return
    // Already loaded - avoid duplicate fetches
    if (Object.keys(i18n.global.getLocaleMessage(locale as string)).length > 0) return

    // Explicit map avoids Vite treating de/en static imports as dynamic duplicates
    const loaders: Record<string, () => Promise<{ default: Record<string, unknown> }>> = {
        nb: () => import('./locales/nb.yaml'),
        sv: () => import('./locales/sv.yaml'),
    }
    try {
        const messages = await loaders[locale]()
        i18n.global.setLocaleMessage(locale, messages.default)
    } catch (err) {
        console.error(`[i18n] Failed to load locale "${locale}", falling back to en`, err)
        // fallbackLocale config handles the rest - no throw needed
    }
}

export async function setLocale(locale: AppLocale): Promise<void> {
    await loadLocaleMessages(locale)
    ;(i18n.global.locale as Ref<string>).value = locale
    localStorage.setItem('ev-locale', locale)
}
