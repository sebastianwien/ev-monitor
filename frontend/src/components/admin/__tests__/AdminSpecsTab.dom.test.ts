// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SpecOverview, CarForReassign } from '../../../api/adminVehicleSpecService'

const overview = vi.fn()
const create = vi.fn()
const reassignCandidates = vi.fn()
const assignCar = vi.fn()
const listRequests = vi.fn()
const markRequestDone = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return {
        ...actual,
        adminVehicleSpecService: {
            overview: () => overview(),
            create: (c: unknown) => create(c),
            update: vi.fn(),
            remove: vi.fn(),
            changes: vi.fn().mockResolvedValue([]),
            reassignCandidates: (m: string) => reassignCandidates(m),
            assignCar: (c: string, s: string) => assignCar(c, s),
            listRequests: () => listRequests(),
            markRequestDone: (id: string) => markRequestDone(id),
        },
    }
})
vi.mock('../../../api/carService', () => ({
    carService: {
        getBrands: vi.fn().mockResolvedValue([{ value: 'TESLA', label: 'Tesla' }]),
        getModelsForBrand: vi.fn().mockResolvedValue([{ value: 'MODEL_3', label: 'Model 3', capacities: [] }]),
    },
}))

import AdminSpecsTab from '../AdminSpecsTab.vue'

const sample: SpecOverview = {
    carsWithoutSpec: 3,
    modelsWithoutRow: [{ carBrand: 'BYD', carModel: 'BYD_ATTO_3', displayName: 'Atto 3', activeCars: 2, logCount: 40 }],
    models: [{
        carBrand: 'TESLA', carModel: 'MODEL_3', displayName: 'Model 3', inEnum: true, logCount: 900, activeCars: 30, carsWithoutSpec: 2,
        rows: [
            { id: 'spec-1', carBrand: 'TESLA', carModel: 'MODEL_3', batteryCapacityKwh: 79, netBatteryCapacityKwh: 75, officialRangeKm: 629,
              officialConsumptionKwhPer100km: 14, ratingSource: 'WLTP', variantName: 'Long Range AWD', trimLevel: 'Long Range',
              availableFrom: '2024-01-01', availableTo: null, sourceUrl: 'https://ev-database.org/car/1', activeCars: 12, allCars: 13, ratings: 1, workLists: [] },
            { id: 'spec-2', carBrand: 'TESLA', carModel: 'MODEL_3', batteryCapacityKwh: 60, netBatteryCapacityKwh: 57.5, officialRangeKm: 491,
              officialConsumptionKwhPer100km: 14.4, ratingSource: 'WLTP', variantName: '60/57.5 kWh', trimLevel: null,
              availableFrom: null, availableTo: null, sourceUrl: null, activeCars: 0, allCars: 0, ratings: 0,
              workLists: ['NO_TRIM', 'NO_PERIOD', 'NO_SOURCE', 'USER_ROW'] },
        ],
    }],
}

const candidates: CarForReassign[] = [{
    carId: 'car-1', carModel: 'MODEL_3', year: 2022, trim: 'LR', currentNetKwh: 74, currentSpecId: null, logCount: 55,
    candidates: [{ specId: 'spec-1', variantName: 'Long Range AWD', trimLevel: 'Long Range', netKwh: 75, diffKwh: 1, availableFrom: '2024-01-01', availableTo: null }],
}]

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
    overview.mockReset(); create.mockReset(); reassignCandidates.mockReset(); assignCar.mockReset()
    listRequests.mockReset(); markRequestDone.mockReset()
})

async function mount(): Promise<HTMLElement> {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(AdminSpecsTab)
    app.mount(host)
    await nextTick(); await nextTick(); await nextTick()
    return host
}

const button = (host: HTMLElement, text: string) =>
    [...host.querySelectorAll('button')].find(b => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(text))!

