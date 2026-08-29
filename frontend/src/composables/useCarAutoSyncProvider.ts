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

/** Shared normalization so every brand-keyed Set/lookup in this file compares consistently. */
function normalizeBrand(car: Pick<Car, 'brand'>): string {
    return (car.brand ?? '').toUpperCase().trim()
}

/**
 * Lookup set derived from the shared SMARTCAR_BRANDS display list. We index by
 * uppercase and additionally store an accent-stripped alias (e.g. CITROEN for
 * "Citroën") so brand strings match regardless of diacritics. None of the brands
 * below (TESLA, XPENG) carry diacritics today, so FREE_DATA_SOURCE_BRANDS doesn't
 * need the same alias treatment - revisit if that ever changes.
 */
const SMARTCAR_BRAND_LOOKUP = new Set<string>(
    SMARTCAR_BRANDS.flatMap((brand) => {
        const upper = brand.toUpperCase()
        const ascii = upper.normalize('NFD').replace(/[̀-ͯ]/g, '')
        return ascii === upper ? [upper] : [upper, ascii]
    }),
)

export function autoSyncProviderFor(car: Pick<Car, 'brand'>): AutoSyncProvider {
    const brand = normalizeBrand(car)
    if (brand === 'TESLA') return 'TESLA'
    if (SMARTCAR_BRAND_LOOKUP.has(brand)) return 'SMARTCAR'
    return 'NONE'
}

/**
 * Brands whose data (charges + trips) already flows into ev_log/ev_trip for free,
 * without an AutoSync purchase - independent of `autoSyncProviderFor`, which models
 * the paid connect/OAuth flow (see its usages in AutoSyncCarPicker/useCarSetupTeaser)
 * and deliberately excludes XPeng since it never goes through that flow.
 * - Tesla: Fleet Telemetry
 * - XPeng: EU Data Act email round-trip (XpengConnectionService)
 *
 * XPeng is hardcoded here rather than routed through a real connector because the
 * EU Data Act round-trip is the only option today. Revisit once XPeng ships a public
 * API (announced for later this year, as of 2026-08) - at that point it likely earns
 * its own AutoSyncProvider value instead of living in this brand list.
 */
const FREE_DATA_SOURCE_BRANDS = new Set(['TESLA', 'XPENG'])

/** Whether this car's data already arrives without needing to buy AutoSync. */
export function hasFreeDataSource(car: Pick<Car, 'brand'>): boolean {
    return FREE_DATA_SOURCE_BRANDS.has(normalizeBrand(car))
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
