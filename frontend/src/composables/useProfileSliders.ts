import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from './useLocaleFormat'
import { PROFILE_KEYS, PROFILE_SLIDERS, highwayPercent, type ProfileKey, type SliderRange } from '../utils/drivingProfile'

/** The answered value behind each slider, null while it is open */
export type ProfileValues = Record<ProfileKey, number | null>

export interface ProfileSliderItem extends SliderRange {
  key: ProfileKey
  label: string
  value: number | null
  format: (value: number) => string
  /** between the scale ends once answered: the motorway share of the yearly km */
  detail?: (value: number) => string
}

/**
 * The five profile sliders of the list and the model page from one place: range, label, format, the
 * motorway capped at the answered yearly km with its share, and the profile in one line
 * ("15.000 km im Jahr · 40 km Arbeitsweg · ..."), built from the answered sliders only.
 */
export function useProfileSliders(values: () => ProfileValues) {
  const { t } = useI18n()
  const { formatDistance, formatNumber } = useLocaleFormat()

  const FORMAT: Record<ProfileKey, (v: number) => string> = {
    annualKm: km => formatDistance(km),
    highwayKm: km => formatDistance(km),
    commute: km => formatDistance(km),
    commuteDays: n => t('model.page.days_value', { n }, n),
    longTrip: km => formatDistance(km),
  }

  const sliders = computed<ProfileSliderItem[]>(() => {
    const v = values()
    const annual = v.annualKm
    return PROFILE_KEYS.map(key => ({
      key,
      ...PROFILE_SLIDERS[key],
      // motorway km cannot exceed the yearly mileage once that is answered
      ...(key === 'highwayKm' && annual != null ? { max: annual } : {}),
      label: t(`model.page.q_${key}`),
      value: v[key],
      format: FORMAT[key],
      detail: key === 'highwayKm' && annual != null
        ? (km: number) => t('model.story.slider_highway_pct', { pct: formatNumber(highwayPercent(km, annual)) })
        : undefined,
    }))
  })

  const summary = computed(() => sliders.value
    .filter(s => s.value != null)
    .map(s => t(`model.page.summary_${s.key}`, { value: s.format(s.value!) }))
    .join(' · '))

  return { sliders, summary }
}
