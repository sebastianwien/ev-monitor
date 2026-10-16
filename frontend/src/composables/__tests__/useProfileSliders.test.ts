// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { reactive } from 'vue'
import { createApp, defineComponent, h } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../i18n'
import { useProfileSliders, type ProfileValues } from '../useProfileSliders'

/** Runs the composable inside a component, so useI18n and the locale formatters are there */
function setup(values: ProfileValues) {
  let out!: ReturnType<typeof useProfileSliders>
  const app = createApp(defineComponent({ setup() { out = useProfileSliders(() => values); return () => h('div') } }))
  app.use(createPinia())
  app.use(i18n)
  app.mount(document.createElement('div'))
  return out
}

const EMPTY: ProfileValues = { annualKm: null, highwayKm: null, commute: null, commuteDays: null, longTrip: null }

describe('useProfileSliders', () => {
  it('five sliders in page order, open ones without a value, the motorway capped only once the yearly km are answered', () => {
    const values = reactive({ ...EMPTY })
    const { sliders } = setup(values)
    expect(sliders.value.map(s => s.key)).toEqual(['annualKm', 'highwayKm', 'commute', 'commuteDays', 'longTrip'])
    expect(sliders.value.every(s => s.value === null)).toBe(true)
    expect(sliders.value[1].max).toBe(75_000)
    expect(sliders.value[1].detail).toBeUndefined()

    values.annualKm = 20_000
    expect(sliders.value[1].max).toBe(20_000)
    expect(sliders.value[1].detail!(5_000)).toContain('25')
  })

  it('one profile line from the answered sliders only, in the model page wording', () => {
    const { summary } = setup({ ...EMPTY, annualKm: 15_000, commute: 40, commuteDays: 5, longTrip: 600 })
    expect(summary.value).toBe('15.000 km im Jahr · 40 km Arbeitsweg · 5 Tage pendeln · 600 km Urlaubsfahrt')
  })

  it('no answer, no line', () => {
    expect(setup(EMPTY).summary.value).toBe('')
  })
})
