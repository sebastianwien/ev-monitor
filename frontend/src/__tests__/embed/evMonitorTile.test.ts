// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const SCRIPT = readFileSync(resolve(__dirname, '../../../public/embed/ev-monitor-tile.js'), 'utf8')

type Tile = { render: (el: Element) => Promise<void> }

function loadScript(): Tile {
  // eslint-disable-next-line no-new-func
  new Function(SCRIPT)()
  return (window as unknown as { EvMonitorTile: Tile }).EvMonitorTile
}

function mount(attrs: Record<string, string>): HTMLElement {
  const host = document.createElement('div')
  host.setAttribute('data-ev-monitor-tile', '')
  Object.entries(attrs).forEach(([k, v]) => host.setAttribute(k, v))
  document.body.appendChild(host)
  return host
}

function mockFetch(body: unknown, ok = true) {
  const fn = vi.fn().mockResolvedValue({ ok, json: () => Promise.resolve(body) })
  vi.stubGlobal('fetch', fn)
  return fn
}

const text = (host: HTMLElement) => host.shadowRoot!.textContent ?? ''
const link = (host: HTMLElement) => host.shadowRoot!.querySelector('a') as HTMLAnchorElement

describe('ev-monitor-tile.js', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('renders consumption and seasonal ranges with UTM link', async () => {
    const fetchMock = mockFetch({
      hasData: true, modelDisplayName: 'Volkswagen ID.3', realConsumptionKwhPer100km: 16.8,
      typicalRangeKm: 410, summerRangeKm: 450, winterRangeKm: 320, logCount: 1812, uniqueCars: 34,
      modelUrl: 'https://ev-monitor.net/modelle/Volkswagen/ID.3',
    })
    const host = mount({ 'data-brand': 'Volkswagen', 'data-model': 'ID.3', 'data-utm-source': 'firstev' })
    const tile = loadScript()
    await tile.render(host)

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.pathname).toBe('/api/public/embed/model-tile')
    expect(url.searchParams.get('brand')).toBe('Volkswagen')
    expect(fetchMock.mock.calls[0][1]).toEqual({ credentials: 'omit' })

    expect(text(host)).toContain('16,8 kWh')
    expect(text(host)).toContain('320 km')
    expect(text(host)).toContain('450 km')
    expect(text(host)).toContain('1.812 Ladevorgängen von 34 Fahrzeugen')
    const href = new URL(link(host).href)
    expect(href.pathname).toBe('/modelle/Volkswagen/ID.3')
    expect(href.searchParams.get('utm_source')).toBe('firstev')
    expect(href.searchParams.get('utm_medium')).toBe('tile')
    expect(href.searchParams.get('utm_campaign')).toBe('volkswagen_id_3')
    expect(link(host).rel).toBe('noopener')
  })

  it('shows the typical range when seasonal values are missing', async () => {
    mockFetch({
      hasData: true, modelDisplayName: 'Volkswagen ID.3', realConsumptionKwhPer100km: 16.8,
      typicalRangeKm: 410, summerRangeKm: null, winterRangeKm: null, logCount: 40, uniqueCars: 1,
      modelUrl: 'https://ev-monitor.net/modelle/Volkswagen/ID.3',
    })
    const host = mount({ 'data-brand': 'Volkswagen', 'data-model': 'ID.3' })
    await loadScript().render(host)

    expect(text(host)).toContain('410 km')
    expect(text(host)).toContain('1 Fahrzeug.')
    expect(text(host)).not.toContain('Winter')
  })

  it('renders the fallback with a registration link when there is no data', async () => {
    mockFetch({ hasData: false, modelDisplayName: null })
    const host = mount({ 'data-brand': 'Foo', 'data-model': 'Bar', 'data-utm-source': 'firstev' })
    await loadScript().render(host)

    expect(text(host)).toContain('Noch keine Messdaten. Fährst du einen? Trag ihn ein.')
    const href = new URL(link(host).href)
    expect(href.pathname).toBe('/register')
    expect(href.searchParams.get('utm_source')).toBe('firstev')
  })

  it('renders the fallback when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const host = mount({ 'data-brand': 'Volkswagen', 'data-model': 'ID.3' })
    await loadScript().render(host)

    expect(text(host)).toContain('Noch keine Messdaten')
  })

  it('treats API strings as text, never as HTML', async () => {
    mockFetch({
      hasData: true, modelDisplayName: '<img src=x onerror=alert(1)>', realConsumptionKwhPer100km: 16.8,
      typicalRangeKm: 410, summerRangeKm: null, winterRangeKm: null, logCount: 1, uniqueCars: 1,
      modelUrl: 'javascript:alert(1)',
    })
    const host = mount({ 'data-brand': 'X', 'data-model': 'Y' })
    await loadScript().render(host)

    expect(host.shadowRoot!.querySelector('img')).toBeNull()
    expect(link(host).href.startsWith('javascript:')).toBe(false)
  })
})
