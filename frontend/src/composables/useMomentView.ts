import { computed, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from './useLocaleFormat'
import { heroLine } from './storyModel'
import { DAY, type Moment } from './storyMoments'
import { CAT_LABEL } from './useTickerItems'

/**
 * Text und Handlung je Moment, geteilt von Moment-Hero und Feed. Links fuehren selbst weiter
 * (to), alles andere (Sheet, Teilen) ist eine Handlung, die der View ausfuehrt.
 */
export interface MomentAction { id: string; label: string; to?: string }
export interface MomentView {
  eyebrow: string
  sentence: string
  sub?: string
  badge?: string
  action?: MomentAction
  secondary?: MomentAction
  dismiss?: boolean
}

export function useMomentView(props: { moment: Moment; carId: string | null; carName: string; now?: number }): ComputedRef<MomentView> {
  const { t, locale } = useI18n()
  const { formatDecimal, formatDistance, formatCurrency } = useLocaleFormat()

  const now = computed(() => props.now ?? Date.now())
  const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)
  const rtf = computed(() => new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' }))
  const km = (v: number) => formatDistance(v, { round: true })
  const eur = (v: number) => formatCurrency(v, { decimals: 0 })

  /** "Heute", "Gestern", "Vor 3 Tagen" nach Kalendertagen. */
  function dayWord(at: number): string {
    const a = new Date(at); a.setHours(0, 0, 0, 0)
    const n = new Date(now.value); n.setHours(0, 0, 0, 0)
    return cap(rtf.value.format(Math.round((a.getTime() - n.getTime()) / DAY), 'day'))
  }
  const shortDate = (at: number) => new Date(at).toLocaleDateString(locale.value, { day: 'numeric', month: 'numeric' })
  function agoWord(at: number): string {
    const min = Math.max(1, Math.round((now.value - at) / 60_000))
    return min < 60 ? rtf.value.format(-min, 'minute') : rtf.value.format(-Math.round(min / 60), 'hour')
  }

  return computed<MomentView>(() => {
    const m = props.moment
    switch (m.kind) {
      case 'just_charged': {
        const kwh = m.kwh != null ? `${formatDecimal(m.kwh, 1)} kWh` : null
        const parts = [
          kwh && m.socFrom != null ? t('story.moment_charged_energy_soc', { kwh, from: Math.round(m.socFrom), to: `${Math.round(m.socTo!)} %` })
            : kwh ? t('story.moment_charged_energy', { kwh }) : null,
          m.rangeKm != null ? t('story.moment_charged_range', { km: km(m.rangeKm) }) : null,
          m.costEur != null ? t('story.moment_charged_cost', { eur: formatCurrency(m.costEur) }) : null,
        ]
        return {
          eyebrow: now.value - m.at < 15 * 60_000 ? t('story.moment_charged_eyebrow_now') : t('story.moment_charged_eyebrow', { ago: agoWord(m.at) }),
          sentence: parts.filter(Boolean).join(' '),
          action: m.costEur == null && props.carId
            ? { id: 'add_price', label: t('story.moment_action_add_price'), to: `/logs?car=${encodeURIComponent(props.carId)}&nachtragen=preis` }
            : undefined,
        }
      }
      case 'long_trip': {
        const p = { when: dayWord(m.at), km: km(m.km), n: m.stops, eur: m.costEur != null ? eur(m.costEur) : '' }
        const key = m.stops === 0 ? 'moment_trip_no_stop' : m.costEur != null ? 'moment_trip_stops_cost' : 'moment_trip_stops'
        return { eyebrow: t('story.moment_trip_eyebrow'), sentence: t(`story.${key}`, p, m.stops), action: { id: 'trips', label: t('story.moment_action_trips'), to: '/logs' } }
      }
      case 'first_frost':
        return {
          eyebrow: t('story.moment_frost_eyebrow'),
          sentence: t('story.moment_frost', { when: dayWord(m.at), temp: `${formatDecimal(m.tempC, 0)} °C`, winter: km(m.winterKm), summer: km(m.summerKm) }),
          action: { id: 'winter', label: t('story.moment_action_winter') },
        }
      case 'month_change': {
        const line = heroLine({ distanceKm: m.km, energyCostEur: m.eur, charges: m.charges })
        const month = cap(new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'long' }))
        return {
          eyebrow: t('story.moment_month_eyebrow'),
          sentence: t(`story.hero_${line.kind}`, { month, km: line.km != null ? km(line.km) : '', eur: line.eur != null ? eur(line.eur) : '', n: m.charges }, m.charges),
          sub: m.petrolEur != null ? t('story.hero_petrol_total', { eur: eur(m.petrolEur) }) : undefined,
          action: { id: 'share', label: t('story.moment_action_share') },
        }
      }
      case 'battery': {
        const soh = (v: number) => `${m.estimated ? '~' : ''}${formatDecimal(v, 0)} %`
        const months = m.previousAt != null ? Math.round((now.value - m.previousAt) / (30.44 * DAY)) : null
        return {
          eyebrow: t(m.estimated ? 'story.moment_battery_eyebrow_estimated' : 'story.moment_battery_eyebrow'),
          sentence: [
            t('story.moment_battery', { soh: soh(m.sohPercent) }),
            m.previousSohPercent != null && months != null
              ? t('story.moment_battery_previous', { ago: cap(rtf.value.format(-months, 'month')), soh: soh(m.previousSohPercent) })
              : null,
          ].filter(Boolean).join(' '),
          badge: m.estimated ? t('story.moment_battery_estimated') : undefined,
          action: { id: 'battery', label: t('story.moment_action_battery') },
        }
      }
      case 'rank':
        return {
          eyebrow: t('story.moment_rank_eyebrow'),
          sentence: t('story.feed_rank', { rank: m.rank, category: t(`leaderboard.cat_${CAT_LABEL[m.category] ?? 'kwh'}`) }),
          action: { id: 'rank', label: t('story.moment_action_rank'), to: '/leaderboard' },
        }
      case 'silence':
        return {
          eyebrow: t('story.moment_silence_eyebrow'),
          sentence: m.kwh != null
            ? t('story.moment_silence', { days: m.days, kwh: `${formatDecimal(m.kwh, 1)} kWh` })
            : t('story.moment_silence_plain', { days: m.days }),
          sub: m.autoSync ? t('story.moment_silence_autosync', { car: props.carName }) : undefined,
          action: { id: 'capture', label: t('story.moment_action_capture'), to: '/erfassen' },
          secondary: m.autoSync ? { id: 'autosync', label: t('story.moment_action_autosync'), to: '/imports' } : undefined,
          dismiss: true,
        }
      case 'milestone': {
        const n = (v: number) => formatDecimal(v, 0)
        const tonnes = (kg: number, d: number) => `${formatDecimal(kg / 1000, d)} t`
        const co2Sub = m.co2Kg != null && m.co2Kg >= 100 ? t('story.moment_milestone_co2', { t: tonnes(m.co2Kg, 1) }) : undefined
        const byMetric: Record<typeof m.metric, () => Pick<MomentView, 'sentence' | 'sub'>> = {
          charges: () => ({ sentence: t('story.moment_milestone_charges', { n: n(m.value) }), sub: co2Sub }),
          km: () => ({ sentence: t('story.moment_milestone_km', { km: km(m.value) }), sub: co2Sub }),
          co2: () => ({ sentence: t('story.moment_milestone_co2', { t: tonnes(m.value, 0) }), sub: t('story.moment_milestone_co2_after', { km: km(m.km) }) }),
          years: () => ({
            sentence: m.km > 0
              ? t('story.moment_milestone_years', { years: m.value, km: km(m.km), n: n(m.charges) }, m.value)
              : t('story.moment_milestone_years_plain', { years: m.value, n: n(m.charges) }, m.value),
          }),
        }
        return { eyebrow: t('story.moment_milestone_eyebrow'), ...byMetric[m.metric](), action: { id: 'share', label: t('story.moment_action_share') } }
      }
      case 'gap': {
        const date = shortDate(m.at)
        return {
          eyebrow: t('story.moment_gap_eyebrow'),
          sentence: m.count > 1 ? t('story.moment_gap_many', { n: m.count, date: shortDate(m.since) })
            : m.kwh != null ? t('story.moment_gap_kwh', { kwh: `${formatDecimal(m.kwh, 1)} kWh`, date })
            : t('story.moment_gap', { date }),
          action: props.carId
            ? { id: 'add_price', label: t(m.count > 1 ? 'story.moment_action_add_prices' : 'story.moment_action_add_price'), to: `/logs?car=${encodeURIComponent(props.carId)}&nachtragen=preis` }
            : undefined,
          // Je Eintrag: bei mehreren entscheidet der Nutzer in der Liste.
          secondary: m.count === 1 ? { id: 'price_unknown', label: t('story.moment_action_price_unknown') } : undefined,
        }
      }
      default:
        return { eyebrow: '', sentence: '' }
    }
  })
}
