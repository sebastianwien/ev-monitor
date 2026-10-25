// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { createApp, type App } from 'vue'
import Spark from '../Spark.vue'

function mount(props: Record<string, unknown>): { el: HTMLElement; app: App } {
  const el = document.createElement('div')
  document.body.appendChild(el)
  const app = createApp(Spark, props)
  app.mount(el)
  return { el, app }
}

describe('Spark', () => {
  it('line: zeichnet einen Pfad über die volle Breite und markiert den letzten Punkt', () => {
    const { el, app } = mount({ values: [10, 20, 15], variant: 'line' })
    const path = el.querySelector('path[data-role="line"]')!
    expect(path).not.toBeNull()
    const d = path.getAttribute('d')!
    expect(d.startsWith('M0')).toBe(true)
    expect(d).toContain('200')
    expect(el.querySelector('circle')).not.toBeNull()
    app.unmount()
  })

  it('line: Referenzlinie wird gezeichnet, wenn gesetzt', () => {
    const { el, app } = mount({ values: [10, 20], variant: 'line', reference: 15 })
    expect(el.querySelector('line[data-role="reference"]')).not.toBeNull()
    app.unmount()
  })

  it('bars: ein Rechteck je Wert, höchster Wert füllt die Höhe', () => {
    const { el, app } = mount({ values: [5, 10], variant: 'bars' })
    const rects = el.querySelectorAll('rect[data-role="bar"]')
    expect(rects).toHaveLength(2)
    expect(Number(rects[1].getAttribute('height'))).toBeGreaterThan(Number(rects[0].getAttribute('height')))
    app.unmount()
  })

  it('leere Werte rendern kein SVG', () => {
    const { el, app } = mount({ values: [], variant: 'line' })
    expect(el.querySelector('svg')).toBeNull()
    app.unmount()
  })
})

describe('Spark mit Endwert', () => {
  it('schreibt den letzten Wert rechts neben die Linie, auf Höhe des Endpunkts', () => {
    const { el, app } = mount({ values: [10, 20, 40], variant: 'line', endLabel: '40 €' })
    const label = el.querySelector('[data-role="end-label"]') as HTMLElement
    expect(label.textContent).toBe('40 €')
    // Höchster Wert: Endpunkt oben (PAD von 44 = 9 %)
    expect(label.style.top).toBe('9.1%')
    app.unmount()
  })

  it('ohne Endwert kein Platz dafür', () => {
    const { el, app } = mount({ values: [10, 20, 40], variant: 'line' })
    expect(el.querySelector('[data-role="end-label"]')).toBeNull()
    app.unmount()
  })
})