describe('AdminSpecsTab', () => {
    it('zeigt Modelle mit Zeilen, Arbeitslisten-Zähler und Modelle ohne Zeile', async () => {
        overview.mockResolvedValue(sample)
        const host = await mount()
        button(host, 'Modelle ohne Zeile').click()
        await nextTick()
        const text = host.textContent ?? ''

        expect(text).toContain('Model 3')
        expect(text).toContain('Long Range AWD')
        expect(text).toContain('Atto 3')
        expect(button(host, 'Modelle ohne Zeile').textContent).toContain('1')
        expect(button(host, 'Ohne Trim').textContent).toContain('1')
        expect(button(host, 'Nutzer-Zeile').textContent).toContain('1')
        expect(button(host, 'Autos ohne Zeile').textContent).toContain('3')
    })

    it('zeigt Meldungen als Arbeitsliste, legt die Zeile vorbelegt an und erledigt mit Nutzer-Mail', async () => {
        overview.mockResolvedValue(sample)
        listRequests.mockResolvedValue([{
            id: 'req-1', freeText: 'Polestar 2 Long Range Dual Motor 2022', brand: 'POLESTAR', model: 'POLESTAR_2',
            netKwh: 78, buildYear: 2022, createdAt: '2026-10-15T16:00:00', adminNotified: true, contactAvailable: true,
        }])
        markRequestDone.mockResolvedValue(undefined)
        vi.spyOn(window, 'confirm').mockReturnValue(true)
        const host = await mount()

        expect(button(host, 'Gemeldet').textContent).toContain('1')
        expect(host.textContent).toContain('Polestar 2 Long Range Dual Motor 2022')

        const section = host.querySelector('[data-testid="reported-requests"]')!
        ;[...section.querySelectorAll('button')].find(b => b.textContent?.includes('Zeile anlegen'))!.click()
        await nextTick()
        expect(host.querySelector('form')).not.toBeNull()

        ;[...section.querySelectorAll('button')].find(b => b.textContent?.includes('Erledigt'))!.click()
        await nextTick(); await nextTick()
        expect(markRequestDone).toHaveBeenCalledWith('req-1')
        expect(listRequests).toHaveBeenCalledTimes(2)
    })

    it('filtert per Arbeitsliste auf die betroffenen Zeilen', async () => {
        overview.mockResolvedValue(sample)
        const host = await mount()

        button(host, 'Nutzer-Zeile').click()
        await nextTick()

        expect(host.textContent).toContain('60/57.5 kWh')
        expect(host.textContent).not.toContain('Long Range AWD')
    })

    it('zeigt den Feldtext des Servers, wenn das Anlegen scheitert', async () => {
        overview.mockResolvedValue(sample)
        create.mockRejectedValue({ response: { status: 400, data: { message: 'Netto darf nicht größer als Brutto sein.' } } })
        const host = await mount()

        button(host, 'Neue Zeile').click()
        await nextTick()
        const form = host.querySelector('form')!
        form.dispatchEvent(new Event('submit', { cancelable: true }))
        await nextTick(); await nextTick()

        expect(host.textContent).toContain('Netto darf nicht größer als Brutto sein.')
    })

    it('listet Autos zum Umhängen mit Diff je Kandidat und hängt per Klick um', async () => {
        overview.mockResolvedValue(sample)
        reassignCandidates.mockResolvedValue(candidates)
        assignCar.mockResolvedValue(undefined)
        const host = await mount()

        button(host, 'Autos umhängen').click()
        await nextTick(); await nextTick()

        expect(reassignCandidates).toHaveBeenCalledWith('MODEL_3')
        const text = host.textContent ?? ''
        expect(text).toContain('74')
        expect(text).toContain('55 Ladevorgänge')
        expect(text).toContain('+1')

        button(host, 'Umhängen auf Long Range AWD').click()
        await nextTick(); await nextTick()
        expect(assignCar).toHaveBeenCalledWith('car-1', 'spec-1')
    })
})
