import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import apiClient from '../api/axios'
import { useLocaleFormat } from './useLocaleFormat'

/**
 * Raw ticker item as delivered by the backend. LEADER/STAT/PERSONAL items carry an i18n
 * messageKey plus raw params; the sentence is rendered here so all four locales
 * live in the frontend. NEWS items carry a passed-through external title.
 */
export interface RawTickerItem {
    type: 'LEADER' | 'STAT' | 'NEWS' | 'PERSONAL'
    messageKey?: string
    params?: Record<string, string>
    variant: 'leader' | 'eco' | 'money' | 'energy' | 'news' | 'personal'
    text?: string
    url?: string
}

export interface TickerItem {
    type: RawTickerItem['type']
    /** Backend messageKey, für Plausible (nie die Werte selbst) */
    key?: string
    text: string
    variant: RawTickerItem['variant']
    url?: string
    /** Interne Zielseite; nur Einträge mit echter Seite bekommen einen Link */
    target?: string
}

/**
 * Zielseite je Eintrag. Antippen des Texts bleibt "nächster Eintrag", das Ziel bekommt einen
 * eigenen Link. Community-Statistiken haben keine Seite und bleiben ohne Ziel.
 */
const PERSONAL_TARGET: Record<string, string> = {
    my_month: '/dashboard',
    my_consumption: '/dashboard',
    my_rank: '/leaderboard',
    my_rank_leader: '/leaderboard',
}

export function tickerTarget(item: RawTickerItem): string | undefined {
    if (item.type === 'LEADER') return '/leaderboard'
    if (item.type === 'PERSONAL' && item.messageKey) return PERSONAL_TARGET[item.messageKey]
    return undefined
}

/** Fügt `extra` ab Index `start` an jeder `step`-ten Stelle ein; Überzählige landen am Ende. */
export function interleave<T>(base: T[], extra: T[], start: number, step: number): T[] {
    const result = [...base]
    extra.forEach((item, i) => {
        result.splice(Math.min(start + i * step, result.length), 0, item)
    })
    return result
}

/** Persönliche Einträge an zweiter, sechster und zehnter Stelle. */
export function mixPersonal(community: RawTickerItem[], personal: RawTickerItem[]): RawTickerItem[] {
    return interleave(community, personal, 1, 4)
}

type TickerGet = (url: string) => Promise<{ data: RawTickerItem[] }>

const settledData = (r: PromiseSettledResult<{ data: RawTickerItem[] }>) =>
    r.status === 'fulfilled' && Array.isArray(r.value.data) ? r.value.data : []

/**
 * Lädt die Community-Liste und für eingeloggte Nutzer zusätzlich "Heute"-Ladungen und
 * persönliche Einträge. Fällt ein Teil aus, fehlt nur dieser Teil.
 */
export async function loadTickerRaw(get: TickerGet, withUserItems: boolean): Promise<RawTickerItem[]> {
    if (!withUserItems) return settledData((await Promise.allSettled([get('/public/leaderboard/ticker')]))[0])
    const [community, today, personal] = await Promise.allSettled([
        get('/public/leaderboard/ticker'),
        get('/ticker/today'),
        get('/ticker/me'),
    ])
    return mixPersonal(interleave(settledData(community), settledData(today), 3, 4), settledData(personal))
}

// Backend category enum name -> i18n key suffix (reused leaderboard.cat_* labels)
const CAT_LABEL: Record<string, string> = {
    MONTHLY_KWH: 'kwh',
    MONTHLY_CHARGES: 'charges',
    MONTHLY_DISTANCE: 'distance',
    MONTHLY_CHEAPEST: 'cheapest',
    MONTHLY_NIGHT_OWL: 'night_owl',
    MONTHLY_ICE_CHARGER: 'ice_charger',
    MONTHLY_HEAT_CHARGER: 'heat_charger',
    MONTHLY_POWER_CHARGER: 'power_charger',
}

// Backend category enum name -> ticker.units.* key
const CAT_UNIT: Record<string, string> = {
    MONTHLY_KWH: 'kwh',
    MONTHLY_CHARGES: 'charges',
    MONTHLY_DISTANCE: 'km',
    MONTHLY_CHEAPEST: 'ct_kwh',
    MONTHLY_NIGHT_OWL: 'night_charges',
    MONTHLY_ICE_CHARGER: 'celsius',
    MONTHLY_HEAT_CHARGER: 'celsius',
    MONTHLY_POWER_CHARGER: 'kw',
}

