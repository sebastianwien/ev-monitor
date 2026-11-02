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

    /** Desktop-Zeile im Foto: Aktionen vor Infos. */
    const desktopRow = () => document.querySelector('[data-testid="car-hero-chips"]') as HTMLElement
    /** Mobil: Kennzahlen-Leiste und Technik-Zeile unter dem Foto. */
    const stats = () => document.querySelector('[data-testid="car-hero-stats-mobile"]') as HTMLElement
    const specs = () => document.querySelector('[data-testid="car-hero-specs-mobile"]') as HTMLElement

    it('renders SoH as its own chip, separate from capacity', async () => {
        await mount()
        const soh = desktopRow().querySelector('button[data-chip="soh"]')!
        expect(soh.textContent).toContain('92')
        expect(soh.textContent).not.toContain('kWh')
    })

    it('makes rated and shared clickable and emits their targets', async () => {
        const emitted = await mount()
        ;(desktopRow().querySelector('button[data-chip="soh"]') as HTMLButtonElement).click()
        ;(desktopRow().querySelector('button[data-chip="rated"]') as HTMLButtonElement).click()
        ;(desktopRow().querySelector('button[data-chip="shared"]') as HTMLButtonElement).click()
        expect(emitted).toEqual(['soh', 'rating', 'share'])
    })

    it('puts action chips before info chips', async () => {
        await mount()
        const order = [...desktopRow().querySelectorAll('[data-chip]')].map(el => el.getAttribute('data-chip'))
        expect(order).toEqual(['soh', 'rated', 'shared', 'capacity', 'power'])
    })

    it('mobile: no swipeable chip row any more', async () => {
        await mount()
        expect(document.querySelector('[data-testid="car-hero-chips-mobile"]')).toBeNull()
    })

    it('mobile: odometer, consumption and SoH as key figures, SoH opens the sheet', async () => {
        const emitted = await mount({ odometerKm: 66113, avgConsumptionKwhPer100km: 17.34 })
        const order = [...stats().querySelectorAll('[data-stat]')].map(el => el.getAttribute('data-stat'))
        expect(order).toEqual(['odometer', 'consumption', 'soh'])
        expect(stats().querySelector('[data-stat="odometer"]')!.textContent).toContain('66.113')
        expect(stats().querySelector('[data-stat="odometer"]')!.textContent).toContain('Kilometerstand')
        expect(stats().querySelector('[data-stat="consumption"]')!.textContent).toContain('17,3')
        expect(stats().querySelector('[data-stat="soh"]')!.textContent).toContain('92')
        ;(stats().querySelector('button[data-stat="soh"]') as HTMLButtonElement).click()
        expect(emitted).toEqual(['soh'])
    })

    it('mobile: leaves key figures out without a value, SoH stays', async () => {
        await mount({ odometerKm: null, avgConsumptionKwhPer100km: null })
        const order = [...stats().querySelectorAll('[data-stat]')].map(el => el.getAttribute('data-stat'))
        expect(order).toEqual(['soh'])
    })

    it('mobile: technical data as one quiet line, rated and shared live in their own cards', async () => {
        await mount()
        expect(specs().textContent).toContain('67,7 kWh')
        expect(specs().textContent).toContain('220 kW')
        expect(stats().querySelector('[data-chip="rated"], [data-chip="shared"]')).toBeNull()
    })
})
