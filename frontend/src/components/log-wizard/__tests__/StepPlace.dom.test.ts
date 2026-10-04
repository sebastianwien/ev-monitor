// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeAll } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))
import StepPlace from '../StepPlace.vue'

/**
 * Privat und öffentlich sind kein gespeichertes Zuhause, sondern "nicht öffentlich" bzw. "öffentlich"
 * an der Position des Logs. Ohne Position (GPS noch nicht da, verweigert, keine Adresse) gibt es
 * darum beide Zeilen nicht: ein Log ohne Ort soll nicht entstehen.
 */
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeAll(() => { i18n.global.locale.value = 'de' })

const kaufland = { name: 'Kaufland', known: true, distanceMeters: 221, maxAcKw: 43, maxDcKw: 50, fastCharging: true, chargePoints: 2,
  address: 'Storkower Str. 139, 10407 Berlin', plugTypes: ['CCS'], registerId: 1, geohash: 'u33dc8x' }
const HERE = { latitude: 52.53, longitude: 13.45, locationStatus: 'success' }

async function mount(props: Record<string, unknown>) {
  app?.unmount(); document.body.innerHTML = ''
  const host = document.createElement('div')
  document.body.appendChild(host)
  const choose = vi.fn()
  app = createApp(StepPlace, {
    place: null, selectedCpo: null, selectedSite: null, stations: [], stationsLoading: false,
    permission: 'unavailable', locationStatus: 'idle', onChoose: choose, ...props,
  })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  const q = (id: string) => host.querySelector(`[data-testid="${id}"]`) as HTMLButtonElement | null
  return { host, q, choose }
}

describe('StepPlace: privat und öffentlich hier', () => {
  it('mit gewählter Adresse: hier privat mit der Adresse, Tap wählt privat', async () => {
    const { q, choose } = await mount({ ...HERE, addressLabel: 'Storkower Str. 140, Berlin' })
    expect(q('place-here')!.textContent).toContain('Hier privat geladen')
    expect(q('place-here')!.textContent).toContain('Storkower Str. 140, Berlin')
    q('place-here')!.click()
    expect(choose).toHaveBeenCalledWith({ kind: 'home' })
  })

  it('mit GPS ohne Adresse: hier privat an deinem Standort', async () => {
    const { q } = await mount(HERE)
    expect(q('place-here')!.textContent).toContain('an deinem Standort')
  })

  it('hier öffentlich: wählt öffentlich ohne Anbieter, keine Anbieterauswahl', async () => {
    const { q, host, choose } = await mount({ ...HERE, stations: [kaufland] })
    expect(q('place-public')!.textContent).toContain('Hier öffentlich geladen')
    q('place-public')!.click()
    expect(choose).toHaveBeenCalledWith({ kind: 'other', cpoName: null })
    expect(host.querySelector('input[type="search"]')).toBeNull()
  })

  it('ohne Position weder privat noch öffentlich - kein Log ohne Ort', async () => {
    const { q } = await mount({})
    expect(q('place-here')).toBeNull()
    expect(q('place-public')).toBeNull()
  })

  it('während der Ortung noch keine Zeile, damit kein schneller Tap ohne Ort speichert', async () => {
    const { q } = await mount({ permission: 'granted', locationStatus: 'loading' })
    expect(q('place-here')).toBeNull()
  })

  it('Bearbeiten-Dialog mit gespeichertem Ort: beide Zeilen ohne neue Adresse', async () => {
    const { q } = await mount({ storedPlace: true, place: 'other', selectedCpo: 'Ionity' })
    expect(q('place-here')!.textContent).toContain('Privat geladen')
    expect(q('place-public')!.textContent).toContain('Ionity')
  })
})
