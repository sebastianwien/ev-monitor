import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import apiClient from '../api/axios'
import { useLocaleFormat } from './useLocaleFormat'

/**
 * Raw ticker item as delivered by the backend. LEADER/STAT items carry an i18n
 * messageKey plus raw params; the sentence is rendered here so all four locales
 * live in the frontend. NEWS items carry a passed-through external title.
 */
export interface RawTickerItem {
    type: 'LEADER' | 'STAT' | 'NEWS'
    messageKey?: string
    params?: Record<string, string>
    variant: 'leader' | 'eco' | 'money' | 'energy' | 'news'
    text?: string
    url?: string
}

export interface TickerItem {
    type: 'LEADER' | 'STAT' | 'NEWS'
    text: string
    variant: RawTickerItem['variant']
    url?: string
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
            default:
                return ''
        }
    }

    // Re-renders reactively on locale change (t/locale are reactive).
    // Drop items that render empty (unknown messageKey, missing params) so a stale
    // backend never produces bare-icon items with no text.
    const items = computed<TickerItem[]>(() =>
        raw.value
            .map(r => ({ type: r.type, text: render(r), variant: r.variant, url: r.url }))
            .filter(i => i.text.trim().length > 0)
    )

    async function fetchTicker(): Promise<void> {
        try {
            const res = await apiClient.get<RawTickerItem[]>('/public/leaderboard/ticker')
            raw.value = res.data
        } catch {
            // silent fail - the consuming section simply stays hidden
        }
    }

    return { items, fetchTicker }
}
