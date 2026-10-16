// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { CarForReassign } from '../../../api/adminVehicleSpecService'

const carsOfSpec = vi.fn()
const assignCar = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return { ...actual, adminVehicleSpecService: { carsOfSpec: (id: string) => carsOfSpec(id), assignCar: (c: string, s: string) => assignCar(c, s) } }
})

import AdminSpecRowCars from '../AdminSpecRowCars.vue'

const cars: CarForReassign[] = [
    { carId: 'car-1', carModel: 'ID_4', year: 2022, trim: 'GTX', currentNetKwh: null, currentSpecId: 'spec-1', logCount: 40,
      candidates: [
          { specId: 'spec-2', variantName: 'ID.4 GTX', trimLevel: 'GTX', netKwh: 77, diffKwh: 0, availableFrom: '2022-06-01', availableTo: '2023-05-31' },
          { specId: 'spec-3', variantName: 'ID.4 Pro', trimLevel: 'Pro', netKwh: 77, diffKwh: 0, availableFrom: '2021-01-01', availableTo: null },
      ] },
    { carId: 'car-2', carModel: 'ID_4', year: 2021, trim: null, currentNetKwh: 76, currentSpecId: 'spec-1', logCount: 3, candidates: [] },
]

let app: App | null = null
const tick = async () => { await new Promise(r => setTimeout(r, 0)); await nextTick() }
const changed = vi.fn()
async function mount() {
    const host = document.createElement('div'); document.body.appendChild(host)
    app = createApp(AdminSpecRowCars, { specId: 'spec-1', onChanged: changed }); app.mount(host)
    await tick()
    return host
}

beforeEach(() => {
    carsOfSpec.mockReset(); assignCar.mockReset(); changed.mockReset()
    carsOfSpec.mockResolvedValue(cars.map(c => ({ ...c })))
    assignCar.mockResolvedValue(undefined)
})
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

describe('AdminSpecRowCars', () => {
    it('listet die Autos der Zeile, Umhängen erst mit gewähltem Ziel, danach ist das Auto weg', async () => {
        const host = await mount()
        expect(carsOfSpec).toHaveBeenCalledWith('spec-1')
        const rows = host.querySelectorAll('tr[data-car]')
        expect(rows).toHaveLength(2)
        expect(rows[0].textContent).toContain('2022')
        const button = rows[0].querySelector('button') as HTMLButtonElement
        expect(button.disabled).toBe(true)
        const select = rows[0].querySelector('select') as HTMLSelectElement
        select.value = 'spec-2'; select.dispatchEvent(new Event('change')); await nextTick()
        expect(button.disabled).toBe(false)
        button.click(); await tick()
        expect(assignCar).toHaveBeenCalledWith('car-1', 'spec-2')
        expect(host.querySelectorAll('tr[data-car]')).toHaveLength(1)
        expect(changed).toHaveBeenCalledTimes(1)
    })

    it('Auto ohne andere Zeile des Modells: kein Umhängen möglich', async () => {
        const host = await mount()
        const row = host.querySelectorAll('tr[data-car]')[1]
        expect(row.querySelector('select')).toBeNull()
        expect(row.textContent).toContain('keine andere Zeile')
    })
})
