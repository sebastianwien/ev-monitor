// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SpecOverview } from '../../../api/adminVehicleSpecService'

const overview = vi.fn()
const update = vi.fn()
const create = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return { ...actual, adminVehicleSpecService: { overview: () => overview(), update: (id: string, c: unknown) => update(id, c), create: (c: unknown) => create(c) } }
})

import AdminSpecSuggestionsTab from '../AdminSpecSuggestionsTab.vue'

const row = (id: string, extra: Partial<SpecOverview['models'][0]['rows'][0]>) => ({
    id, carBrand: 'TESLA', carModel: 'MODEL_3', batteryCapacityKwh: 60, netBatteryCapacityKwh: 57.5, officialRangeKm: 491,
    officialConsumptionKwhPer100km: 14.4, ratingSource: 'WLTP' as const, variantName: 'SR', trimLevel: 'Standard', availableFrom: null,
    availableTo: null, sourceUrl: null, activeCars: 0, allCars: 0, ratings: 0, workLists: [], ...extra,
})
const sample: SpecOverview = {
    carsWithoutSpec: 0, modelsWithoutRow: [],
    models: [{ carBrand: 'TESLA', carModel: 'MODEL_3', displayName: 'Model 3', inEnum: true, logCount: 1, activeCars: 1, carsWithoutSpec: 0,
        rows: [row('spec-1', {}), row('spec-2', { availableFrom: '2020-01-01', sourceUrl: 'https://x' })] }],
}
const suggestions = { _helper: 'http://localhost:8766', 'spec-1': [{ label: 'Model 3 SR (60 kWh)', availableFrom: '2023-01-01', availableTo: '2024-09-01', sourceUrl: 'https://ev-database.org/car/1' }] }
const detail = { batteryCapacityKwh: 64, netBatteryCapacityKwh: 60, officialRangeKm: 572, officialConsumptionKwhPer100km: 12.2 }

let app: App | null = null
const mount = async () => {
    const host = document.createElement('div'); document.body.appendChild(host)
    app = createApp(AdminSpecSuggestionsTab); app.mount(host)
    await new Promise(r => setTimeout(r, 0)); await nextTick(); await nextTick()
    return host
}
beforeEach(() => {
    overview.mockReset(); update.mockReset(); create.mockReset()
    create.mockImplementation(async (c: Record<string, unknown>) => ({ row: { ...row('spec-neu', {}), ...c }, warnings: [] }))
    overview.mockResolvedValue(sample)
    update.mockImplementation(async (id: string, c: Record<string, unknown>) => ({ row: { ...row(id, {}), ...c }, warnings: [] }))
    vi.stubGlobal('fetch', vi.fn(async (url: string) => url.startsWith('http://localhost:8766/spec') ? { ok: true, json: async () => detail } : { ok: true, json: async () => suggestions }))
})
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.unstubAllGlobals() })

describe('AdminSpecSuggestionsTab', () => {
    it('zeigt nur Zeilen mit Vorschlag und übernimmt Zeitraum und Quelle über das volle Update-Command', async () => {
        const host = await mount()
        expect(host.textContent).toContain('Model 3 SR')
        expect(host.textContent).toContain('bestehend')
        expect(host.textContent).not.toContain('spec-2')
        const btn = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Übernehmen'))!
        btn.click(); await new Promise(r => setTimeout(r, 0)); await nextTick()
        expect(update).toHaveBeenCalledTimes(1)
        const [id, cmd] = update.mock.calls[0] as [string, Record<string, unknown>]
        expect(id).toBe('spec-1')
        expect(cmd).toMatchObject({ model: 'MODEL_3', batteryCapacityKwh: 60, trimLevel: 'Standard', availableFrom: '2023-01-01', availableTo: '2024-09-01', sourceUrl: 'https://ev-database.org/car/1' })
        expect(host.textContent).toContain('1 übernommen')
    })

    it('Werte laden holt die Zahlen vom lokalen Helfer, markiert Abweichungen und schreibt sie beim Übernehmen mit', async () => {
        const host = await mount()
        ;[...host.querySelectorAll('button')].find(b => b.textContent?.includes('Werte laden'))!.click()
        await new Promise(r => setTimeout(r, 0)); await nextTick(); await nextTick()
        expect(host.textContent).toContain('572')
        const amber = [...host.querySelectorAll('td.text-amber-300')].map(td => td.textContent?.trim())
        expect(amber).toContain('64')
        expect(amber).toContain('572')
        ;[...host.querySelectorAll('button')].find(b => b.textContent?.includes('Übernehmen'))!.click()
        await new Promise(r => setTimeout(r, 0)); await nextTick()
        expect(update.mock.calls[0][1]).toMatchObject({ batteryCapacityKwh: 64, netBatteryCapacityKwh: 60, officialRangeKm: 572, officialConsumptionKwhPer100km: 12.2, availableFrom: '2023-01-01' })
    })

    it('legt aus einem Kandidaten eine neue Zeile an: Zahlen und Trim der Ausgangszeile, Name ohne kWh-Klammer, Zeitraum und Quelle vom Kandidaten', async () => {
        const host = await mount()
        const btn = [...host.querySelectorAll('button')].find(b => b.textContent?.includes('Neue Zeile'))!
        btn.click(); await new Promise(r => setTimeout(r, 0)); await nextTick()
        expect(update).not.toHaveBeenCalled()
        expect(create).toHaveBeenCalledTimes(1)
        expect(create.mock.calls[0][0]).toMatchObject({ model: 'MODEL_3', batteryCapacityKwh: 60, netBatteryCapacityKwh: 57.5, officialRangeKm: 491,
            trimLevel: 'Standard', variantName: 'Model 3 SR', availableFrom: '2023-01-01', availableTo: '2024-09-01', sourceUrl: 'https://ev-database.org/car/1' })
        expect(host.textContent).toContain('1 angelegt')
    })

    it('ohne Vorschlagsdatei nur ein Hinweis, kein Fehler', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }))
        const host = await mount()
        expect(host.textContent).toContain('Keine Vorschlagsdatei')
        expect(overview).not.toHaveBeenCalled()
    })
})
