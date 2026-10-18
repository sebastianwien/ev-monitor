import { computed, type ComputedRef, type Ref } from 'vue'
import type { Car } from '../api/carService'
import { isVwEudaBrand } from '../api/vwEudaSyncService'
import { autoSyncProviderFor, hasOnlyUnsupportedBrands, isAutoSyncUnsupported } from './useCarAutoSyncProvider'

const brandIs = (car: Car | null | undefined, brand: string) => car?.brand?.toLowerCase() === brand

/** XPeng laeuft nicht ueber Smartcar, sondern ueber den EU-Data-Act-Weg - eigene Einrichtung. */
export const isXpengCar = (car: Car | null | undefined): boolean => brandIs(car, 'xpeng')

/** Tesla laeuft ueber die eigene Fleet-API (OAuth + Virtual Key) - eigene Einrichtung. */
export const isTeslaCar = (car: Car | null | undefined): boolean => brandIs(car, 'tesla')

/**
 * Sichtbarkeits-Regeln der /imports-Sektionen. Alles haengt am **aktiven Auto**
 * (`isPrimary`, genau eines pro User) - dieselbe Auswahl, die der User im
 * Dashboard trifft.
 *
 * - **Tesla aktiv:** Fleet-Telemetry ist gratis, ein AutoSync-Upsell waere
 *   sinnlos. Die AutoSync-Sektion entfaellt komplett (samt Live-Promo), die
 *   Tesla-Sektion steht oben.
 * - **XPeng aktiv:** AutoSync wird angeboten - aber der XPeng-Weg (EU Data Act,
 *   wir fragen die Excel an, wir verarbeiten die Antwort). Kein Live: Live ist
 *   serverseitig Tesla-only. Kein Smartcar-Kauf-Pitch, solange der User kein
 *   Smartcar-Fahrzeug besitzt - das Abo wuerde seine XPeng-Daten nicht anfassen.
 * - **Sonst:** unveraenderter Smartcar-Pitch inkl. Kauf-Teaser.
 */
export function useImportGating(cars: Ref<Car[]>): {
    activeCarIsTesla: ComputedRef<boolean>
    activeCarIsXpeng: ComputedRef<boolean>
    activeCarIsPolestar: ComputedRef<boolean>
    /** Aktives Auto ist eine VW-Group-Marke: der EU-Data-Act-Tab startet dann aufgeklappt. */
    activeCarIsVwEudaBrand: ComputedRef<boolean>
    showTeslaSection: ComputedRef<boolean>
    showAutoSyncSection: ComputedRef<boolean>
    showSmartcarPitch: ComputedRef<boolean>
    showXpengAutoSync: ComputedRef<boolean>
    allowLivePromo: ComputedRef<boolean>
    smartcarCars: ComputedRef<Car[]>
    /** Aktives Auto hat keinen AutoSync-Weg (z. B. Zeekr, Leapmotor): AutoSync-Werbung wäre ein Fehlverkauf. */
    activeCarIsAutoSyncUnsupported: ComputedRef<boolean>
    /** Das aktive Auto (isPrimary, sonst das erste), null bei leerer Garage. */
    activeCar: ComputedRef<Car | null>
    /** Garage nur aus Marken ohne AutoSync-Weg - für Stellen ohne Begriff des aktiven Autos (Einstellungen). */
    onlyUnsupportedBrands: ComputedRef<boolean>
} {
    const carList = computed(() => (Array.isArray(cars.value) ? cars.value : []))
    const registeredCars = computed(() => carList.value.filter(c => c.status === 'ACTIVE'))

    // Aktives Auto: dieselbe Regel wie im Dashboard - primary, sonst das erste.
    const activeCar = computed(() => carList.value.find(c => c.isPrimary) ?? carList.value[0] ?? null)
    const activeCarIsTesla = computed(() => brandIs(activeCar.value, 'tesla'))
    const activeCarIsXpeng = computed(() => brandIs(activeCar.value, 'xpeng'))
    const activeCarIsPolestar = computed(() => brandIs(activeCar.value, 'polestar'))
    const activeCarIsVwEudaBrand = computed(() => isVwEudaBrand(activeCar.value?.brand ?? ''))
    const activeCarIsAutoSyncUnsupported = computed(() => activeCar.value != null && isAutoSyncUnsupported(activeCar.value))

    const hasAnyTesla = computed(() => carList.value.some(c => brandIs(c, 'tesla')))
    const hasAnyXpeng = computed(() => carList.value.some(c => brandIs(c, 'xpeng')))

    // Nur Marken, die Smartcar wirklich bedient. Tesla (eigene Fleet-API), XPeng (EU-Data-Act-Mail)
    // und Marken ohne Connector (Zeekr, Leapmotor, Lucid, Rivian) fallen heraus.
    const smartcarCars = computed(() =>
        registeredCars.value.filter(c => autoSyncProviderFor(c) === 'SMARTCAR'),
    )
    const onlyUnsupportedBrands = computed(() => hasOnlyUnsupportedBrands(registeredCars.value))

    const showAutoSyncSection = computed(() => !activeCarIsTesla.value)

    return {
        activeCarIsTesla,
        activeCarIsXpeng,
        activeCarIsPolestar,
        activeCarIsVwEudaBrand,
        smartcarCars,
        activeCar,
        activeCarIsAutoSyncUnsupported,
        onlyUnsupportedBrands,
        showTeslaSection: hasAnyTesla,
        showAutoSyncSection,
        // Leere Garage: Pitch bleibt, sonst saehe ein frisch registrierter User eine leere Sektion.
        showSmartcarPitch: computed(() =>
            showAutoSyncSection.value && (smartcarCars.value.length > 0 || carList.value.length === 0),
        ),
        showXpengAutoSync: computed(() => showAutoSyncSection.value && hasAnyXpeng.value),
        allowLivePromo: computed(() =>
            hasAnyTesla.value && !activeCarIsTesla.value && !activeCarIsXpeng.value,
        ),
    }
}
