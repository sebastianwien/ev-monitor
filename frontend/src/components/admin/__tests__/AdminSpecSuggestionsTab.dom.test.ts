// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { SpecOverview, SpecRow } from '../../../api/adminVehicleSpecService'

const overview = vi.fn()
const update = vi.fn()
const create = vi.fn()
const remove = vi.fn()
vi.mock('../../../api/adminVehicleSpecService', async () => {
    const actual = await vi.importActual<typeof import('../../../api/adminVehicleSpecService')>('../../../api/adminVehicleSpecService')
    return { ...actual, adminVehicleSpecService: {
        overview: () => overview(), update: (id: string, c: unknown) => update(id, c),
        create: (c: unknown) => create(c), remove: (id: string, reason: string) => remove(id, reason),
    } }
})

import AdminSpecSuggestionsTab from '../AdminSpecSuggestionsTab.vue'

const row = (id: string, extra: Partial<SpecRow>): SpecRow => ({
    id, carBrand: 'VW', carModel: 'ID_3', batteryCapacityKwh: 62, netBatteryCapacityKwh: 58, officialRangeKm: 426,
    officialConsumptionKwhPer100km: 15.5, ratingSource: 'WLTP', variantName: 'ID.3 Pro', trimLevel: 'Pro', availableFrom: '2020-07-01',
    availableTo: '2022-12-31', sourceUrl: null, activeCars: 4, allCars: 5, ratings: 0, workLists: [], ...extra,
})
const sample: SpecOverview = {
    carsWithoutSpec: 0, modelsWithoutRow: [],
    models: [{ carBrand: 'VW', carModel: 'ID_3', displayName: 'ID.3', inEnum: true, logCount: 1, activeCars: 7, carsWithoutSpec: 0,
        rows: [row('pro', {}), row('pros', { variantName: 'ID.3 Pro S', trimLevel: 'Pro S', batteryCapacityKwh: 82, netBatteryCapacityKwh: 77, activeCars: 3, allCars: 3 })] }],
}
const entries = [
    { label: 'ID.3 Pro', battery: 58, availableFrom: '2020-11-01', availableTo: '2021-08-01', sourceUrl: 'https://ev-database.org/car/1/ID3-Pro' },
    { label: 'ID.3 Pro S', battery: 77, availableFrom: '2020-11-01', availableTo: '2021-08-01', sourceUrl: 'https://ev-database.org/car/2/ID3-Pro-S' },
]
const file = { _helper: 'http://localhost:8766', _models: { ID_3: entries } }
const detail = { batteryCapacityKwh: 62, netBatteryCapacityKwh: 58, officialRangeKm: 424, officialConsumptionKwhPer100km: 15.6 }

let app: App | null = null
const tick = async () => { await new Promise(r => setTimeout(r, 0)); await nextTick(); await nextTick() }
const mount = async () => {
    const host = document.createElement('div'); document.body.appendChild(host)
    app = createApp(AdminSpecSuggestionsTab); app.mount(host)
    await tick()
    return host
}
const btns = (host: HTMLElement, label: string) => [...host.querySelectorAll('button')]
    .filter(b => b.textContent?.trim() === label || b.getAttribute('aria-label') === label) as HTMLButtonElement[]
const radios = (host: HTMLElement) => [...host.querySelectorAll('input[type=radio]')] as HTMLInputElement[]

beforeEach(() => {
    overview.mockReset(); update.mockReset(); create.mockReset(); remove.mockReset()
    overview.mockResolvedValue(sample)
    update.mockImplementation(async (id: string, c: Record<string, unknown>) => ({ row: { ...row(id, {}), ...c }, warnings: [] }))
    create.mockImplementation(async (c: Record<string, unknown>) => ({ row: { ...row('neu', {}), ...c, activeCars: 0, allCars: 0 }, warnings: [] }))
    remove.mockResolvedValue(undefined)
    vi.stubGlobal('fetch', vi.fn(async (url: string) => url.startsWith('http://localhost:8766/spec')
        ? { ok: true, json: async () => detail } : { ok: true, json: async () => file }))
})
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.unstubAllGlobals() })

