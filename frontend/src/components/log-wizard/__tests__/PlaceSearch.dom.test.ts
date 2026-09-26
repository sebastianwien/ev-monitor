// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../../api/axios'
import PlaceSearch from '../PlaceSearch.vue'

const enbw = { name: 'EnBW', known: true, maxAcKw: null, maxDcKw: 300, fastCharging: true, chargePoints: 4,
  address: 'Am Fuchsgraben 1, 91586 Lichtenau', plugTypes: ['CCS'], registerId: 1, geohash: 'u0z87g2' }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.useRealTimers() })

function mount() {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const choose = vi.fn(); const picked = vi.fn()
  app = createApp(PlaceSearch, { label: 'Ort', onChoose: choose, onPicked: picked })
  app.use(i18n)
  app.mount(host)
  return { host, choose, picked }
}

async function type(host: HTMLElement, text: string) {
  const input = host.querySelector('input') as HTMLInputElement
  input.value = text
  input.dispatchEvent(new Event('input'))
  await nextTick(); await vi.runAllTimersAsync(); await nextTick()
}

describe('PlaceSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(api.get).mockReset()
    globalThis.fetch = vi.fn().mockResolvedValue({ json: async () => [{ place_id: 7, lat: '49.28', lon: '10.71', display_name: 'Am Fuchsgraben, Lichtenau' }] }) as any
  })

  it('zeigt Register-Treffer als Säulen-Zeile und meldet die Wahl als Standort', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [enbw] } as never)
    const { host, choose } = mount()
    await type(host, 'EnBW Lichtenau')

    const row = host.querySelector('[data-testid="place-search-station"]') as HTMLElement
    expect(row.textContent).toContain('EnBW')
    expect(row.textContent).toContain('Am Fuchsgraben 1, 91586 Lichtenau')
    expect(row.textContent).toContain('DC 300 kW')
    row.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(choose).toHaveBeenCalledWith({ kind: 'station', station: enbw })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('Adresse suchen ist eine eigene Zeile, ruft Nominatim erst auf Tap und meldet den gewählten Ort', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never)
    const { host, picked, choose } = mount()
    await type(host, 'Fuchsgraben 1 Lichtenau')

    expect(host.querySelector('[data-testid="place-search-station"]')).toBeNull()
    const addressRow = host.querySelector('[data-testid="place-search-address"]') as HTMLElement
    expect(addressRow.textContent).toContain('Fuchsgraben 1 Lichtenau')
    expect(fetch).not.toHaveBeenCalled()

    addressRow.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await vi.runAllTimersAsync(); await nextTick()
    expect(fetch).toHaveBeenCalledTimes(1)
    const suggestion = host.querySelector('[data-testid="place-search-suggestion"]') as HTMLElement
    expect(suggestion.textContent).toContain('Am Fuchsgraben, Lichtenau')
    suggestion.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(picked).toHaveBeenCalledWith({ latitude: 49.28, longitude: 10.71, name: 'Am Fuchsgraben, Lichtenau' })
    expect(choose).not.toHaveBeenCalled()
  })

  it('ohne Adress-Treffer steht ein Hinweis statt einer leeren Liste', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never)
    globalThis.fetch = vi.fn().mockResolvedValue({ json: async () => [] }) as any
    const { host } = mount()
    await type(host, 'Nirgendwo 99')
    ;(host.querySelector('[data-testid="place-search-address"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await vi.runAllTimersAsync(); await nextTick()
    expect(host.querySelector('[data-testid="place-search-no-address"]')).not.toBeNull()
  })
})
