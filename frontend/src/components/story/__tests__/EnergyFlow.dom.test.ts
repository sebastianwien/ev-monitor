// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { createApp, type App } from 'vue'
import EnergyFlow from '../EnergyFlow.vue'
import type { EnergyFlow as Flow } from '../../../composables/storyEnergy'

const flow: Flow = {
  gridKwh: 500, vehicleKwh: 450, lossKwh: 50, lossPercent: 10,
  acKwh: 400, dcKwh: 100, privateKwh: 350, publicKwh: 150,
  distanceKm: 2500, coveredLogCount: 10, totalLogCount: 12, partial: true,
}
const labels = { grid: 'Netz', battery: 'Akku', road: 'Strasse', loss: 'Verlust', home: 'Zuhause', public: 'Unterwegs' }

function mount(props: Record<string, unknown>): { el: HTMLElement; app: App } {
  const el = document.createElement('div')
  document.body.appendChild(el)
  const app = createApp(EnergyFlow, props)
  app.mount(el)
  return { el, app }
}

describe('EnergyFlow', () => {
  it('zeichnet drei Baender, deren Hoehen den kWh entsprechen', () => {
    const { el, app } = mount({ flow, labels })
    const ribbons = el.querySelectorAll('path[data-role="ribbon"]')
    expect(ribbons).toHaveLength(3)
    const h = (name: string) => Number(el.querySelector(`path[data-ribbon="${name}"]`)!.getAttribute('data-height'))
    expect(h('vehicle')).toBeCloseTo(h('loss') * 9, 5)
    expect(h('road')).toBeCloseTo(h('vehicle'), 5)
    app.unmount()
  })

  it('stapelt links Zuhause und Unterwegs proportional', () => {
    const { el, app } = mount({ flow, labels })
    const home = Number(el.querySelector('rect[data-role="home"]')!.getAttribute('height'))
    const pub = Number(el.querySelector('rect[data-role="public"]')!.getAttribute('height'))
    expect(home / pub).toBeCloseTo(350 / 150, 3)
    app.unmount()
  })

  it('beschriftet Knoten mit den uebergebenen Labels und zeigt den Verlust in Prozent', () => {
    const { el, app } = mount({ flow, labels })
    expect(el.textContent).toContain('Netz')
    expect(el.textContent).toContain('Akku')
    expect(el.textContent).toContain('Verlust')
    expect(el.textContent).toContain('10')
    app.unmount()
  })

  it('rendert ohne Verlust kein Verlust-Band', () => {
    const { el, app } = mount({ flow: { ...flow, lossKwh: 0, lossPercent: 0, vehicleKwh: 500 }, labels })
    expect(el.querySelector('path[data-ribbon="loss"]')).toBeNull()
    app.unmount()
  })
})
