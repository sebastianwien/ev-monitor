import type { Car } from '../api/carService'

/**
 * Single source of truth for which AutoSync provider serves a given car.
 *
 * Decision tree (per project lead, 2026-05-05):
 * - Tesla -> TESLA (via Fleet Telemetry)
 * - Any Smartcar-supported brand (incl. VW Group) -> SMARTCAR (OAuth + webhook)
 * - Anything else -> NONE (Manual Import / Public API only)
 *
 * VW Group is intentionally NOT a separate provider in the user-facing flow even
 * though connectors-service has a dedicated VW MQTT integration - we route VW
 * cars through Smartcar to keep the UX choice simple.
 */
export type AutoSyncProvider = 'TESLA' | 'SMARTCAR' | 'NONE'

/**
 * Brands Smartcar can serve (EU, 2026). Mirrors the list rendered in
 * SmartcarIntegration.vue. Tesla is excluded here because it goes Telemetry.
 */
const SMARTCAR_BRANDS = new Set<string>([
    'BMW', 'MINI', 'VW', 'MERCEDES', 'AUDI', 'PORSCHE', 'SKODA', 'SEAT', 'CUPRA', 'OPEL',
    'HYUNDAI', 'KIA', 'VOLVO', 'POLESTAR', 'RENAULT', 'DACIA', 'NISSAN', 'FORD',
    'FIAT', 'ALFA ROMEO', 'PEUGEOT', 'CITROEN', 'CITROËN', 'MAZDA', 'MG', 'BYD',
    'JAGUAR', 'LAND ROVER',
])

export function autoSyncProviderFor(car: Pick<Car, 'brand'>): AutoSyncProvider {
    const brand = (car.brand ?? '').toUpperCase().trim()
    if (brand === 'TESLA') return 'TESLA'
    if (SMARTCAR_BRANDS.has(brand)) return 'SMARTCAR'
    return 'NONE'
}

export function providerLabel(provider: AutoSyncProvider): string {
    switch (provider) {
        case 'TESLA':
            return 'Tesla Telemetry'
        case 'SMARTCAR':
            return 'Smartcar'
        case 'NONE':
            return 'Nicht verfügbar'
    }
}
