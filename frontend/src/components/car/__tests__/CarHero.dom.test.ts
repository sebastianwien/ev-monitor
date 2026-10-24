// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createI18n } from 'vue-i18n'
import { createPinia } from 'pinia'
import de from '../../../locales/de.yaml'
import CarHero from '../CarHero.vue'
import type { Car } from '../../../api/carService'

/** Chips im Hero: Aktionen (SoH, Bewertet, Geteilt) sind Knöpfe und stehen vor den reinen Infos. */
describe('CarHero chips', () => {
    let app: App | null = null
    let host: HTMLElement | null = null

    afterEach(() => {
        app?.unmount()
        host?.remove()
        document.body.innerHTML = ''
    })

    const car = {
        id: 'c1', model: 'MODEL_3', brand: 'Tesla', year: 2019, trim: 'LR RWD',
        powerKw: 220, batteryDegradationPercent: 8, effectiveBatteryCapacityKwh: 67.7,
    } as unknown as Car

    async function mount(extra: Record<string, unknown> = {}) {
        host = document.createElement('div')
        document.body.appendChild(host)
        const i18n = createI18n({ legacy: false, locale: 'de', messages: { de }, missingWarn: false, fallbackWarn: false })
        const emitted: string[] = []
        app = createApp(CarHero, {
            car, modelLabel: 'Model 3', imageUrl: null, uploading: false, teslaStatus: null,
            showActive: false, shared: true, rated: true,
            onSoh: () => emitted.push('soh'), onRating: () => emitted.push('rating'), onShare: () => emitted.push('share'),
            ...extra,
        })
        app.use(createPinia())
        app.use(i18n)
        app.mount(host)
        await nextTick()
        return emitted
    }

    /** Mobile Zeile, damit jeder Chip genau einmal vorkommt. */
    const mobileRow = () => document.querySelector('[data-testid="car-hero-chips-mobile"]') as HTMLElement

    it('renders SoH as its own chip, separate from capacity', async () => {
        await mount()
        const soh = mobileRow().querySelector('button[data-chip="soh"]')!
        expect(soh.textContent).toContain('92')
        expect(soh.textContent).not.toContain('kWh')
    })

    it('makes rated and shared clickable and emits their targets', async () => {
        const emitted = await mount()
        ;(mobileRow().querySelector('button[data-chip="soh"]') as HTMLButtonElement).click()
        ;(mobileRow().querySelector('button[data-chip="rated"]') as HTMLButtonElement).click()
        ;(mobileRow().querySelector('button[data-chip="shared"]') as HTMLButtonElement).click()
        expect(emitted).toEqual(['soh', 'rating', 'share'])
    })

    it('puts action chips before info chips', async () => {
        await mount()
        const order = [...mobileRow().querySelectorAll('[data-chip]')].map(el => el.getAttribute('data-chip'))
        expect(order).toEqual(['soh', 'rated', 'shared', 'capacity', 'power'])
    })

    it('shows odometer and overall consumption as info chips when known', async () => {
        await mount({ odometerKm: 66113, avgConsumptionKwhPer100km: 17.34 })
        const row = mobileRow()
        expect(row.querySelector('[data-chip="odometer"]')!.textContent).toContain('66.113 km')
        expect(row.querySelector('[data-chip="consumption"]')!.textContent).toContain('17,3')
        const order = [...row.querySelectorAll('[data-chip]')].map(el => el.getAttribute('data-chip'))
        expect(order).toEqual(['soh', 'rated', 'shared', 'odometer', 'consumption', 'capacity', 'power'])
    })

    it('leaves the chips out without a value', async () => {
        await mount({ odometerKm: null, avgConsumptionKwhPer100km: null })
        expect(mobileRow().querySelector('[data-chip="odometer"]')).toBeNull()
        expect(mobileRow().querySelector('[data-chip="consumption"]')).toBeNull()
    })
})
