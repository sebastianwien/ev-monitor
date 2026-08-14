// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import PowerCurveChart from '../PowerCurveChart.vue'

// 4 Punkte ueber 3 Minuten - x-Positionen im ViewBox-Raum: 0, 200, 400, 600
const POINTS = [
  { ts: 0, kw: 50 },
  { ts: 60_000, kw: 150 },
  { ts: 120_000, kw: 250 },
  { ts: 180_000, kw: 100 },
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
  app = createApp(PowerCurveChart, { points: POINTS, xAxisMode: 'duration', ...props })
  app.mount(host)
  const svg = host.querySelector('svg') as SVGSVGElement
  // jsdom macht kein Layout - die tatsaechliche Renderbreite muss gestellt werden,
  // sonst ist rect.width 0 und die Zeigerposition nicht abbildbar.
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

const tooltipOf = (host: HTMLElement) => host.querySelector('[data-testid="power-curve-tooltip"]')

describe('PowerCurveChart - Scrubbing', () => {
  it('zeigt ohne Zeiger keinen Tooltip', () => {
    const { host } = mountChart()
    expect(tooltipOf(host)).toBeNull()
  })

  it('zeigt den Momentanwert des naechstgelegenen Punktes', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(pointerEvent('pointermove', 400))
    await nextTick()

    expect(tooltipOf(host)?.textContent).toContain('250')
    expect(tooltipOf(host)?.textContent).toContain('2 Min')
  })

  it('folgt dem Zeiger zum naechsten Punkt', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(pointerEvent('pointermove', 190))
    await nextTick()
    expect(tooltipOf(host)?.textContent).toContain('150')

    svg.dispatchEvent(pointerEvent('pointermove', 580))
    await nextTick()
    expect(tooltipOf(host)?.textContent).toContain('100')
  })

  it('blendet den Tooltip aus, wenn der Zeiger den Chart verlaesst', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(pointerEvent('pointermove', 400))
    await nextTick()
    expect(tooltipOf(host)).not.toBeNull()

    svg.dispatchEvent(pointerEvent('pointerleave', 400))
    await nextTick()
    expect(tooltipOf(host)).toBeNull()
  })

  it('reagiert bei Touch erst nach dem Aufsetzen des Fingers', async () => {
    const { host, svg } = mountChart()

    // Ein pointermove ohne vorheriges pointerdown kommt auf Touch-Geraeten nicht
    // vom Nutzer, sondern z.B. beim Scrollen - darf nichts anzeigen.
    svg.dispatchEvent(pointerEvent('pointermove', 400, 'touch'))
    await nextTick()
    expect(tooltipOf(host)).toBeNull()

    svg.dispatchEvent(pointerEvent('pointerdown', 400, 'touch'))
    await nextTick()
    expect(tooltipOf(host)?.textContent).toContain('250')

    svg.dispatchEvent(pointerEvent('pointerup', 400, 'touch'))
    await nextTick()
    expect(tooltipOf(host)).toBeNull()
  })

  it('laesst sich per Tastatur bedienen', async () => {
    const { host, svg } = mountChart()

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await nextTick()
    expect(tooltipOf(host)?.textContent).toContain('50')

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await nextTick()
    expect(tooltipOf(host)?.textContent).toContain('150')

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(tooltipOf(host)).toBeNull()
  })

  it('zeigt die nachgeladene Reichweite, wenn ein Verbrauch vorliegt', async () => {
    const { host, svg } = mountChart({ consumptionKwhPer100km: 20 })

    svg.dispatchEvent(pointerEvent('pointermove', 600))
    await nextTick()

    expect(tooltipOf(host)?.textContent).toMatch(/km/)
  })

  it('zeigt bei Zeitachse die Uhrzeit statt der Dauer', async () => {
    const { host, svg } = mountChart({ xAxisMode: 'time' })

    svg.dispatchEvent(pointerEvent('pointermove', 400))
    await nextTick()

    expect(tooltipOf(host)?.textContent).toMatch(/\d{1,2}:\d{2}/)
  })
})

const socAxisOf = (host: HTMLElement) => host.querySelector('[data-testid="power-curve-soc-axis"]')

describe('PowerCurveChart - SoC-Achse', () => {
  it('zeigt keine SoC-Achse ohne gemessenen SoC und ohne Log-Grenzen', () => {
    const { host } = mountChart()
    expect(socAxisOf(host)).toBeNull()
  })

  it('beschriftet die SoC-Achse aus den gemessenen Werten', () => {
    const points = POINTS.map((p, i) => ({ ...p, soc: 20 + i * 10 }))
    const { host } = mountChart({ points })

    // 5 Achsen-Stuetzstellen ueber 4 Messpunkte: die Zwischenwerte werden
    // ueber die Zeit interpoliert, Rand-Labels treffen die Messwerte exakt.
    const labels = [...socAxisOf(host)!.querySelectorAll('span')].map(s => s.textContent)
    expect(labels).toEqual(['20 %', '28 %', '35 %', '43 %', '50 %'])
  })

  it('leitet die SoC-Achse aus den Log-Grenzen ab wenn kein SoC gemessen wurde', () => {
    const { host } = mountChart({ socBeforePercent: 10, socAfterPercent: 80 })

    const labels = [...socAxisOf(host)!.querySelectorAll('span')].map(s => s.textContent)
    expect(labels).toHaveLength(5)
    expect(labels[0]).toBe('10 %')
    expect(labels[4]).toBe('80 %')
  })

  it('zeigt den SoC am Zeiger im Tooltip', async () => {
    const { host, svg } = mountChart({ socBeforePercent: 10, socAfterPercent: 80 })

    svg.dispatchEvent(pointerEvent('pointermove', 600))
    await nextTick()

    expect(tooltipOf(host)?.textContent).toContain('80 %')
  })
})
