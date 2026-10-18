// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../i18n'
import UpgradeView from '../UpgradeView.vue'
import type { Car } from '../../api/carService'

// Die beiden Pitch-Komponenten stubben - hier interessiert nur, WELCHE gewaehlt wird.
vi.mock('../../components/AutoSyncPitch.vue', () => ({
    default: { template: '<div data-testid="autosync-pitch" />' },
}))
vi.mock('../../components/XpengUpgradeNotice.vue', () => ({
    default: { template: '<div data-testid="xpeng-notice" />' },
}))

vi.mock('../../components/AutoSyncUnsupportedNotice.vue', () => ({
    default: { props: ['carName'], template: '<div data-testid="unsupported-notice">{{ carName }}</div>' },
}))

vi.mock('../../services/analytics', () => ({ analytics: { trackUpgradePageViewed: () => {} } }))
vi.mock('../../api/subscriptionService', () => ({
    subscriptionService: {
        getStatus: () => Promise.resolve({ tier: 'NONE' }),
        createPortalSession: () => Promise.resolve({ portalUrl: '' }),
        createCheckoutSession: () => Promise.resolve({ checkoutUrl: '' }),
    },
}))

const carsRef: { value: Car[] } = { value: [] }
vi.mock('../../api/carService', () => ({
    carService: { getCars: () => Promise.resolve(carsRef.value) },
}))

function buildCar(overrides: Partial<Car>): Car {
    return { id: 'c', brand: 'VW', model: 'ID.4', year: 2022, status: 'ACTIVE', ...overrides } as Car
}

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
})

const flush = async () => {
    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    await nextTick()
}

async function mountUpgrade(cars: Car[]): Promise<HTMLElement> {
    carsRef.value = cars
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(UpgradeView)
    app.use(createPinia())
    app.use(i18n)
    app.mount(host)
    await flush()
    return host
}

describe('UpgradeView - Pitch-Auswahl ohne Abo', () => {
    it('XPeng-Fahrer bekommen die XPeng-Seite, nicht den AutoSync-Pitch', async () => {
        const host = await mountUpgrade([buildCar({ id: 'x', brand: 'XPENG', isPrimary: true })])

        expect(host.querySelector('[data-testid="xpeng-notice"]')).not.toBeNull()
        expect(host.querySelector('[data-testid="autosync-pitch"]')).toBeNull()
    })

    it.each(['ZEEKR', 'LEAPMOTOR', 'LUCID'])('Fahrer einer Marke ohne AutoSync (%s) bekommen den Hinweis statt des Verkaufs', async (brand) => {
        const host = await mountUpgrade([buildCar({ id: 'u', brand, isPrimary: true })])

        expect(host.querySelector('[data-testid="unsupported-notice"]')).not.toBeNull()
        expect(host.querySelector('[data-testid="autosync-pitch"]')).toBeNull()
        expect(host.querySelector('[data-testid="xpeng-notice"]')).toBeNull()
    })

    it('der Hinweis nennt das aktive Fahrzeug beim Namen', async () => {
        const host = await mountUpgrade([
            buildCar({ id: 'vw', brand: 'VW', model: 'ID_4' }),
            buildCar({ id: 'z', brand: 'ZEEKR', model: 'ZEEKR_001', isPrimary: true }),
        ])

        expect(host.querySelector('[data-testid="unsupported-notice"]')?.textContent).toBe('Zeekr 001')
    })

    it.each(['VW', 'TESLA'])('aktiver Zeekr mit %s in der Garage: der aktive Wagen entscheidet, also der Hinweis', async (other) => {
        const host = await mountUpgrade([
            buildCar({ id: 'z', brand: 'ZEEKR', isPrimary: true }),
            buildCar({ id: 'o', brand: other }),
        ])

        expect(host.querySelector('[data-testid="unsupported-notice"]')).not.toBeNull()
        expect(host.querySelector('[data-testid="autosync-pitch"]')).toBeNull()
    })

    it('aktiver VW mit einem Zeekr in der Garage: weiter der AutoSync-Pitch', async () => {
        const host = await mountUpgrade([
            buildCar({ id: 'vw', brand: 'VW', isPrimary: true }),
            buildCar({ id: 'z', brand: 'ZEEKR' }),
        ])

        expect(host.querySelector('[data-testid="autosync-pitch"]')).not.toBeNull()
        expect(host.querySelector('[data-testid="unsupported-notice"]')).toBeNull()
    })

    it('leere Garage: weiter der AutoSync-Pitch (Onboarding)', async () => {
        const host = await mountUpgrade([])

        expect(host.querySelector('[data-testid="autosync-pitch"]')).not.toBeNull()
        expect(host.querySelector('[data-testid="unsupported-notice"]')).toBeNull()
    })

    it('Nicht-XPeng-Fahrer bekommen den normalen AutoSync-Pitch', async () => {
        const host = await mountUpgrade([buildCar({ id: 'vw', brand: 'VW', isPrimary: true })])

        expect(host.querySelector('[data-testid="autosync-pitch"]')).not.toBeNull()
        expect(host.querySelector('[data-testid="xpeng-notice"]')).toBeNull()
    })
})
