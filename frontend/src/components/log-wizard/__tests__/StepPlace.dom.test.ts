// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeAll } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))
import StepPlace from '../StepPlace.vue'

/**
 * Privat ist kein gespeichertes Zuhause, sondern "nicht öffentlich" an der Position des Logs.
 * Die Zeile sagt darum, wo: hier (mit Adresse), am gespeicherten Ort oder ohne Ortsangabe.
 */
let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
beforeAll(() => { i18n.global.locale.value = 'de' })

const kaufland = { name: 'Kaufland', known: true, distanceMeters: 221, maxAcKw: 43, maxDcKw: 50, fastCharging: true, chargePoints: 2,
  address: 'Storkower Str. 139, 10407 Berlin', plugTypes: ['CCS'], registerId: 1, geohash: 'u33dc8x' }

async function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const choose = vi.fn()
  app = createApp(StepPlace, {
    place: null, selectedCpo: null, selectedSite: null, stations: [], stationsLoading: false,
    permission: 'unavailable', locationStatus: 'idle', recentCpos: [], allCpos: [], onChoose: choose, ...props,
  })
  app.use(i18n)
  app.mount(host)
  await nextTick()
  const row = host.querySelector('[data-testid="place-here"]') as HTMLButtonElement
  return { host, row, choose }
}

describe('StepPlace: Privat-Zeile', () => {
  it('mit gewählter Adresse: hier privat, die Adresse als Untertitel, Tap wählt privat', async () => {
    const { row, choose } = await mount({ latitude: 52.53, longitude: 13.45, locationStatus: 'success', addressLabel: 'Storkower Str. 140, Berlin' })
    expect(row.textContent).toContain('Hier privat geladen')
    expect(row.textContent).toContain('Storkower Str. 140, Berlin')
    row.click()
    expect(choose).toHaveBeenCalledWith({ kind: 'home' })
  })

  it('mit GPS ohne Adresse: hier privat an deinem Standort', async () => {
    const { row } = await mount({ latitude: 52.53, longitude: 13.45, locationStatus: 'success' })
    expect(row.textContent).toContain('an deinem Standort')
  })

  it('ohne Position: privat ohne Ortsangabe, im Bearbeiten-Dialog mit gespeichertem Ort ohne diesen Zusatz', async () => {
    const { row } = await mount({})
    expect(row.textContent).toContain('Privat geladen')
    expect(row.textContent).toContain('ohne Ortsangabe')
    app!.unmount(); app = null; document.body.innerHTML = ''
    const edit = await mount({ storedPlace: true })
    expect(edit.row.textContent).not.toContain('ohne Ortsangabe')
  })

  it('öffentlich ohne Säule heißt "Säule nicht dabei?", solange Säulen in der Liste stehen', async () => {
    const withList = await mount({ latitude: 52.53, longitude: 13.45, locationStatus: 'success', stations: [kaufland] })
    expect(withList.host.querySelector('[data-testid="place-other"]')!.textContent).toContain('Säule nicht dabei?')
    app!.unmount(); app = null; document.body.innerHTML = ''
    const empty = await mount({})
    expect(empty.host.querySelector('[data-testid="place-other"]')!.textContent).toContain('Öffentlich geladen')
  })
})
