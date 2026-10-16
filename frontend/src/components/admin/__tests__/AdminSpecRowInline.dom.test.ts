// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { SpecCommand, SpecRow } from '../../../api/adminVehicleSpecService'

const update = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return { ...actual, adminVehicleSpecService: { update: (id: string, c: unknown) => update(id, c) } }
})

import AdminSpecRowInline from '../AdminSpecRowInline.vue'

const row: SpecRow = {
    id: 'spec-1', carBrand: 'TESLA', carModel: 'MODEL_3', batteryCapacityKwh: 79, netBatteryCapacityKwh: 75, officialRangeKm: 629,
    officialConsumptionKwhPer100km: 14, ratingSource: 'WLTP', variantName: 'Long Range AWD', trimLevel: 'Long Range',
    availableFrom: '2024-01-01', availableTo: null, sourceUrl: null, dcMaxPowerKw: 250, dcAvgPower1080Kw: 170, activeCars: 12, allCars: 13, ratings: 1, workLists: ['NO_SOURCE'],
}

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; update.mockReset() })

type Props = { editing?: boolean; focusField?: keyof SpecCommand | null; canEdit?: boolean }
function mount(props: Props = {}, listeners: Record<string, (...args: unknown[]) => void> = {}): HTMLElement {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp({ render: () => h(AdminSpecRowInline, { row, editing: false, focusField: null, canEdit: true, changes: null, ...props, ...listeners }) })
    app.mount(host)
    return host
}
const input = (host: HTMLElement, name: string) => host.querySelector<HTMLInputElement>(`[data-field="${name}"]`)!
const key = (el: Element, k: string) => el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }))

describe('AdminSpecRowInline', () => {
    it('zeigt die Zeile ohne Inputs an', () => {
        const host = mount()
        expect(host.textContent).toContain('Long Range AWD')
        expect(host.textContent).toContain('79 / 75 kWh')
        expect(host.textContent).toContain('2024-')
        expect(host.querySelector('input')).toBeNull()
    })

    it('zeigt im Bearbeiten alle Felder mit den Werten der Zeile', async () => {
        const host = mount({ editing: true })
        await nextTick()
        expect(input(host, 'variantName').value).toBe('Long Range AWD')
        expect(input(host, 'trimLevel').value).toBe('Long Range')
        expect(input(host, 'netBatteryCapacityKwh').value).toBe('75')
        expect(input(host, 'availableFrom').value).toBe('2024-01-01')
        expect(input(host, 'sourceUrl').value).toBe('')
    })

    it('speichert mit Enter das volle Command und emittiert saved', async () => {
        const saved = vi.fn()
        update.mockResolvedValue({ row, warnings: [] })
        const host = mount({ editing: true }, { onSaved: saved })
        await nextTick()
        const src = input(host, 'sourceUrl')
        src.value = 'https://ev-database.org/car/1'
        src.dispatchEvent(new Event('input'))
        key(src, 'Enter')
        await nextTick(); await nextTick()

        expect(update).toHaveBeenCalledWith('spec-1', expect.objectContaining({
            model: 'MODEL_3', batteryCapacityKwh: 79, netBatteryCapacityKwh: 75, officialRangeKm: 629, officialConsumptionKwhPer100km: 14,
            variantName: 'Long Range AWD', trimLevel: 'Long Range', availableFrom: '2024-01-01', availableTo: null,
            sourceUrl: 'https://ev-database.org/car/1', ratingSource: 'WLTP',
        }))
        expect(saved).toHaveBeenCalledWith({ row, warnings: [] })
    })

    it('zeigt die DC-Ladeleistung unter dem Normwert', () => {
        const host = mount()
        expect(host.textContent).toContain('DC 250 / 170 kW')
    })

    it('bearbeitet und speichert die DC-Ladeleistung mit dem vollen Command', async () => {
        update.mockResolvedValue({ row, warnings: [] })
        const host = mount({ editing: true })
        await nextTick()
        expect(input(host, 'dcMaxPowerKw').value).toBe('250')
        const avg = input(host, 'dcAvgPower1080Kw')
        avg.value = '175'
        avg.dispatchEvent(new Event('input'))
        key(avg, 'Enter')
        await nextTick(); await nextTick()
        expect(update).toHaveBeenCalledWith('spec-1', expect.objectContaining({ dcMaxPowerKw: 250, dcAvgPower1080Kw: 175, sourceUrl: '' }))
    })

    it('Lückenmodus auf einem anderen Feld behält die DC-Ladeleistung', async () => {
        update.mockResolvedValue({ row, warnings: [] })
        const host = mount({ editing: true, focusField: 'sourceUrl' })
        await nextTick()
        const src = input(host, 'sourceUrl')
        src.value = 'https://ev-database.org/car/1'
        src.dispatchEvent(new Event('input'))
        key(src, 'Enter')
        await nextTick(); await nextTick()
        expect(update).toHaveBeenCalledWith('spec-1', expect.objectContaining({ dcMaxPowerKw: 250, dcAvgPower1080Kw: 170 }))
    })

    it('verwirft mit Esc und emittiert cancel', async () => {
        const cancel = vi.fn()
        const host = mount({ editing: true }, { onCancel: cancel })
        await nextTick()
        key(input(host, 'trimLevel'), 'Escape')
        expect(cancel).toHaveBeenCalled()
        expect(update).not.toHaveBeenCalled()
    })

    it('zeigt den Serverfehler in der Zeile und bleibt offen', async () => {
        const saved = vi.fn()
        update.mockRejectedValue({ response: { status: 400, data: { message: 'Quelle muss https sein.' } } })
        const host = mount({ editing: true }, { onSaved: saved })
        await nextTick()
        key(input(host, 'sourceUrl'), 'Enter')
        await nextTick(); await nextTick()
        expect(host.textContent).toContain('Quelle muss https sein.')
        expect(saved).not.toHaveBeenCalled()
        expect(host.querySelector('input')).not.toBeNull()
    })

    it('zeigt mit focusField nur dieses Feld als Input und fokussiert es', async () => {
        const host = mount({ editing: true, focusField: 'sourceUrl' })
        await nextTick(); await nextTick()
        expect(host.querySelectorAll('input').length).toBe(1)
        expect(document.activeElement).toBe(input(host, 'sourceUrl'))
    })

    it('überspringt im Lückenmodus ein leeres Feld bei Enter ohne Request', async () => {
        const next = vi.fn()
        const host = mount({ editing: true, focusField: 'sourceUrl' }, { onNext: next })
        await nextTick()
        key(input(host, 'sourceUrl'), 'Enter')
        await nextTick()
        expect(update).not.toHaveBeenCalled()
        expect(next).toHaveBeenCalled()
    })
})
