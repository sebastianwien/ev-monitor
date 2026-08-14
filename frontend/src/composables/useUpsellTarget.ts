import { computed, type ComputedRef } from 'vue'
import type { Car } from '../api/carService'
import { autoSyncProviderFor } from './useCarAutoSyncProvider'
import { useAuthStore } from '../stores/auth'
import { useCarStore } from '../stores/car'

export type UpsellTarget = '/supporter' | '/upgrade'

/**
 * Wohin ein "schalt die Auswertungen frei"-CTA fuehren muss.
 *
 * Die Frage dahinter ist nicht "welches Abo ist teurer", sondern **was diesem User
 * gerade fehlt**:
 *
 * - **Datenquelle vorhanden** (Tesla - Fleet Telemetry laeuft fuer ihn gratis): es fehlt
 *   nur die Auswertungsebene, und die kostet 2 EUR im Supporter-Pack. Ihn auf die
 *   AutoSync-Preistabelle zu schicken verkauft ihm Datensammlung, die er laengst hat.
 * - **Keine Datenquelle** (Smartcar-Marke ohne Abo, oder Marke ohne Connector): ohne
 *   AutoSync gibt es keine Ladeerkennung und keine Trips - das Supporter-Pack haette
 *   nichts auszuwerten. Hier ist /upgrade der ehrliche Weg.
 *
 * Gemischte Garage zaehlt als Tesla: eine laufende Quelle genuegt, damit die
 * freigeschalteten Widgets sofort etwas anzeigen.
 *
 * Gilt nur fuer Analytics-CTAs (Ladekurven, Standverluste, Energie-Split, Trip-Telemetrie).
 * CTAs, die eine Datenquelle einrichten wollen, zeigen weiterhin fest auf /upgrade.
 *
 * `tier` kommt aus dem JWT und fehlt in Tokens von vor 2026-05 - ein fehlender Wert
 * wird wie NONE behandelt, genau wie beim Analytics-Gate im Auth-Store. Dadurch sehen
 * beide dieselbe Welt: wer den gesperrten Zustand sieht, bekommt auch das passende Ziel.
 */
export function analyticsUpsellTarget(
    cars: Pick<Car, 'brand'>[],
    tier: string,
): UpsellTarget {
    if (tier !== 'NONE') return '/upgrade'
    return cars.some(car => autoSyncProviderFor(car) === 'TESLA') ? '/supporter' : '/upgrade'
}

/** Bindet die Regel an Auth- und Car-Store. */
export function useAnalyticsUpsellTarget(): ComputedRef<UpsellTarget> {
    const authStore = useAuthStore()
    const carStore = useCarStore()
    return computed(() => analyticsUpsellTarget(
        carStore.cars,
        authStore.user?.subscriptionTier ?? 'NONE',
    ))
}
