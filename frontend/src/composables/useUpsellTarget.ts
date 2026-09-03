import { computed, type ComputedRef } from 'vue'
import type { Car } from '../api/carService'
import { hasFreeDataSource } from './useCarAutoSyncProvider'
import { useAuthStore } from '../stores/auth'
import { useCarStore } from '../stores/car'

export type UpsellTarget = '/supporter' | '/upgrade'

/**
 * Wohin ein "schalt die Auswertungen frei"-CTA fuehren muss.
 *
 * Die Frage dahinter ist nicht "welches Abo ist teurer", sondern **was diesem User
 * gerade fehlt**:
 *
 * - **Datenquelle vorhanden** (`hasFreeDataSource` - aktuell Tesla via Fleet
 *   Telemetry): es fehlt nur die Auswertungsebene, und die kostet 2 EUR im
 *   Supporter-Pack. Ihn auf die AutoSync-Preistabelle zu schicken verkauft ihm
 *   Datensammlung, die er laengst hat.
 * - **Keine Datenquelle** (Smartcar-Marke ohne Abo, oder Marke ohne Connector): ohne
 *   AutoSync gibt es keine Ladeerkennung und keine Trips - das Supporter-Pack haette
 *   nichts auszuwerten. Hier ist /upgrade der ehrliche Weg.
 *
 * Gemischte Garage zaehlt als Treffer: eine laufende Quelle genuegt, damit die
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
    return cars.some(hasFreeDataSource) ? '/supporter' : '/upgrade'
}

/**
 * Ob dieser User fuer **keines** seiner Autos AutoSync braucht - jedes liefert seine
 * Daten schon gratis (aktuell Tesla via Fleet Telemetry). Fuer sie ist die
 * AutoSync-Preistabelle ein Fehlverkauf; der richtige Weg ist das Supporter-Pack.
 *
 * Bewusst strenger als {@link analyticsUpsellTarget}: dort genuegt **eine** Gratis-Quelle
 * (die freigeschalteten Widgets zeigen dann sofort etwas), hier muessen es **alle** sein.
 * Eine gemischte Garage (Tesla + VW) braucht AutoSync weiterhin fuer den VW - da darf kein
 * "du brauchst kein AutoSync"-Hinweis erscheinen. Leere Garage = keine Aussage.
 */
export function hasOnlyFreeDataSourceCars(cars: Pick<Car, 'brand'>[]): boolean {
    return cars.length > 0 && cars.every(hasFreeDataSource)
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
