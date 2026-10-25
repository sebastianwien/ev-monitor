// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'
import FlowBar from '../FlowBar.vue'
import type { EnergyFlow } from '../../../composables/storyEnergy'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const flow = (over: Partial<EnergyFlow> = {}): EnergyFlow => ({
  gridKwh: 500, vehicleKwh: 450, lossKwh: 50, lossPercent: 10,
  acKwh: 400, dcKwh: 100, privateKwh: 350, publicKwh: 100,
  distanceKm: 2500, coveredLogCount: 10, totalLogCount: 12, partial: true, ...over,
})

function mount(f: EnergyFlow) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(FlowBar, { flow: f })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  return host
}
const text = (el: Element | null) => [...el!.querySelectorAll('[data-role="label"]')].map(x => x.textContent!.replace(/\s+/g, ' ').trim()).join(' ')

describe('FlowBar', () => {
  it('ein Balken Zuhause, Unterwegs, Verlust als Anteile des Netzstroms, jede Fläche mit Zahl', () => {
    const host = mount(flow())
    const segs = host.querySelectorAll('[data-role="segment"]')
    expect([...segs].map(s => (s as HTMLElement).style.width)).toEqual(['70%', '20%', '10%'])
    expect(text(host)).toBe('Zuhause 70 % Unterwegs 20 % Verlust 10 %')
  })

  it('ohne Ortsaufteilung ein Segment "Im Akku" plus Verlust', () => {
    const host = mount(flow({ privateKwh: 0, publicKwh: 0 }))
    const segs = host.querySelectorAll('[data-role="segment"]')
    expect([...segs].map(s => (s as HTMLElement).style.width)).toEqual(['90%', '10%'])
    expect(text(host)).toBe('Im Akku 90 % Verlust 10 %')
  })

  it('nur unterwegs geladen: kein Stück "Zuhause 0 %"', () => {
    const host = mount(flow({ privateKwh: 0, publicKwh: 50, lossPercent: 5 }))
    expect(text(host)).toBe('Unterwegs 95 % Verlust 5 %')
  })
})
