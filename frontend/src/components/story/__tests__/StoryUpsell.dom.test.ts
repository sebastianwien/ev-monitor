// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, type App, type Component } from 'vue'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'
import SheetFlow from '../sheets/SheetFlow.vue'
import SheetCost from '../sheets/SheetCost.vue'
import { useAuthStore } from '../../../stores/auth'
import { useCarStore } from '../../../stores/car'
import type { EnergyUse } from '../../../composables/storyEnergy'
import type { StoryTile } from '../../../composables/storyModel'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })
const $ = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''

/** Nutzer und Garage für das Gate: Tier, Registrierung (Probemonat) und Marke. */
function setup(tier: string, brand: string, registeredAt = '2025-01-01T00:00:00Z'): Pinia {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = { subscriptionTier: tier, registeredAt } as any
  useCarStore().cars = [{ id: 'c1', brand } as any]
  return pinia
}
function mount(c: Component, props: Record<string, unknown>, pinia: Pinia) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(c, props)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div />' } }] })
  app.use(pinia).use(i18n).use(router)
  app.mount(host)
  return host
}

const lockedUse = (): EnergyUse => ({
  driveKwh: 33.5, standbyKwh: null, chargeLossKwh: 3, trips: 46, charges: 2, estimatedCharges: 0,
  lossEur: null, standbyEur: null, chargeEur: 0.87, homePct: 0,
  shares: [{ key: 'drive', pct: 92 }, { key: 'standby', pct: null }, { key: 'charge', pct: 8 }],
})

describe('Strom-Sheet: Stand gesperrt', () => {
  it('Tesla ohne Abo: Knopf zum Supporter-Pack mit Herkunft', () => {
    mount(SheetFlow, { use: lockedUse() }, setup('NONE', 'TESLA'))
    const row = $('story-upsell-flow')!
    expect(text(row)).toContain('Sieh, was dein Auto parkend verliert')
    expect(row.getAttribute('href')).toBe('/supporter?from=story_flow')
    expect(text(row)).toMatch(/Supporter-Pack · 1,99\s?€ im Monat/)
  })

  it('andere Marke ohne Abo: Knopf zu AutoSync', () => {
    mount(SheetFlow, { use: lockedUse() }, setup('NONE', 'VW'))
    const link = $('story-upsell-flow')!
    expect(link.getAttribute('href')).toBe('/upgrade?from=story_flow')
    expect(text(link)).toContain('AutoSync ·')
  })

  it('im Probemonat: Stand sichtbar und "Gratis bis" statt Knopf', () => {
    const pinia = setup('NONE', 'TESLA', new Date().toISOString())
    mount(SheetFlow, { use: { ...lockedUse(), standbyKwh: 12, standbyEur: 3.5 } }, pinia)
    expect($('story-upsell-flow')).toBeNull()
    expect(text($('story-trial-flow'))).toMatch(/^Inklusive bis /)
  })
})

const costTile = (): StoryTile => ({ id: 'cost', value: 42, delta: null, deltaPercent: null, sentiment: 'neutral', spark: [40, 42], sparkVariant: 'bars' } as StoryTile)
const costProps = (savings: { locked: boolean; trialEndsAt: string | null; eur: number | null }) => ({
  tile: costTile(), stats: null, monthLabels: ['Sep', 'Okt'], monthName: 'Oktober', runningMonth: true,
  hasFixedCosts: false, loadMonthlyCosts: async () => null, savings,
})

describe('Kosten-Sheet: Heimlade-Ersparnis', () => {
  it('gesperrt: Zeile mit Knopf, Ziel nach Marke', () => {
    mount(SheetCost, costProps({ locked: true, trialEndsAt: null, eur: null }), setup('NONE', 'VW'))
    const row = $('story-upsell-cost')!
    expect(text(row)).toContain('Was du zu Hause gegenüber öffentlichem Laden sparst')
    expect(row.getAttribute('href')).toBe('/upgrade?from=story_cost')
  })

  it('im Probemonat: "Gratis bis", kein Knopf', () => {
    mount(SheetCost, costProps({ locked: false, trialEndsAt: '2026-11-15', eur: 84 }), setup('NONE', 'TESLA'))
    expect($('story-upsell-cost')).toBeNull()
    expect(text($('story-trial-cost'))).toMatch(/^Inklusive bis /)
    expect(text($('story-savings-cost'))).toMatch(/Heimladen-Ersparnis.*84\s?€/)
  })

  it('entsperrt ohne Probemonat: nichts davon', () => {
    mount(SheetCost, costProps({ locked: false, trialEndsAt: null, eur: null }), setup('SUPPORTER', 'TESLA'))
    expect($('story-upsell-cost')).toBeNull()
    expect($('story-trial-cost')).toBeNull()
  })
})
