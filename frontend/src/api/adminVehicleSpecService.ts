import api from './axios'

export type WorkList = 'NO_NAME' | 'NO_TRIM' | 'NO_PERIOD' | 'NO_SOURCE' | 'NO_NET' | 'USER_ROW' | 'ROW_WITHOUT_ENUM_MODEL'

export interface SpecRow {
    id: string
    carBrand: string
    carModel: string
    batteryCapacityKwh: number
    netBatteryCapacityKwh: number | null
    officialRangeKm: number
    officialConsumptionKwhPer100km: number
    ratingSource: 'WLTP' | 'EPA'
    variantName: string | null
    trimLevel: string | null
    availableFrom: string | null
    availableTo: string | null
    sourceUrl: string | null
    activeCars: number
    allCars: number
    ratings: number
    workLists: WorkList[]
}

export interface ModelGroup {
    carBrand: string
    carModel: string
    displayName: string
    inEnum: boolean
    logCount: number
    activeCars: number
    carsWithoutSpec: number
    rows: SpecRow[]
}

export interface ModelWithoutRow {
    carBrand: string
    carModel: string
    displayName: string
    activeCars: number
    logCount: number
}

export interface SpecOverview {
    models: ModelGroup[]
    modelsWithoutRow: ModelWithoutRow[]
    carsWithoutSpec: number
}

export interface SpecCommand {
    model: string
    batteryCapacityKwh: number | null
    netBatteryCapacityKwh: number | null
    officialRangeKm: number | null
    officialConsumptionKwhPer100km: number | null
    variantName: string
    trimLevel: string
    availableFrom: string | null
    availableTo: string | null
    sourceUrl: string
    ratingSource: 'WLTP' | 'EPA'
}

export interface SaveResult {
    row: SpecRow
    warnings: string[]
}

export interface Candidate {
    specId: string
    variantName: string | null
    trimLevel: string | null
    netKwh: number | null
    diffKwh: number | null
    availableFrom: string | null
    availableTo: string | null
}

export interface CarForReassign {
    carId: string
    carModel: string
    year: number | null
    trim: string | null
    currentNetKwh: number | null
    currentSpecId: string | null
    logCount: number
    candidates: Candidate[]
}

export interface ChangeEntry {
    id: string
    adminUserId: string
    changedAt: string
    action: 'CREATE' | 'UPDATE' | 'DELETE' | 'REASSIGN_CAR'
    field: string | null
    oldValue: string | null
    newValue: string | null
    warning: string | null
}

const BASE = '/admin/vehicle-specifications'

export const adminVehicleSpecService = {
    async overview(): Promise<SpecOverview> {
        return (await api.get<SpecOverview>(BASE)).data
    },
    async create(cmd: SpecCommand): Promise<SaveResult> {
        return (await api.post<SaveResult>(BASE, cmd)).data
    },
    async update(id: string, cmd: SpecCommand): Promise<SaveResult> {
        return (await api.put<SaveResult>(`${BASE}/${id}`, cmd)).data
    },
    async remove(id: string, reason: string): Promise<void> {
        await api.delete(`${BASE}/${id}`, { params: { reason } })
    },
    async changes(id: string): Promise<ChangeEntry[]> {
        return (await api.get<ChangeEntry[]>(`${BASE}/${id}/changes`)).data
    },
    async reassignCandidates(model: string): Promise<CarForReassign[]> {
        return (await api.get<CarForReassign[]>(`${BASE}/reassign-candidates`, { params: { model } })).data
    },
    async assignCar(carId: string, specId: string): Promise<void> {
        await api.post(`${BASE}/cars/${carId}/assign/${specId}`)
    },
}

/** Fehlertext aus einer 4xx-Antwort, sonst ein fester Satz. */
export function apiErrorText(err: unknown, fallback: string): string {
    const e = err as { response?: { data?: { message?: string } } }
    return e?.response?.data?.message ?? fallback
}
