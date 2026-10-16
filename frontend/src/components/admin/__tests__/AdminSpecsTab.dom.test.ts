// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SpecOverview, CarForReassign } from '../../../api/adminVehicleSpecService'

const overview = vi.fn()
const create = vi.fn()
const update = vi.fn()
const changes = vi.fn()
const reassignCandidates = vi.fn()
const assignCar = vi.fn()
const carsOfSpec = vi.fn()
const listRequests = vi.fn()
const markRequestDone = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return {
        ...actual,
        adminVehicleSpecService: {
            overview: () => overview(),
            create: (c: unknown) => create(c),
            update: (id: string, c: unknown) => update(id, c),
            remove: vi.fn(),
            changes: (id: string) => changes(id),
            reassignCandidates: (m: string) => reassignCandidates(m),
            assignCar: (c: string, s: string) => assignCar(c, s),
            carsOfSpec: (id: string) => carsOfSpec(id),
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
            { id: 'spec-3', carBrand: 'TESLA', carModel: 'MODEL_3', batteryCapacityKwh: 82, netBatteryCapacityKwh: 79, officialRangeKm: 702,
              officialConsumptionKwhPer100km: 13.2, ratingSource: 'WLTP', variantName: 'Long Range RWD', trimLevel: 'Long Range',
              availableFrom: '2024-01-01', availableTo: null, sourceUrl: null, activeCars: 0, allCars: 0, ratings: 0, workLists: ['NO_SOURCE'] },
        ],
    }],
}

const candidates: CarForReassign[] = [{
    carId: 'car-1', carModel: 'MODEL_3', year: 2022, trim: 'LR', currentNetKwh: 74, currentSpecId: null, logCount: 55,
    candidates: [{ specId: 'spec-1', variantName: 'Long Range AWD', trimLevel: 'Long Range', netKwh: 75, diffKwh: 1, availableFrom: '2024-01-01', availableTo: null }],
}]

