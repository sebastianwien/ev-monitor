import { createI18n } from 'vue-i18n'
import de from './locales/de.yaml'
import en from './locales/en.yaml'

const savedLocale = (localStorage.getItem('ev-locale') as 'de' | 'en' | null) ?? 'de'

export const i18n = createI18n({
    legacy: false,
    locale: savedLocale,
    fallbackLocale: 'de',
    messages: { de, en }
})