export function useTickerItems() {
    const { t, locale } = useI18n()
    const { formatNumber, formatDecimal } = useLocaleFormat()

    const raw = ref<RawTickerItem[]>([])

    function monthName(m: number): string {
        // Intl gives locale-correct month names for all four locales - no translation entries needed.
        return new Intl.DateTimeFormat(locale.value, { month: 'long' }).format(new Date(2020, m - 1, 1))
    }

    const num = (v: string) => formatNumber(Number(v))

    function render(item: RawTickerItem): string {
        if (item.type === 'NEWS') return item.text ?? ''
        const p = item.params ?? {}
        const month = p.month ? monthName(Number(p.month)) : ''
        const unit = (u?: string) => (u ? t(`ticker.units.${u}`) : '')

        switch (item.messageKey) {
            case 'leader':
                return t('ticker.leader', {
                    category: t(`leaderboard.cat_${CAT_LABEL[p.category]}`),
                    name: p.name,
                    value: num(p.value),
                    unit: unit(CAT_UNIT[p.category]),
                })
            case 'stat_base':
                return t('ticker.stat_base', { month, kwh: num(p.kwh), charges: num(p.charges) })
            case 'co2_saved':
                return t('ticker.co2_saved', { month, amount: num(p.amount), unit: unit(p.unit) })
            case 'households':
                return t('ticker.households', { month, count: num(p.count) })
            case 'solar':
                return t('ticker.solar', { month, count: num(p.count) })
            case 'wind':
                return t('ticker.wind', { month, amount: num(p.amount), unit: unit(p.unit) })
            case 'savings_vs_fuel':
                return t('ticker.savings_vs_fuel', { month, amount: num(p.amount), price: formatDecimal(Number(p.price), 2) })
            case 'charge_time':
                return t('ticker.charge_time', { month, amount: num(p.amount), unit: unit(p.unit) })
            case 'home_quota':
                return t('ticker.home_quota', { month, percent: p.percent })
            case 'top_provider':
                return t('ticker.top_provider', { month, provider: p.provider, count: num(p.count) })
            case 'today_charge':
                return t('ticker.today_charge', { kwh: num(p.kwh), provider: p.provider, cost: formatDecimal(Number(p.cost), 2), ct: num(p.ct) })
            case 'today_charge_anon':
                return t('ticker.today_charge_anon', { kwh: num(p.kwh), cost: formatDecimal(Number(p.cost), 2), ct: num(p.ct) })
            case 'my_month':
                return t('ticker.my_month', { month, kwh: num(p.kwh), charges: num(p.charges), percent: p.homePercent })
            case 'my_consumption':
                return t('ticker.my_consumption', { month, mine: formatDecimal(Number(p.mine), 1), peers: formatDecimal(Number(p.peers), 1) })
            case 'my_rank':
                return t('ticker.my_rank', {
                    category: t(`leaderboard.cat_${CAT_LABEL[p.category]}`),
                    rank: p.rank,
                    gap: num(p.gap),
                    unit: unit(CAT_UNIT[p.category]),
                })
            case 'my_rank_leader':
                return t('ticker.my_rank_leader', {
                    category: t(`leaderboard.cat_${CAT_LABEL[p.category]}`),
                    value: num(p.value),
                    unit: unit(CAT_UNIT[p.category]),
                })
            default:
                return ''
        }
    }

    // Re-renders reactively on locale change (t/locale are reactive).
    // Drop items that render empty (unknown messageKey, missing params) so a stale
    // backend never produces bare-icon items with no text.
    const items = computed<TickerItem[]>(() =>
        raw.value
            .map(r => ({ type: r.type, key: r.messageKey, text: render(r), variant: r.variant, url: r.url, target: tickerTarget(r) }))
            .filter(i => i.text.trim().length > 0)
    )

    /** withUserItems nur im Ticker eingeloggter Nutzer, nie auf der Landing Page. */
    async function fetchTicker(options: { withUserItems?: boolean } = {}): Promise<void> {
        // Fehler pro Teil werden in loadTickerRaw geschluckt - ohne Daten bleibt die Section verborgen.
        raw.value = await loadTickerRaw(url => apiClient.get<RawTickerItem[]>(url), options.withUserItems ?? false)
    }

    return { items, fetchTicker }
}
