// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { i18n } from '../../../i18n'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../../stores/auth'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn() } }))
import api from '../../../api/axios'
import PlaceSearch from '../PlaceSearch.vue'

const enbw = { name: 'EnBW', known: true, maxAcKw: null, maxDcKw: 300, fastCharging: true, chargePoints: 4,
  address: 'Am Fuchsgraben 1, 91586 Lichtenau', plugTypes: ['CCS'], registerId: 1, geohash: 'u0z87g2' }

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.useRealTimers() })

function mount(opts: { userId?: string } = {}) {
  if (opts.userId) {
    const pinia = createPinia(); setActivePinia(pinia)
    useAuthStore().user = { userId: opts.userId } as never
  }
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
    expect(choose).toHaveBeenCalledWith({ kind: 'station', station: enbw, viaSearch: true })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('nach der Wahl startet der übernommene Name keine neue Suche und die Liste bleibt zu', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [enbw] } as never)
    const { host } = mount()
    await type(host, 'EnBW Lichtenau')
    const calls = vi.mocked(api.get).mock.calls.length
    ;(host.querySelector('[data-testid="place-search-station"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick(); await vi.runAllTimersAsync(); await nextTick()
    expect(vi.mocked(api.get).mock.calls.length).toBe(calls)
    expect(host.querySelector('[role="listbox"]')).toBeNull()
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

  it('beim Fokus ins leere Feld: zuletzt gewählte Adressen, ein Tap übernimmt sie', async () => {
    localStorage.setItem('recent-addresses:u1', JSON.stringify([{ latitude: 52.53, longitude: 13.45, name: 'Storkower Str. 140, Berlin' }]))
    const { host, picked } = mount({ userId: 'u1' })
    expect(host.querySelector('[data-testid="place-search-recent"]')).toBeNull()
    const input = host.querySelector('input') as HTMLInputElement
    input.dispatchEvent(new Event('focus')); await nextTick()
    const row = host.querySelector('[data-testid="place-search-recent"]') as HTMLElement
    expect(row.textContent).toContain('Storkower Str. 140, Berlin')
    row.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })); await nextTick()
    expect(picked).toHaveBeenCalledWith({ latitude: 52.53, longitude: 13.45, name: 'Storkower Str. 140, Berlin' })
    expect(input.value).toBe('Storkower Str. 140, Berlin')
    expect(host.querySelector('[data-testid="place-search-recent"]')).toBeNull()
  })

  it('eine gewählte Adresse steht beim nächsten Mal oben', async () => {
    localStorage.clear()
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never)
    const { host } = mount({ userId: 'u1' })
    await type(host, 'Fuchsgraben 1 Lichtenau')
    ;(host.querySelector('[data-testid="place-search-address"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await vi.runAllTimersAsync(); await nextTick()
    ;(host.querySelector('[data-testid="place-search-suggestion"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(JSON.parse(localStorage.getItem('recent-addresses:u1')!)[0].name).toBe('Am Fuchsgraben, Lichtenau')
  })

  it('X im Feld leert die Eingabe', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never)
    const { host } = mount()
    expect(host.querySelector('[data-testid="place-search-clear"]')).toBeNull()
    await type(host, 'Storkower')
    ;(host.querySelector('[data-testid="place-search-clear"]') as HTMLElement).click(); await nextTick()
    expect((host.querySelector('input') as HTMLInputElement).value).toBe('')
    expect(host.querySelector('[data-testid="place-search-clear"]')).toBeNull()
  })
})
