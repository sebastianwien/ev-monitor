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

  const MODEL_3 = {
    hasData: true, modelDisplayName: 'Tesla Model 3', realConsumptionKwhPer100km: 20.0,
    typicalRangeKm: 340, summerRangeKm: 330, winterRangeKm: 300, logCount: 6666, uniqueCars: 42,
    modelUrl: 'https://ev-monitor.net/modelle/Tesla/Model_3',
    summerConsumptionKwhPer100km: 19.0, winterConsumptionKwhPer100km: 22.0, summerLogCount: 1861, winterLogCount: 1565,
    typicalChargeCostEur: 8.86, fastStopCostEur: 19.29,
  }
  const cta = (host: HTMLElement) => host.shadowRoot!.querySelector('[data-cta]') as HTMLAnchorElement

  it('links the main button to the model page with UTM, before any chip is tapped', async () => {
    mockFetch(MODEL_3)
    const host = mount({ 'data-brand': 'Tesla', 'data-model': 'Model_3', 'data-utm-source': 'firstev' })
    await loadScript().render(host)

    const href = new URL(cta(host).href)
    expect(href.pathname).toBe('/modelle/Tesla/Model_3')
    expect(href.searchParams.get('utm_source')).toBe('firstev')
    expect(href.searchParams.get('utm_medium')).toBe('tile')
    expect(href.searchParams.get('utm_campaign')).toBe('tesla_model_3')
    expect(href.searchParams.get('utm_content')).toBe('cta')
    expect(cta(host).rel).toBe('noopener')
  })

  it('each of the three questions links to the model page with its own utm_content', async () => {
    mockFetch(MODEL_3)
    const host = mount({ 'data-brand': 'Tesla', 'data-model': 'Model_3', 'data-utm-source': 'firstev' })
    await loadScript().render(host)

    const questions = [...host.shadowRoot!.querySelectorAll('a.q')].map(a => new URL((a as HTMLAnchorElement).href))
    expect(questions.map(u => u.searchParams.get('utm_content'))).toEqual(['frage-woche', 'frage-kosten', 'frage-urlaub'])
    for (const u of questions) {
      expect(u.pathname).toBe('/modelle/Tesla/Model_3')
      expect(u.searchParams.get('utm_source')).toBe('firstev')
    }
  })

  it('renders the fallback with a registration link when there is no data', async () => {
    mockFetch({ hasData: false, modelDisplayName: null })
    const host = mount({ 'data-brand': 'Foo', 'data-model': 'Bar', 'data-utm-source': 'firstev' })
    await loadScript().render(host)

    expect(text(host)).toContain('Noch keine Messdaten')
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