let app: App | null = null
let desktop = true
beforeEach(() => {
    desktop = true
    window.matchMedia = ((q: string) => ({ matches: desktop && q.includes('1024'), media: q, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia
    Element.prototype.scrollIntoView = vi.fn()
})
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
    overview.mockReset(); create.mockReset(); update.mockReset(); changes.mockReset(); reassignCandidates.mockReset(); assignCar.mockReset(); carsOfSpec.mockReset()
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
        expect(button(host, 'Ohne Quelle').textContent).toContain('2')
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

    it('öffnet Bearbeiten auf dem Desktop inline statt als Formular', async () => {
        overview.mockResolvedValue(sample)
        const host = await mount()

        button(host, 'Bearbeiten').click()
        await nextTick()

        expect(host.querySelector('form')).toBeNull()
        expect(host.querySelector<HTMLInputElement>('[data-field="variantName"]')?.value).toBe('Long Range AWD')
    })

    it('öffnet Bearbeiten unter lg weiter im Formular', async () => {
        desktop = false
        overview.mockResolvedValue(sample)
        const host = await mount()

        button(host, 'Bearbeiten').click()
        await nextTick()

        expect(host.querySelector('form')).not.toBeNull()
        expect(host.querySelector('[data-field="variantName"]')).toBeNull()
    })

    it('Lückenmodus: Chip öffnet die erste Zeile mit Fokus auf dem Feld, saved springt weiter, Esc beendet', async () => {
        overview.mockResolvedValue(sample)
        update.mockImplementation(async (id: string) => ({ row: sample.models[0].rows.find(r => r.id === id), warnings: [] }))
        const host = await mount()

        button(host, 'Ohne Quelle').click()
        await nextTick(); await nextTick()

        expect(host.textContent).toContain('Ohne Quelle: 0 von 2 erledigt')
        let inputs = host.querySelectorAll<HTMLInputElement>('input')
        expect(inputs.length).toBe(1)
        expect(inputs[0].dataset.field).toBe('sourceUrl')
        expect(inputs[0].closest('li')?.textContent).toContain('60/57.5 kWh')
        expect(document.activeElement).toBe(inputs[0])

        inputs[0].value = 'https://ev-database.org/car/2'
        inputs[0].dispatchEvent(new Event('input'))
        inputs[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
        await nextTick(); await nextTick(); await nextTick(); await nextTick()

        expect(update).toHaveBeenCalledWith('spec-2', expect.objectContaining({ sourceUrl: 'https://ev-database.org/car/2' }))
        expect(host.textContent).toContain('Ohne Quelle: 1 von 2 erledigt')
        inputs = host.querySelectorAll<HTMLInputElement>('input')
        expect(inputs.length).toBe(1)
        expect(inputs[0].closest('li')?.textContent).toContain('Long Range RWD')

        inputs[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
        await nextTick()
        expect(host.querySelector('input')).toBeNull()
        expect(host.textContent).not.toContain('erledigt')
    })

    it('Lückenmodus: leeres Enter überspringt die Zeile ohne Request', async () => {
        overview.mockResolvedValue(sample)
        const host = await mount()

        button(host, 'Ohne Quelle').click()
        await nextTick(); await nextTick()
        const first = host.querySelector<HTMLInputElement>('input')!
        first.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
        await nextTick(); await nextTick()

        expect(update).not.toHaveBeenCalled()
        expect(host.querySelector('input')?.closest('li')?.textContent).toContain('Long Range RWD')
    })

    it('"Neue Zeile" aus der Modellgruppe ist mit Trim und Akku der neuesten Zeile vorbelegt', async () => {
        overview.mockResolvedValue(sample)
        create.mockResolvedValue({ row: sample.models[0].rows[0], warnings: [] })
        const host = await mount()

        const section = [...host.querySelectorAll('section')].find(sec => sec.textContent?.includes('Model 3'))!
        ;[...section.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Neue Zeile')!.click()
        await nextTick(); await nextTick()

        const form = host.querySelector('form')!
        const values = [...form.querySelectorAll<HTMLInputElement>('input')].map(i => i.value)
        expect(values).toContain('Long Range')
        expect(values).toContain('79')
        expect(values).toContain('75')
        expect(values).toContain('629')
        expect(values).not.toContain('Long Range AWD')
        expect(values).not.toContain('2024-01-01')
        expect(values).not.toContain('https://ev-database.org/car/1')
    })

    it('Protokoll klappt unter der Zeile auf und scrollt nicht', async () => {
        overview.mockResolvedValue(sample)
        changes.mockResolvedValue([{ id: 'c1', adminUserId: 'a', changedAt: '2026-10-15T10:00:00', action: 'UPDATE', field: 'sourceUrl', oldValue: null, newValue: 'https://x', warning: null }])
        const host = await mount()

        button(host, 'Protokoll').click()
        await nextTick(); await nextTick(); await nextTick()

        expect(changes).toHaveBeenCalledWith('spec-1')
        const li = button(host, 'Protokoll').closest('li')!
        expect(li.textContent).toContain('UPDATE')
        expect(li.textContent).toContain('https://x')
        expect(host.querySelector('section > header h3')?.textContent ?? '').not.toContain('Protokoll:')
        expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled()
    })

    it('Autos klappen unter der Zeile auf, zweiter Klick schließt', async () => {
        overview.mockResolvedValue(sample)
        carsOfSpec.mockResolvedValue([{ carId: 'car-1', carModel: 'MODEL_3', year: 2023, trim: null, currentNetKwh: null, currentSpecId: 'spec-1', logCount: 7, candidates: [] }])
        const host = await mount()

        expect(button(host, 'Autos (0)')).toBeUndefined()
        button(host, 'Autos (12)').click()
        await nextTick(); await nextTick(); await nextTick()

        expect(carsOfSpec).toHaveBeenCalledWith('spec-1')
        expect(button(host, 'Autos (12)').closest('li')!.querySelectorAll('tr[data-car]')).toHaveLength(1)
        button(host, 'Autos (12)').click(); await new Promise(r => setTimeout(r, 50)); await nextTick()
        expect(host.querySelectorAll('tr[data-car]')).toHaveLength(0)
    })

    it('Spaltenkopf sortiert die Zeilen je Modell, zweiter Klick dreht die Richtung', async () => {
        overview.mockResolvedValue(sample)
        const host = await mount()
        const names = () => [...host.querySelectorAll('section li span.font-medium')].map(e => e.textContent?.trim())
        expect(names()).toEqual(['Long Range AWD', '60/57.5 kWh', 'Long Range RWD'])
        const head = host.querySelector('[data-sort="name"]') as HTMLButtonElement
        head.click(); await nextTick()
        expect(names()).toEqual(['60/57.5 kWh', 'Long Range AWD', 'Long Range RWD'])
        head.click(); await nextTick()
        expect(names()).toEqual(['Long Range RWD', 'Long Range AWD', '60/57.5 kWh'])
        expect(head.closest('[aria-sort]')?.getAttribute('aria-sort')).toBe('descending')
        ;(host.querySelector('[data-sort="from"]') as HTMLButtonElement).click(); await nextTick()
        expect(names()[2]).toBe('60/57.5 kWh')
    })

    it('Bearbeiten und Lückenmodus scrollen nicht', async () => {
        overview.mockResolvedValue(sample)
        const host = await mount()
        button(host, 'Bearbeiten').click()
        await nextTick(); await nextTick()
        button(host, 'Ohne Quelle').click()
        await nextTick(); await nextTick()
        expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled()
    })
})
