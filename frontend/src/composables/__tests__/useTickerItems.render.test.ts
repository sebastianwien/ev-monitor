// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createApp, defineComponent } from 'vue'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import de from '../../locales/de.yaml'
import en from '../../locales/en.yaml'
import nb from '../../locales/nb.yaml'
import sv from '../../locales/sv.yaml'
import type { RawTickerItem } from '../useTickerItems'

const get = vi.fn()
vi.mock('../../api/axios', () => ({ default: { get: (url: string) => get(url) } }))

import { useTickerItems } from '../useTickerItems'

const mine = (messageKey: string, params: Record<string, string>): RawTickerItem =>
  ({ type: 'PERSONAL', messageKey, params, variant: 'personal' })
const stat = (messageKey: string, params: Record<string, string>): RawTickerItem =>
  ({ type: 'STAT', messageKey, params, variant: 'money' })

const PERSONAL: RawTickerItem[] = [
  mine('my_month', { month: '10', kwh: '187', charges: '12', homePercent: '75' }),
  mine('my_consumption', { month: '10', mine: '16.8', peers: '18.2' }),
  mine('my_rank', { category: 'MONTHLY_KWH', rank: '3', gap: '12.5' }),
  mine('my_rank_leader', { category: 'MONTHLY_CHARGES', value: '14' }),
]
const TODAY: RawTickerItem[] = [
  stat('today_charge', { kwh: '42', cost: '16.38', ct: '39', provider: 'Ionity' }),
  stat('today_charge_anon', { kwh: '26', cost: '12.00', ct: '47' }),
]

/** Rendert die neuen Einträge mit echtem i18n in der gewünschten Sprache. */
async function renderAll(locale: 'de' | 'en' | 'nb' | 'sv'): Promise<string[]> {
  get.mockImplementation((url: string) => Promise.resolve({
    data: url === '/ticker/me' ? PERSONAL : url === '/ticker/today' ? TODAY : [],
  }))
  const i18n = createI18n({ legacy: false, locale, messages: { de, en, nb, sv }, missingWarn: false, fallbackWarn: false })
  let api!: ReturnType<typeof useTickerItems>
  createApp(defineComponent({ setup() { api = useTickerItems(); return () => null } }))
    .use(createPinia())
    .use(i18n)
    .mount(document.createElement('div'))
  await api.fetchTicker({ withUserItems: true })
  return api.items.value.map(i => i.text)
}

describe('useTickerItems - Sätze der neuen Einträge', () => {
  beforeEach(() => get.mockReset())

  it('rendert alle Platzhalter auf Deutsch', async () => {
    const texts = await renderAll('de')

    expect(texts).toEqual(expect.arrayContaining([
      'Dein Oktober: 187 kWh in 12 Ladevorgängen, 75% zu Hause',
      'Dein Verbrauch im Oktober: 16,8 kWh/100 km, Fahrer deines Modells: 18,2',
      '#3 Meiste Energie geladen: du, 12,5 kWh hinter dem Platz davor',
      '#1 Ladevorgänge: du, 14 Ladevorgänge',
      'Heute hat jemand 42 kWh bei Ionity für 16,38 € geladen (39 ct/kWh)',
      'Heute hat jemand 26 kWh öffentlich für 12,00 € geladen (47 ct/kWh)',
    ]))
    expect(texts).toHaveLength(6)
  })

  it.each(['de', 'en', 'nb', 'sv'] as const)('lässt in %s keinen Platzhalter und keinen Eintrag leer', async (locale) => {
    const texts = await renderAll(locale)

    expect(texts).toHaveLength(6)
    for (const text of texts) expect(text).not.toMatch(/[{}]|undefined|NaN/)
  })
})
