// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../../i18n'
import SheetPeer from '../SheetPeer.vue'
import type { ModelPeers } from '../../../../composables/storyInsights'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

const peers = (cars: number): ModelPeers => ({
  cars, deltaPercent: -7, reason: 'price',
  theirs: { costPerKwh: 0.2641, consumption: 20.27, costPer100km: 5.35 },
  mine: { costPerKwh: 0.3062, consumption: 18.77, costPer100km: 5.75 },
  variants: [
    { name: 'Premium RWD Highland', costPer100km: 4.3, cars: 14, isUser: false },
    { name: 'LR RWD (2019-2021)', costPer100km: 5.1, cars: 1, isUser: true },
    { name: 'Performance', costPer100km: 6.2, cars: 9, isUser: false },
  ],
})

async function mount(modelPeers: ModelPeers | null) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(SheetPeer, {
    tile: { id: 'peer', value: 18.8, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [1, 2], sparkVariant: 'range' },
    stats: null, modelPeers, modelName: 'Model 3',
  })
  app.use(createPinia()).use(i18n)
  app.mount(host)
  await nextTick()
  return host
}

describe('SheetPeer', () => {
  it('du gegen alle Fahrer des Modells: Ladepreis, Verbrauch und Euro je 100 km', async () => {
    const host = await mount(peers(62))
    expect(text(host)).toContain('Du gegen alle Model 3-Fahrer')
    expect(text(host)).toContain('62 Autos aller Varianten')
    const rows = [...host.querySelectorAll('[data-testid="story-peer-model"] tbody tr')].map(tr => [...tr.children].map(text).join(' '))
    expect(rows).toHaveLength(3)
    expect(rows[0]).toMatch(/^Ladepreis 30,6\sct\/kWh 26,4\sct\/kWh$/)
  })

  it('Varianten nur mit mindestens 5 Autos', async () => {
    const host = await mount(peers(62))
    const names = [...host.querySelectorAll('[data-testid="story-variants"] li')].map(li => text(li))
    expect(names).toHaveLength(2)
    expect(names.join(' ')).not.toContain('LR RWD')
  })

  it('unter 5 Autos aller Varianten kein Modellvergleich', async () => {
    const host = await mount(peers(4))
    expect(host.querySelector('[data-testid="story-peer-model"]')).toBeNull()
  })
})
