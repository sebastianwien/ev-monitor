// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach, beforeAll } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../../stores/auth'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../../api/axios'
import PlaceSearchLayer from '../PlaceSearchLayer.vue'

/**
 * "Woanders geladen?": tippen genügt. Der beste Adresstreffer gilt sofort, darunter "Privat geladen" an dieser
 * Adresse und die Säulen dort - ohne erst einen Vorschlag antippen zu müssen.
 */
const photon = (...features: unknown[]) => ({ ok: true, json: async () => ({ type: 'FeatureCollection', features }) })
const hit = (id: number, street: string, city: string, lon: number, lat: number) => ({ type: 'Feature',
  geometry: { type: 'Point', coordinates: [lon, lat] },
  properties: { osm_id: id, osm_type: 'N', type: 'house', street, housenumber: '3', city } })
const LINDEN_POTSDAM = hit(1, 'Lindenweg', 'Potsdam', 13.06, 52.39)
const LINDEN_BERLIN = hit(2, 'Lindenweg', 'Berlin', 13.4, 52.5)
const billa = { name: 'Billa', known: false, distanceMeters: 120, geohash: 'u33dc81', address: 'Lindenweg 1, Potsdam',
  fastCharging: false, chargePoints: 2, maxAcKw: 22, maxDcKw: null, plugTypes: [], registerId: null, latitude: 52.391, longitude: 13.061 }
const ionity = { name: 'Ionity', known: true, geohash: 'u33dc82', address: 'Autobahn 1, Kassel',
  fastCharging: true, chargePoints: 6, maxAcKw: null, maxDcKw: 350, plugTypes: ['CCS'], registerId: 9 }

let app: App | null = null
beforeAll(() => { i18n.global.locale.value = 'de' })
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.useRealTimers(); localStorage.clear() })

async function mount(props: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const choose = vi.fn(); const picked = vi.fn(); const close = vi.fn()
  app = createApp(PlaceSearchLayer, { near: null, onChoose: choose, onPicked: picked, onClose: close, ...props })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  const q = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null
  return { q, choose, picked, close }
}
async function type(text: string) {
  const input = document.querySelector('[data-testid="place-layer"] input') as HTMLInputElement
  input.value = text
  input.dispatchEvent(new Event('input'))
  await nextTick(); await vi.runAllTimersAsync(); await nextTick()
}
/** Sichtbar = nicht in einer zugeklappten Collapse (inert; jsdom schreibt offen inert="false") */
const shown = (el: Element | null) => !!el && !el.closest('[inert]:not([inert="false"])')
const nearbyCalls = () => vi.mocked(api.get).mock.calls.filter(c => String(c[0]).includes('nearby-stations'))

