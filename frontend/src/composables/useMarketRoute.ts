import { computed } from 'vue'
import { useRoute } from 'vue-router'

export type Market = 'de' | 'en' | 'gb' | 'us' | 'no' | 'se'

const CANONICAL_BASE = 'https://ev-monitor.net'

const MARKET_BASE_PATHS: Record<Market, string> = {
    de: '/modelle',
    en: '/en/models',
    gb: '/gb/models',
    us: '/us/models',
    no: '/no/modeller',
    se: '/se/modeller',
}

const HREFLANG_MAP: Array<{ hreflang: string; market: Market }> = [
    { hreflang: 'de', market: 'de' },
    { hreflang: 'en', market: 'en' },
    { hreflang: 'en-GB', market: 'gb' },
    { hreflang: 'en-US', market: 'us' },
    { hreflang: 'nb', market: 'no' },
    { hreflang: 'sv', market: 'se' },
]

export function detectMarket(path: string): Market {
    if (path.startsWith('/us/')) return 'us'
    if (path.startsWith('/gb/')) return 'gb'
    if (path.startsWith('/no/')) return 'no'
    if (path.startsWith('/se/')) return 'se'
    if (path.startsWith('/en/')) return 'en'
    if (path.startsWith('/modelle')) return 'de'
    return 'de'
}

export function getMarketBasePath(market: Market): string {
    return MARKET_BASE_PATHS[market]
}

export function buildMarketUrl(market: Market, suffix = ''): string {
    return `${CANONICAL_BASE}${MARKET_BASE_PATHS[market]}${suffix}`
}

export const OG_LOCALE: Record<Market, string> = {
    de: 'de_DE',
    en: 'en_US',   // generic EN fallback (NL/BE) - en_US is the neutral default
    gb: 'en_GB',
    us: 'en_US',
    no: 'nb_NO',
    se: 'sv_SE',
}

export function getHreflangLinks(suffix = ''): Array<{ rel: string; hreflang: string; href: string }> {
    const links = HREFLANG_MAP.map(({ hreflang, market }) => ({
        rel: 'alternate',
        hreflang,
        href: buildMarketUrl(market, suffix),
    }))
    links.push({
        rel: 'alternate',
        hreflang: 'x-default',
        href: buildMarketUrl('en', suffix),
    })
    return links
}

/** Composable für Vue-Komponenten - baut auf den reinen Funktionen auf */
export function useMarketRoute() {
    const route = useRoute()

    const currentMarket = computed<Market>(() => detectMarket(route.path))

    const isDE = computed(() => currentMarket.value === 'de')
    const isEN = computed(() => currentMarket.value === 'en')
    const isGB = computed(() => currentMarket.value === 'gb')
    const isUS = computed(() => currentMarket.value === 'us')

    function marketUrl(market: Market, suffix = ''): string {
        return buildMarketUrl(market, suffix)
    }

    function hreflangLinks(suffix = '') {
        return getHreflangLinks(suffix)
    }

    return {
        currentMarket,
        isDE,
        isEN,
        isGB,
        isUS,
        marketUrl,
        hreflangLinks,
    }
}
