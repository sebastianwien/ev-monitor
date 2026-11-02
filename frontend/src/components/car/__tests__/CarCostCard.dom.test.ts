// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, ref, computed, type App } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { i18n } from '../../../i18n'

// Daten wie aus useYearCosts: zwei Monate mit Strom und Versicherung.
vi.mock('../../../composables/useYearCosts', () => {
    const point = (month: number, energyEur: number, insurance: number) =>
        ({ year: 2026, month, energyEur, fixedEur: insurance, fixedByCategory: { INSURANCE: insurance } })
    const series = [point(1, 40, 900), point(2, 45, 0)]
    return {
        ENERGY: 'ENERGY',
        useYearCosts: () => ({
            ranked: ref(['INSURANCE']), thisYear: 2026, year: ref(2026), dataYear: ref(2026), minYear: computed(() => 2026),
            points: ref([]), distance: ref(null), items: ref([]), loading: ref(false), error: ref(false), load: vi.fn(),
            spread: ref(false), hidden: ref(new Set()), setShown: vi.fn(),
            series: computed(() => series), allSeries: computed(() => series), proRataSeries: computed(() => series),
        }),
    }
})

import CarCostCard from '../CarCostCard.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

async function mount() {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)', component: { template: '<div />' } }] })
    await router.push('/cars')
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(CarCostCard, { carId: 'c1', registrationDate: null })
    app.use(i18n).use(router).use(createPinia())
    app.mount(host)
    await nextTick()
    return host
}

/** Die Monatsbalken gehoeren auf die Fahrzeugseite: aufklappbar, ohne Umweg uebers Dashboard. */
describe('CarCostCard Monatsverlauf', () => {
    it('verlinkt nicht mehr aufs Dashboard', async () => {
        const host = await mount()
        expect(host.querySelector('[data-testid="car-cost-history-link"]')).toBeNull()
        expect(host.querySelector('a[href="/dashboard"]')).toBeNull()
    })

    it('zeigt die Monate erst nach dem Aufklappen', async () => {
        const host = await mount()
        const toggle = host.querySelector('[data-testid="car-cost-months-toggle"]') as HTMLButtonElement
        expect(toggle.getAttribute('aria-expanded')).toBe('false')
        // Collapse haelt den Inhalt im DOM, zugeklappt ist er inert und unsichtbar
        const months = () => host.querySelector('#car-cost-months')!
        expect(months().closest('[inert]:not([inert="false"])')).not.toBeNull()

        toggle.click()
        await nextTick()
        expect(toggle.getAttribute('aria-expanded')).toBe('true')
        expect(months().closest('[inert]:not([inert="false"])')).toBeNull()
        expect(months().querySelectorAll('[data-testid="cost-history-month"]')).toHaveLength(2)
    })
})