describe('PlaceSearchLayer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(api.get).mockReset()
    vi.mocked(api.get).mockImplementation(((url: string) => Promise.resolve({ data: url.includes('nearby-stations') ? [billa] : [] })) as never)
    globalThis.fetch = vi.fn().mockResolvedValue(photon(LINDEN_POTSDAM, LINDEN_BERLIN)) as any
  })

  it('der beste Treffer gilt sofort: "Privat geladen" mit der vervollständigten Adresse übernimmt sie', async () => {
    const { q, picked, choose } = await mount()
    await type('Lindenweg 3')
    expect(q('place-layer-private')!.textContent).toContain('Privat geladen')
    expect(q('place-layer-private')!.textContent).toContain('Lindenweg 3, Potsdam')
    q('place-layer-private')!.click()
    expect(picked).toHaveBeenCalledWith({ latitude: 52.39, longitude: 13.06, name: 'Lindenweg 3, Potsdam' })
    expect(choose).toHaveBeenCalledWith({ kind: 'home' })
    expect(picked.mock.invocationCallOrder[0]).toBeLessThan(choose.mock.invocationCallOrder[0])
  })

  it('zeigt die Säulen an der Adresse; ein Tap übernimmt Adresse und Säule', async () => {
    const { q, picked, choose } = await mount()
    await type('Lindenweg 3')
    expect(nearbyCalls()[0][1]).toMatchObject({ params: { lat: 52.39, lon: 13.06 } })
    const tile = q('place-layer-stations')!.querySelector('button')!
    expect(tile.textContent).toContain('Billa')
    tile.click()
    expect(picked).toHaveBeenCalledWith(expect.objectContaining({ name: 'Lindenweg 3, Potsdam' }))
    expect(choose).toHaveBeenCalledWith({ kind: 'station', station: billa, viaSearch: true })
  })

  it('ein anderer Treffer wird per Tap zur Adresse, ohne schon etwas zu wählen', async () => {
    const { q, choose } = await mount()
    await type('Lindenweg 3')
    const other = q('place-layer-other')!.querySelector('button')!
    expect(other.textContent).toContain('Lindenweg 3, Berlin')
    other.click()
    await nextTick(); await vi.runAllTimersAsync(); await nextTick()
    expect(q('place-layer-private')!.textContent).toContain('Lindenweg 3, Berlin')
    expect(nearbyCalls()[nearbyCalls().length - 1][1]).toMatchObject({ params: { lat: 52.5, lon: 13.4 } })
    expect(choose).not.toHaveBeenCalled()
  })

  it('Säulen aus dem Register nach Namen: Tap wählt die Säule, ohne den Ort zu ändern', async () => {
    vi.mocked(api.get).mockImplementation(((url: string) => Promise.resolve({ data: url.includes('search-stations') ? [ionity] : [] })) as never)
    globalThis.fetch = vi.fn().mockResolvedValue(photon()) as any
    const { q, picked, choose } = await mount()
    await type('Ionity Kassel')
    const tile = q('place-layer-matches')!.querySelector('button')!
    expect(tile.textContent).toContain('Ionity')
    tile.click()
    expect(choose).toHaveBeenCalledWith({ kind: 'station', station: ionity, viaSearch: true })
    expect(picked).not.toHaveBeenCalled()
  })

  it('bevorzugt Adressen nahe der eigenen Position', async () => {
    await mount({ near: { lat: 52.53, lon: 13.47 } })
    await type('Lindenweg 3')
    expect(String(vi.mocked(fetch).mock.calls[0][0])).toContain('&lat=52.5&lon=13.5')
  })

  it('leeres Feld: zuletzt gewählte Adressen, ein Tap macht sie zur Adresse', async () => {
    localStorage.setItem('recent-addresses:u1', JSON.stringify([{ latitude: 52.53, longitude: 13.45, name: 'Storkower Str. 140, Berlin' }]))
    setActivePinia(createPinia()); useAuthStore().user = { userId: 'u1' } as never
    const { q } = await mount()
    const row = q('place-layer-recent')!.querySelector('button')!
    expect(row.textContent).toContain('Storkower Str. 140, Berlin')
    row.click()
    await nextTick(); await vi.runAllTimersAsync(); await nextTick()
    expect(q('place-layer-private')!.textContent).toContain('Storkower Str. 140, Berlin')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('während der Tipp-Pause ist der alte Treffer gesperrt: ein schneller Tap speichert keinen falschen Ort', async () => {
    const { q, picked } = await mount()
    await type('Lindenweg')
    const input = document.querySelector('[data-testid="place-layer"] input') as HTMLInputElement
    input.value = 'Lindenweg 3, Berlin'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    expect((q('place-layer-private') as HTMLButtonElement).disabled).toBe(true)
    q('place-layer-private')!.click()
    expect(picked).not.toHaveBeenCalled()
  })

  it('Such-Taste sucht sofort, ohne die Tipp-Pause', async () => {
    await mount()
    const input = document.querySelector('[data-testid="place-layer"] input') as HTMLInputElement
    input.value = 'Lindenweg 3'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('neue Adresse: die alten Säulen bleiben gedimmt stehen, bis die neuen da sind - kein Springen über einen Spinner', async () => {
    const { q } = await mount()
    await type('Lindenweg 3')
    let release: (v: unknown) => void = () => {}
    vi.mocked(api.get).mockImplementation(((url: string) => url.includes('nearby-stations')
      ? new Promise(r => { release = r }) : Promise.resolve({ data: [] })) as never)
    q('place-layer-other')!.querySelector('button')!.click()
    await nextTick(); await nextTick()
    const list = q('place-layer-stations')!
    expect(list.textContent).toContain('Billa')
    expect(list.querySelector('[data-testid="place-layer-station-list"]')!.className).toContain('opacity-50')
    release({ data: [] })
  })

  describe('Suchleiste gleitet vom Knopf nach oben', () => {
    let animate: ReturnType<typeof vi.fn>
    beforeEach(() => {
      animate = vi.fn(() => ({ finished: Promise.resolve(), cancel: () => {} }))
      ;(HTMLElement.prototype as any).animate = animate
    })
    afterEach(() => { delete (HTMLElement.prototype as any).animate })

    it('startet an der Stelle von "Woanders geladen?" und gleitet zurück, bevor sie schließt', async () => {
      const { q, close } = await mount({ origin: { top: 700, left: 16, width: 358, height: 44 } })
      await nextTick()
      const bar = animate.mock.contexts.find((el: HTMLElement) => el.dataset.testid === 'place-layer-bar') as HTMLElement
      expect(bar).toBeTruthy()
      const first = animate.mock.calls[animate.mock.contexts.indexOf(bar)][0][0]
      expect(first.transform).toBe('translate(16px, 700px)')
      animate.mockClear()
      q('place-layer-back')!.click()
      expect(close).not.toHaveBeenCalled()
      expect(animate.mock.contexts).toContain(bar)
      await vi.runAllTimersAsync(); await nextTick()
      expect(close).toHaveBeenCalledTimes(1)
    })

    it('ohne Herkunft (Bearbeiten, Tastatur) keine Bewegung', async () => {
      await mount()
      await nextTick()
      expect(animate.mock.contexts.some((el: HTMLElement) => el.dataset.testid === 'place-layer-bar')).toBe(false)
    })
  })

  it('Zurück schließt die Ebene, ein zweites Schließen meldet nichts doppelt', async () => {
    const { q, close } = await mount()
    q('place-layer-back')!.click()
    q('place-layer')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(close).toHaveBeenCalledTimes(1)
  })

  it('Escape schließt die Ebene', async () => {
    const { q, close } = await mount()
    q('place-layer')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(close).toHaveBeenCalledTimes(1)
  })

  it('ohne Adresse und ohne Säule ein Hinweis statt leerer Fläche', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(photon()) as any
    const { q } = await mount()
    await type('Nirgendwo 99')
    expect(shown(q('place-layer-none'))).toBe(true)
    expect(shown(q('place-layer-private'))).toBe(false)
  })
})
