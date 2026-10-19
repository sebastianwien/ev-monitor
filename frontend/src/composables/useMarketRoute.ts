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

export const MARKET_HTML_LANG: Record<Market, string> = {
    de: 'de',
    en: 'en',
    gb: 'en',
    us: 'en',
    no: 'nb',
    se: 'sv',
}

export const OG_LOCALE: Record<Market, string> = {
    de: 'de_DE',
    en: 'en_US',   // generic EN fallback (NL/BE) - en_US is the neutral default
    gb: 'en_GB',
    us: 'en_US',
    no: 'nb_NO',
    se: 'sv_SE',
}

// DE ist die einzige Sprache, die Google indexieren soll. Alle anderen Märkte bleiben
// für Nutzer erreichbar (Sprachumschalter), tragen aber noindex, damit Google keine
// fremdsprachigen Duplikate der deutschen Seiten bewertet.
export function robotsFor(market: Market): string {
    return market === 'de' ? 'index, follow' : 'noindex, follow'
}

// Pfadbasierte Variante für Seiten ohne eigenes useHead (z. B. /en/login): App.vue setzt
// damit ein Sicherheitsnetz, die Views überschreiben es mit robotsFor(market).
const NON_DE_PATH = /^\/(en|gb|us|no|se)(\/|$)/

export function robotsForPath(path: string): string {
    return NON_DE_PATH.test(path) ? 'noindex, follow' : 'index, follow'
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

    const robots = computed(() => robotsFor(currentMarket.value))

    return {
        currentMarket,
        isDE,
        isEN,
        isGB,
        isUS,
        marketUrl,
        robots,
    }
}
