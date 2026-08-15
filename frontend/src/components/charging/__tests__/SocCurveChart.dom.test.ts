// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import SocCurveChart from '../SocCurveChart.vue'

// 5 Samples im typischen Smartcar-Takt von 4 Minuten
const POINTS = [
  { ts: 0, soc: 40 },
  { ts: 240_000, soc: 45 },
  { ts: 480_000, soc: 50 },
  { ts: 720_000, soc: 55 },
  { ts: 960_000, soc: 65 },
]

const RENDERED_WIDTH = 600
let app: App | null = null

afterEach(() => {
  app?.unmount()
  app = null
  document.body.innerHTML = ''
})

function mountChart(props: Record<string, unknown> = {}) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(SocCurveChart, { points: POINTS, ...props })
  app.mount(host)
  const svg = host.querySelector('svg') as SVGSVGElement
  svg.getBoundingClientRect = () => ({
    left: 0, top: 0, width: RENDERED_WIDTH, height: 200,
    right: RENDERED_WIDTH, bottom: 200, x: 0, y: 0, toJSON: () => ({}),
  })
  return { host, svg }
}

function pointerEvent(type: string, clientX: number, pointerType = 'mouse'): Event {
  const e = new Event(type, { bubbles: true, cancelable: true })
  Object.assign(e, { clientX, clientY: 100, pointerType, pointerId: 1 })
  return e
}

const tooltipOf = (host: HTMLElement) => host.querySelector('[data-testid="soc-curve-tooltip"]')

describe('SocCurveChart', () => {
  it('rendert eine Stufenlinie statt einer geglaetteten Kurve', () => {
    const { host } = mountChart()
    const d = host.querySelectorAll('path')[1].getAttribute('d')!
    // Treppe: je Schritt erst waagerecht, dann senkrecht - zwei L pro Punkt.
    expect(d.match(/L /g)!.length).toBe((POINTS.length - 1) * 2)
    expect(d).not.toContain('C')
  })

  it('skaliert fest auf 0-100 Prozent', () => {
    // Bei Autoskalierung saehe eine 10-Punkte-Ladung so steil aus wie eine ueber 60.
    const { host } = mountChart({ points: [{ ts: 0, soc: 0 }, { ts: 1000, soc: 100 }] })
    const d = host.querySelectorAll('path')[1].getAttribute('d')!
    expect(d).toContain('200.0')
    expect(d).toContain('0.0')
  })

  it('zeigt den Ladestand am Zeiger', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(pointerEvent('pointermove', 600))
    await nextTick()

    expect(tooltipOf(host)?.textContent).toContain('65 %')
    expect(tooltipOf(host)?.textContent).toContain('16 Min')
  })

  it('reagiert bei Touch erst nach dem Aufsetzen des Fingers', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(pointerEvent('pointermove', 300, 'touch'))
    await nextTick()
    expect(tooltipOf(host)).toBeNull()

    svg.dispatchEvent(pointerEvent('pointerdown', 300, 'touch'))
    await nextTick()
    expect(tooltipOf(host)).not.toBeNull()
  })

  it('laesst sich per Tastatur bedienen', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await nextTick()
    expect(tooltipOf(host)?.textContent).toContain('40 %')

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(tooltipOf(host)).toBeNull()
  })

  it('kommt mit einem einzelnen Messpunkt klar', () => {
    const { host } = mountChart({ points: [{ ts: 0, soc: 42 }] })
    expect(host.querySelector('[data-testid="soc-curve-chart"]')).not.toBeNull()
  })

  it('rendert ohne Punkte keine Linie', () => {
    const { host } = mountChart({ points: [] })
    expect(host.querySelectorAll('path')).toHaveLength(0)
  })
})
