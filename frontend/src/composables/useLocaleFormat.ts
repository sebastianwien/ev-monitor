import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

export function useLocaleFormat() {
    const { locale } = useI18n()

    const numberLocale = computed(() => locale.value === 'en' ? 'en-GB' : 'de-DE')

    function formatNumber(value: number): string {
        return value.toLocaleString(numberLocale.value)
    }

    function formatDecimal(value: number, decimals = 1): string {
        return value.toLocaleString(numberLocale.value, {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        })
    }

    return { locale, formatNumber, formatDecimal }
}

export function useLocaleRoutes() {
    const route = useRoute()

    function getAlternateUrl(targetLocale: 'de' | 'en'): string {
        const path = route.path

        if (targetLocale === 'en') {
            if (path === '/') return '/en'
            if (path.startsWith('/modelle/')) {
                const rest = path.slice('/modelle'.length)
                return '/en/models' + rest
            }
            if (path === '/modelle') return '/en/models'
        } else {
            if (path === '/en') return '/'
            if (path.startsWith('/en/models/')) {
                const rest = path.slice('/en/models'.length)
                return '/modelle' + rest
            }
            if (path === '/en/models') return '/modelle'
        }

        // For non-translatable routes, fall back to root
        return targetLocale === 'en' ? '/en' : '/'
    }

    function getCanonicalBase(): string {
        return 'https://ev-monitor.net'
    }

    return { getAlternateUrl, getCanonicalBase }
}
