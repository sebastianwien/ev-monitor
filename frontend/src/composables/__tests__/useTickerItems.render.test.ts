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
  mine('my_standing', { category: 'MONTHLY_POWER_CHARGER', rank: '46', value: '48.3', month: '10' }),
  mine('my_standing', { category: 'MONTHLY_CHARGES', rank: '1', value: '14', month: '10' }),
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
      'Dein schnellster Ladevorgang im Oktober: 48,3 kW - damit bist du #46 in der Rangliste',
      'Deine Ladevorgänge im Oktober: 14 - damit bist du #1 in der Rangliste',
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

describe('useTickerItems - Rang-Satz je Kategorie', () => {
  beforeEach(() => get.mockReset())

  it.each(['de', 'en', 'nb', 'sv'] as const)('hat in %s für jede Kategorie einen eigenen Satz', async (locale) => {
    const cats = ['MONTHLY_KWH', 'MONTHLY_CHARGES', 'MONTHLY_DISTANCE', 'MONTHLY_CHEAPEST',
      'MONTHLY_NIGHT_OWL', 'MONTHLY_ICE_CHARGER', 'MONTHLY_HEAT_CHARGER', 'MONTHLY_POWER_CHARGER']
    get.mockImplementation((url: string) => Promise.resolve({
      data: url === '/ticker/me' ? cats.map(category => mine('my_standing', { category, rank: '5', value: '-3.5', month: '1' })) : [],
    }))
    const i18n = createI18n({ legacy: false, locale, messages: { de, en, nb, sv }, missingWarn: false, fallbackWarn: false })
    let api!: ReturnType<typeof useTickerItems>
    createApp(defineComponent({ setup() { api = useTickerItems(); return () => null } }))
      .use(createPinia()).use(i18n).mount(document.createElement('div'))
    await api.fetchTicker({ withUserItems: true })
    const texts = api.items.value.map(i => i.text)

    expect(new Set(texts).size).toBe(8)
    for (const text of texts) expect(text).toMatch(/#5/)
    for (const text of texts) expect(text).not.toMatch(/[{}]|undefined|NaN|ticker\./)
  })
})

describe('useTickerItems - Platz-1-Satz', () => {
  beforeEach(() => get.mockReset())

  async function leaderTexts(locale: 'de' | 'en', items: RawTickerItem[]): Promise<string[]> {
    get.mockImplementation((url: string) => Promise.resolve({ data: url === '/public/leaderboard/ticker' ? items : [] }))
    const i18n = createI18n({ legacy: false, locale, messages: { de, en, nb, sv }, missingWarn: false, fallbackWarn: false })
    let api!: ReturnType<typeof useTickerItems>
    createApp(defineComponent({ setup() { api = useTickerItems(); return () => null } }))
      .use(createPinia()).use(i18n).mount(document.createElement('div'))
    await api.fetchTicker({ withUserItems: false })
    return api.items.value.map(i => i.text)
  }
  const leader = (category: string, value: string): RawTickerItem =>
    ({ type: 'LEADER', messageKey: 'leader', params: { category, name: 'max', value }, variant: 'leader' })

  it('dekliniert die Anzahl nach "mit" und kennt die Einzahl', async () => {
    const texts = await leaderTexts('de', [
      leader('MONTHLY_CHARGES', '5'), leader('MONTHLY_CHARGES', '1'),
      leader('MONTHLY_NIGHT_OWL', '1'), leader('MONTHLY_NIGHT_OWL', '4'),
    ])
    expect(texts.map(x => x.replace(/^#1 [^:]+: /, ''))).toEqual([
      'max mit 5 Ladevorgängen', 'max mit 1 Ladevorgang', 'max mit 1 Nacht-Ladung', 'max mit 4 Nacht-Ladungen',
    ])
  })

  it('nimmt auf Englisch die Einzahl bei 1', async () => {
    const texts = await leaderTexts('en', [leader('MONTHLY_CHARGES', '1'), leader('MONTHLY_CHARGES', '3')])
    expect(texts.map(x => x.replace(/^#1 [^:]+: /, ''))).toEqual(['max with 1 charge', 'max with 3 charges'])
  })
})
