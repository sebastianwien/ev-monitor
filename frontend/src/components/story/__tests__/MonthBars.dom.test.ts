// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { createApp, type App } from 'vue'
import MonthBars from '../MonthBars.vue'

function mount(props: Record<string, unknown>): { el: HTMLElement; app: App } {
  const el = document.createElement('div')
  document.body.appendChild(el)
  const app = createApp(MonthBars, props)
  app.mount(el)
  return { el, app }
}

const base = { values: [20, 10, 40], labels: ['Aug', 'Sep', 'Okt'], format: (v: number) => `${v} €` }

describe('MonthBars', () => {
  it('schreibt jeden Wert und Monat als Text, der höchste Balken füllt die Höhe', () => {
    const { el, app } = mount(base)
    expect([...el.querySelectorAll('[data-role="value"]')].map(e => e.textContent)).toEqual(['20 €', '10 €', '40 €'])
    expect([...el.querySelectorAll('[data-role="label"]')].map(e => e.textContent)).toEqual(['Aug', 'Sep', 'Okt'])
    const heights = [...el.querySelectorAll<HTMLElement>('[data-role="bar"]')].map(b => b.style.height)
    expect(heights).toEqual(['50%', '25%', '100%'])
    app.unmount()
  })

  it('Referenzlinie auf ihrer Höhe, mit Beschriftung in der Legende', () => {
    const { el, app } = mount({ ...base, reference: { value: 80, label: 'Hersteller 80' } })
    const line = el.querySelector<HTMLElement>('[data-role="reference"]')!
    expect(line.style.bottom).toBe('100%')
    expect(el.textContent).toContain('Hersteller 80')
    expect(el.querySelector<HTMLElement>('[data-role="bar"]')!.style.height).toBe('25%')
    app.unmount()
  })

  it('laufender Monat: letzter Balken heller, Legende nennt ihn', () => {
    const { el, app } = mount({ ...base, partialLabel: 'Okt bis heute' })
    const bars = el.querySelectorAll('[data-role="bar"]')
    expect(bars[2].getAttribute('data-partial')).toBe('true')
    expect(bars[0].getAttribute('data-partial')).toBeNull()
    expect(el.textContent).toContain('Okt bis heute')
    app.unmount()
  })
})