describe('AdminSpecSuggestionsTab', () => {
    it('zeigt je Modell unsere Zeilen mit Autos und alle ev-database-Einträge genau einmal, Filter wirkt nur auf den Modellnamen', async () => {
        const host = await mount()
        expect(host.querySelectorAll('section')).toHaveLength(1)
        expect(radios(host)).toHaveLength(2)
        expect(btns(host, 'Übernehmen')).toHaveLength(2)
        expect([...host.querySelectorAll('td[data-cars]')].map(td => td.textContent?.trim())).toEqual(['4', '3'])
        const input = host.querySelector('input[type=search]') as HTMLInputElement
        const type = async (v: string) => { input.value = v; input.dispatchEvent(new Event('input')); await nextTick() }
        await type('pro s')
        expect(host.querySelectorAll('section')).toHaveLength(0)
        await type('id.3')
        expect(radios(host)).toHaveLength(2)
        expect(btns(host, 'Übernehmen')).toHaveLength(2)
    })

    it('Übernehmen erst mit Ziel, schreibt dann Zeitraum und Quelle in die Zielzeile', async () => {
        const host = await mount()
        expect(btns(host, 'Übernehmen')[0].disabled).toBe(true)
        radios(host)[0].dispatchEvent(new Event('change')); await nextTick()
        btns(host, 'Übernehmen')[0].click(); await tick()
        expect(update).toHaveBeenCalledTimes(1)
        expect(update.mock.calls[0]).toEqual(['pro', expect.objectContaining({ trimLevel: 'Pro', availableFrom: '2020-11-01', availableTo: '2021-08-01', sourceUrl: 'https://ev-database.org/car/1/ID3-Pro' })])
        expect(host.textContent).toContain('übernommen')
    })

    it('Neue Zeile erst nach Werte laden, Zahlen vom Eintrag, Trim vom Ziel; Rückgängig löscht sie wieder', async () => {
        const host = await mount()
        radios(host)[0].dispatchEvent(new Event('change')); await nextTick()
        expect(btns(host, 'Neue Zeile')[0].disabled).toBe(true)
        btns(host, 'Werte laden')[0].click(); await tick(); await tick()
        expect(btns(host, 'Neue Zeile')[0].disabled).toBe(false)
        btns(host, 'Neue Zeile')[0].click(); await tick()
        expect(create.mock.calls[0][0]).toMatchObject({ model: 'ID_3', variantName: 'ID.3 Pro', trimLevel: 'Pro', batteryCapacityKwh: 62,
            officialRangeKm: 424, officialConsumptionKwhPer100km: 15.6, availableFrom: '2020-11-01', sourceUrl: 'https://ev-database.org/car/1/ID3-Pro' })
        btns(host, 'Angelegte Zeile löschen')[0].click(); await tick()
        expect(remove).toHaveBeenCalledWith('neu', 'Vorschlag zurückgenommen')
    })

    it('Rückgängig nach Übernehmen schreibt den alten Stand der Zielzeile zurück', async () => {
        const host = await mount()
        radios(host)[0].dispatchEvent(new Event('change')); await nextTick()
        btns(host, 'Übernehmen')[0].click(); await tick()
        btns(host, 'Übernehmen zurücknehmen')[0].click(); await tick()
        expect(update.mock.calls[1]).toEqual(['pro', expect.objectContaining({ availableFrom: '2020-07-01', availableTo: '2022-12-31', sourceUrl: '' })])
    })

    it('Eintrag mit Link einer unserer Zeilen ist als verknüpft markiert', async () => {
        overview.mockResolvedValue({ ...sample, models: [{ ...sample.models[0], rows: [row('pro', { sourceUrl: 'https://ev-database.org/car/1/ID3-Pro' }), row('offen', { variantName: 'ID.3 offen' })] }] })
        const host = await mount()
        expect(host.textContent).toContain('verknüpft')
    })

    it('Spaltenkopf sortiert beide Hälften, zweiter Klick dreht die Richtung', async () => {
        const host = await mount()
        const cars = () => [...host.querySelectorAll('td[data-cars]')].map(td => td.textContent?.trim())
        const head = (label: string) => [...host.querySelectorAll('th button')].find(b => b.textContent?.includes(label)) as HTMLButtonElement
        head('Autos').click(); await nextTick()
        expect(cars()).toEqual(['3', '4'])
        expect(head('Autos').closest('th')?.getAttribute('aria-sort')).toBe('ascending')
        head('Autos').click(); await nextTick()
        expect(cars()).toEqual(['4', '3'])
        expect(head('Autos').closest('th')?.getAttribute('aria-sort')).toBe('descending')
        head('Variante').click(); head('Variante').click(); await nextTick()
        const labels = [...host.querySelectorAll('tr[data-entry] td:first-child')].map(td => td.textContent?.trim())
        expect(labels).toEqual(['ID.3 Pro S', 'ID.3 Pro'])
    })

    it('ohne Vorschlagsdatei nur ein Hinweis, kein Fehler', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }))
        const host = await mount()
        expect(host.textContent).toContain('Keine Vorschlagsdatei')
        expect(overview).not.toHaveBeenCalled()
    })
})
