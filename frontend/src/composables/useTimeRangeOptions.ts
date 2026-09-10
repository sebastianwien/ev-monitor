import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

export interface TimeRangeOption {
  value: string
  label: string
  /** Kurzform fuer Chips und Trigger: "Sep", "Jul-Sep", "Okt'25-Sep'26". */
  shortLabel: string
}

/**
 * Symbolische Zeitraeume, die Dashboard und Log-Feed gemeinsam anbieten. Ein Wortschatz
 * fuer beide Seiten - wer die Chips im Dashboard kennt, findet sich im Feed sofort zurecht.
 * Aufgeloest werden sie in `resolveTripWindow`.
 */
export function useTimeRangeOptions() {
  const { t, locale } = useI18n()
  return computed<TimeRangeOption[]>(() => {
    const now = new Date()
    const mo = (offsetMonths: number) =>
      new Date(now.getFullYear(), now.getMonth() + offsetMonths)
    const fmt = (date: Date) =>
      date.toLocaleDateString(locale.value, { month: 'short' }).replace('.', '')
    const fmtShort = (date: Date) =>
      fmt(date) + '\'' + String(date.getFullYear()).slice(2)
    const thisYear = String(now.getFullYear())
    // range labels: "Mär-Mai", "Nov-Mai", "Mai'25-Mai'26"
    const range3  = `${fmt(mo(-2))}-${fmt(mo(0))}`
    const range6  = `${fmt(mo(-5))}-${fmt(mo(0))}`
    const range12 = `${fmtShort(mo(-11))}-${fmtShort(mo(0))}`
    return [
      { value: 'THIS_MONTH',    label: t('dashboard.time_this_month'), shortLabel: fmt(mo(0)) },
      { value: 'LAST_MONTH',    label: t('dashboard.time_last_month'), shortLabel: fmt(mo(-1)) },
      { value: 'LAST_3_MONTHS',  label: t('dashboard.time_last_3m'),   shortLabel: range3 },
      { value: 'LAST_6_MONTHS',  label: t('dashboard.time_last_6m'),   shortLabel: range6 },
      { value: 'LAST_12_MONTHS', label: t('dashboard.time_last_12m'),  shortLabel: range12 },
      { value: 'THIS_YEAR',     label: t('dashboard.time_this_year'),  shortLabel: thisYear },
      { value: 'ALL_TIME',      label: t('dashboard.time_all'),        shortLabel: t('dashboard.time_all') },
      { value: 'CUSTOM',        label: t('dashboard.time_custom'),     shortLabel: '' },
    ]
  })
}
