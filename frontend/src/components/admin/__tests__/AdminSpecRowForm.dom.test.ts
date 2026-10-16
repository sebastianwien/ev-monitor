// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { SpecRow } from '../../../api/adminVehicleSpecService'

const update = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return { ...actual, adminVehicleSpecService: { update: (id: string, c: unknown) => update(id, c) } }
})
vi.mock('../../../api/carService', () => ({
    carService: {
        getBrands: async () => [{ value: 'TESLA', label: 'Tesla' }],
        getModelsForBrand: async () => [{ value: 'MODEL_3', label: 'Model 3' }],
    },
}))

import AdminSpecRowForm from '../AdminSpecRowForm.vue'

const row: SpecRow = {
    id: 'spec-1', carBrand: 'TESLA', carModel: 'MODEL_3', batteryCapacityKwh: 79, netBatteryCapacityKwh: 75, officialRangeKm: 629,
    officialConsumptionKwhPer100km: 14, ratingSource: 'WLTP', variantName: 'Long Range AWD', trimLevel: 'Long Range',
    availableFrom: '2024-01-01', availableTo: null, sourceUrl: 'https://ev-database.org/car/1', dcMaxPowerKw: 250, dcAvgPower1080Kw: 170,
    activeCars: 0, allCars: 0, ratings: 0, workLists: [],
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; update.mockReset() })
const tick = async () => { await new Promise(r => setTimeout(r, 0)); await nextTick() }
const byLabel = (host: HTMLElement, text: string) => [...host.querySelectorAll('label')]
    .find(l => l.textContent?.includes(text))!.querySelector('input') as HTMLInputElement

describe('AdminSpecRowForm', () => {
    it('zeigt und speichert die DC-Ladeleistung', async () => {
        update.mockResolvedValue({ row, warnings: [] })
        const host = document.createElement('div'); document.body.appendChild(host)
        app = createApp({ render: () => h(AdminSpecRowForm, { row, presetModel: null, presetBrand: null }) })
        app.mount(host)
        await tick()

        expect(byLabel(host, 'DC max').value).toBe('250')
        const avg = byLabel(host, 'DC 10-80 %')
        expect(avg.value).toBe('170')
        avg.value = '160'
        avg.dispatchEvent(new Event('input'))
        host.querySelector('form')!.dispatchEvent(new Event('submit'))
        await tick()

        expect(update).toHaveBeenCalledWith('spec-1', expect.objectContaining({ dcMaxPowerKw: 250, dcAvgPower1080Kw: 160 }))
    })
})
