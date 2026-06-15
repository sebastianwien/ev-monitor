import type { Car } from '../api/carService'
import { SMARTCAR_BRANDS } from '../config/smartcarBrands'

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
 * Lookup set derived from the shared SMARTCAR_BRANDS display list. We index by
 * uppercase and additionally store an accent-stripped alias (e.g. CITROEN for
 * "Citroën") so brand strings match regardless of diacritics.
 */
const SMARTCAR_BRAND_LOOKUP = new Set<string>(
    SMARTCAR_BRANDS.flatMap((brand) => {
        const upper = brand.toUpperCase()
        const ascii = upper.normalize('NFD').replace(/[̀-ͯ]/g, '')
        return ascii === upper ? [upper] : [upper, ascii]
    }),
)

export function autoSyncProviderFor(car: Pick<Car, 'brand'>): AutoSyncProvider {
    const brand = (car.brand ?? '').toUpperCase().trim()
    if (brand === 'TESLA') return 'TESLA'
    if (SMARTCAR_BRAND_LOOKUP.has(brand)) return 'SMARTCAR'
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
