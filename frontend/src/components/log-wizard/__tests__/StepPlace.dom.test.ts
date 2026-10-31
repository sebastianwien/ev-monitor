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
  it('mit gewählter Adresse: "Privat geladen" mit der Adresse (nicht "hier", der Nutzer ist nicht dort), Tap wählt privat', async () => {
    const { q, choose } = await mount({ ...HERE, addressLabel: 'Storkower Str. 140, Berlin' })
    expect(q('place-here')!.textContent).toContain('Privat geladen')
    expect(q('place-here')!.textContent).toContain('Storkower Str. 140, Berlin')
    q('place-here')!.click()
    expect(choose).toHaveBeenCalledWith({ kind: 'home' })
  })

  it('mit GPS ohne Adresse: hier privat, Dein Standort', async () => {
    const { q } = await mount(HERE)
    expect(q('place-here')!.textContent).toContain('Dein Standort')
  })

  it('hier öffentlich: wählt öffentlich ohne Anbieter, keine Anbieterauswahl', async () => {
    const { q, host, choose } = await mount({ ...HERE, stations: [kaufland] })
    expect(q('place-public')!.textContent).toContain('Hier öffentlich')
    expect(q('place-public')!.textContent).toContain('Ladesäule nicht gefunden')
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

  it('öffentlich ohne Säule steht unter der Säulenliste, damit niemand sie statt der Säule tippt', async () => {
    const { host } = await mount({ ...HERE, stations: [kaufland], canExpand: true, nextRadius: 1000 })
    const order = [...host.querySelectorAll('button')].map(b => b.dataset.testid ?? b.textContent!.trim().slice(0, 8))
    expect(order.indexOf('place-public')).toBeGreaterThan(order.indexOf('Kaufland'))
    expect(order.indexOf('place-public')).toBeGreaterThan(order.indexOf('expand-radius'))
  })

  it('solange die Säulen laden, ist öffentlich ohne Säule gesperrt, aber schon da - privat springt nicht unter dem Daumen', async () => {
    const { q } = await mount({ ...HERE, stationsLoading: true })
    expect(q('place-here')).not.toBeNull()
    expect(q('place-public')!.disabled).toBe(true)
  })

  it('"Woanders geladen?" öffnet die Suchebene, Zurück schließt sie', async () => {
    const { q } = await mount({ ...HERE, stations: [kaufland] })
    expect(q('place-elsewhere')!.textContent).toContain('Woanders geladen?')
    q('place-elsewhere')!.click()
    await nextTick()
    expect(document.querySelector('[data-testid="place-layer"]')).not.toBeNull()
    ;(document.querySelector('[data-testid="place-layer-back"]') as HTMLButtonElement).click()
    await nextTick()
    expect(document.querySelector('[data-testid="place-layer"]')).toBeNull()
  })

  it('ohne Säulen steht die Suche ohnehin offen: kein "Woanders"', async () => {
    const { q } = await mount({ ...HERE, exhausted: true })
    expect(q('place-elsewhere')).toBeNull()
  })

  it('Umkreis erweitern: der Spinner steht dort, wo der Knopf war; öffentlich bleibt stehen', async () => {
    const { host, q } = await mount({ ...HERE, stations: [kaufland], stationsLoading: true, radiusMeters: 1000 })
    const order = [...host.querySelectorAll('button, [data-testid="stations-loading"]')].map(b => (b as HTMLElement).dataset.testid ?? b.textContent!.trim().slice(0, 8))
    expect(order.indexOf('stations-loading')).toBeGreaterThan(order.indexOf('Kaufland'))
    expect(order.filter(x => x === 'stations-loading')).toHaveLength(1)
    expect(q('place-public')).not.toBeNull()
    // Über dem Spinner stehen Säulen: "keine Säule in 250 m" wäre falsch
    expect(q('stations-loading')!.textContent).toContain('Suche im Umkreis von 1 km')
    expect(q('stations-loading')!.textContent).not.toContain('Keine Säule')
  })
})

const station = (name: string, i: number, extra: Record<string, unknown> = {}) =>
  ({ ...kaufland, name, distanceMeters: 100 + i * 50, geohash: `u33dc8${i}`, ...extra })
const five = ['Kaufland', 'Ionity', 'EnBW', 'Aldi', 'Lidl'].map((n, i) => station(n, i))
const order = (host: HTMLElement) => [...host.querySelectorAll('button')].map(b => b.dataset.testid ?? b.textContent!.trim().slice(0, 8))
const shows = (host: HTMLElement, name: string) => [...host.querySelectorAll('button b')].some(b => b.textContent === name)

describe('StepPlace: Säulen zuerst, privat zuletzt', () => {
  it('unten öffentlich und privat in einer Zeile, darunter Woanders über die ganze Breite', async () => {
    const { host, q } = await mount({ ...HERE, stations: [kaufland] })
    const o = order(host)
    expect(o.slice(-3)).toEqual(['place-public', 'place-here', 'place-elsewhere'])
    expect(o.indexOf('place-public')).toBeGreaterThan(o.indexOf('Kaufland'))
    expect(q('place-public')!.parentElement).toBe(q('place-here')!.parentElement)
    expect(q('place-elsewhere')!.parentElement).not.toBe(q('place-here')!.parentElement)
  })

  it('Überschrift der Liste nennt, was dort steht', async () => {
    const { host } = await mount({ ...HERE, stations: [kaufland] })
    expect(host.querySelector('h2')!.textContent).toContain('Ladesäulen in der Nähe')
  })

  it('zeigt drei Säulen, der Rest klappt auf; Umkreis erweitern erst danach', async () => {
    const { host, q } = await mount({ ...HERE, stations: five, canExpand: true, nextRadius: 1000 })
    expect(shows(host, 'EnBW')).toBe(true)
    expect(shows(host, 'Aldi')).toBe(false)
    expect(q('expand-radius')).toBeNull()
    expect(q('more-stations')!.textContent).toContain('2 weitere Säulen')
    q('more-stations')!.click()
    await nextTick()
    expect(shows(host, 'Lidl')).toBe(true)
    expect(q('more-stations')).toBeNull()
    expect(q('expand-radius')).not.toBeNull()
  })

  it('eine gewählte Säule weiter unten bleibt sichtbar', async () => {
    const lidl = five[4]
    const { host, q } = await mount({ ...HERE, stations: five, place: 'station', selectedSite: { name: lidl.name, geohash: lidl.geohash } })
    expect(shows(host, 'Lidl')).toBe(true)
    expect(q('more-stations')).toBeNull()
  })

  it('Säule mit Position bekommt ein Kartenbild, ohne Position bleibt das Symbol', async () => {
    const { host } = await mount({ ...HERE, stations: [station('Kaufland', 0, { latitude: 52.5342, longitude: 13.4516 }), station('Ionity', 1)] })
    const thumbs = host.querySelectorAll('[data-testid="station-thumb"]')
    expect(thumbs).toHaveLength(1)
    expect(thumbs[0].querySelector('img')!.getAttribute('src')).toMatch(/tile\.openstreetmap\.org\/17\//)
  })
})
